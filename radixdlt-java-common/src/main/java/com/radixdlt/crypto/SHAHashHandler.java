/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;

/**
 * Collection of Hashing methods using SHA-2 family and invoking each hash function <b>twice</>.
 */
@SecurityCritical(SecurityKind.HASHING)
class SHAHashHandler implements HashHandler {
	// Note that default provide around 20-25% faster than Bouncy Castle.
	// See jmh/org.radix.benchmark.HashBenchmark
	private final ThreadLocal<MessageDigest> hash256DigesterInner = ThreadLocal.withInitial(() -> getDigester("SHA-256"));
	private final ThreadLocal<MessageDigest> hash256DigesterOuter = ThreadLocal.withInitial(() -> getDigester("SHA-256"));
	private final ThreadLocal<MessageDigest> hash512Digester = ThreadLocal.withInitial(() -> getDigester("SHA-512"));

	SHAHashHandler() {
		// Nothing to do here
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This implementation uses two rounds of SHA-2 with 256 bits of length,
	 * i.e. {@code SHA-256(SHA-256(data))}, in order to avoid length-extension attack
	 */
	@Override
	public byte[] hash256(byte[] data, int offset, int length) {
		return hash256Twice(data, offset, length);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * This implementation uses two rounds of SHA-2 with 512 bits of length,
	 * i.e. {@code SHA-512(SHA-512(data))}, in order to avoid length-extension attack
	 */
	@Override
	public byte[] hash512(byte[] data, int offset, int length) {
		return hash512Twice(data, offset, length);
	}

	private byte[] hash256Twice(byte[] data, int offset, int length) {
		final MessageDigest hash256DigesterOuterLocal = hash256DigesterOuter.get();
		final MessageDigest hash256DigesterInnerLocal = hash256DigesterInner.get();
		hash256DigesterOuterLocal.reset();
		hash256DigesterInnerLocal.reset();
		hash256DigesterInnerLocal.update(data, offset, length);
		return hash256DigesterOuterLocal.digest(hash256DigesterInnerLocal.digest());
	}

	private byte[] hash512Twice(byte[] data, int offset, int length) {
		final MessageDigest hash512DigesterLocal = hash512Digester.get();
		hash512DigesterLocal.reset();
		hash512DigesterLocal.update(data, offset, length);
		return hash512DigesterLocal.digest(hash512DigesterLocal.digest());
	}

	private static MessageDigest getDigester(String algorithm) {
		try {
			return  MessageDigest.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException("No such algorithm: " + algorithm, e);
		}
	}
}
