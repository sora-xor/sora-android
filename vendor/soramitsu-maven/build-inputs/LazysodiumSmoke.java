import com.goterl.lazysodium.LazySodiumAndroid;
import com.goterl.lazysodium.SodiumAndroid;
import com.goterl.lazysodium.utils.Key;
import java.util.Arrays;

public class LazysodiumSmoke {
    public static void main(String[] ignored) throws Exception {
        System.setProperty("jna.library.path", System.getenv("SODIUM_DIR"));
        System.setProperty("jna.nosys", "true");
        LazySodiumAndroid sodium = new LazySodiumAndroid(new SodiumAndroid());
        byte[] nonce = new byte[24];
        byte[] secret = new byte[32];
        for (int i = 0; i < nonce.length; i++) nonce[i] = (byte) (0x40 + i);
        for (int i = 0; i < secret.length; i++) secret[i] = (byte) (0xa0 + i);
        Key key = Key.fromBytes(secret);
        String ciphertext = sodium.cryptoSecretBoxEasy("sora-wallet-lazysodium", nonce, key);
        String plaintext = sodium.cryptoSecretBoxOpenEasy(ciphertext, nonce, key);
        if (!"sora-wallet-lazysodium".equals(plaintext)) throw new AssertionError(plaintext);
        byte[] badNonce = Arrays.copyOf(nonce, nonce.length);
        badNonce[0] ^= 1;
        try {
            sodium.cryptoSecretBoxOpenEasy(ciphertext, badNonce, key);
            throw new AssertionError("tampered nonce accepted");
        } catch (com.goterl.lazysodium.exceptions.SodiumException expected) {
            // Authenticated decryption must reject the modified nonce.
        }
        System.out.println("LAZYSODIUM_JNA_SMOKE_PASS " + ciphertext);
    }
}
