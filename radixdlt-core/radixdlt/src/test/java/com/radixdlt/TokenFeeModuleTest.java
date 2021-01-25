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

import java.util.List;
import java.util.Map;

import org.assertj.core.util.Lists;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.AtomChecker;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.UInt256;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenFeeModuleTest {
	private static class SupportModule extends AbstractModule {
		private final int tokenCount;

		private SupportModule(int tokenCount) {
			this.tokenCount = tokenCount;
		}

		@Override
		protected void configure() {
			bind(Serialization.class).toInstance(mock(Serialization.class));

			Universe universe = mock(Universe.class);
			final RadixAddress address = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
			RRI rri = RRI.of(address, "XRD");
			Map<TokenTransition, TokenPermission> permissions = ImmutableMap.of(
				TokenTransition.BURN, TokenPermission.ALL,
				TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
			);

			final List<ParticleGroup> particleGroups = Lists.newArrayList();
			for (int i = 0; i < this.tokenCount; ++i) {
				particleGroups.add(
					ParticleGroup.of(
						SpunParticle.up(
							new MutableSupplyTokenDefinitionParticle(
								rri,
								"Test token",
								"Test token",
								UInt256.ONE,
								null,
								null,
								permissions
							)
						)
					)
				);
			}
			Atom atom = new Atom(particleGroups, ImmutableMap.of());
			when(universe.getGenesis()).thenReturn(ImmutableList.of(atom));
			bind(Universe.class).toInstance(universe);
		}
	}

	@Test
	public void testTokenFeeModuleMutableXrd() {
		Injector injector = Guice.createInjector(
			new SupportModule(1),
			new TokenFeeModule()
		);

		AtomChecker<LedgerAtom> checker = injector.getInstance(Key.get(new TypeLiteral<AtomChecker<LedgerAtom>>() { }));
		assertNotNull(checker);
	}

	@Test(expected = ProvisionException.class)
	public void testTokenFeeModuleMutableNoXrd() {
		Injector injector = Guice.createInjector(
			new SupportModule(0),
			new TokenFeeModule()
		);

		injector.getInstance(Key.get(new TypeLiteral<AtomChecker<LedgerAtom>>() { }));
		fail();
	}

	@Test(expected = ProvisionException.class)
	public void testTokenFeeModuleTwoXrd() {
		Injector injector = Guice.createInjector(
			new SupportModule(2),
			new TokenFeeModule()
		);

		injector.getInstance(Key.get(new TypeLiteral<AtomChecker<LedgerAtom>>() { }));
		fail();
	}
}
