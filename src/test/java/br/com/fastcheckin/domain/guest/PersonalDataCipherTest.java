package br.com.fastcheckin.domain.guest;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonalDataCipherTest {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private final PersonalDataCipher cipher = new PersonalDataCipher(KEY);

    @Test
    void encryptsWithDifferentNoncesAndCanDecryptBothValues() {
        String first = cipher.encrypt("dados sintéticos");
        String second = cipher.encrypt("dados sintéticos");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("dados sintéticos");
        assertThat(cipher.decrypt(second)).isEqualTo("dados sintéticos");
    }

    @Test
    void rejectsTamperedCiphertext() {
        String encrypted = cipher.encrypt("dados sintéticos");
        byte[] payload = Base64.getDecoder().decode(encrypted.substring("v1:".length()));
        payload[12] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(payload);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Não foi possível decifrar os dados pessoais.");
    }

    @Test
    void rejectsEncryptionKeysThatAreNotThirtyTwoBytes() {
        assertThatThrownBy(() -> new PersonalDataCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PERSONAL_DATA_ENCRYPTION_KEY deve codificar exatamente 32 bytes.");
    }
}
