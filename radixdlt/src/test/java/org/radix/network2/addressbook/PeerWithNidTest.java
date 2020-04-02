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

package org.radix.network2.addressbook;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.radix.network2.transport.TransportException;
import org.radix.network2.transport.TransportInfo;
import org.radix.time.Time;
import org.radix.time.Timestamps;

import com.google.common.collect.ImmutableList;
import com.radixdlt.identifiers.EUID;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

public class PeerWithNidTest {

	private EUID nid;
	private PeerWithNid pwn;

	@Before
	public void setUp() throws Exception {
		this.nid = EUID.ONE;
		this.pwn = new PeerWithNid(this.nid);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testToString() {
		String s = this.pwn.toString();

		assertThat(s, containsString("PeerWithNid")); // class name
		assertThat(s, containsString(this.nid.toString())); // nid
	}

	@Test
	public void testGetNID() {
		assertThat(this.pwn.getNID(), is(this.nid));
	}

	@Test
	public void testHasNID() {
		assertThat(this.pwn.hasNID(), is(true));
	}

	@Test
	public void testSupportsTransport() {
		assertThat(this.pwn.supportsTransport("ANY"), is(false));
	}

	@Test
	public void testSupportedTransports() {
		ImmutableList<TransportInfo> tis = this.pwn.supportedTransports().collect(ImmutableList.toImmutableList());
		assertThat(tis, empty());
	}

	@Test(expected = TransportException.class)
	public void testConnectionDataThrows() {
		this.pwn.connectionData("ANY");
		fail();
	}

	@Test
	public void testHasSystem() {
		assertThat(this.pwn.hasSystem(), is(false));
	}

	@Test
	public void testGetSystem() {
		assertThat(this.pwn.getSystem(), nullValue());
	}

	@Test
	public void testBan() {
		long now = Time.currentTimestamp();
		this.pwn.ban("Reason for ban");
		assertThat(this.pwn.getTimestamp(Timestamps.BANNED), greaterThanOrEqualTo(now));
		assertThat(this.pwn.getBanReason(), is("Reason for ban"));
	}
}
