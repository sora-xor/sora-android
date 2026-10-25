import java.util.Arrays;
import jp.co.soramitsu.xcrypto.encryption.xsalsa20poly1305.XSalsa20Poly1305Cryptor;
import jp.co.soramitsu.xcrypto.keygenerator.ScryptKeyGenerator;

/** Public fixture arguments: encrypted envelope, password, expected plaintext, all hex. */
public final class VerifyEncryptedBackupFixture {
    public static void main(String[] args) {
        byte[] encrypted = SignatureMalleabilityProbe.hex(args[0]);
        byte[] password = SignatureMalleabilityProbe.hex(args[1]);
        byte[] expected = SignatureMalleabilityProbe.hex(args[2]);
        byte[] decrypted = XSalsa20Poly1305Cryptor.INSTANCE.decrypt(ScryptKeyGenerator.INSTANCE.generate(encrypted, password));
        if (!Arrays.equals(expected, decrypted)) throw new AssertionError("Backup compatibility failed");
        System.out.println("backup-compatible");
    }
}
