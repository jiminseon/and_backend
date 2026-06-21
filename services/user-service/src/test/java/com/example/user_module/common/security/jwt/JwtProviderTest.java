package com.example.user_module.common.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    @Test
    void issuesAndValidatesRsaSignedAccessToken() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        JwtProperties properties = new JwtProperties();
        properties.setPrivateKey(new ByteArrayResource(pem("PRIVATE KEY", keyPair.getPrivate().getEncoded())));
        properties.setPublicKey(new ByteArrayResource(pem("PUBLIC KEY", keyPair.getPublic().getEncoded())));
        properties.setIssuer("and-user-service");
        properties.setAudience("and-services");
        properties.setAccessExpirationTime(60_000);
        properties.setRefreshExpirationTime(120_000);

        JwtProvider provider = new JwtProvider(properties);
        provider.init();

        String token = provider.generateAccessToken(42L);

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getId(token)).isEqualTo("42");
    }

    private byte[] pem(String type, byte[] der) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return ("-----BEGIN " + type + "-----\n" + encoded + "\n-----END " + type + "-----\n").getBytes();
    }
}
