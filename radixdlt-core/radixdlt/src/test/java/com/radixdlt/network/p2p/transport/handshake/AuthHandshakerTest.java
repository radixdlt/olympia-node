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

package com.radixdlt.network.p2p.transport.handshake;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.crypto.ECKeyOps;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.serialization.Serialization;
import org.junit.Test;
import java.security.SecureRandom;

import static org.junit.Assert.assertArrayEquals;

public final class AuthHandshakerTest {
	private final Serialization serialization = DefaultSerialization.getInstance();
	private final SecureRandom secureRandom = new SecureRandom();

	@Test
	public void test_auth_handshake() throws Exception {
		final var nodeKey1 = ECKeyPair.generateNew();
		final var nodeKey2 = ECKeyPair.generateNew();
		final var handshaker1 = new AuthHandshaker(serialization, secureRandom, ECKeyOps.fromKeyPair(nodeKey1), nodeKey1.getPublicKey());
		final var handshaker2 = new AuthHandshaker(serialization, secureRandom, ECKeyOps.fromKeyPair(nodeKey2), nodeKey2.getPublicKey());

		final var initMessage = handshaker1.initiate(nodeKey2.getPublicKey());
		final var handshaker2ResultPair = handshaker2.handleInitialMessage(initMessage);
		final var handshaker2Result = handshaker2ResultPair.getSecond();
		final var responseMessage = handshaker2ResultPair.getFirst();
		final var handshaker1Result = handshaker1.handleResponseMessage(responseMessage);

		assertArrayEquals(handshaker1Result.getSecrets().aes, handshaker2Result.getSecrets().aes);
		assertArrayEquals(handshaker1Result.getSecrets().mac, handshaker2Result.getSecrets().mac);
		assertArrayEquals(handshaker1Result.getSecrets().token, handshaker2Result.getSecrets().token);
	}
}
