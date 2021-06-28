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

import com.radixdlt.qualifier.NetworkId;
import org.assertj.core.util.Files;
import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.serialization.TestSetupUtils;

import com.google.inject.Guice;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.RadixKeyStore;
import com.radixdlt.properties.RuntimeProperties;

import java.io.File;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RadixNodeModuleTest {
	@NetworkId
	private int networkId;

	@BeforeClass
	public static void beforeClass() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	@Test
	public void testInjectorNotNullToken() {
		final var properties = createDefaultProperties();
		when(properties.get(eq("network.id"))).thenReturn("99");
		when(properties.get(eq("network.genesis_txn"))).thenReturn("00");
		Guice.createInjector(new RadixNodeModule(properties)).injectMembers(this);
	}

	private RuntimeProperties createDefaultProperties() {
		final var properties = mock(RuntimeProperties.class);
		doReturn("127.0.0.1").when(properties).get(eq("host.ip"), any());
		var keyStore = new File("nonesuch.ks");
		Files.delete(keyStore);
		generateKeystore(keyStore);

		when(properties.get(eq("node.key.path"), any(String.class))).thenReturn("nonesuch.ks");
		return properties;
	}

	private void generateKeystore(File keyStore) {
		try {
			RadixKeyStore.fromFile(keyStore, null, true)
				.writeKeyPair("node", ECKeyPair.generateNew());
		} catch (Exception e) {
			throw new IllegalStateException("Unable to create keystore");
		}
	}
}
