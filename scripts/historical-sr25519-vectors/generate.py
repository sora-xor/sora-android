#!/usr/bin/env python3
"""Regenerates the historical sr25519 key vectors used by the wallet-upgrade tests.

    cargo build --release --offline            (in this directory)
    python3 generate.py > ../../feature_account_impl/src/sharedTest/java/jp/co/soramitsu/feature_account_impl/historical/HistoricalSr25519Vectors.kt

The output is a Kotlin file of constants shared by the JVM tests and the device tests of
:feature_account_impl (src/sharedTest/java).

What produces each value:
  * phrase -> entropy -> seed: BIP-39 word list (read from the vendored x-crypto sources) and
    PBKDF2-HMAC-SHA512(entropy, "mnemonic" + passphrase, 2048), the Substrate rule every release
    used (fearless-utils Bip39.generateSeed, x-crypto SeedCreator.deriveSeed). Python's hashlib is
    an independent implementation; the result is checked against the vectors fearless-utils
    asserts in its own tests.
  * seed -> private key, nonce, public key, ed25519 expanded secret, signature: the Rust tool in
    this directory, which calls schnorrkel 0.9.1 exactly as the released `sr25519-java` JNI
    library does (see src/main.rs).
  * addresses: SS58 with prefix 69 (SORA) and 42 (generic), blake2b-512 checksum.

Every field is deterministic except `signature` (sr25519 signatures are randomised); a
regenerated file differs only there.
"""
import hashlib
import json
import os
import re
import subprocess
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
SOURCES_JAR = os.path.join(
    REPO, "vendor/soramitsu-maven/jp/co/soramitsu/xcrypto/1.2.7/xcrypto-1.2.7-sources.jar"
)
TOOL = os.path.join(HERE, "target/release/historical-sr25519-vectors")
MESSAGE = "sora-wallet-historical-sr25519-vector-v1"

# (id, phrase, where the phrase comes from)
PHRASES = [
    ("fearless-sr25519HDKD",
     "bottom drive obey lake curtain smoke basket hold race lonely fit walk",
     "fearless-utils-Android fearless-utils/src/androidTest/resources/crypto/sr25519HDKD.json"),
    ("fearless-Bip39Test",
     "clean wait kiss trip humor pledge useless survey prevent toddler express knock",
     "fearless-utils-Android@68cf19bc Bip39Test"),
    ("twelve-words",
     "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
     "WalletUpgradeScenarioTest.TWELVE_WORDS"),
    ("fifteen-words",
     "adapt explain ecology dinner glare old unfair empty expect road off suggest dash dolphin case",
     "WalletUpgradeScenarioTest.FIFTEEN_WORDS"),
    ("eighteen-words",
     "fabric expire sight menu twenty pretty piano eager weasel unit name submit buffalo lemon "
     "drift prefer glide cat",
     "WalletUpgradeScenarioTest.EIGHTEEN_WORDS"),
    ("twenty-one-words",
     "adapt explain ecology dinner glare old unfair empty expect road off suggest dash dolphin "
     "captain version oval census uncover inform apology",
     "WalletUpgradeScenarioTest.TWENTY_ONE_WORDS"),
    ("twenty-four-words",
     "abandon amount liar amount expire adjust cage candy arch gather drum bullet absurd math era "
     "live bid rhythm alien crouch range attend journey unaware",
     "WalletUpgradeScenarioTest.TWENTY_FOUR_WORDS"),
]
RAW_SEEDS = [
    ("raw-seed", "cf0010cf0010cf0010cf0010cf0010cfcf0010cf0010cf0010cf0010cf0010cf",
     "WalletUpgradeScenarioTest.RAW_SEED"),
]

