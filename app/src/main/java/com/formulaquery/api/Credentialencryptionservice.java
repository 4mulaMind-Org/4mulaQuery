package com.formulaquery.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Encrypts/decrypts external-database passwords before they're stored
 * in MongoDB, so credentials are never persisted in plain text.
 *
 * Uses AES with a key derived from an environment-provided secret
 * (DB_CONN_ENCRYPTION_KEY) — same pattern as the app's other secrets
 * (GEMINI_API_KEY, MONGODB_URI, etc.), never hardcoded.
 */
@Service
public class CredentialEncryptionService {

    @Value("${db.connection.encryption.key}")
    private String rawKey;

    private static final String ALGORITHM = "AES";

    private SecretKeySpec keySpec() throws Exception {
        // Derive a fixed 256-bit key from whatever string secret is provided,
        // so the raw env var doesn't need to be an exact-length AES key.
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha.digest(rawKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    public String encrypt(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec());
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public String decrypt(String encryptedText) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec());
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}