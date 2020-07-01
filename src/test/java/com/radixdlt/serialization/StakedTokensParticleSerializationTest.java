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

package com.radixdlt.serialization;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.TestSetupUtils;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition.MINT;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class StakedTokensParticleSerializationTest extends SerializeObjectEngine<StakedTokensParticle> {
	public static final RadixAddress DELEGATE_ADDRESS = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");
	public static final RadixAddress ADDRESS = RadixAddress.from("23B6fH3FekJeP6e5guhZAk6n9z4fmTo5Tngo3a11Wg5R8gsWTV2x");
	public static final RRI TOKEN = RRI.of(ADDRESS, "COOKIE");
	public static final UInt256 AMOUNT = UInt256.EIGHT;
	public static final UInt256 GRANULARITY = UInt256.ONE;
	public static final int PLANCK = 100;
	public static final ImmutableMap<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> TOKEN_PERMISSIONS = ImmutableMap.of();

	public StakedTokensParticleSerializationTest() {
		super(StakedTokensParticle.class, StakedTokensParticleSerializationTest::get);
	}

	@BeforeClass
	public static void startStakedTokensParticleSerializationTest() {
		TestSetupUtils.installBouncyCastleProvider();
	}

	@Test
	public void testGetters() {
		StakedTokensParticle p = get();
		assertEquals(DELEGATE_ADDRESS, p.getDelegateAddress());
		assertEquals(ADDRESS, p.getAddress());
		assertEquals(AMOUNT, p.getAmount());
		assertEquals(GRANULARITY, p.getGranularity());
		assertEquals(TOKEN, p.getTokDefRef());
		assertEquals(PLANCK, p.getPlanck());
		assertEquals(TOKEN_PERMISSIONS, p.getTokenPermissions());
		assertEquals(TOKEN_PERMISSIONS.get(MINT), p.getTokenPermission(MINT));
	}

	@Test
	public void testToString() {
		StakedTokensParticle p = get();
		String str = p.toString();
		assertThat(str, containsString(p.getAddress().toString()));
		assertThat(str, containsString(p.getAmount().toString()));
		assertThat(str, containsString(p.getDelegateAddress().toString()));
	}

	private static StakedTokensParticle get() {
		return new StakedTokensParticle(
			DELEGATE_ADDRESS,
			ADDRESS,
			AMOUNT,
			GRANULARITY,
			TOKEN,
			PLANCK,
			TOKEN_PERMISSIONS
		);
	}
}
