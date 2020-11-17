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

package org.radix;

import java.io.File;

import org.assertj.core.util.Files;
import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.serialization.TestSetupUtils;

import com.radixdlt.properties.RuntimeProperties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GlobalInjectorTest {

	private GlobalInjector globalInjector;

	@BeforeClass
	public static void beforeClass() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	@Test
	public void testInjectorNotNullToken() {
		RuntimeProperties properties = mock(RuntimeProperties.class);
		doReturn("127.0.0.1").when(properties).get(eq("host.ip"), any());
		Files.delete(new File("nonesuch.ks"));
		when(properties.get(eq("node.key.path"), any(String.class))).thenReturn("nonesuch.ks");

		this.globalInjector = new GlobalInjector(properties);

		assertNotNull(this.globalInjector.getInjector());
	}
}
