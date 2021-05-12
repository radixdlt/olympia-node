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

package com.radixdlt;

import java.io.File;

import com.google.inject.Guice;
import com.radixdlt.network.transport.tcp.TCPConstants;
import org.assertj.core.util.Files;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.radix.serialization.TestSetupUtils;

import com.radixdlt.properties.RuntimeProperties;
import org.radix.universe.system.LocalSystem;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RadixNodeModuleTest {

	private RadixNodeModule radixNodeModule;

	@BeforeClass
	public static void beforeClass() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	@Test
	public void testInjectorNotNullToken() {
		final var properties = createDefaultProperties();
		when(properties.get(eq("consensus.pacemaker_rate"), anyDouble())).thenReturn(2.0);
		this.radixNodeModule = new RadixNodeModule(properties);
		assertNotNull(Guice.createInjector(this.radixNodeModule));
	}

	@Test
	@Ignore("Too many weird dependencies to hook up, need to cleanup.")
	public void testUseCorrectBroadcastPortForLocalSystem() {
		final var properties = createDefaultProperties();
		when(properties.get(eq("network.tcp.broadcast_port"), anyInt())).thenReturn(30001);
		when(properties.get(eq("network.tcp.listen_port"), anyInt())).thenReturn(30000);

		this.radixNodeModule = new RadixNodeModule(properties);

		final var localSystem = Guice.createInjector(this.radixNodeModule).getInstance(LocalSystem.class);
		final var broadcastPort = localSystem.supportedTransports()
			.findFirst().get().metadata().get(TCPConstants.METADATA_PORT);

		assertEquals("30001", broadcastPort);
	}

	private RuntimeProperties createDefaultProperties() {
		final var properties = mock(RuntimeProperties.class);
		doReturn("127.0.0.1").when(properties).get(eq("host.ip"), any());
		Files.delete(new File("nonesuch.ks"));
		when(properties.get(eq("node.key.path"), any(String.class))).thenReturn("nonesuch.ks");
		return properties;
	}
}
