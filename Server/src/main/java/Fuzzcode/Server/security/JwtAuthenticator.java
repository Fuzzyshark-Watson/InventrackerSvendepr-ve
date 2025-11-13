package Fuzzcode.Server.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.*;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.*;

import java.net.URL;
import java.util.*;

public class JwtAuthenticator {
    public record Config(
            String issuer,
            String audience,
            ConfigurableJWTProcessor<SecurityContext> processor
    ) {}
    private final Map<String, Config> byIssuer;

    private final String hmacIssuer;
    private final String hmacAudience;
    private final byte[] hmacSecret;

    private JwtAuthenticator(Map<String, Config> byIssuer,
                             String hmacIssuer,
                             String hmacAudience,
                             byte[] hmacSecret) {
        this.byIssuer = byIssuer;
        this.hmacIssuer = hmacIssuer;
        this.hmacAudience = hmacAudience;
        this.hmacSecret = hmacSecret;
    }
    public static JwtAuthenticator buildDefault() {
        try {
            Config auth0 = buildRemote(
                    "https://your-tenant.auth0.com/",
                    "your-audience",
                    "https://your-tenant.auth0.com/.well-known/jwks.json"
            );
            Config keycloak = buildRemote(
                    "https://id.example.com/realms/app/",
                    "ws-service",
                    "https://id.example.com/realms/app/protocol/openid-connect/certs"
            );
            Map<String, Config> map = new HashMap<>();
            map.put(auth0.issuer(), auth0);
            map.put(keycloak.issuer(), keycloak);

            return new JwtAuthenticator(map, null, null, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public AuthContext verify(String token) throws Exception {
        SignedJWT jwt = SignedJWT.parse(token);
        String iss = jwt.getJWTClaimsSet().getIssuer();
        Config cfg = byIssuer.get(iss);
        if (cfg == null) throw new BadJWTException("unknown issuer");

        var claims = cfg.processor.process(jwt, null);

        List<String> aud = claims.getAudience();
        if (aud == null || aud.stream().noneMatch(a -> a.equals(cfg.audience)))
            throw new BadJWTException("bad audience");

        Set<String> scopes = extractScopes(claims);

        return new AuthContext(
                iss,
                claims.getSubject(),
                scopes,
                cfg.audience(),
                claims.getExpirationTime().toInstant()
        );
    }
    private static Set<String> extractScopes(com.nimbusds.jwt.JWTClaimsSet claims) {
        Object scope = claims.getClaim("scope");
        if (scope instanceof String s) return Set.of(s.split("\\s+"));
        Object scp = claims.getClaim("scp");
        if (scp instanceof List<?> l) {
            Set<String> r = new HashSet<>();
            l.forEach(v -> r.add(String.valueOf(v)));
            return r;
        }
        return Set.of();
    }
    private static Config buildRemote(String issuer, String audience, String jwksUrl) throws Exception {
        var jwks = new RemoteJWKSet<SecurityContext>(new URL(jwksUrl));
        var selector = new JWSVerificationKeySelector<SecurityContext>(JWSAlgorithm.RS256, jwks);

        var proc = new DefaultJWTProcessor<SecurityContext>();
        proc.setJWSKeySelector(selector);

        proc.setJWTClaimsSetVerifier((claims, ctx) -> {
            Date now = new Date();
            Date exp = claims.getExpirationTime();
            if (exp == null || exp.before(new Date(now.getTime() - 300_000)))
                throw new BadJWTException("expired");
            Date nbf = claims.getNotBeforeTime();
            if (nbf != null && nbf.after(new Date(now.getTime() + 300_000)))
                throw new BadJWTException("not yet valid");
            if (!issuer.equals(claims.getIssuer()))
                throw new BadJWTException("issuer mismatch");
        });

        return new Config(issuer, audience, proc);
    }
    public static JwtAuthenticator buildHmacForTests(String issuer, String audience, byte[] secret) {
        var jwkSource = new ImmutableSecret<SecurityContext>(secret);
        var selector  = new JWSVerificationKeySelector<SecurityContext>(JWSAlgorithm.HS256, jwkSource);
        var proc      = new DefaultJWTProcessor<SecurityContext>();
        proc.setJWSKeySelector(selector);
        proc.setJWTClaimsSetVerifier((claims, ctx) -> {
            var now = new Date();
            var exp = claims.getExpirationTime();
            if (exp == null || exp.before(new Date(now.getTime() - 300_000L)))
                throw new BadJWTException("expired");
            if (!issuer.equals(claims.getIssuer()))
                throw new BadJWTException("issuer mismatch");
        });
        var cfg = new Config(issuer, audience, proc);

        return new JwtAuthenticator(Map.of(issuer, cfg), issuer, audience, secret);
    }

    public String issueToken(String subject, String scope, int ttlSeconds) {
        if (hmacSecret == null || hmacIssuer == null || hmacAudience == null) {
            throw new IllegalStateException(
                    "This JwtAuthenticator is not configured for issuing HMAC tokens. " +
                            "Use buildHmacForTests(...) to create an issuing authenticator."
            );
        }
        try {
            Date now = new Date();
            Date exp = new Date(now.getTime() + ttlSeconds * 1000L);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(hmacIssuer)
                    .subject(subject)
                    .audience(Collections.singletonList(hmacAudience))
                    .claim("scope", scope)
                    .issueTime(now)
                    .expirationTime(exp)
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(hmacSecret));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to issue JWT", e);
        }
    }
    public static String issueHmacTestToken(String issuer, String audience, byte[] secret, String subject,
                                            String scope, long ttlSeconds ) throws Exception {
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttlSeconds * 1000L);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .audience(Collections.singletonList(audience))
                .claim("scope", scope)         // <-- string; your extractScopes() supports this
                .issueTime(now)
                .expirationTime(exp)
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret));
        return jwt.serialize();
    }
}
