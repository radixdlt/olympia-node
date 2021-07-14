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
import com.google.common.primitives.UnsignedBytes;
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
import com.radixdlt.utils.KeyComparator;
import com.radixdlt.utils.UInt256;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.TokenIssuance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Generates a genesis atom
 */
public final class GenesisProvider implements Provider<VerifiedTxnsAndProof> {
	private static final Logger logger = LogManager.getLogger();
	private final ImmutableList<TokenIssuance> tokenIssuances;
	private final Set<ECPublicKey> validatorKeys;
	private final Set<StakeTokens> stakeTokens;
	private final Optional<List<TxAction>> additionalActions;
	private final GenesisBuilder genesisBuilder;
	private final long timestamp;

	@Inject
	public GenesisProvider(
		GenesisBuilder genesisBuilder,
		@Genesis long timestamp,
		@Genesis ImmutableList<TokenIssuance> tokenIssuances,
		@Genesis Set<StakeTokens> stakeTokens,
		@Genesis Set<ECPublicKey> validatorKeys,
		@Genesis Optional<List<TxAction>> additionalActions
	) {
		this.genesisBuilder = genesisBuilder;
		this.timestamp = timestamp;
		this.tokenIssuances = tokenIssuances;
		this.validatorKeys = validatorKeys;
		this.stakeTokens = stakeTokens;
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

			validatorKeys.stream()
				.sorted(KeyComparator.instance())
				.forEach(k -> {
					actions.add(new RegisterValidator(k));
					actions.add(new UpdateAllowDelegationFlag(k, true));
				});

			stakeTokens.stream()
				.sorted(
					Comparator.<StakeTokens, byte[]>comparing(t -> t.from().getBytes(), UnsignedBytes.lexicographicalComparator())
						.thenComparing(t -> t.from().getBytes(), UnsignedBytes.lexicographicalComparator())
						.thenComparing(StakeTokens::amount)
				)
				.forEach(actions::add);

			additionalActions.ifPresent(actions::addAll);
			var genesis = genesisBuilder.build(timestamp, actions);

			logger.info("gen_create{tx_id={}}", genesis.getId());

			var proof = genesisBuilder.generateGenesisProof(genesis);
			return VerifiedTxnsAndProof.create(List.of(genesis), proof);
		} catch (TxBuilderException | RadixEngineException e) {
			throw new IllegalStateException(e);
		}
	}
}
