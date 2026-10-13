package jp.co.soramitsu.sora.crypto

import java.io.ByteArrayInputStream
import java.security.spec.ECParameterSpec
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.SignedData
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.crypto.params.DESParameters
import org.jmrtd.Util
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.SecurityInfo
import org.jmrtd.lds.SignedDataUtil
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the app's resolved crypto/KYC graph; no passport, device or live KYC service is used. */
class BouncyCastleKycCompatibilityTest {
    @Test
    fun `CMS tagged content parses with the wallet BC provider`() {
        val content = ContentInfo.getInstance(ASN1Primitive.fromByteArray(signedContent()))
        assertEquals(PKCSObjectIdentifiers.signedData, content.contentType)
        val signed = SignedData.getInstance(content.content)
        assertArrayEquals(payload, (signed.encapContentInfo.content as ASN1OctetString).octets)
    }

    @Test
    fun `JMRTD signed data parser uses available ASN1 APIs`() {
        // JMRTD 0.7.34 calls the removed ASN1TaggedObject.getObject() here, even with new bcutil.
        val signed = SignedDataUtil.readSignedData(ByteArrayInputStream(signedContent()))
        assertEquals(PKCSObjectIdentifiers.data, signed.encapContentInfo.contentType)
        assertArrayEquals(payload, (signed.encapContentInfo.content as ASN1OctetString).octets)
    }

    @Test
    fun `IDensic card access and PACE parameter operations retain their contract`() {
        val pace = PACEInfo(SecurityInfo.ID_PACE_ECDH_GM_AES_CBC_CMAC_128, 2, PACEInfo.PARAM_ID_ECP_NIST_P256_R1)
        val encoded = CardAccessFile(listOf(pace)).encoded
        val decoded = CardAccessFile(ByteArrayInputStream(encoded)).securityInfos.single() as PACEInfo
        assertEquals(pace, decoded)
        assertEquals("ECDH", PACEInfo.toKeyAgreementAlgorithm(decoded.objectIdentifier))
        assertEquals("AES", PACEInfo.toCipherAlgorithm(decoded.objectIdentifier))
        val parameters = PACEInfo.toParameterSpec(decoded.parameterId) as ECParameterSpec
        assertEquals(256, parameters.order.bitLength())
        assertTrue(Util.isPointOnCurve(parameters.generator, parameters))
    }

    @Test
    fun `BAC derives the published ICAO example keys`() {
        // ICAO Doc 9303 Part 11, Appendix D example MRZ; these are public test values.
        val seed = Util.computeKeySeed("L898902C<", "690806", "940623", "SHA-1", true)
        assertArrayEquals(hex("239AB9CB282DAF66231DC5A4DF6BFBAE"), seed)
        val encryptionKey = Util.deriveKey(seed, Util.ENC_MODE)
        val macKey = Util.deriveKey(seed, Util.MAC_MODE)
        // JMRTD retains the SHA-1 parity bits; DES ignores them. Normalize copies for the vector.
        assertArrayEquals(
            hex("AB94FDECF2674FDFB9B391F85D7F76F2AB94FDECF2674FDF"),
            encryptionKey.encoded.also(DESParameters::setOddParity),
        )
        assertArrayEquals(
            hex("7962D9ECE03D1ACD4C76089DCE1315437962D9ECE03D1ACD"),
            macKey.encoded.also(DESParameters::setOddParity),
        )
        val cipher = Util.getCipher("DESede/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, IvParameterSpec(ByteArray(8)))
        val encrypted = cipher.doFinal(hex("781723860C06C2264608F919887022120B795240CB7049B01C19B33E32804F0B"))
        assertArrayEquals(hex("72C29C2371CC9BDB65B779B8E8D37B29ECC154AA56A8799FAE2F498F76ED92F2"), encrypted)
        val mac = Util.getMac("ISO9797Alg3Mac", macKey)
        assertArrayEquals(hex("5F1448EEA8AD90A7"), mac.doFinal(Util.pad(encrypted, 8)))
    }

    @Test
    fun `JMRTD elliptic curve key encoding signing and agreement work together`() {
        val generator = Util.getKeyPairGenerator("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val alice = generator.generateKeyPair()
        val bob = generator.generateKeyPair()
        val decoded = Util.toPublicKey(Util.toSubjectPublicKeyInfo(alice.public))
        assertArrayEquals(alice.public.encoded, decoded.encoded)
        val signer = Util.getSignature("SHA256withECDSA")
        signer.initSign(alice.private)
        signer.update(payload)
        val signature = signer.sign()
        signer.initVerify(decoded)
        signer.update(payload)
        assertTrue(signer.verify(signature))
        val first = Util.getKeyAgreement("ECDH")
        first.init(alice.private)
        first.doPhase(bob.public, true)
        val second = Util.getKeyAgreement("ECDH")
        second.init(bob.private)
        second.doPhase(decoded, true)
        assertArrayEquals(first.generateSecret(), second.generateSecret())
    }

    private fun signedContent(): ByteArray {
        val data = ContentInfo(PKCSObjectIdentifiers.data, DEROctetString(payload))
        val signed = SignedData(DERSet(), data, null, null, DERSet())
        return ContentInfo(PKCSObjectIdentifiers.signedData, signed).getEncoded("DER")
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private val payload = byteArrayOf(1, 2, 3, 4)
}
