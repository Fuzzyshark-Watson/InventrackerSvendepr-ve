package Fuzzcode.utilities;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;

import java.util.Date;

public class SystemClientTokenGenerator {
    public static void main(String[] args) throws Exception {
        byte[] secret = "e3f7a9c4b8d1f0a2c6e9d4b3f7a8c1e2d3f4b5a6c7d8e9f0a1b2c3d4e5f6a7b8".getBytes();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("system-client")           // Issuer
                .subject("system-client")          // Subject
                .audience("ws-service")            // Audience
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000)) // 1 hour
                .claim("scope", "read write")      // Optional scopes
                .build();

        JWSSigner signer = new MACSigner(secret);
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJWT.sign(signer);

        String token = signedJWT.serialize();
        System.out.println("System Client JWT: " + token);
    }
}