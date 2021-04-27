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
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.network.p2p.P2PConfig;
import org.junit.Before;
import org.junit.Test;
import com.radixdlt.properties.RuntimeProperties;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SeedNodesConfigParserTest {
	private RuntimeProperties config;
	private P2PConfig p2PConfig;

	@Before
	public void setUp() throws IOException {
		config = defaultProperties();
		p2PConfig = mock(P2PConfig.class);
		when(p2PConfig.defaultPort()).thenReturn(30000);
	}

	@Test
	public void parse_seads_from_config() {
		doReturn(String.format(
			"radix://%s@1.1.1.1",
			ECKeyPair.generateNew().getPublicKey().toBase58()
		)).when(config).get("network.seeds", "");
		SeedNodesConfigParser testSubject = new SeedNodesConfigParser(config, p2PConfig);
		assertEquals(1, testSubject.getResolvedSeedNodes().size());
	}

	private static RuntimeProperties defaultProperties() {
		RuntimeProperties properties = mock(RuntimeProperties.class);
		doAnswer(invocation -> invocation.getArgument(1)).when(properties).get(any(), any());
		return properties;
	}
}
