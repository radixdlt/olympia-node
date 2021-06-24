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
import com.radixdlt.atommodel.system.state.ValidatorStakeData;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.system.state.ValidatorBFTData;
import com.radixdlt.atommodel.tokens.state.ExittingStake;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Arrays;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

public final class NextEpochConstructorV2 implements ActionConstructor<SystemNextEpoch> {
	@Override
	public void construct(SystemNextEpoch action, TxBuilder txBuilder) throws TxBuilderException {
		var epochData = txBuilder.find(EpochData.class, p -> true);
		final HasEpochData prevEpoch;
		if (epochData.isPresent()) {
			txBuilder.down(
				RoundData.class,
				p -> true,
				Optional.empty(),
				"No round data available"
			);
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

		var exitting = txBuilder.shutdownAll(ExittingStake.class, i -> {
			final TreeSet<ExittingStake> exit = new TreeSet<>(
				(o1, o2) -> Arrays.compare(o1.dataKey(), o2.dataKey())
			);
			i.forEachRemaining(exit::add);
			return exit;
		});
		for (var e : exitting) {
			if (e.getEpochUnlocked() == prevEpoch.getEpoch()) {
				txBuilder.up(e.unlock());
			} else {
				txBuilder.up(e); // TODO: optimize and remove this unneed read/write
			}
		}

		var validatorsToUpdate = new TreeMap<ECPublicKey, ValidatorStakeData>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);
		var proposals = txBuilder.shutdownAll(ValidatorBFTData.class, i -> {
			final TreeMap<ECPublicKey, Long> proposalsCompleted = new TreeMap<>(
				(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
			);
			i.forEachRemaining(e -> proposalsCompleted.put(e.validatorKey(), e.proposalsCompleted()));
			return proposalsCompleted;
		});
		var preparingStake = new TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);
		for (var e : proposals.entrySet()) {
			var k = e.getKey();
			var numProposals = e.getValue();
			var currentStake = txBuilder.down(
				ValidatorStakeData.class,
				s -> s.getValidatorKey().equals(k),
				"Validator not found"
			);
			var nodeEmission = SystemConstraintScryptV2.REWARDS_PER_PROPOSAL.multiply(UInt256.from(numProposals));
			final UInt256 noneFeeEmissions;
			if (!SystemConstraintScryptV2.CONSTANT_FEE.isZero() && nodeEmission.compareTo(SystemConstraintScryptV2.CONSTANT_FEE) >= 0) {
				var validatorOwner = REAddr.ofPubKeyAccount(k);
				var initStake = new TreeMap<REAddr, UInt256>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()));
				initStake.put(validatorOwner, SystemConstraintScryptV2.CONSTANT_FEE);
				preparingStake.put(k, initStake);
				noneFeeEmissions = nodeEmission.subtract(SystemConstraintScryptV2.CONSTANT_FEE);
			} else {
				noneFeeEmissions = nodeEmission;
			}
			validatorsToUpdate.put(k, currentStake.addEmission(noneFeeEmissions));
		}

		var allPreparedUnstake = txBuilder.shutdownAll(PreparedUnstakeOwnership.class, i -> {
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
					ValidatorStakeData.class,
					p -> p.getValidatorKey().equals(k),
					Optional.of(SubstateWithArg.noArg(ValidatorStakeData.createV1(k))),
					"Validator not found"
				);
				validatorsToUpdate.put(k, currentStake);
			}

			var unstakes = e.getValue();
			var curValidatorStake = validatorsToUpdate.get(k);
			for (var entry : unstakes.entrySet()) {
				var addr = entry.getKey();
				var amt = entry.getValue();
				var nextStakeAndAmt = curValidatorStake.unstakeOwnership(addr, amt, prevEpoch.getEpoch());
				curValidatorStake = nextStakeAndAmt.getFirst();
				var exittingStake = nextStakeAndAmt.getSecond();
				txBuilder.up(exittingStake);
			}
			validatorsToUpdate.put(k, curValidatorStake);
		}

		var allPreparedStake = txBuilder.shutdownAll(PreparedStake.class, i -> {
			i.forEachRemaining(preparedStake ->
				preparingStake
					.computeIfAbsent(
						preparedStake.getDelegateKey(),
						k -> new TreeMap<>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()))
					)
					.merge(preparedStake.getOwner(), preparedStake.getAmount(), UInt256::add)
			);
			return preparingStake;
		});
		for (var e : allPreparedStake.entrySet()) {
			var k = e.getKey();
			var stakes = e.getValue();
			if (!validatorsToUpdate.containsKey(k)) {
				var currentStake = txBuilder.down(
					ValidatorStakeData.class,
					p -> p.getValidatorKey().equals(k),
					Optional.of(SubstateWithArg.noArg(ValidatorStakeData.createV1(k))),
					"Validator not found"
				);
				validatorsToUpdate.put(k, currentStake);
			}
			var curValidator = validatorsToUpdate.get(k);
			for (var entry : stakes.entrySet()) {
				var addr = entry.getKey();
				var amt = entry.getValue();

				try {
					var nextValidatorAndOwnership = curValidator.stake(addr, amt);
					curValidator = nextValidatorAndOwnership.getFirst();
					var stakeOwnership = nextValidatorAndOwnership.getSecond();
					txBuilder.up(stakeOwnership);
				} catch (ProcedureException ex) {
					throw new TxBuilderException(ex);
				}
			}
			validatorsToUpdate.put(k, curValidator);
		}

		validatorsToUpdate.forEach((k, validator) -> txBuilder.up(validator));
		var validatorKeys = action.validators(validatorsToUpdate.values());
		validatorKeys.forEach(k -> txBuilder.up(new ValidatorBFTData(k, 0, 0)));

		txBuilder.up(new EpochData(prevEpoch.getEpoch() + 1));
		txBuilder.up(new RoundData(0, 0));
	}
}
