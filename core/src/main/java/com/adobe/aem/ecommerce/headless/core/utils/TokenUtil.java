package com.adobe.aem.ecommerce.headless.core.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class TokenUtil {

    private static final String SECRET_KEY = "MY_SUPER_SECRET_KEY_123";

    public static String generateToken(String email) {

        long timestamp = System.currentTimeMillis();

        String payload = email + ":" + timestamp;

        String encodedPayload = Base64.getEncoder()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        String signature = sign(payload);

        return encodedPayload + "." + signature;
    }

    public static boolean validateToken(String token) {

        try {

            String[] parts = token.split("\\.");

            if (parts.length != 2) {
                return false;
            }

            String encodedPayload = parts[0];
            String receivedSignature = parts[1];

            String payload = new String(
                    Base64.getDecoder().decode(encodedPayload),
                    StandardCharsets.UTF_8
            );

            String expectedSignature = sign(payload);

            return expectedSignature.equals(receivedSignature);

        } catch (Exception e) {
            return false;
        }
    }

    public static String extractEmail(String token) {

        try {

            String[] parts = token.split("\\.");

            String payload = new String(
                    Base64.getDecoder().decode(parts[0]),
                    StandardCharsets.UTF_8
            );

            return payload.split(":")[0];

        } catch (Exception e) {
            return null;
        }
    }

    private static String sign(String data) {

        try {

            Mac sha256Hmac = Mac.getInstance("HmacSHA256");

            SecretKeySpec secretKey = new SecretKeySpec(
                    SECRET_KEY.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );

            sha256Hmac.init(secretKey);

            byte[] signedBytes = sha256Hmac.doFinal(
                    data.getBytes(StandardCharsets.UTF_8)
            );

            StringBuilder sb = new StringBuilder();

            for (byte b : signedBytes) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}