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

import com.google.inject.Inject;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atom.actions.NextEpoch;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.StakedValidators;
import com.radixdlt.statecomputer.StakedValidatorsReducer;
import com.radixdlt.statecomputer.forks.ForkBuilder;
import com.radixdlt.store.InMemoryEngineStore;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class GenesisBuilder {
	private static final String RADIX_ICON_URL  = "https://assets.radixdlt.com/icons/icon-xrd-32x32.png";
	private static final String RADIX_TOKEN_URL = "https://tokens.radixdlt.com/";

	private final LedgerAccumulator ledgerAccumulator;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;

	@Inject
	public GenesisBuilder(
		Set<ForkBuilder> forks,
		LedgerAccumulator ledgerAccumulator,
		StakedValidatorsReducer reducer
	) {
		final var genesisFork = forks.stream()
			.filter(f -> f.fixedOrMinEpoch() == 0L)
			.findFirst()
			.orElseThrow()
			.build();

		this.ledgerAccumulator = ledgerAccumulator;
		final var rules = genesisFork.getEngineRules();
		var cmConfig = rules.getConstraintMachineConfig();
		var cm = new ConstraintMachine(
			cmConfig.getVirtualStoreLayer(),
			cmConfig.getProcedures(),
			cmConfig.getMeter()
		);
		this.radixEngine = new RadixEngine<>(
			rules.getParser(),
			rules.getSerialization(),
			rules.getActionConstructors(),
			cm,
			new InMemoryEngineStore<>(),
			rules.getBatchVerifier()
		);
		radixEngine.addStateReducer(reducer, true);
	}

	public Txn build(long timestamp, List<TxAction> actions) throws TxBuilderException, RadixEngineException {
		var txnConstructionRequest = TxnConstructionRequest.create();
		txnConstructionRequest.action(new CreateSystem(timestamp));

		var tokenDef = new MutableTokenDefinition(
			null,
			"xrd",
			"Rads",
			"Radix Tokens",
			RADIX_ICON_URL,
			RADIX_TOKEN_URL
		);
		txnConstructionRequest.createMutableToken(tokenDef);
		actions.forEach(txnConstructionRequest::action);
		var tempTxn = Txn.create(radixEngine.construct(txnConstructionRequest).buildForExternalSign().blob());
		var branch = radixEngine.transientBranch();

		branch.execute(List.of(tempTxn), PermissionLevel.SYSTEM);
		var stakedValidators = branch.getComputedState(StakedValidators.class);
		var genesisValidatorSet = new AtomicReference<BFTValidatorSet>();
		txnConstructionRequest.action(new NextEpoch(updates -> {
			var cur = stakedValidators;
			for (var u : updates) {
				cur = cur.setStake(u.getValidatorKey(), u.getAmount());
			}
			var validatorSet = cur.toValidatorSet();
			if (validatorSet == null) {
				throw new IllegalStateException("No validator set created in genesis.");
			}
			// FIXME: cur.toValidatorSet() may be null
			genesisValidatorSet.set(validatorSet);
			return genesisValidatorSet.get().nodes().stream()
				.map(BFTNode::getKey)
				.sorted(Comparator.comparing(ECPublicKey::getBytes, Arrays::compare))
				.collect(Collectors.toList());
		}, timestamp));

		radixEngine.deleteBranches();
		return radixEngine.construct(txnConstructionRequest).buildWithoutSignature();
	}

	public LedgerProof generateGenesisProof(Txn txn) throws RadixEngineException {
		var branch = radixEngine.transientBranch();
		branch.execute(List.of(txn), PermissionLevel.SYSTEM);
		var stakedValidators = branch.getComputedState(StakedValidators.class);
		var genesisValidatorSet = stakedValidators.toValidatorSet();
		radixEngine.deleteBranches();

		var init = new AccumulatorState(0, HashUtils.zero256());
		var accumulatorState = ledgerAccumulator.accumulate(init, txn.getId().asHashCode());
		var genesisProof = LedgerProof.genesis(
			accumulatorState,
			genesisValidatorSet,
			0L
		);
		if (!genesisProof.isEndOfEpoch()) {
			throw new IllegalStateException("Genesis must be end of epoch");
		}

		return genesisProof;
	}
}
