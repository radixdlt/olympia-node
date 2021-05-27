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

package com.radixdlt.atommodel.system.construction;

import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.SystemNextEpoch;
import com.radixdlt.atommodel.system.state.EpochData;
import com.radixdlt.atommodel.system.state.RoundData;
import com.radixdlt.atommodel.system.state.Stake;
import com.radixdlt.atommodel.system.state.StakeShares;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.system.state.ValidatorEpochData;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

public final class NextEpochConstructorV2 implements ActionConstructor<SystemNextEpoch> {


	@Override
	public void construct(SystemNextEpoch action, TxBuilder txBuilder) throws TxBuilderException {
		emissionsAndNextValidatorSet(action.validators(), txBuilder);
		updatePreparedStake(txBuilder);
		updateEpochData(txBuilder);
		updateRoundData(txBuilder);
	}

	public void emissionsAndNextValidatorSet(List<ECPublicKey> validators, TxBuilder txBuilder) {
		txBuilder.shutdownAll(ValidatorEpochData.class, i -> null);
		validators.forEach(k -> txBuilder.up(new ValidatorEpochData(k, 0)));
	}

	private void updatePreparedStake(TxBuilder txBuilder) throws TxBuilderException {
		var allPreparedStake = txBuilder.shutdownAll(PreparedStake.class, i -> {
			var map = new TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>>(
				(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
			);
			i.forEachRemaining(preparedStake ->
				map
					.computeIfAbsent(
						preparedStake.getDelegateKey(),
						k -> new TreeMap<>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()))
					)
					.merge(preparedStake.getOwner(), preparedStake.getAmount(), UInt256::add)
			);
			return map;
		});
		for (var e : allPreparedStake.entrySet()) {
			var k = e.getKey();
			var stakes = e.getValue();
			stakes.forEach((addr, amt) -> txBuilder.up(new StakeShares(k, addr, amt)));
			var totalPreparedStake = stakes.values().stream().reduce(UInt256::add).orElseThrow();
			txBuilder.swap(
				Stake.class,
				p -> p.getValidatorKey().equals(k),
				Optional.of(SubstateWithArg.noArg(new Stake(UInt256.ZERO, k))),
				"Validator not found"
			).with(s -> List.of(new Stake(s.getAmount().add(totalPreparedStake), s.getValidatorKey())));
		}
	}

	private void updateEpochData(TxBuilder txBuilder) throws TxBuilderException {
		var epochData = txBuilder.find(EpochData.class, p -> true);
		if (epochData.isPresent()) {
			txBuilder.swap(
				EpochData.class,
				p -> true,
				Optional.of(SubstateWithArg.noArg(new EpochData(0))),
				"No epoch data available"
			).with(substateDown -> List.of(new EpochData(substateDown.getEpoch() + 1)));
		} else {
			txBuilder.swap(
				SystemParticle.class,
				p -> true,
				"No epoch data available"
			).with(substateDown -> List.of(new EpochData(substateDown.getEpoch() + 1)));
		}
	}

	private void updateRoundData(TxBuilder txBuilder) throws TxBuilderException {
		txBuilder.swap(
			RoundData.class,
			p -> true,
			Optional.of(SubstateWithArg.noArg(new RoundData(0, 0))),
			"No round data available"
		).with(substateDown -> List.of(new RoundData(0, 0)));
	}
}
