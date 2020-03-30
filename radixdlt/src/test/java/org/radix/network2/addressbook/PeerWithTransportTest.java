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
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportException;
import org.radix.network2.transport.TransportInfo;

import com.google.common.collect.ImmutableList;
import com.radixdlt.identifiers.EUID;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

public class PeerWithTransportTest {

	private TransportInfo dummy;
	private PeerWithTransport pwt;

	@Before
	public void setUp() throws Exception {
		dummy = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());
		pwt = new PeerWithTransport(dummy);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testToString() {
		String s = this.pwt.toString();

		assertThat(s, containsString("PeerWithTransport")); // class name
		assertThat(s, containsString(this.dummy.name())); // transport name
	}

	@Test
	public void testGetNID() {
		assertThat(this.pwt.getNID(), is(EUID.ZERO));
	}

	@Test
	public void testHasNID() {
		assertThat(this.pwt.hasNID(), is(false));
	}

	@Test
	public void testSupportsTransport() {
		assertThat(this.pwt.supportsTransport("NOTEXIST"), is(false));
		assertThat(this.pwt.supportsTransport(this.dummy.name()), is(true));
	}

	@Test
	public void testSupportedTransports() {
		ImmutableList<TransportInfo> tis = this.pwt.supportedTransports().collect(ImmutableList.toImmutableList());
		assertThat(tis, contains(this.dummy));
	}

	@Test
	public void testConnectionData() {
		assertThat(this.pwt.connectionData("DUMMY"), is(this.dummy.metadata()));
	}

	@Test(expected = TransportException.class)
	public void testConnectionDataThrows() {
		this.pwt.connectionData("NONESUCH");
		fail();
	}

	@Test
	public void testHasSystem() {
		assertThat(this.pwt.hasSystem(), is(false));
	}

	@Test
	public void testGetSystem() {
		assertThat(this.pwt.getSystem(), nullValue());
	}
}
