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
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxActionListBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.SystemNextEpoch;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.StakedValidators;
import com.radixdlt.utils.UInt256;
import org.radix.StakeDelegation;
import org.radix.TokenIssuance;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Generates a genesis atom
 */
public final class GenesisProvider implements Provider<VerifiedTxnsAndProof> {
	private final ImmutableList<TokenIssuance> tokenIssuances;
	private final ImmutableList<ECKeyPair> validatorKeys;
	private final ImmutableList<StakeDelegation> stakeDelegations;
	private final MutableTokenDefinition tokenDefinition;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final LedgerAccumulator ledgerAccumulator;
	private final long timestamp;
	private final List<TxAction> additionalActions;

	@Inject
	public GenesisProvider(
		@Genesis long timestamp,
		RadixEngine<LedgerAndBFTProof> radixEngine,
		LedgerAccumulator ledgerAccumulator,
		@NativeToken MutableTokenDefinition tokenDefinition,
		@Genesis ImmutableList<TokenIssuance> tokenIssuances,
		@Genesis ImmutableList<StakeDelegation> stakeDelegations,
		@Genesis ImmutableList<ECKeyPair> validatorKeys, // TODO: Remove private keys, replace with public keys
		@Genesis List<TxAction> additionalActions
	) {
		this.timestamp = timestamp;
		this.radixEngine = radixEngine;
		this.ledgerAccumulator = ledgerAccumulator;
		this.tokenDefinition = tokenDefinition;
		this.tokenIssuances = tokenIssuances;
		this.validatorKeys = validatorKeys;
		this.stakeDelegations = stakeDelegations;
		this.additionalActions = additionalActions;
	}

	@Override
	public VerifiedTxnsAndProof get() {
		// Check that issuances are sufficient for delegations
		final var issuances = tokenIssuances.stream()
			.collect(ImmutableMap.toImmutableMap(TokenIssuance::receiver, TokenIssuance::amount, UInt256::add));
		final var requiredDelegations = stakeDelegations.stream()
			.collect(ImmutableMap.toImmutableMap(StakeDelegation::staker, StakeDelegation::amount, UInt256::add));
		requiredDelegations.forEach((pk, amount) -> {
			final var issuedAmount = issuances.getOrDefault(pk, UInt256.ZERO);
			if (amount.compareTo(issuedAmount) > 0) {
				throw new IllegalStateException(
					String.format("%s wants to stake %s, but was only issued %s", pk, amount, issuedAmount)
				);
			}
		});

		var branch = radixEngine.transientBranch();
		var genesisBuilder = TxActionListBuilder.create();
		var rri = REAddr.ofNativeToken();
		try {
			// Initialize system address
			genesisBuilder.action(new CreateSystem());

			// Network token
			genesisBuilder.createMutableToken(tokenDefinition);
			for (var e : issuances.entrySet()) {
				var addr = REAddr.ofPubKeyAccount(e.getKey());
				genesisBuilder.mint(rri, addr, e.getValue());
			}

			// Initial validator registration
			for (var validatorKey : validatorKeys) {
				genesisBuilder.registerAsValidator(validatorKey.getPublicKey());
			}

			// Initial stakes
			for (var stakeDelegation : stakeDelegations) {
				var delegateAddr = REAddr.ofPubKeyAccount(stakeDelegation.staker());
				var stakeTokens = new StakeTokens(delegateAddr, stakeDelegation.delegate(), stakeDelegation.amount());
				genesisBuilder.action(stakeTokens);
			}

			if (!additionalActions.isEmpty()) {
				additionalActions.forEach(genesisBuilder::action);
			}
			var temp = branch.construct(genesisBuilder.build()).buildWithoutSignature();
			branch.execute(List.of(temp), PermissionLevel.SYSTEM);

			var stakedValidators = branch.getComputedState(StakedValidators.class);
			var genesisValidatorSet = new AtomicReference<BFTValidatorSet>();
			genesisBuilder.action(new SystemNextEpoch(updates -> {
				var cur = stakedValidators;
				for (var u : updates) {
					cur = cur.setStake(u.getValidatorKey(), u.getTotalStake());
				}
				// FIXME: cur.toValidatorSet() may be null
				genesisValidatorSet.set(cur.toValidatorSet());
				return genesisValidatorSet.get().nodes().stream()
					.map(BFTNode::getKey)
					.sorted(Comparator.comparing(ECPublicKey::getBytes, Arrays::compare))
					.collect(Collectors.toList());
			}, timestamp));
			radixEngine.deleteBranches();
			var txn = radixEngine.construct(genesisBuilder.build()).buildWithoutSignature();

			var accumulatorState = new AccumulatorState(0, txn.getId().asHashCode());
			var genesisProof = LedgerProof.genesis(
				accumulatorState,
				genesisValidatorSet.get(),
				timestamp
			);
			if (!genesisProof.isEndOfEpoch()) {
				throw new IllegalStateException("Genesis must be end of epoch");
			}

			return VerifiedTxnsAndProof.create(List.of(txn), genesisProof);
		} catch (TxBuilderException | RadixEngineException e) {
			throw new IllegalStateException(e);
		}
	}
}
