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

package org.radix.serialization;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import org.junit.BeforeClass;
import org.mockito.stubbing.Answer;
import org.radix.time.NtpService;
import org.radix.universe.system.LocalSystem;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class RadixTest {
	private static Serialization serialization;
	private static NtpService ntpService;
	private static RuntimeProperties properties;
	private static LocalSystem localSystem;
	private static Universe universe;
	private static ECKeyPair ecKeyPair;

	@BeforeClass
	public static void startRadixTest() {
		TestSetupUtils.installBouncyCastleProvider();

		properties = mock(RuntimeProperties.class);
		doAnswer(invocation -> invocation.getArgument(1)).when(properties).get(any(), any());

		universe = mock(Universe.class);
		when(universe.getMagic()).thenReturn(2);

		ntpService = mock(NtpService.class);
		when(ntpService.getUTCTimeMS()).thenAnswer((Answer<Long>) invocation -> System.currentTimeMillis());

		serialization = DefaultSerialization.getInstance();

		ecKeyPair = ECKeyPair.generateNew();

		localSystem = LocalSystem.create(BFTNode.create(ecKeyPair.getPublicKey()), ImmutableMap::of);
	}

	public static Serialization getSerialization() {
		return serialization;
	}

	public static NtpService getNtpService() {
		return ntpService;
	}

	public static RuntimeProperties getProperties() {
		return properties;
	}

	public static LocalSystem getLocalSystem() {
		return localSystem;
	}

	public static ECKeyPair getKeyPair() {
		return ecKeyPair;
	}

	public static Universe getUniverse() {
		return universe;
	}
}
