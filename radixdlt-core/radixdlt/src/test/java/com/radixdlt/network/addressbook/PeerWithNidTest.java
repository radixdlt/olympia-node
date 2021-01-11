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

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Before;
import org.junit.Test;
import org.radix.time.Time;
import org.radix.time.Timestamps;

import com.google.common.collect.ImmutableList;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.transport.TransportException;
import com.radixdlt.network.transport.TransportInfo;

import static org.junit.Assert.*;
import static org.assertj.core.api.Assertions.assertThat;

// Retaining these tests, even though PeerWithNid has moved into tests
public class PeerWithNidTest {

	private EUID nid;
	private PeerWithNid pwn;

	@Before
	public void setUp() {
		this.nid = EUID.ONE;
		this.pwn = new PeerWithNid(this.nid);
	}

	@Test
	public void testToString() {
		String s = this.pwn.toString();

		assertThat(s).contains("PeerWithNid"); // class name
		assertThat(s).contains(this.nid.toString()); // nid
	}

	@Test
	public void testGetNID() {
		assertThat(this.pwn.getNID()).isEqualTo(this.nid);
	}

	@Test
	public void testHasNID() {
		assertThat(this.pwn.hasNID()).isTrue();
	}

	@Test
	public void testSupportsTransport() {
		assertThat(this.pwn.supportsTransport("ANY")).isFalse();
	}

	@Test
	public void testSupportedTransports() {
		ImmutableList<TransportInfo> tis = this.pwn.supportedTransports().collect(ImmutableList.toImmutableList());
		assertThat(tis).isEmpty();
	}

	@Test(expected = TransportException.class)
	public void testConnectionDataThrows() {
		this.pwn.connectionData("ANY");
		fail();
	}

	@Test
	public void testHasSystem() {
		assertThat(this.pwn.hasSystem()).isFalse();
	}

	@Test
	public void testGetSystem() {
		assertThat(this.pwn.getSystem()).isNull();
	}

	@Test
	public void testBan() {
		long now = Time.currentTimestamp();
		this.pwn.ban("Reason for ban");
		assertThat(this.pwn.getTimestamp(Timestamps.BANNED)).isGreaterThanOrEqualTo(now);
		assertThat(this.pwn.getBanReason()).isEqualTo("Reason for ban");
	}

	@Test
	public void testBanCopy() {
		long now = Time.currentTimestamp();
		this.pwn.setBan("Reason for ban", now);
		assertThat(this.pwn.getTimestamp(Timestamps.BANNED)).isEqualTo(now);
		assertThat(this.pwn.getBanReason()).isEqualTo("Reason for ban");
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(PeerWithNid.class)
				.withIgnoredFields("banReason", "timestamps")
				.suppress(Warning.NONFINAL_FIELDS)
				.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
				.verify();
	}
}
