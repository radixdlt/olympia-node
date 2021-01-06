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

package com.radixdlt.network.messaging;

import org.junit.Before;
import org.junit.Test;

import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportInfo;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import nl.jqno.equalsverifier.EqualsVerifier;

public class InboundMessageTest {

	private final TransportInfo transportInfo = TransportInfo.of("TEST", StaticTransportMetadata.empty());
	private final byte[] message = new byte[] {
		0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
	};
	private InboundMessage inboundMessage;

	@Before
	public void setUp() {
		this.inboundMessage = InboundMessage.of(this.transportInfo, this.message);
	}

	@Test
	public void equalsContract() {
	    EqualsVerifier.forClass(InboundMessage.class).verify();
	}

	@Test
	public void testSource() {
		assertThat(inboundMessage.source(), equalTo(transportInfo));
	}

	@Test
	public void testMessage() {
		assertThat(inboundMessage.message(), equalTo(message));
	}

	@Test
	public void testToString() {
		assertThat(inboundMessage.toString(), containsString("TEST")); // Transport name
		assertThat(inboundMessage.toString(), containsString("000102030405060708090a")); // Message data in hex
	}
}
