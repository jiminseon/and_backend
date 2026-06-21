package com.example.common_service.security;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RsaKeyLoader {

    private RsaKeyLoader() {
    }

    public static RSAPublicKey readPublicKey(Resource resource) {
        try {
            return (RSAPublicKey) keyFactory().generatePublic(new X509EncodedKeySpec(readDer(resource)));
        } catch (InvalidKeySpecException exception) {
            throw new IllegalStateException("Invalid RSA public key: " + resource, exception);
        }
    }

    public static RSAPrivateKey readPrivateKey(Resource resource) {
        try {
            return (RSAPrivateKey) keyFactory().generatePrivate(new PKCS8EncodedKeySpec(readDer(resource)));
        } catch (InvalidKeySpecException exception) {
            throw new IllegalStateException("Invalid RSA private key: " + resource, exception);
        }
    }

    private static KeyFactory keyFactory() {
        try {
            return KeyFactory.getInstance("RSA");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("RSA is not supported by this JVM", exception);
        }
    }

    private static byte[] readDer(Resource resource) {
        try {
            String pem = new String(resource.getInputStream().readAllBytes());
            String base64 = pem
                    .replaceAll("-----BEGIN [^-]+-----", "")
                    .replaceAll("-----END [^-]+-----", "")
                    .replaceAll("\\s", "");
            return Base64.getDecoder().decode(base64);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to read RSA key from " + resource, exception);
        }
    }
}
