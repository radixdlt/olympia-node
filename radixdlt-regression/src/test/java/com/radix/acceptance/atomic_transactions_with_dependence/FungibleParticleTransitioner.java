/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radix.acceptance.atomic_transactions_with_dependence;

import com.google.common.collect.ImmutableList;
import com.radixdlt.client.core.fungible.NotEnoughFungiblesException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt256s;

/**
 * Helper class for transitioning fungible particles
 * TODO: cleanup interfaces and generics once particles are less tied to classes and more tied to state machine
 *
 * @param <T> particle class to transition from
 * @param <U> particle class to transition to
 */
class FungibleParticleTransitioner<T, U> {
	private final BiFunction<UInt256, T, U> transitioner;
	private final UnaryOperator<List<U>> transitionedCombiner;
	private final BiFunction<UInt256, T, T> migrator;
	private final UnaryOperator<List<T>> migratedCombiner;
	private final Function<T, UInt256> amountMapper;

	FungibleParticleTransitioner(
		BiFunction<UInt256, T, U> transitioner,
		UnaryOperator<List<U>> transitionedCombiner,
		BiFunction<UInt256, T, T> migrator,
		UnaryOperator<List<T>> migratedCombiner,
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

		FungibleParticleTransition(
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
	) throws NotEnoughFungiblesException {
		UInt256 balance = unconsumedFungibles.stream().map(amountMapper).reduce(UInt256.ZERO, UInt256::add);
		if (balance.compareTo(toAmount) < 0) {
			throw new NotEnoughFungiblesException(toAmount, balance);
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
