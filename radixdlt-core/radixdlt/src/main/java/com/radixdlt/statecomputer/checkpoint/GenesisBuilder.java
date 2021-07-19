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
import com.radixdlt.application.system.NextValidatorSetEvent;
import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atom.actions.NextEpoch;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.store.InMemoryEngineStore;

import java.util.List;

public final class GenesisBuilder {
	private static final String RADIX_ICON_URL  = "https://assets.radixdlt.com/icons/icon-xrd-32x32.png";
	private static final String RADIX_TOKEN_URL = "https://tokens.radixdlt.com/";

	private final LedgerAccumulator ledgerAccumulator;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;

	@Inject
	public GenesisBuilder(
		Forks forks,
		LedgerAccumulator ledgerAccumulator
	) {
		this.ledgerAccumulator = ledgerAccumulator;
		final var rules = forks.genesisFork().engineRules();
		var cmConfig = rules.getConstraintMachineConfig();
		var cm = new ConstraintMachine(
			cmConfig.getProcedures(),
			cmConfig.getDeserialization(),
			cmConfig.getVirtualSubstateDeserialization(),
			cmConfig.getMeter()
		);
		this.radixEngine = new RadixEngine<>(
			rules.getParser(),
			rules.getSerialization(),
			rules.getActionConstructors(),
			cm,
			new InMemoryEngineStore<>(),
			rules.getPostProcessor()
		);
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
		txnConstructionRequest.action(new NextEpoch(timestamp));

		radixEngine.deleteBranches();
		return radixEngine.construct(txnConstructionRequest).buildWithoutSignature();
	}

	public LedgerProof generateGenesisProof(Txn txn) throws RadixEngineException {
		var branch = radixEngine.transientBranch();
		var result = branch.execute(List.of(txn), PermissionLevel.SYSTEM);
		radixEngine.deleteBranches();
		var genesisValidatorSet = result.getProcessedTxn().getEvents().stream()
			.filter(NextValidatorSetEvent.class::isInstance)
			.map(NextValidatorSetEvent.class::cast)
			.findFirst()
			.map(e -> BFTValidatorSet.from(
				e.nextValidators().stream()
					.map(v -> BFTValidator.from(BFTNode.create(v.getValidatorKey()), v.getAmount())))
			).orElseThrow(() -> new IllegalStateException("No validator set in genesis."));

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
