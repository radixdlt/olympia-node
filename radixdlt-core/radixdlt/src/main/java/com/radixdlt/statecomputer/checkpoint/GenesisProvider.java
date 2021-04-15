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
import com.radixdlt.atom.TxActionListBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.SystemNextEpoch;
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
import java.util.List;

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

		var branch = radixEngine.transientBranch();
		var genesisTxns = new ArrayList<Txn>();
		var universeAddress = new RadixAddress(magic, universeKey.getPublicKey());
		var rri = RRI.of(universeAddress, tokenDefinition.getSymbol());
		try {
			// Network token
			var createTokenActions = TxActionListBuilder.create()
				.createMutableToken(tokenDefinition);
			for (var e : issuances.entrySet()) {
				var to = new RadixAddress(magic, e.getKey());
				createTokenActions.mint(rri, to, e.getValue());
			}

			var tokenTxn = branch.construct(universeAddress, createTokenActions.build()).signAndBuild(universeKey::sign);
			branch.execute(List.of(tokenTxn), PermissionLevel.SYSTEM);
			genesisTxns.add(tokenTxn);

			// Initial validator registration
			for (var validatorKey : validatorKeys) {
				var validatorAddress = new RadixAddress(magic, validatorKey.getPublicKey());
				var validatorTxn = branch.construct(validatorAddress, new RegisterValidator())
					.signAndBuild(validatorKey::sign);
				branch.execute(List.of(validatorTxn), PermissionLevel.SYSTEM);
				genesisTxns.add(validatorTxn);
			}

			// Initial stakes
			for (var stakeDelegation : stakeDelegations) {
				var stakerAddress = new RadixAddress(magic, stakeDelegation.staker().getPublicKey());
				var delegateAddress = new RadixAddress(magic, stakeDelegation.delegate());
				var stakerTxn = branch.construct(stakerAddress, new StakeTokens(delegateAddress, stakeDelegation.amount()))
					.signAndBuild(stakeDelegation.staker()::sign);
				branch.execute(List.of(stakerTxn), PermissionLevel.SYSTEM);
				genesisTxns.add(stakerTxn);
			}

			var systemTxn = branch.construct(new SystemNextEpoch(0, 0))
				.buildWithoutSignature();
			branch.execute(List.of(systemTxn), PermissionLevel.SYSTEM);
			genesisTxns.add(systemTxn);
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
		} catch (TxBuilderException | RadixEngineException e) {
			throw new IllegalStateException(e);
		}
	}
}
