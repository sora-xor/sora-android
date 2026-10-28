//! Public regression vectors; no wallet or credential inputs.
use curve25519_dalek::scalar::Scalar;

/// Keep an optimized scalar subtraction entry point available for LLVM inspection.
#[no_mangle]
#[inline(never)]
pub fn audited_scalar_sub(a: &Scalar, b: &Scalar) -> Scalar { a - b }

#[cfg(test)]
mod tests {
    use super::*;
    use rand::RngCore;
    use std::sync::atomic::{AtomicUsize, Ordering};

    struct CountingLogger;
    static CALLBACKS: AtomicUsize = AtomicUsize::new(0);
    static LOGGER: CountingLogger = CountingLogger;
    impl log::Log for CountingLogger {
        fn enabled(&self, _: &log::Metadata) -> bool { true }
        fn log(&self, _: &log::Record) { CALLBACKS.fetch_add(1, Ordering::SeqCst); }
        fn flush(&self) {}
    }

    #[test]
    fn rng_reseeding_never_calls_custom_logger_even_with_log_feature() {
        log::set_logger(&LOGGER).unwrap();
        log::set_max_level(log::LevelFilter::Trace);
        // Cross the 64-KiB reseeding threshold repeatedly, which triggered the
        // old internal trace callback and allowed a logger to reenter ThreadRng.
        let mut rng = rand::thread_rng();
        let mut bytes = [0u8; 8192];
        for _ in 0..64 { rng.fill_bytes(&mut bytes); }
        assert_eq!(CALLBACKS.load(Ordering::SeqCst), 0);
    }

    #[test]
    fn subtraction_wraps_at_group_order_without_changing_encoding() {
        let order_minus_one = [
            0xec, 0xd3, 0xf5, 0x5c, 0x1a, 0x63, 0x12, 0x58,
            0xd6, 0x9c, 0xf7, 0xa2, 0xde, 0xf9, 0xde, 0x14,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x10,
        ];
        assert_eq!(audited_scalar_sub(&Scalar::zero(), &Scalar::one()).to_bytes(), order_minus_one);
        let high = Scalar::from_canonical_bytes(order_minus_one).unwrap();
        assert_eq!(audited_scalar_sub(&high, &high), Scalar::zero());
        assert_eq!(high + Scalar::one(), Scalar::zero());
        for i in 0..1024u64 {
            let a = Scalar::from(i);
            let b = Scalar::from(i + 1);
            assert_eq!(audited_scalar_sub(&a, &b).to_bytes(), order_minus_one);
            assert_eq!(audited_scalar_sub(&b, &a), Scalar::one());
        }
    }
}
