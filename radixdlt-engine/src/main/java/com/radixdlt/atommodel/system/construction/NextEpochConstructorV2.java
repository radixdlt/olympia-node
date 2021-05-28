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
import com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV2;
import com.radixdlt.atommodel.system.state.EpochData;
import com.radixdlt.atommodel.system.state.HasEpochData;
import com.radixdlt.atommodel.system.state.RoundData;
import com.radixdlt.atommodel.system.state.ValidatorStake;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.system.state.ValidatorEpochData;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.PreparedUnstakeOwned;
import com.radixdlt.atommodel.tokens.state.TokensParticle;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import static com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV2.EPOCHS_LOCKED;

public final class NextEpochConstructorV2 implements ActionConstructor<SystemNextEpoch> {
	@Override
	public void construct(SystemNextEpoch action, TxBuilder txBuilder) throws TxBuilderException {
		updateEpoch(action.validators(), txBuilder);
		updateRoundData(txBuilder);
	}

	private void updateEpoch(List<ECPublicKey> validatorKeys, TxBuilder txBuilder) throws TxBuilderException {
		var epochData = txBuilder.find(EpochData.class, p -> true);
		final HasEpochData prevEpoch;
		if (epochData.isPresent()) {
			prevEpoch = txBuilder.down(
				EpochData.class,
				p -> true,
				Optional.of(SubstateWithArg.noArg(new EpochData(0))),
				"No epoch data available"
			);
		} else {
			prevEpoch = txBuilder.down(
				SystemParticle.class,
				p -> true,
				"No epoch data available"
			);
		}
		long epochUnlocked = prevEpoch.getEpoch() + 1 + EPOCHS_LOCKED;

		var validatorsToUpdate = new TreeMap<ECPublicKey, ValidatorStake>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);
		var proposals = txBuilder.shutdownAll(ValidatorEpochData.class, i -> {
			final TreeMap<ECPublicKey, Long> proposalsCompleted = new TreeMap<>(
				(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
			);
			i.forEachRemaining(e -> proposalsCompleted.put(e.validatorKey(), e.proposalsCompleted()));
			return proposalsCompleted;
		});
		for (var e : proposals.entrySet()) {
			var k = e.getKey();
			var numProposals = e.getValue();
			var currentStake = txBuilder.down(
				ValidatorStake.class,
				s -> s.getValidatorKey().equals(k),
				"Validator not found"
			);
			var emission = SystemConstraintScryptV2.REWARDS_PER_PROPOSAL.multiply(UInt256.from(numProposals));
			validatorsToUpdate.put(k, currentStake.addEmission(emission));
		}

		var allPreparedUnstake = txBuilder.shutdownAll(PreparedUnstakeOwned.class, i -> {
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
		for (var e : allPreparedUnstake.entrySet()) {
			var k = e.getKey();
			if (!validatorsToUpdate.containsKey(k)) {
				var currentStake = txBuilder.down(
					ValidatorStake.class,
					p -> p.getValidatorKey().equals(k),
					Optional.of(SubstateWithArg.noArg(new ValidatorStake(k, UInt256.ZERO, UInt256.ZERO))),
					"Validator not found"
				);
				validatorsToUpdate.put(k, currentStake);
			}

			var unstakes = e.getValue();
			var curValidatorStake = validatorsToUpdate.get(k);
			for (var entry : unstakes.entrySet()) {
				var addr = entry.getKey();
				var amt = entry.getValue();
				var nextStakeAndAmt = curValidatorStake.unstakeOwnership(amt);
				curValidatorStake = nextStakeAndAmt.getFirst();
				var unstakedAmt = nextStakeAndAmt.getSecond();
				txBuilder.up(new TokensParticle(addr, unstakedAmt, REAddr.ofNativeToken(), epochUnlocked));
			}
			validatorsToUpdate.put(k, curValidatorStake);
		}

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
			if (!validatorsToUpdate.containsKey(k)) {
				var currentStake = txBuilder.down(
					ValidatorStake.class,
					p -> p.getValidatorKey().equals(k),
					Optional.of(SubstateWithArg.noArg(new ValidatorStake(k, UInt256.ZERO, UInt256.ZERO))),
					"Validator not found"
				);
				validatorsToUpdate.put(k, currentStake);
			}
			var curValidator = validatorsToUpdate.get(k);
			for (var entry : stakes.entrySet()) {
				var addr = entry.getKey();
				var amt = entry.getValue();

				var nextValidatorAndOwnership = curValidator.stake(addr, amt);
				curValidator = nextValidatorAndOwnership.getFirst();
				var stakeOwnership = nextValidatorAndOwnership.getSecond();
				txBuilder.up(stakeOwnership);
			}
			validatorsToUpdate.put(k, curValidator);
		}

		validatorsToUpdate.forEach((k, validator) -> txBuilder.up(validator));
		validatorKeys.forEach(k -> txBuilder.up(new ValidatorEpochData(k, 0)));

		txBuilder.up(new EpochData(prevEpoch.getEpoch() + 1));
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
