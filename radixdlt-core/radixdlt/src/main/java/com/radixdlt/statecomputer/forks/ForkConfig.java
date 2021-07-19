/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.statecomputer.forks;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

/**
 * Configuration used for hard forks
 */
public interface ForkConfig {
	String name();
	HashCode hash();
	RERules engineRules();
	ForkConfig withForksVerifier(ForkConfig nextFork);

	static HashCode voteHash(ECPublicKey publicKey, ForkConfig forkConfig) {
		return voteHash(publicKey, forkConfig.hash());
	}

	static HashCode voteHash(ECPublicKey publicKey, HashCode forkHash) {
		final var bytes = ByteUtils.concatenate(publicKey.getBytes(), forkHash.asBytes());
		return HashUtils.sha256(bytes); // it's actually hashed twice (see HashUtils impl)
	}
}
