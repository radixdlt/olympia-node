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
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.Bytes;
import org.radix.Radix;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.udp.UDPConstants;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.RadixSystem;
import org.radix.universe.system.SystemMessage;

public class SystemMessageSerializeTest extends SerializeMessageObject<SystemMessage> {
		public SystemMessageSerializeTest() {
			super(SystemMessage.class, SystemMessageSerializeTest::get);
		}

		private static SystemMessage get() {
			try {
				ECKeyPair keyPair = new ECKeyPair(Bytes.fromHexString(Strings.repeat("deadbeef", 8)));
				RadixSystem system = new LocalSystem(keyPair, Radix.AGENT, Radix.AGENT_VERSION, Radix.PROTOCOL_VERSION, ImmutableList.of(
						TransportInfo.of(
								UDPConstants.UDP_NAME,
								StaticTransportMetadata.of(
										UDPConstants.METADATA_UDP_HOST,"127.0.0.1",
										UDPConstants.METADATA_UDP_PORT,"30000"
								)
						)
				));
				SystemMessage message = new SystemMessage(system, 1337);
				message.sign(keyPair, true);
				return message;
			} catch (CryptoException e) {
				throw new IllegalStateException("Failed to create key", e);
			}
		}
	}