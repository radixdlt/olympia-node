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

package org.radix.network.discovery;

import java.io.IOException;

import com.google.common.collect.ImmutableList;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.network.p2p.P2PConfig;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SeedNodesConfigParserTest {
	private P2PConfig p2pConfig;

	@Before
	public void setUp() throws IOException {
		p2pConfig = mock(P2PConfig.class);
		when(p2pConfig.defaultPort()).thenReturn(30000);
	}

	@Test
	public void parse_seads_from_config() {
		doReturn(ImmutableList.of(String.format(
			"radix://%s@1.1.1.1",
			ECKeyPair.generateNew().getPublicKey().toBase58()
		))).when(p2pConfig).seedNodes();
		final var testSubject = new SeedNodesConfigParser(p2pConfig);
		assertEquals(1, testSubject.getResolvedSeedNodes().size());
	}
}
