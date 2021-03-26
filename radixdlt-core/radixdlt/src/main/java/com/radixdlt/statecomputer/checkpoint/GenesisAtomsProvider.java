/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.statecomputer.checkpoint;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.atom.Atom;
import com.radixdlt.utils.UInt256;
import org.radix.StakeDelegation;
import org.radix.TokenIssuance;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a genesis atom
 */
public final class GenesisAtomsProvider implements Provider<List<Atom>> {
	private final byte magic;
	private final ECKeyPair universeKey;
	private final ImmutableList<TokenIssuance> tokenIssuances;
	private final ImmutableList<ECKeyPair> validatorKeys;
	private final ImmutableList<StakeDelegation> stakeDelegations;
	private final MutableTokenDefinition tokenDefinition;

	@Inject
	public GenesisAtomsProvider(
		@Named("magic") int magic,
		@Named("universeKey") ECKeyPair universeKey, // TODO: Remove
		@NativeToken MutableTokenDefinition tokenDefinition,
		@Genesis ImmutableList<TokenIssuance> tokenIssuances,
		@Genesis ImmutableList<StakeDelegation> stakeDelegations,
		@Genesis ImmutableList<ECKeyPair> validatorKeys // TODO: Remove private keys, replace with public keys
	) {
		this.magic = (byte) magic;
		this.universeKey = universeKey;
		this.tokenDefinition = tokenDefinition;
		this.tokenIssuances = tokenIssuances;
		this.validatorKeys = validatorKeys;
		this.stakeDelegations = stakeDelegations;
	}

	@Override
	public List<Atom> get() {
		// Check that issuances are sufficient for delegations
		final var issuances = tokenIssuances.stream()
			.collect(ImmutableMap.toImmutableMap(TokenIssuance::receiver, TokenIssuance::amount, UInt256::add));
		final var requiredDelegations = stakeDelegations.stream()
			.collect(ImmutableMap.toImmutableMap(sd -> sd.staker().getPublicKey(), StakeDelegation::amount, UInt256::add));
		requiredDelegations.forEach((pk, amount) -> {
			final var issuedAmount = issuances.getOrDefault(pk, UInt256.ZERO);
			if (amount.compareTo(issuedAmount) > 0) {
				throw new IllegalStateException(
					String.format(
						"%s wants to stake %s, but was only issued %s",
						new RadixAddress(magic, pk), amount, issuedAmount
					)
				);
			}
		});

		var genesisAtoms = new ArrayList<Atom>();
		var universeAddress = new RadixAddress(magic, universeKey.getPublicKey());
		var rri = RRI.of(universeAddress, tokenDefinition.getSymbol());
		try {
			// Network token
			var tokenBuilder = TxBuilder.newBuilder(universeAddress)
				.createMutableToken(tokenDefinition);

			for (var e : issuances.entrySet()) {
				var to = new RadixAddress(magic, e.getKey());
				tokenBuilder.mint(rri, to, e.getValue());
			}
			var tokenAtom = tokenBuilder.signAndBuild(universeKey::sign);
			genesisAtoms.add(tokenAtom);

			var upParticles = tokenBuilder.upParticles();

			// Initial validator registration
			for (var validatorKey : validatorKeys) {
				var validatorAddress = new RadixAddress(magic, validatorKey.getPublicKey());
				var validatorBuilder = TxBuilder.newBuilder(validatorAddress, upParticles);
				var validatorAtom = validatorBuilder
					.registerAsValidator()
					.signAndBuild(validatorKey::sign);
				genesisAtoms.add(validatorAtom);

				upParticles = validatorBuilder.upParticles();
			}

			for (var stakeDelegation : stakeDelegations) {
				var stakerAddress = new RadixAddress(magic, stakeDelegation.staker().getPublicKey());
				var delegateAddress = new RadixAddress(magic, stakeDelegation.delegate());
				var stakesBuilder = TxBuilder.newBuilder(stakerAddress, upParticles)
					.stakeTo(rri, delegateAddress, stakeDelegation.amount());
				var stakeAtom = stakesBuilder.signAndBuild(stakeDelegation.staker()::sign);
				genesisAtoms.add(stakeAtom);
				upParticles = stakesBuilder.upParticles();
			}

			var epochUpdateBuilder = TxBuilder.newSystemBuilder()
				.systemNextEpoch(0);
			genesisAtoms.add(epochUpdateBuilder.buildWithoutSignature());
		} catch (TxBuilderException e) {
			throw new IllegalStateException(e);
		}

		return genesisAtoms;
	}
}
