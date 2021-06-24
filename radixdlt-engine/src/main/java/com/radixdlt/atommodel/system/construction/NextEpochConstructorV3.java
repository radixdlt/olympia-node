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
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.SystemNextEpoch;
import com.radixdlt.atommodel.system.scrypt.EpochUpdateConstraintScrypt;
import com.radixdlt.atommodel.system.state.EpochData;
import com.radixdlt.atommodel.system.state.RoundData;
import com.radixdlt.atommodel.system.state.ValidatorBFTData;
import com.radixdlt.atommodel.system.state.ValidatorStakeData;
import com.radixdlt.atommodel.tokens.state.ExittingStake;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.atommodel.validators.state.ValidatorOwnerCopy;
import com.radixdlt.atommodel.validators.state.PreparedOwnerUpdate;
import com.radixdlt.atommodel.validators.state.ValidatorRakeCopy;
import com.radixdlt.atommodel.validators.state.PreparedRakeUpdate;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.constraintmachine.ShutdownAllIndex;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.radixdlt.atommodel.validators.state.PreparedRakeUpdate.RAKE_MAX;

public class NextEpochConstructorV3 implements ActionConstructor<SystemNextEpoch> {
	private static Logger logger = LogManager.getLogger();

	private static ValidatorStakeData loadValidatorStakeData(
		TxBuilder txBuilder,
		ECPublicKey k,
		TreeMap<ECPublicKey, ValidatorStakeData> validatorsToUpdate
	) throws TxBuilderException {
		if (!validatorsToUpdate.containsKey(k)) {
			var validatorData = txBuilder.down(
				ValidatorStakeData.class,
				p -> p.getValidatorKey().equals(k),
				Optional.of(SubstateWithArg.noArg(ValidatorStakeData.createVirtual(k))),
				"Validator not found"
			);
			validatorsToUpdate.put(k, validatorData);
		}
		return validatorsToUpdate.get(k);
	}

	@Override
	public void construct(SystemNextEpoch action, TxBuilder txBuilder) throws TxBuilderException {
		txBuilder.down(
			RoundData.class,
			p -> true,
			Optional.empty(),
			"No round data available"
		);
		var prevEpoch = txBuilder.down(
			EpochData.class,
			p -> true,
			Optional.of(SubstateWithArg.noArg(new EpochData(0))),
			"No epoch data available"
		);

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
			i.forEachRemaining(e -> {
				proposalsCompleted.put(e.validatorKey(), e.proposalsCompleted());
				logger.info("Validator {} completed {} missed {}", e.validatorKey(), e.proposalsCompleted(), e.proposalsMissed());
			});
			return proposalsCompleted;
		});
		var preparingStake = new TreeMap<ECPublicKey, TreeMap<REAddr, UInt256>>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);
		for (var e : proposals.entrySet()) {
			var k = e.getKey();
			var numProposals = e.getValue();
			var validatorStakeData = txBuilder.down(
				ValidatorStakeData.class,
				s -> s.getValidatorKey().equals(k),
				"Validator not found"
			);
			var nodeEmission = EpochUpdateConstraintScrypt.REWARDS_PER_PROPOSAL.multiply(UInt256.from(numProposals));
			int rakePercentage = validatorStakeData.getRakePercentage();
			final UInt256 rakedEmissions;
			if (rakePercentage != 0 && !nodeEmission.isZero()) {
				var rake = nodeEmission
					.multiply(UInt256.from(rakePercentage))
					.divide(UInt256.from(RAKE_MAX));
				var validatorOwner = validatorStakeData.getOwnerAddr();
				var initStake = new TreeMap<REAddr, UInt256>((o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes()));
				initStake.put(validatorOwner, rake);
				preparingStake.put(k, initStake);
				rakedEmissions = nodeEmission.subtract(rake);
			} else {
				rakedEmissions = nodeEmission;
			}
			validatorsToUpdate.put(k, validatorStakeData.addEmission(rakedEmissions));
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
			var curValidator = loadValidatorStakeData(txBuilder, k, validatorsToUpdate);
			var unstakes = e.getValue();
			for (var entry : unstakes.entrySet()) {
				var addr = entry.getKey();
				var amt = entry.getValue();
				var nextStakeAndAmt = curValidator.unstakeOwnership(addr, amt, prevEpoch.getEpoch());
				curValidator = nextStakeAndAmt.getFirst();
				var exittingStake = nextStakeAndAmt.getSecond();
				txBuilder.up(exittingStake);
			}
			validatorsToUpdate.put(k, curValidator);
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
			var curValidator = loadValidatorStakeData(txBuilder, k, validatorsToUpdate);
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

		var preparingRakeUpdates = new TreeMap<ECPublicKey, PreparedRakeUpdate>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);
		var buf = ByteBuffer.allocate(1 + Long.BYTES);
		buf.put(SubstateTypeId.PREPARED_RAKE_UPDATE.id());
		buf.putLong(prevEpoch.getEpoch() + 1);
		var index = new ShutdownAllIndex(buf.array(), PreparedRakeUpdate.class);
		txBuilder.shutdownAll(index, (Iterator<PreparedRakeUpdate> i) -> {
			i.forEachRemaining(preparedValidatorUpdate ->
				preparingRakeUpdates.put(preparedValidatorUpdate.getValidatorKey(), preparedValidatorUpdate)
			);
			return preparingRakeUpdates;
		});
		for (var e : preparingRakeUpdates.entrySet()) {
			var k = e.getKey();
			var update = e.getValue();
			var curValidator = loadValidatorStakeData(txBuilder, k, validatorsToUpdate);
			validatorsToUpdate.put(k, curValidator.setRakePercentage(update.getNextRakePercentage()));
			txBuilder.up(new ValidatorRakeCopy(k, update.getNextRakePercentage()));
		}

		var preparingUpdates = new TreeMap<ECPublicKey, PreparedOwnerUpdate>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);
		txBuilder.shutdownAll(PreparedOwnerUpdate.class, i -> {
			i.forEachRemaining(update -> preparingUpdates.put(update.getValidatorKey(), update));
			return preparingUpdates;
		});
		for (var e : preparingUpdates.entrySet()) {
			var k = e.getKey();
			var update = e.getValue();
			var curValidator = loadValidatorStakeData(txBuilder, k, validatorsToUpdate);
			validatorsToUpdate.put(k, curValidator.setOwnerAddr(update.getOwnerAddress()));
			txBuilder.up(new ValidatorOwnerCopy(k, update.getOwnerAddress()));
		}

		validatorsToUpdate.forEach((k, validator) -> txBuilder.up(validator));
		var validatorKeys = action.validators(validatorsToUpdate.values());
		validatorKeys.forEach(k -> txBuilder.up(new ValidatorBFTData(k, 0, 0)));

		txBuilder.up(new EpochData(prevEpoch.getEpoch() + 1));
		txBuilder.up(new RoundData(0, 0));
	}
}
