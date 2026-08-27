package com.lecture.course.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Converter
public class SecretMetadataConverter implements AttributeConverter<String, String> {

    public static final String KEY_ENV = "CREDENTIAL_ENCRYPTION_KEY";
    public static final String KEY_PROPERTY = "credential.encryption.key";

    private static final String PREFIX = "ENC:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int AES_256_KEY_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    resolveKey(),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv)
            );
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();

            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Credential metadata 암호화에 실패했습니다.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String encryptedValue) {
        if (encryptedValue == null) {
            return null;
        }
        if (!encryptedValue.startsWith(PREFIX)) {
            throw new IllegalStateException("지원하지 않는 Credential metadata 형식입니다.");
        }

        try {
            byte[] payload = Base64.getDecoder().decode(encryptedValue.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH_BYTES) {
                throw new IllegalStateException("Credential metadata 암호문이 손상되었습니다.");
            }

            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    resolveKey(),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv)
            );
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Credential metadata 복호화에 실패했습니다.", e);
        }
    }

    private SecretKey resolveKey() {
        String configuredKey = System.getProperty(KEY_PROPERTY);
        if (configuredKey == null || configuredKey.isBlank()) {
            configuredKey = System.getenv(KEY_ENV);
        }
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalStateException(
                    KEY_ENV + " 환경변수에 Base64 인코딩된 32바이트 키가 필요합니다."
            );
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configuredKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(KEY_ENV + " 값은 올바른 Base64 형식이어야 합니다.", e);
        }
        if (decoded.length != AES_256_KEY_BYTES) {
            throw new IllegalStateException(KEY_ENV + " 값은 디코딩 후 32바이트여야 합니다.");
        }
        return new SecretKeySpec(decoded, "AES");
    }
}
