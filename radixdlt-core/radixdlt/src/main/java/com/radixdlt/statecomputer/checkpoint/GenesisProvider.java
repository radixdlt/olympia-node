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
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.UpdateAllowDelegationFlag;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.utils.UInt256;
import org.radix.StakeDelegation;
import org.radix.TokenIssuance;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a genesis atom
 */
public final class GenesisProvider implements Provider<VerifiedTxnsAndProof> {
	private final ImmutableList<TokenIssuance> tokenIssuances;
	private final ImmutableList<ECPublicKey> validatorKeys;
	private final ImmutableList<StakeDelegation> stakeDelegations;
	private final List<TxAction> additionalActions;
	private final GenesisBuilder genesisBuilder;
	private final long timestamp;

	@Inject
	public GenesisProvider(
		GenesisBuilder genesisBuilder,
		@Genesis long timestamp,
		@Genesis ImmutableList<TokenIssuance> tokenIssuances,
		@Genesis ImmutableList<StakeDelegation> stakeDelegations,
		@Genesis ImmutableList<ECPublicKey> validatorKeys,
		@Genesis List<TxAction> additionalActions
	) {
		this.genesisBuilder = genesisBuilder;
		this.timestamp = timestamp;
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

		var actions = new ArrayList<TxAction>();
		var rri = REAddr.ofNativeToken();
		try {
			for (var e : issuances.entrySet()) {
				var addr = REAddr.ofPubKeyAccount(e.getKey());
				actions.add(new MintToken(rri, addr, e.getValue()));
			}

			// Initial validator registration
			for (var validatorKey : validatorKeys) {
				actions.add(new RegisterValidator(validatorKey));
				actions.add(new UpdateAllowDelegationFlag(validatorKey, true));
			}

			// Initial stakes
			for (var stakeDelegation : stakeDelegations) {
				var delegateAddr = REAddr.ofPubKeyAccount(stakeDelegation.staker());
				actions.add(new StakeTokens(delegateAddr, stakeDelegation.delegate(), stakeDelegation.amount()));
			}

			actions.addAll(additionalActions);
			var genesis = genesisBuilder.build(timestamp, actions);
			var proof = genesisBuilder.generateGenesisProof(genesis);
			return VerifiedTxnsAndProof.create(List.of(genesis), proof);
		} catch (TxBuilderException | RadixEngineException e) {
			throw new IllegalStateException(e);
		}
	}
}
