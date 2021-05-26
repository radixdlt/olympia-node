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
import com.radixdlt.atommodel.system.state.StakeShare;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class NextEpochConstructorV2 implements ActionConstructor<SystemNextEpoch> {
	@Override
	public void construct(SystemNextEpoch action, TxBuilder txBuilder) throws TxBuilderException {

		var allPreparedStake = txBuilder.downAll(PreparedStake.class, i -> {
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

		allPreparedStake.forEach((k, stakes) -> {
			stakes.forEach((addr, amt) -> txBuilder.up(new StakeShare(k, addr, amt)));

			var totalPreparedStake = stakes.values().stream().reduce(UInt256::add).orElseThrow();
			txBuilder.up(new Stake(totalPreparedStake, k));
		});

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

		txBuilder.swap(
			RoundData.class,
			p -> true,
			Optional.of(SubstateWithArg.noArg(new RoundData(0, 0))),
			"No round data available"
		).with(substateDown -> List.of(new RoundData(0, 0)));
	}
}
