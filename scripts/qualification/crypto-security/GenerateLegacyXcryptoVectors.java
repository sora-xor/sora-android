import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jp.co.soramitsu.xcrypto.encryption.KeypairWithSeed;
import jp.co.soramitsu.xcrypto.encryption.ed25519.Ed25519;
import jp.co.soramitsu.xcrypto.encryption.xsalsa20poly1305.XSalsa20Poly1305Cryptor;
import jp.co.soramitsu.xcrypto.keygenerator.ScryptKeyGenerator;

/** Run ONLY against the retained old xcrypto classpath; all values are synthetic/public. */
public final class GenerateLegacyXcryptoVectors {
    static String hex(byte[] value) {
        StringBuilder text = new StringBuilder();
        for (byte b : value) text.append(String.format("%02x", b & 255));
        return text.toString();
    }
    public static void main(String[] args) throws Exception {
        System.out.println("{\"source\":\"xcrypto-1.2.7 with BC1.78.1 and EdDSA0.3.0; public synthetic fixtures\",\"vectors\":[");
        for (int index = 0; index < 16; index++) {
            byte[] seed = MessageDigest.getInstance("SHA-256").digest(("sora-public-compatibility-seed-" + index).getBytes(StandardCharsets.UTF_8));
            byte[] message = index == 0 ? new byte[0] : ("sora-public-compatibility-message-" + index).getBytes(StandardCharsets.UTF_8);
            KeypairWithSeed key = Ed25519.INSTANCE.deriveFromSeed(seed);
            byte[] signature = Ed25519.INSTANCE.sign(message, key.getPrivateKey());
            if (!Ed25519.INSTANCE.verify(message, signature, key.getPublicKey())) throw new AssertionError("old vector invalid");
            System.out.println((index == 0 ? "" : ",") + "{\"seed\":\"" + hex(seed) + "\",\"privateKey\":\"" + hex(key.getPrivateKey()) + "\",\"publicKey\":\"" + hex(key.getPublicKey()) + "\",\"message\":\"" + hex(message) + "\",\"signature\":\"" + hex(signature) + "\"}");
        }
        byte[] password = "public-test-password".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "public retained encrypted backup fixture".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = XSalsa20Poly1305Cryptor.INSTANCE.encrypt(ScryptKeyGenerator.INSTANCE.generate(password), plaintext);
        System.out.println("],\"backup\":{\"password\":\"" + hex(password) + "\",\"plaintext\":\"" + hex(plaintext) + "\",\"encrypted\":\"" + hex(encrypted) + "\"}}");
    }
}
