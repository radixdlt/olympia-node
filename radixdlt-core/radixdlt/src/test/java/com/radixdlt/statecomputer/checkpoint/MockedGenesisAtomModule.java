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
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.consensus.GenesisValidatorSetProvider;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.utils.UInt256;
import org.radix.StakeDelegation;
import org.radix.TokenIssuance;

import java.util.Comparator;
import java.util.Set;

public final class MockedGenesisAtomModule extends AbstractModule {
	@Override
	public void configure() {
	    install(new RadixNativeTokenModule());
		bindConstant().annotatedWith(Names.named("magic")).to(0);
		Multibinder.newSetBinder(binder(), TokenIssuance.class);
		bind(Atom.class).annotatedWith(Genesis.class).toProvider(GenesisAtomProvider.class).in(Scopes.SINGLETON);
		bind(UInt256.class).annotatedWith(Genesis.class)
			.toInstance(UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 + 9));
	}

	// TODO: Remove
	@Provides
	private GenesisValidatorSetProvider genesisValidatorSetProvider(@Genesis ImmutableList<ECKeyPair> genesisValidatorKeys) {
		return () -> BFTValidatorSet.from(genesisValidatorKeys.stream()
			.map(k -> BFTValidator.from(BFTNode.create(k.getPublicKey()), UInt256.ONE)));
	}

	@Provides
	@Genesis
	public ImmutableList<StakeDelegation> stakeDelegations() {
		return ImmutableList.of();
	}

	@Provides
	@Genesis
	public ImmutableList<TokenIssuance> tokenIssuanceList(Set<TokenIssuance> tokenIssuanceSet) {
		return tokenIssuanceSet.stream()
			.sorted(Comparator.comparing(t -> t.receiver().toBase58()))
			.collect(ImmutableList.toImmutableList());
	}
}
