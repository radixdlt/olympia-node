package com.radixdlt.client.core.fungible;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.radix.utils.UInt256;
import org.radix.utils.UInt256s;

/**
 * Helper class for transitioning fungible particles
 * TODO: cleanup interfaces and generics once particles are less tied to classes and more tied to state machine
 *
 * @param <T> particle class to transition from
 * @param <U> particle class to transition to
 */
public class FungibleParticleTransitioner<T, U> {
	private final BiFunction<UInt256, T, U> transitioner;
	private final Function<List<U>, List<U>> transitionedCombiner;
	private final BiFunction<UInt256, T, T> migrator;
	private final Function<List<T>, List<T>> migratedCombiner;
	private final Function<T, UInt256> amountMapper;

	public FungibleParticleTransitioner(
		BiFunction<UInt256, T, U> transitioner,
		Function<List<U>, List<U>> transitionedCombiner,
		BiFunction<UInt256, T, T> migrator,
		Function<List<T>, List<T>> migratedCombiner,
		Function<T, UInt256> amountMapper
	) {
		this.transitioner = transitioner;
		this.transitionedCombiner = transitionedCombiner;
		this.migrator = migrator;
		this.migratedCombiner = migratedCombiner;
		this.amountMapper = amountMapper;
	}

	public static class FungibleParticleTransition<T, U> {
		private final ImmutableList<T> removed;
		private final ImmutableList<T> migrated;
		private final ImmutableList<U> transitioned;

		private FungibleParticleTransition(
			ImmutableList<T> removed,
			ImmutableList<T> migrated,
			ImmutableList<U> transitioned
		) {
			this.removed = removed;
			this.migrated = migrated;
			this.transitioned = transitioned;
		}

		public List<T> getRemoved() {
			return removed;
		}

		public List<T> getMigrated() {
			return migrated;
		}

		public List<U> getTransitioned() {
			return transitioned;
		}
	}

	private static class FungibleParticleTransitionBuilder<T, U> {
		private ImmutableList.Builder<T> removedBuilder = ImmutableList.builder();
		private List<T> migrated = new ArrayList<>();
		private List<U> transitioned = new ArrayList<>();
		private final Function<List<U>, List<U>> transitionedCombiner;
		private final Function<List<T>, List<T>> migratedCombiner;

		private FungibleParticleTransitionBuilder(
			Function<List<U>, List<U>> transitionedCombiner,
			Function<List<T>, List<T>> migratedCombiner
		) {
			this.transitionedCombiner = transitionedCombiner;
			this.migratedCombiner = migratedCombiner;
		}


		private FungibleParticleTransitionBuilder<T, U> addTransitioned(U u) {
			transitioned.add(u);
			return this;
		}

		private FungibleParticleTransitionBuilder<T, U> addMigrated(T t) {
			migrated.add(t);
			return this;
		}

		private FungibleParticleTransitionBuilder<T, U> addRemoved(T t) {
			removedBuilder.add(t);
			return this;
		}

		private FungibleParticleTransition<T, U> build() {
			return new FungibleParticleTransition<>(
				removedBuilder.build(),
				ImmutableList.copyOf(migratedCombiner.apply(migrated)),
				ImmutableList.copyOf(transitionedCombiner.apply(transitioned)));
		}
	}

	public FungibleParticleTransition<T, U> createTransition(
		List<T> unconsumedFungibles,
		UInt256 toAmount
	) {
		UInt256 balance = unconsumedFungibles.stream().map(amountMapper).reduce(UInt256.ZERO, UInt256::add);
		if (balance.compareTo(toAmount) < 0) {
			throw new RuntimeException("Not enough to cannot create transition");
		}

		UInt256 consumerTotal = UInt256.ZERO;
		Iterator<T> iterator = unconsumedFungibles.iterator();

		final FungibleParticleTransitionBuilder<T, U> transitionBuilder = new FungibleParticleTransitionBuilder<>(
			transitionedCombiner,
			migratedCombiner
		);

		while (consumerTotal.compareTo(toAmount) < 0 && iterator.hasNext()) {
			final UInt256 left = toAmount.subtract(consumerTotal);

			T unconsumed = iterator.next();
			UInt256 particleAmount = amountMapper.apply(unconsumed);
			consumerTotal = consumerTotal.add(particleAmount);

			final UInt256 amountToTransfer = UInt256s.min(left, particleAmount);
			final UInt256 amountToKeep = particleAmount.subtract(amountToTransfer);

			U newTransitioned = transitioner.apply(amountToTransfer, unconsumed);
			transitionBuilder.addTransitioned(newTransitioned);
			if (amountToKeep.compareTo(UInt256.ZERO) > 0) {
				T newMigrated = migrator.apply(amountToKeep, unconsumed);
				transitionBuilder.addMigrated(newMigrated);
			}

			transitionBuilder.addRemoved(unconsumed);
		}

		return transitionBuilder.build();
	}
}
