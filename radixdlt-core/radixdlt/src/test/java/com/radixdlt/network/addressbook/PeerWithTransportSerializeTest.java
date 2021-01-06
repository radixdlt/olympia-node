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

package com.radixdlt.network.addressbook;

import org.radix.serialization.SerializeMessageObject;

import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.udp.UDPConstants;

/**
 * Check serialization of PeerWithTransport
 */
public class PeerWithTransportSerializeTest extends SerializeMessageObject<PeerWithTransport> {
	public PeerWithTransportSerializeTest() {
		super(PeerWithTransport.class, PeerWithTransportSerializeTest::get);
	}

	private static PeerWithTransport get() {
		TransportInfo ti = TransportInfo.of(UDPConstants.NAME,
			StaticTransportMetadata.of(
				UDPConstants.METADATA_HOST, "127.0.0.1",
				UDPConstants.METADATA_PORT, "10000"
			)
		);
		return new PeerWithTransport(ti);
	}
}
