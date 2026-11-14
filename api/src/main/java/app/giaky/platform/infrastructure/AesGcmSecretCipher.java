package app.giaky.platform.infrastructure;

import app.giaky.platform.domain.SecretCipher;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM over the secrets in platform settings.
 *
 * <p>Format: {@code v1:} + base64(12-byte IV ‖ ciphertext ‖ 16-byte tag). The prefix leaves room
 * for a second scheme without guessing which one a stored value used. GCM authenticates as well as
 * encrypts, so a ciphertext edited in the database fails to decrypt instead of decrypting to
 * garbage.
 *
 * <p>The key comes from {@code SETTINGS_ENCRYPTION_KEY} and has no default outside {@code dev} and
 * {@code test}, so a deployment without one fails at startup. That is the same rule as {@code
 * JWT_SECRET}. Rotating it makes the stored SMTP password unreadable, and the remedy is to type it
 * in again; the mail settings screen says so.
 */
@Component
public class AesGcmSecretCipher implements SecretCipher {

    private static final String PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretCipher(@Value("${app.platform.settings-encryption-key}") String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException notBase64) {
            throw new IllegalStateException("SETTINGS_ENCRYPTION_KEY must be base64", notBase64);
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "SETTINGS_ENCRYPTION_KEY must decode to exactly 32 bytes (AES-256); got "
                            + raw.length
                            + ". Generate one with: openssl rand -base64 32");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException impossible) {
            // AES-GCM is mandatory in every Java runtime; reaching here is a broken JVM.
            throw new IllegalStateException("AES-GCM is unavailable", impossible);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
            throw new IllegalStateException("The stored secret is in an unknown format.");
        }
        try {
            byte[] in = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            if (in.length <= IV_BYTES) {
                throw new IllegalStateException("The stored secret is truncated.");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException unreadable) {
            throw new IllegalStateException(
                    "The stored SMTP password cannot be decrypted with the current"
                            + " SETTINGS_ENCRYPTION_KEY. Enter the password again.",
                    unreadable);
        }
    }
}
