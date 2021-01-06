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

package org.radix.serialization;

import com.google.common.base.Strings;
import com.google.common.hash.HashCode;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.utils.Bytes;

import org.junit.Test;
import org.radix.network.messages.PeerPingMessage;
import org.radix.universe.system.RadixSystem;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Check serialization of PeerPingMessage
 */
public class PeerPingMessageSerializeTest extends SerializeMessageObject<PeerPingMessage> {

	private static final Hasher hasher = Sha256Hasher.withDefaultSerialization();

	public PeerPingMessageSerializeTest() {
		super(PeerPingMessage.class, PeerPingMessageSerializeTest::get);
	}

	private static PeerPingMessage get() {
		try {
			PeerPingMessage pingMessage = new PeerPingMessage(1, 17L, 18L, getLocalSystem());
			ECKeyPair keyPair = ECKeyPair.fromPrivateKey(Bytes.fromHexString(Strings.repeat("deadbeef", 8)));
			HashCode pingMsgHash = hasher.hash(pingMessage);
			ECDSASignature signature = keyPair
					.sign(pingMsgHash.asBytes(), true, false);
			pingMessage.setSignature(signature);
			return pingMessage;
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalStateException("Failed to create key", e);
		}
	}

	@Test
	public void sensibleToString() {
		RadixSystem system = mock(RadixSystem.class);
		when(system.getNID()).thenReturn(EUID.TWO);
		String s = new PeerPingMessage(0, 1234L, 5678L, system).toString();

		assertThat(s, containsString(PeerPingMessage.class.getSimpleName()));
		assertThat(s, containsString(EUID.TWO.toString()));
		assertThat(s, containsString(Long.toHexString(1234)));
		assertThat(s, containsString("5678"));
	}
}
