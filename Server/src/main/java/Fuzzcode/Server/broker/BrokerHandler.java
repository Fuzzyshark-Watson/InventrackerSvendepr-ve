package Fuzzcode.Server.broker;

import Fuzzcode.Server.utilities.LoggerHandler;
import Fuzzcode.Server.utilities.MessageHandler;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.MqttTopicSubscription;
import org.eclipse.paho.client.mqttv3.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class BrokerHandler implements MqttCallback {

    private static final String HOST = "0.0.0.0";
    private static final int PORT = 1883;
    private MqttClient client;

    public void startBroker() {
        LoggerHandler.log("=== START startBroker ===");
        Vertx vertx = Vertx.vertx();
        MqttServerOptions opts = new MqttServerOptions()
                .setHost(HOST)
                .setPort(PORT);

        MqttServer mqttServer = MqttServer.create(vertx, opts);

        mqttServer
                .endpointHandler(this::handleClientConnection)
                .exceptionHandler(err ->
                        LoggerHandler.log(LoggerHandler.Level.ERROR, "Broker exception: " + err.getMessage())
                );
        mqttServer.listen()
                .toCompletionStage()
                .toCompletableFuture()
                .join();

        LoggerHandler.log("MQTT Broker started on tcp://" + HOST + ":" + PORT);
        LoggerHandler.log("=== END startBroker ===");
    }
    private void handleClientConnection(MqttEndpoint endpoint) {
        LoggerHandler.log("=== START handleClientConnection ===");
        LoggerHandler.log("Client connected: " + endpoint.clientIdentifier());

        endpoint.accept(true);

        endpoint.subscribeHandler(sub -> {
            sub.topicSubscriptions().forEach(t ->
                    LoggerHandler.log("Client subscribed to: " + t.topicName()));

            List<MqttQoS> grantedQosLevels = sub.topicSubscriptions()
                    .stream()
                    .map(MqttTopicSubscription::qualityOfService)
                    .toList();

            endpoint.subscribeAcknowledge(sub.messageId(), grantedQosLevels);
        });

        endpoint.publishHandler(message -> {
            String payload = message.payload().toString(StandardCharsets.UTF_8);
            LoggerHandler.log("Inbound: " + message.topicName() + " : " + payload);

            MessageHandler.getInstance().enqueueMessage("BROKER " + payload);

            endpoint.publishAcknowledge(message.messageId());
        });
        LoggerHandler.log("=== END handleClientConnection ===");

    }
    public void startSubscriber(String clientId, String topic) throws Exception {
        LoggerHandler.log("=== START startSubscriber ===");
        client = new MqttClient("tcp://" + HOST + ":" + PORT, clientId, null);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setKeepAliveInterval(60);
        options.setCleanSession(true);

        client.setCallback(this);
        client.connect(options);

        client.subscribe(topic, 0);
        LoggerHandler.log("Subscribed to topic: " + topic);
        LoggerHandler.log("=== END startSubscriber ===");
    }
    public void publish(String topic, String msg) throws Exception {
        LoggerHandler.log("=== START publish ===");
        client.publish(topic, msg.getBytes(StandardCharsets.UTF_8), 0, false);
        LoggerHandler.log("=== END publish ===");
    }
    @Override
    public void connectionLost(Throwable cause) {
        LoggerHandler.log("=== START connectionLost ===");
        LoggerHandler.log("Connection lost!!!!");
        LoggerHandler.log("=== END connectionLost ===");
    }
    @Override
    public void messageArrived(String topic, MqttMessage message) {
        LoggerHandler.log("=== START messageArrived ===");
        MessageHandler.getInstance().enqueueMessage("BROKER " + new String(message.getPayload()));
        LoggerHandler.log("=== END messageArrived ===");
    }
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) { }
    public void stopBroker() {
        LoggerHandler.log("=== START stopBroker ===");
        if (client != null) {
            try {
                System.out.println("Stopping Broker...");
                client.disconnect();
            } catch (Exception e) {
                System.err.println("Failed to stop Broker: " + e.getMessage());
            }
        }
        LoggerHandler.log("=== END stopBroker ===");
    }
}
