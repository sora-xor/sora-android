import java.math.BigInteger;
import jp.co.soramitsu.xcrypto.encryption.ed25519.Ed25519;

/** The old implementation accepts S+L; the replacement must print false. Public RFC8032 vector. */
public final class SignatureMalleabilityProbe {
    static byte[] hex(String text) {
        byte[] value = new byte[text.length() / 2];
        for (int i = 0; i < value.length; i++) value[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return value;
    }
    public static void main(String[] args) {
        byte[] seed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60");
        byte[] pub = Ed25519.INSTANCE.deriveFromSeed(seed).getPublicKey();
        byte[] signature = Ed25519.INSTANCE.sign(new byte[0], seed);
        byte[] scalar = new byte[32];
        for (int i = 0; i < 32; i++) scalar[31-i] = signature[32+i];
        BigInteger order = new BigInteger("1000000000000000000000000000000014def9dea2f79cd65812631a5cf5d3ed",16);
        byte[] altered = new BigInteger(1, scalar).add(order).toByteArray();
        for (int i = 0; i < 32; i++) signature[32+i] = i < altered.length ? altered[altered.length - 1 - i] : 0;
        System.out.println(Ed25519.INSTANCE.verify(new byte[0], signature, pub));
    }
}
