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

package com.radixdlt.network.hostip;

import java.util.Optional;

import org.junit.Test;

import com.radixdlt.properties.RuntimeProperties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RuntimePropertiesHostIpTest {

	@Test
	public void testEmpty() {
		RuntimePropertiesHostIp rphi1 = make("");
		assertFalse(rphi1.hostIp().isPresent());
		RuntimePropertiesHostIp rphi2 = make(null);
		assertFalse(rphi2.hostIp().isPresent());
	}

	@Test
	public void testInvalid() {
		RuntimePropertiesHostIp rphi = make("a:b");
		assertFalse(rphi.hostIp().isPresent());
	}

	@Test
	public void testValid() {
		RuntimePropertiesHostIp rphi = make("192.168.0.1");
		Optional<String> host = rphi.hostIp();
		assertTrue(host.isPresent());
		assertEquals("192.168.0.1", host.get());
	}

	private static RuntimePropertiesHostIp make(String value) {
		RuntimeProperties properties = mock(RuntimeProperties.class);
		when(properties.get(eq(RuntimePropertiesHostIp.HOST_IP_PROPERTY), any())).thenReturn(value);
		return (RuntimePropertiesHostIp) RuntimePropertiesHostIp.create(properties);
	}
}
