/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.consensus.simulation;

import com.google.inject.AbstractModule;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.Hash;

/**
 * For testing where verification and signing is skipped
 */
public class NullCryptoModule extends AbstractModule {
	@Override
	public void configure() {
		bind(Hasher.class).toInstance(o -> Hash.ZERO_HASH);
		bind(HashVerifier.class).toInstance((pubKey, hash, sig) -> true);
		bind(HashSigner.class).toInstance(h -> new ECDSASignature());
	}
}
