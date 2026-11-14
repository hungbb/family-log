package app.giaky.platform.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesGcmSecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY;

    static {
        byte[] other = new byte[32];
        other[0] = 1;
        OTHER_KEY = Base64.getEncoder().encodeToString(other);
    }

    private final AesGcmSecretCipher cipher = new AesGcmSecretCipher(KEY);

    @Test
    @DisplayName("round-trips, and the stored form does not contain the secret")
    void roundTrips() {
        String sealed = cipher.encrypt("hunter2-smtp-password");

        assertThat(sealed).startsWith("v1:").doesNotContain("hunter2");
        assertThat(cipher.decrypt(sealed)).isEqualTo("hunter2-smtp-password");
    }

    @Test
    @DisplayName("a fresh IV each time: the same secret never encrypts to the same text")
    void freshIvEachTime() {
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    @DisplayName("a ciphertext edited in the database fails instead of decrypting to garbage")
    void tamperingIsDetected() {
        String sealed = cipher.encrypt("secret");
        byte[] raw = Base64.getDecoder().decode(sealed.substring(3));
        raw[raw.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a rotated key cannot read the old value, and says what to do about it")
    void rotatedKey() {
        String sealed = cipher.encrypt("secret");

        assertThatThrownBy(() -> new AesGcmSecretCipher(OTHER_KEY).decrypt(sealed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Enter the password again");
    }

    @Test
    @DisplayName("a key that is not 32 bytes stops startup")
    void wrongKeyLength() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new AesGcmSecretCipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
