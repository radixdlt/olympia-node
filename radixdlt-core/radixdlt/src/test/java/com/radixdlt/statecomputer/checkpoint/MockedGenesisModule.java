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

import com.google.inject.Key;
import com.google.inject.multibindings.OptionalBinder;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import org.radix.TokenIssuance;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.radixdlt.atom.TxAction;
import com.radixdlt.ledger.VerifiedTxnsAndProof;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Genesis atom to be used with tests. Given a set of parameters the genesis transaction
 * generated should be deterministic.
 */
public final class MockedGenesisModule extends AbstractModule {
	private final Set<ECPublicKey> validators;
	private final Amount xrdPerValidator;
	private final Amount stakePerValidator;

	public MockedGenesisModule(Set<ECPublicKey> validators, Amount xrdPerValidator, Amount stakePerValidator) {
		this.validators = validators;
		this.xrdPerValidator = xrdPerValidator;
		this.stakePerValidator = stakePerValidator;
	}

	@Override
	public void configure() {
		bind(new TypeLiteral<Set<ECPublicKey>>() { }).annotatedWith(Genesis.class).toInstance(validators);
		bind(new TypeLiteral<VerifiedTxnsAndProof>() { }).annotatedWith(Genesis.class).toProvider(GenesisProvider.class).in(Scopes.SINGLETON);
		OptionalBinder.newOptionalBinder(binder(), Key.get(new TypeLiteral<List<TxAction>>() { }, Genesis.class));
	}

	@Provides
	@Genesis
	public long timestamp() {
		return 1234L;
	}

	@Provides
	@Genesis
	public Set<StakeTokens> stakeDelegations(@Genesis Set<ECPublicKey> validators) {
		return validators.stream()
			.map(v -> new StakeTokens(REAddr.ofPubKeyAccount(v), v, stakePerValidator.toSubunits()))
			.collect(Collectors.toSet());
	}

	@Provides
	@Genesis
	public ImmutableList<TokenIssuance> tokenIssuanceList(@Genesis Set<ECPublicKey> validators) {
		return validators.stream().map(v -> TokenIssuance.of(v, xrdPerValidator.toSubunits()))
			.sorted(Comparator.comparing(t -> t.receiver().toHex()))
			.collect(ImmutableList.toImmutableList());
	}
}
