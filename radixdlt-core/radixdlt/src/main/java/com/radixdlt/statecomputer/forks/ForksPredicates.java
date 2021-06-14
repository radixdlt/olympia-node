package com.radixdlt.statecomputer.forks;

import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.StakedValidators;
import com.radixdlt.utils.UInt256;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.BiPredicate;

public final class ForksPredicates {

	private ForksPredicates() {
	}

	/**
	 * Returns a fork predicate that requires the specified percentage of stake votes (strictly higher).
	 */
	public static BiPredicate<RadixEngine<LedgerAndBFTProof>, LedgerAndBFTProof> stakeVoting(HashCode forkHash, double requiredPercentage) {
		return (radixEngine, uncommittedProof) -> {
			if (!uncommittedProof.getProof().getNextValidatorSet().isPresent()) {
				return false;
			}

			final var validatorSet = uncommittedProof.getProof().getNextValidatorSet().get();
			final var stakedValidators = radixEngine.getComputedState(StakedValidators.class);

			final var requiredPower =
				new BigDecimal(new BigInteger(1, validatorSet.getTotalPower().toByteArray()))
					.multiply(BigDecimal.valueOf(requiredPercentage));
			final var forkVotes = stakedValidators.getForkVotes(forkHash);
			final var forkVotesPower = calculateStakeVotePower(validatorSet, forkVotes);
			return forkVotesPower.compareTo(requiredPower) > 0;
		};
	}

	private static BigDecimal calculateStakeVotePower(
		BFTValidatorSet validatorSet,
		ImmutableSet<ECPublicKey> forkVotes
	) {
		final var totalVotePowerForFork = forkVotes.stream()
			.map(BFTNode::create)
			.filter(validatorSet::containsNode)
			.map(validatorSet::getPower)
			.reduce(UInt256.ZERO, UInt256::add);
		return new BigDecimal(new BigInteger(1, totalVotePowerForFork.toByteArray()));
	}

	/**
	 * Returns a fork predicate that uses a fixed epoch number.
	 */
	public static BiPredicate<RadixEngine<LedgerAndBFTProof>, LedgerAndBFTProof> atEpoch(long epoch) {
		return (radixEngine, uncommittedProof) -> {
			if (!uncommittedProof.getProof().getNextValidatorSet().isPresent()) {
				return false;
			}

			final var nextEpoch = uncommittedProof.getProof().getEpoch() + 1;
			return nextEpoch >= epoch;
		};
	}
}
