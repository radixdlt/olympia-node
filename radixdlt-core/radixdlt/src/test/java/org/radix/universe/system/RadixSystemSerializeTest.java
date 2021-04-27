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

package org.radix.universe.system;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.HashUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;
import org.radix.Radix;
import org.radix.serialization.SerializeMessageObject;

import com.radixdlt.crypto.ECKeyPair;

import static org.junit.Assert.*;

/**
 * Check serialization of org.radix.universe.system.System
 */
public class RadixSystemSerializeTest extends SerializeMessageObject<RadixSystem> {
	public RadixSystemSerializeTest() {
		super(RadixSystem.class, RadixSystemSerializeTest::getSystem);
	}

	private static RadixSystem getSystem() {
		return new RadixSystem();
	}

	@Test
	public void sensibleToString() {
		ECKeyPair key = ECKeyPair.generateNew();
		RadixSystem system = new RadixSystem(
			key.getPublicKey(), Radix.AGENT, Radix.AGENT_VERSION, Radix.PROTOCOL_VERSION);

		assertEquals(key.getPublicKey().euid().toString(), system.toString());
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(RadixSystem.class)
			.suppress(Warning.NONFINAL_FIELDS)
			.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
			.usingGetClass()
			.verify();
	}
}
