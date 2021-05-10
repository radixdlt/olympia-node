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

package com.radixdlt.network.p2p.transport;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.crypto.ECKeyOps;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.network.p2p.transport.handshake.AuthHandshaker;
import com.radixdlt.network.p2p.transport.handshake.Secrets;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Pair;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;

import static org.junit.Assert.assertArrayEquals;

public final class FrameCodecTest {
	private final Serialization serialization = DefaultSerialization.getInstance();
	private final SecureRandom secureRandom = new SecureRandom();

	@Test
	public void test_frame_codec_write_read() throws Exception {
		final var nodeKey1 = ECKeyPair.generateNew();
		final var nodeKey2 = ECKeyPair.generateNew();

		final var secrets = agreeSecrets(nodeKey1, nodeKey2);

		final var frameCodec1 = new FrameCodec(secrets.getFirst());
		final var frameCodec2 = new FrameCodec(secrets.getSecond());

		final var messageCount = 1000;
		for (int i = 0; i < messageCount; i++) {
			final var direction = secureRandom.nextBoolean();
			final var source = direction ? frameCodec1 : frameCodec2;
			final var destination = direction ? frameCodec2 : frameCodec1;

			final var messageLength = secureRandom.nextInt(1024 * 10);
			final var message = new byte[messageLength];
			secureRandom.nextBytes(message);

			final var baos = new ByteArrayOutputStream();
			source.writeFrame(message, baos);
			final var readFrame = destination.tryReadSingleFrame(baos.toByteArray());

			assertArrayEquals(message, readFrame.get());
		}
	}

	private Pair<Secrets, Secrets> agreeSecrets(ECKeyPair nodeKey1, ECKeyPair nodeKey2) throws Exception {
		final var handshaker1 = new AuthHandshaker(serialization, secureRandom, ECKeyOps.fromKeyPair(nodeKey1), nodeKey1.getPublicKey());
		final var handshaker2 = new AuthHandshaker(serialization, secureRandom, ECKeyOps.fromKeyPair(nodeKey2), nodeKey2.getPublicKey());

		final var initMessage = handshaker1.initiate(nodeKey2.getPublicKey());
		final var handshaker2ResultPair = handshaker2.handleInitialMessage(initMessage);
		final var handshaker2Result = handshaker2ResultPair.getSecond();
		final var responseMessage = handshaker2ResultPair.getFirst();
		final var handshaker1Result = handshaker1.handleResponseMessage(responseMessage);

		return Pair.of(handshaker1Result.getSecrets(), handshaker2Result.getSecrets());
	}
}