# Values asserted by the historical libraries' own tests, or by earlier wallet tests.
EXPECT = {
    "fearless-sr25519HDKD": {
        "publicKey": "46ebddef8cd9bb167dc30878d7113b7e168e6f0646beffd77d69d39bad76b47a",
        "genericAddress": "5DfhGyQdFobKM8NsWvEeAKk5EQQgYe9AydgJ7rMB6E1EqRzV",
    },
    "fearless-Bip39Test": {"entropy": "2a5ecdeb7466f14d3c06d5aa5c6d433d"},
    "twelve-words": {"soraAddress": "cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m"},
    "twenty-four-words": {"soraAddress": "cnTAiyqa6dqfhfX5HhJfmPdd56sum2WXFvg1i1UgRReB336yz"},
}
# fearless-utils Bip39Test.generateSeed: "abandon ... about" with passphrase "Substrate".
FEARLESS_SEED_WITH_PASSPHRASE = "44e9d125f037ac1d51f0a7d3649689d422c2af8b1ec8e00d71db4d7bf6d127e3"


def word_list():
    with zipfile.ZipFile(SOURCES_JAR) as jar:
        text = jar.read("jp/co/soramitsu/xcrypto/seed/EnglishWordList.kt").decode("utf-8")
    words = re.findall(r'^\s*"([a-z]+)",?\s*$', text, flags=re.M)
    assert len(words) == 2048, len(words)
    return words


