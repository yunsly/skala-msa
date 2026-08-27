package com.lecture.course.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretMetadataConverterTest {

    private final SecretMetadataConverter converter = new SecretMetadataConverter();

    @BeforeEach
    void setEncryptionKey() {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (index + 1);
        }
        System.setProperty(
                SecretMetadataConverter.KEY_PROPERTY,
                Base64.getEncoder().encodeToString(key)
        );
    }

    @AfterEach
    void clearEncryptionKey() {
        System.clearProperty(SecretMetadataConverter.KEY_PROPERTY);
    }

    @Test
    void encryptsWithoutLeavingPlaintextAndDecrypts() {
        String plaintext = "demo-openai-key-project-a";

        String encrypted = converter.convertToDatabaseColumn(plaintext);

        assertThat(encrypted)
                .startsWith("ENC:v1:")
                .doesNotContain(plaintext);
        assertThat(converter.convertToEntityAttribute(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void usesDifferentIvForTheSamePlaintext() {
        String plaintext = "demo-github-key-project-a";

        String first = converter.convertToDatabaseColumn(plaintext);
        String second = converter.convertToDatabaseColumn(plaintext);

        assertThat(first).isNotEqualTo(second);
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo(plaintext);
        assertThat(converter.convertToEntityAttribute(second)).isEqualTo(plaintext);
    }

    @Test
    void rejectsInvalidKeyLength() {
        System.setProperty(
                SecretMetadataConverter.KEY_PROPERTY,
                Base64.getEncoder().encodeToString(new byte[16])
        );

        assertThatThrownBy(() -> converter.convertToDatabaseColumn("demo-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }
}
