package com.radixdlt;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Annotation to be used for classes which require additional scrutiny
 * in code review. These classes will be mostly be low level classes whose
 * correctness is critical for security.
 */
@Documented
@Target(ElementType.TYPE)
public @interface SecurityCritical {
	/**
	 * Returns the kinds of security critical things this annotation is flagging.
	 *
	 * @return The kinds of security critical things this annotation is flagging.
	 */
	SecurityKind[] value();

	/**
	 * Kinds of security critical things that can be flagged.
	 */
	enum SecurityKind {
		/**
		 * A general security kind, where no other kinds apply.
		 */
		GENERAL,
		/**
		 * Code calculates or compares hashes.
		 */
		HASHING,
		/**
		 * Code stores private keys.
		 */
		KEY_STORE,
		/**
		 * Code generates private keys or computes public keys.
		 */
		KEY_GENERATION,
		/**
		 * Numeric calculations affecting system security.
		 */
		NUMERIC,
		/**
		 * Code performs public-key or hybrid decryption, and therefore uses private keys.
		 */
		PK_DECRYPT,
		/**
		 * Code performs public-key or hybrid encryption.
		 */
		PK_ENCRYPT,
		/**
		 * Code that ensures randomness is suitable for cryptographic use.
		 */
		RANDOMNESS,
		/**
		 * Code performs signing, and therefore uses private keys.
		 */
		SIG_SIGN,
		/**
		 * Code performs signature verification.
		 */
		SIG_VERIFY,
	}
}