def entropy_of(phrase, words):
    bits = "".join(format(words.index(word), "011b") for word in phrase.split(" "))
    entropy_bits = len(bits) * 32 // 33
    entropy = int(bits[:entropy_bits], 2).to_bytes(entropy_bits // 8, "big")
    checksum = format(int.from_bytes(hashlib.sha256(entropy).digest(), "big"), "0256b")
    assert bits[entropy_bits:] == checksum[: len(bits) - entropy_bits], phrase
    return entropy


def substrate_seed(entropy, passphrase=""):
    return hashlib.pbkdf2_hmac("sha512", entropy, ("mnemonic" + passphrase).encode(), 2048, 64)


B58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"


def base58(data):
    number = int.from_bytes(data, "big")
    out = ""
    while number:
        number, rest = divmod(number, 58)
        out = B58[rest] + out
    return "1" * (len(data) - len(data.lstrip(b"\0"))) + out


def ss58(public_key, prefix):
    if prefix < 64:
        head = bytes([prefix])
    else:
        head = bytes([((prefix & 0xFC) >> 2) | 0x40, (prefix >> 8) | ((prefix & 0x03) << 6)])
    body = head + public_key
    checksum = hashlib.blake2b(b"SS58PRE" + body, digest_size=64).digest()[:2]
    return base58(body + checksum)


def main():
    words = word_list()
    entries = []
    for ident, phrase, source in PHRASES:
        entropy = entropy_of(phrase, words)
        full = substrate_seed(entropy)
        entries.append({
            "id": ident, "source": source, "phrase": phrase, "entropy": entropy.hex(),
            "fullSeed": full.hex(), "seed": full[:32].hex(),
        })
    for ident, seed, source in RAW_SEEDS:
        entries.append({"id": ident, "source": source, "phrase": None, "entropy": None,
                        "fullSeed": None, "seed": seed})

    result = subprocess.run(
        [TOOL], input="\n".join(entry["seed"] for entry in entries) + "\n",
        capture_output=True, text=True, check=True,
    )
    lines = result.stdout.strip().split("\n")
    assert len(lines) == len(entries)
    for entry, line in zip(entries, lines):
        seed, private_key, nonce, public_key, ed25519, signature = line.split(" ")
        assert seed == entry["seed"]
        entry.update({
            "privateKey": private_key, "nonce": nonce, "publicKey": public_key,
            "ed25519ExpandedSecret": ed25519, "signature": signature,
            "soraAddress": ss58(bytes.fromhex(public_key), 69),
            "genericAddress": ss58(bytes.fromhex(public_key), 42),
        })
        for field, value in EXPECT.get(entry["id"], {}).items():
            assert entry[field] == value, (entry["id"], field, entry[field], value)

    with_passphrase = substrate_seed(entropy_of(PHRASES[2][1], words), "Substrate")[:32].hex()
    assert with_passphrase == FEARLESS_SEED_WITH_PASSPHRASE, with_passphrase

    out = sys.stdout
    out.write(KOTLIN_HEADER)
    for entry in entries:
        out.write("        HistoricalSr25519Vector(\n")
        for field in FIELDS:
            value = entry[field]
            literal = "null" if value is None else json.dumps(value)
            if len(literal) > 80:
                parts = [literal[1:-1][i:i + 64] for i in range(0, len(literal) - 2, 64)]
                literal = " +\n                ".join('"%s"' % part for part in parts)
            out.write("            %s = %s,\n" % (field, literal))
        out.write("        ),\n")
    out.write("    )\n}\n")


FIELDS = [
    "id", "source", "phrase", "entropy", "fullSeed", "seed", "privateKey", "nonce", "publicKey",
    "ed25519ExpandedSecret", "signature", "soraAddress", "genericAddress",
]

KOTLIN_HEADER = '''package jp.co.soramitsu.feature_account_impl.historical

/**
 * sr25519 key vectors made by the native code path of the released SORA Wallet apps.
 *
 * GENERATED by scripts/historical-sr25519-vectors/generate.py. Do not edit by hand.
 *
 * - `seed` to `privateKey`, `nonce`, `publicKey`, `ed25519ExpandedSecret` and `signature`:
 *   schnorrkel 0.9.1 (curve25519-dalek 2.1.3, merlin 2.0.1, sha2 0.8.2), called exactly as the
 *   `sr25519-java` JNI library of fearless-utils, shared-features and x-crypto 1.2.7 calls it:
 *   `MiniSecretKey::from_bytes(seed).expand_to_keypair(ExpansionMode::Ed25519).to_bytes()`,
 *   bytes 0..32 private key, 32..64 nonce, 64..96 public key; `SecretKey::to_ed25519_bytes()`
 *   for the JSON export; `sign_simple("substrate", message, public)`.
 * - `phrase` to `entropy`, `fullSeed` (64 bytes) and `seed` (its first 32 bytes): BIP-39 and
 *   PBKDF2-HMAC-SHA512(entropy, "mnemonic", 2048), checked against the values fearless-utils
 *   asserts in its own tests (Bip39Test, sr25519HDKD.json).
 * - `soraAddress` is SS58 with prefix 69, `genericAddress` with prefix 42.
 *
 * `signature` signs [SIGNED_MESSAGE]; sr25519 signatures are randomised, so it is the only
 * field that changes when the file is regenerated.
 */
class HistoricalSr25519Vector(
    val id: String,
    val source: String,
    /** Null for a vector that starts from a raw seed. */
    val phrase: String?,
    val entropy: String?,
    /** The uncropped 64-byte derivation that 3.6.0.0 to 3.8.2.0 cached as the "seed". */
    val fullSeed: String?,
    val seed: String,
    val privateKey: String,
    val nonce: String,
    val publicKey: String,
    val ed25519ExpandedSecret: String,
    val signature: String,
    val soraAddress: String,
    val genericAddress: String,
)

object HistoricalSr25519Vectors {
    const val SIGNING_CONTEXT = "substrate"
    const val SIGNED_MESSAGE = "%s"

    fun byPhrase(phrase: String): HistoricalSr25519Vector? = ALL.firstOrNull { it.phrase == phrase }

    fun bySeed(seedHex: String): HistoricalSr25519Vector? = ALL.firstOrNull { it.seed == seedHex }

    fun byId(id: String): HistoricalSr25519Vector = ALL.first { it.id == id }

    fun hex(text: String): ByteArray =
        ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    val ALL: List<HistoricalSr25519Vector> = listOf(
''' % MESSAGE


if __name__ == "__main__":
    main()
