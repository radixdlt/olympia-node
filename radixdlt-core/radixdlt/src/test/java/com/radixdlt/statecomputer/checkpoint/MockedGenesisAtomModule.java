/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.statecomputer.checkpoint;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.radixdlt.atom.Atom;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.UInt256;
import org.radix.StakeDelegation;
import org.radix.TokenIssuance;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Genesis atom to be used with tests
 */
public final class MockedGenesisAtomModule extends AbstractModule {
	@Override
	public void configure() {
	    install(new RadixNativeTokenModule());
		bindConstant().annotatedWith(Names.named("magic")).to(0);
		Multibinder.newSetBinder(binder(), TokenIssuance.class);
		bind(new TypeLiteral<List<Atom>>() { }).annotatedWith(Genesis.class).toProvider(GenesisAtomsProvider.class).in(Scopes.SINGLETON);
	}

	@Provides
	@Genesis
	public ImmutableList<StakeDelegation> stakeDelegations(
		@Genesis ImmutableList<ECKeyPair> initialValidators
	) {
		return initialValidators.stream().map(v -> StakeDelegation.of(v, v.getPublicKey(), UInt256.ONE))
			.collect(ImmutableList.toImmutableList());
	}

	@Provides
	@Genesis
	public ImmutableList<TokenIssuance> tokenIssuanceList(
		Set<TokenIssuance> tokenIssuanceSet,
		@Genesis ImmutableList<ECKeyPair> initialValidators
	) {
		return Stream.concat(
			tokenIssuanceSet.stream(),
			initialValidators.stream().map(v -> TokenIssuance.of(v.getPublicKey(), UInt256.ONE))
		)
			.sorted(Comparator.comparing(t -> t.receiver().toBase58()))
			.collect(ImmutableList.toImmutableList());
	}
}
