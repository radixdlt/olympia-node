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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.Sha256Hasher;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.udp.UDPConstants;
import com.radixdlt.utils.Bytes;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;
import org.radix.Radix;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.RadixSystem;
import org.radix.universe.system.SystemMessage;

public class SystemMessageSerializeTest extends SerializeMessageObject<SystemMessage> {

	private static final Hasher hasher = Sha256Hasher.withDefaultSerialization();

	public SystemMessageSerializeTest() {
			super(SystemMessage.class, SystemMessageSerializeTest::get);
		}

	private static SystemMessage get() {
		try {
			ECKeyPair keyPair = ECKeyPair.fromPrivateKey(Bytes.fromHexString(Strings.repeat("deadbeef", 8)));
			RadixSystem system = new LocalSystem(ImmutableMap::of, keyPair.getPublicKey(), Radix.AGENT, Radix.AGENT_VERSION, Radix.PROTOCOL_VERSION, ImmutableList.of(
					TransportInfo.of(
							UDPConstants.NAME,
							StaticTransportMetadata.of(
									UDPConstants.METADATA_HOST,"127.0.0.1",
									UDPConstants.METADATA_PORT,"30000"
							)
					)
			));
			SystemMessage message = new SystemMessage(system, 1337);
			HashCode msgHash = hasher.hash(message);
			ECDSASignature signature = keyPair
					.sign(msgHash.asBytes(), true, false);
			message.setSignature(signature);
			return message;
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalStateException("Failed to create key", e);
		}
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(SystemMessage.class)
				.usingGetClass()
				.suppress(Warning.NONFINAL_FIELDS)
				.withIgnoredFields("instance")
				.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
				.verify();
	}
}
