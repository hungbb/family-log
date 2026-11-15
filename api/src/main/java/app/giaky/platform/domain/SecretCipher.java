package app.giaky.platform.domain;

/**
 * Encrypts the secrets a super admin types into a settings screen, so the database never holds them
 * in the clear.
 *
 * <p>The key lives in the environment, never beside the data: a database dump or backup on its own
 * does not hand over the mailbox.
 */
public interface SecretCipher {

    String encrypt(String plaintext);

    /**
     * @throws IllegalStateException the ciphertext was not produced with the current key, which
     *     means the key was rotated without re-entering the secret
     */
    String decrypt(String ciphertext);
}
