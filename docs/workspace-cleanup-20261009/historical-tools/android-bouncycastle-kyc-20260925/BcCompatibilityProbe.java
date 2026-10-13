import java.io.ByteArrayInputStream;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.cms.SignedData;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.jmrtd.lds.SignedDataUtil;
public class BcCompatibilityProbe {
  interface Checked { void run() throws Exception; }
  static void probe(String name, Checked operation) throws Exception {
    try { operation.run(); System.out.println(name + ": PASS"); }
    catch (LinkageError e) { System.out.println(name + ": " + e); e.printStackTrace(System.out); }
  }
  public static void main(String[] args) throws Exception {
    ContentInfo data = new ContentInfo(PKCSObjectIdentifiers.data, new DEROctetString(new byte[]{1,2,3}));
    SignedData signed = new SignedData(new DERSet(), data, null, null, new DERSet());
    byte[] encoded = new ContentInfo(PKCSObjectIdentifiers.signedData, signed).getEncoded("DER");
    probe("bcutil ContentInfo parse", () -> {
      ContentInfo parsed = ContentInfo.getInstance(ASN1Primitive.fromByteArray(encoded));
      if (!PKCSObjectIdentifiers.signedData.equals(parsed.getContentType())) throw new AssertionError("content OID");
    });
    probe("JMRTD SignedDataUtil.readSignedData", () -> {
      SignedData parsed = SignedDataUtil.readSignedData(new ByteArrayInputStream(encoded));
      if (!PKCSObjectIdentifiers.data.equals(parsed.getEncapContentInfo().getContentType())) throw new AssertionError("eContent OID");
    });
  }
}
