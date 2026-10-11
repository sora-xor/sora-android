//! Prints sr25519 key vectors made by the native code path of the released SORA Wallet apps.
//!
//! Every release derived its SORA2 key pair in `sr25519-java` (fearless-utils, shared-features
//! and x-crypto all carry the same file) as
//!
//!     MiniSecretKey::from_bytes(seed).expand_to_keypair(ExpansionMode::Ed25519).to_bytes()
//!
//! and stored bytes 0..32 as the private key, 32..64 as the nonce and 64..96 as the public key.
//! The JSON export stored `SecretKey::to_ed25519_bytes()`. Signatures used the context
//! "substrate". This tool calls exactly those functions of schnorrkel 0.9.1.
//!
//! Input (stdin): one 32-byte seed per line, as 64 hex characters.
//! Output (stdout): one line per seed:
//!     seed private_key nonce public_key ed25519_expanded_secret signature
//! where `signature` signs the fixed message below with the derived key.

use schnorrkel::{ExpansionMode, Keypair, MiniSecretKey, PublicKey, SecretKey};
use std::io::{self, BufRead};

const SIGNING_CTX: &[u8] = b"substrate";
pub const MESSAGE: &[u8] = b"sora-wallet-historical-sr25519-vector-v1";

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{:02x}", b)).collect()
}

fn unhex(text: &str) -> Vec<u8> {
    assert!(text.len() % 2 == 0, "odd hex length");
    (0..text.len())
        .step_by(2)
        .map(|i| u8::from_str_radix(&text[i..i + 2], 16).expect("hex"))
        .collect()
}

fn main() {
    let stdin = io::stdin();
    for line in stdin.lock().lines() {
        let line = line.expect("line");
        let seed_hex = line.trim();
        if seed_hex.is_empty() {
            continue;
        }
        let seed = unhex(seed_hex);
        // sr25519-java `create_from_seed` + `keypairFromSeed`.
        let keypair: Keypair = MiniSecretKey::from_bytes(&seed)
            .expect("seed")
            .expand_to_keypair(ExpansionMode::Ed25519);
        let bytes = keypair.to_bytes();
        let private_key = &bytes[0..32];
        let nonce = &bytes[32..64];
        let public_key = &bytes[64..96];

        // sr25519-java `sign`: the stored private key and nonce are parsed back with
        // `SecretKey::from_bytes`, the stored public key with `PublicKey::from_bytes`.
        let secret = SecretKey::from_bytes(&bytes[0..64]).expect("secret");
        let public = PublicKey::from_bytes(public_key).expect("public");
        let signature = secret.sign_simple(SIGNING_CTX, MESSAGE, &public);
        assert!(public
            .verify_simple(SIGNING_CTX, MESSAGE, &signature)
            .is_ok());

        // sr25519-java `toEd25519Bytes`, the secret inside the JSON export.
        let ed25519 = secret.to_ed25519_bytes();

        println!(
            "{} {} {} {} {} {}",
            hex(&seed),
            hex(private_key),
            hex(nonce),
            hex(public_key),
            hex(&ed25519),
            hex(&signature.to_bytes())
        );
    }
}
