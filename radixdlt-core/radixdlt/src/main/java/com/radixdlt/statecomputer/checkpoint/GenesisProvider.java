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
import com.radixdlt.atom.SubstateStore;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.RegisteredValidators;
import com.radixdlt.statecomputer.Stakes;
import com.radixdlt.statecomputer.ValidatorSetBuilder;
import com.radixdlt.utils.UInt256;
import org.radix.StakeDelegation;
import org.radix.TokenIssuance;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generates a genesis atom
 */
public final class GenesisProvider implements Provider<VerifiedTxnsAndProof> {
	private final byte magic;
	private final ECKeyPair universeKey;
	private final ImmutableList<TokenIssuance> tokenIssuances;
	private final ImmutableList<ECKeyPair> validatorKeys;
	private final ImmutableList<StakeDelegation> stakeDelegations;
	private final MutableTokenDefinition tokenDefinition;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final ValidatorSetBuilder validatorSetBuilder;
	private final LedgerAccumulator ledgerAccumulator;

	@Inject
	public GenesisProvider(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		ValidatorSetBuilder validatorSetBuilder,
		LedgerAccumulator ledgerAccumulator,
		@Named("magic") int magic,
		@Named("universeKey") ECKeyPair universeKey, // TODO: Remove
		@NativeToken MutableTokenDefinition tokenDefinition,
		@Genesis ImmutableList<TokenIssuance> tokenIssuances,
		@Genesis ImmutableList<StakeDelegation> stakeDelegations,
		@Genesis ImmutableList<ECKeyPair> validatorKeys // TODO: Remove private keys, replace with public keys
	) {
		this.radixEngine = radixEngine;
		this.validatorSetBuilder = validatorSetBuilder;
		this.ledgerAccumulator = ledgerAccumulator;
		this.magic = (byte) magic;
		this.universeKey = universeKey;
		this.tokenDefinition = tokenDefinition;
		this.tokenIssuances = tokenIssuances;
		this.validatorKeys = validatorKeys;
		this.stakeDelegations = stakeDelegations;
	}

	@Override
	public VerifiedTxnsAndProof get() {
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

		var genesisTxns = new ArrayList<Txn>();
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
			var upSubstate = new AtomicReference<SubstateStore>();
			var tokenAtom = tokenBuilder.signAndBuild(universeKey::sign, upSubstate::set);
			genesisTxns.add(tokenAtom);

			// Initial validator registration
			for (var validatorKey : validatorKeys) {
				var validatorAddress = new RadixAddress(magic, validatorKey.getPublicKey());
				var validatorBuilder = TxBuilder.newBuilder(validatorAddress, upSubstate.get());
				var validatorAtom = validatorBuilder
					.registerAsValidator()
					.signAndBuild(validatorKey::sign, upSubstate::set);
				genesisTxns.add(validatorAtom);
			}

			for (var stakeDelegation : stakeDelegations) {
				var stakerAddress = new RadixAddress(magic, stakeDelegation.staker().getPublicKey());
				var delegateAddress = new RadixAddress(magic, stakeDelegation.delegate());
				var stakesBuilder = TxBuilder.newBuilder(stakerAddress, upSubstate.get())
					.stakeTo(delegateAddress, stakeDelegation.amount());
				var stakeAtom = stakesBuilder.signAndBuild(stakeDelegation.staker()::sign, upSubstate::set);
				genesisTxns.add(stakeAtom);
			}

			var epochUpdateBuilder = TxBuilder.newSystemBuilder().systemNextEpoch(0, 0);
			genesisTxns.add(epochUpdateBuilder.buildWithoutSignature());
		} catch (TxBuilderException e) {
			throw new IllegalStateException(e);
		}

		try {
			var branch = radixEngine.transientBranch();
			branch.execute(genesisTxns, PermissionLevel.SYSTEM);
			final var genesisValidatorSet = validatorSetBuilder.buildValidatorSet(
				branch.getComputedState(RegisteredValidators.class),
				branch.getComputedState(Stakes.class)
			);
			radixEngine.deleteBranches();

			AccumulatorState accumulatorState = null;

			for (var txn : genesisTxns) {
				if (accumulatorState == null) {
					accumulatorState = new AccumulatorState(0, txn.getId().asHashCode());
				} else {
					accumulatorState = ledgerAccumulator.accumulate(accumulatorState, txn.getId().asHashCode());
				}
			}

			var genesisProof = LedgerProof.genesis(
				accumulatorState,
				genesisValidatorSet
			);
			if (!genesisProof.isEndOfEpoch()) {
				throw new IllegalStateException("Genesis must be end of epoch");
			}

			return VerifiedTxnsAndProof.create(genesisTxns, genesisProof);
		} catch (RadixEngineException e) {
			throw new IllegalStateException();
		}
	}
}
