package br.com.fastcheckin.domain.guest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PersonalDataCipher {

    private static final String VERSION_PREFIX = "v1:";
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final byte[] key;
    private final SecureRandom secureRandom = new SecureRandom();

    public PersonalDataCipher(@Value("${app.personal-data.encryption-key:}") String encodedKey) {
        try {
            this.key = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("A chave de criptografia deve estar em Base64.", exception);
        }
        if (key.length != 32) {
            throw new IllegalStateException("PERSONAL_DATA_ENCRYPTION_KEY deve codificar exatamente 32 bytes.");
        }
    }

    public String encrypt(String plaintext) {
        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = newCipher(Cipher.ENCRYPT_MODE, nonce);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Não foi possível cifrar os dados pessoais.", exception);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(VERSION_PREFIX)) {
            throw new IllegalArgumentException("Formato de dados pessoais cifrados inválido ou não suportado.");
        }

        final byte[] payload;
        try {
            payload = Base64.getDecoder().decode(ciphertext.substring(VERSION_PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Conteúdo de dados pessoais cifrados inválido.", exception);
        }
        if (payload.length <= NONCE_LENGTH_BYTES) {
            throw new IllegalArgumentException("Conteúdo de dados pessoais cifrados incompleto.");
        }

        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        byte[] encrypted = new byte[payload.length - NONCE_LENGTH_BYTES];
        System.arraycopy(payload, 0, nonce, 0, nonce.length);
        System.arraycopy(payload, nonce.length, encrypted, 0, encrypted.length);
        try {
            Cipher cipher = newCipher(Cipher.DECRYPT_MODE, nonce);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Não foi possível decifrar os dados pessoais.", exception);
        }
    }

    private Cipher newCipher(int mode, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
        return cipher;
    }
}
