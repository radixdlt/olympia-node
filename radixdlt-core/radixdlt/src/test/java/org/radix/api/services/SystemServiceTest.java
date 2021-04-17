/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package org.radix.api.services;

import com.radixdlt.environment.Runners;
import org.junit.Test;
import org.radix.universe.system.LocalSystem;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.ModuleRunner;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.udp.UDPConstants;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Bytes;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.radix.Radix.AGENT;
import static org.radix.Radix.AGENT_VERSION;
import static org.radix.Radix.PROTOCOL_VERSION;

public class SystemServiceTest {
	private static final String PROPERTIES_FILE = "/default.config";

	private final Serialization serialization = DefaultSerialization.getInstance();
	private final Universe universe = loadUniverse();
	private final LocalSystem localSystem = assembleLocalSystem();
	private final ModuleRunner consensusRunner = mock(ModuleRunner.class);
	private final SystemService systemService =
		new SystemService(serialization, universe, localSystem, Map.of(Runners.CONSENSUS, consensusRunner));

	@Test
	public void pingPong() {
		var result = systemService.getPong();

		assertNotNull(result);
		assertEquals("pong", result.getString("response"));
	}

	@Test
	public void bftCanBeStarted() {
		var result = systemService.bftStart();

		assertNotNull(result);
		assertEquals("success", result.getString("response"));
		verify(consensusRunner).start();
	}

	@Test
	public void bftCanBeStopped() {
		var result = systemService.bftStop();

		assertNotNull(result);
		assertEquals("success", result.getString("response"));
		verify(consensusRunner).stop();
	}

	@Test
	public void universeCanBeObtained() {
		var result = systemService.getUniverse();

		assertNotNull(result);
	}

	@Test
	public void localSystemCanBeObtained() {
		var result = systemService.getLocalSystem();

		assertNotNull(result);

		var agent = result.getJSONObject("agent");

		assertEquals(PROTOCOL_VERSION, agent.get("protocol"));
		assertEquals(":str:" + AGENT, agent.getString("name"));
		assertEquals(AGENT_VERSION, agent.get("version"));
	}

	private static Universe loadUniverse() {
		try {
			byte[] bytes = Bytes.fromBase64String(universeBase64());
			return DefaultSerialization.getInstance().fromDson(bytes, Universe.class);
		} catch (Exception e) {
			throw new RuntimeException("Unable to load Universe", e);
		}
	}

	private static String universeBase64() throws IOException {
		final var properties = new Properties();

		try (final var input = SystemService.class.getResourceAsStream(PROPERTIES_FILE)) {
			if (input == null) {
				throw new IOException("Resource not found: " + PROPERTIES_FILE);
			}
			properties.load(input);
		}
		return (String) properties.get("universe");
	}

	private static LocalSystem assembleLocalSystem() {
		try {
			var keyPair = ECKeyPair.fromPrivateKey(Bytes.fromHexString(Strings.repeat("deadbeef", 8)));

			var transports = ImmutableList.of(
				TransportInfo.of(
					UDPConstants.NAME,
					StaticTransportMetadata.of(
						UDPConstants.METADATA_HOST, "127.0.0.1",
						UDPConstants.METADATA_PORT, "30000"
					)
				)
			);

			return new LocalSystem(Map::of, keyPair.getPublicKey(), AGENT, AGENT_VERSION, PROTOCOL_VERSION, transports);
		} catch (Exception e) {
			throw new RuntimeException("Unable to create local system", e);
		}
	}
}