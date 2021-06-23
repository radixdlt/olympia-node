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

package com.radixdlt.atom;

import com.google.common.collect.Iterators;
import com.google.common.collect.Streams;
import com.google.common.hash.HashCode;
import com.radixdlt.atommodel.tokens.ResourceInBucket;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.atomos.UnclaimedREAddr;
import com.radixdlt.constraintmachine.ShutdownAllIndex;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Creates a transaction from high level user actions
 */
public final class TxBuilder {
	private final TxLowLevelBuilder lowLevelBuilder;
	private final SubstateStore remoteSubstate;
	private final SubstateDeserialization deserialization;
	private final SubstateSerialization serialization;

	private TxBuilder(
		SubstateStore remoteSubstate,
		SubstateDeserialization deserialization,
		SubstateSerialization serialization
	) {
		this.lowLevelBuilder = TxLowLevelBuilder.newBuilder(serialization);
		this.remoteSubstate = remoteSubstate;
		this.deserialization = deserialization;
		this.serialization = serialization;
	}

	public static TxBuilder newBuilder(
		SubstateStore remoteSubstate,
		SubstateDeserialization deserialization,
		SubstateSerialization serialization
	) {
		return new TxBuilder(remoteSubstate, deserialization, serialization);
	}

	public static TxBuilder newBuilder(SubstateDeserialization deserialization, SubstateSerialization serialization) {
		return new TxBuilder(SubstateStore.empty(), deserialization, serialization);
	}

	public TxLowLevelBuilder toLowLevelBuilder() {
		return lowLevelBuilder;
	}

	public void end() {
		lowLevelBuilder.end();
	}

	public void up(Particle particle) {
		lowLevelBuilder.up(particle);
	}

	private void virtualDown(SubstateWithArg<?> substateWithArg) {
		substateWithArg.getArg().ifPresentOrElse(
			arg -> lowLevelBuilder.virtualDown(substateWithArg.getSubstate(), arg),
			() -> lowLevelBuilder.virtualDown(substateWithArg.getSubstate())
		);
	}

	public void down(SubstateId substateId) {
		lowLevelBuilder.down(substateId);
	}

	private void localDown(int index) {
		lowLevelBuilder.localDown(index);
	}

	private void read(SubstateId substateId) {
		lowLevelBuilder.read(substateId);
	}

	private void localRead(int index) {
		lowLevelBuilder.localRead(index);
	}

	private void virtualRead(Particle p) {
		lowLevelBuilder.virtualRead(p);
	}

	private CloseableCursor<Substate> createRemoteSubstateCursor(Class<? extends Particle> particleClass) {
		return CloseableCursor.filter(
			remoteSubstate.openIndexedCursor(particleClass, deserialization),
			s -> !lowLevelBuilder.remoteDownSubstate().contains(s.getId())
		);
	}

	private static <T> Stream<T> iteratorToStream(Iterator<T> iterator) {
		return StreamSupport.stream(
			Spliterators.spliteratorUnknownSize(
				iterator,
				Spliterator.ORDERED
			),
			 false
		);
	}

	// For mempool filler
	public <T extends Particle> T downSubstate(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		String errorMessage
	) throws TxBuilderException {
		var localSubstate = lowLevelBuilder.localUpSubstate().stream()
			.filter(s -> particleClass.isInstance(s.getParticle()))
			.filter(s -> particlePredicate.test((T) s.getParticle()))
			.findFirst();

		if (localSubstate.isPresent()) {
			localDown(localSubstate.get().getIndex());
			return (T) localSubstate.get().getParticle();
		}

		try (var cursor = createRemoteSubstateCursor(particleClass)) {
			var substateRead = iteratorToStream(cursor)
				.filter(s -> particlePredicate.test(particleClass.cast(s.getParticle())))
				.findFirst();

			if (substateRead.isEmpty()) {
				throw new TxBuilderException(errorMessage);
			}

			down(substateRead.get().getId());

			return (T) substateRead.get().getParticle();
		}
	}

	public <T extends Particle> Optional<T> find(
		Class<T> particleClass,
		Predicate<T> particlePredicate
	) throws TxBuilderException {
		try (var cursor = createRemoteSubstateCursor(particleClass)) {
			return Streams.concat(
				lowLevelBuilder.localUpSubstate().stream().map(LocalSubstate::getParticle),
				iteratorToStream(cursor).map(Substate::getParticle)
			)
				.filter(particleClass::isInstance)
				.map(particleClass::cast)
				.filter(particlePredicate)
				.findFirst();
		}
	}

	public <T extends Particle> T down(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		String errorMessage
	) throws TxBuilderException {
		return down(particleClass, particlePredicate, Optional.empty(), errorMessage);
	}

	public <T extends Particle> T down(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Optional<SubstateWithArg<T>> virtualParticle,
		String errorMessage
	) throws TxBuilderException {
		var localDown = lowLevelBuilder.localUpSubstate().stream()
			.filter(s -> {
				if (!particleClass.isInstance(s.getParticle())) {
					return false;
				}

				return particlePredicate.test(particleClass.cast(s.getParticle()));
			})
			.peek(s -> this.localDown(s.getIndex()))
			.map(LocalSubstate::getParticle)
			.map(particleClass::cast)
			.findFirst();

		if (localDown.isPresent()) {
			return localDown.get();
		}

		try (var cursor = createRemoteSubstateCursor(particleClass)) {
			var substateDown = iteratorToStream(cursor)
				.filter(s -> particlePredicate.test(particleClass.cast(s.getParticle())))
				.peek(s -> this.down(s.getId()))
				.map(Substate::getParticle)
				.map(particleClass::cast)
				.findFirst()
				.or(() -> {
					virtualParticle.ifPresent(this::virtualDown);
					return virtualParticle.map(SubstateWithArg::getSubstate);
				});

			if (substateDown.isEmpty()) {
				throw new TxBuilderException(errorMessage + " (Substate not found)");
			}

			return substateDown.get();
		}
	}

	public <T extends Particle> T read(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Optional<T> virtualParticle,
		String errorMessage
	) throws TxBuilderException {
		var localRead = lowLevelBuilder.localUpSubstate().stream()
			.filter(s -> {
				if (!particleClass.isInstance(s.getParticle())) {
					return false;
				}

				return particlePredicate.test(particleClass.cast(s.getParticle()));
			})
			.peek(s -> this.localRead(s.getIndex()))
			.map(LocalSubstate::getParticle)
			.map(particleClass::cast)
			.findFirst();

		if (localRead.isPresent()) {
			return localRead.get();
		}

		try (var cursor = createRemoteSubstateCursor(particleClass)) {
			var substateDown = iteratorToStream(cursor)
				.filter(s -> particlePredicate.test(particleClass.cast(s.getParticle())))
				.peek(s -> this.read(s.getId()))
				.map(Substate::getParticle)
				.map(particleClass::cast)
				.findFirst()
				.or(() -> {
					virtualParticle.ifPresent(this::virtualRead);
					return virtualParticle;
				});

			if (substateDown.isEmpty()) {
				throw new TxBuilderException(errorMessage + " (Substate not found)");
			}

			return substateDown.get();
		}
	}

	public <T extends Particle, U> U shutdownAll(
		Class<T> particleClass,
		Function<Iterator<T>, U> mapper
	) {
		try (var cursor = createRemoteSubstateCursor(particleClass)) {
			var localIterator = lowLevelBuilder.localUpSubstate().stream()
				.map(LocalSubstate::getParticle)
				.filter(particleClass::isInstance)
				.map(particleClass::cast)
				.iterator();
			var remoteIterator = Iterators.transform(cursor, s -> (T) s.getParticle());
			var result = mapper.apply(Iterators.concat(localIterator, remoteIterator));
			var typeBytes = deserialization.classToBytes(particleClass);
			if (typeBytes.size() != 1) {
				throw new IllegalStateException("Cannot down all of particle with multiple ids: " + particleClass);
			}
			lowLevelBuilder.downAll(typeBytes.iterator().next());
			return result;
		}
	}

	public <T extends Particle, U> U shutdownAll(
		ShutdownAllIndex index,
		Function<Iterator<T>, U> mapper
	) {
		try (var cursor = createRemoteSubstateCursor(index.getSubstateClass())) {
			var localIterator = lowLevelBuilder.localUpSubstate().stream()
				.map(LocalSubstate::getParticle)
				.filter(index.getSubstateClass()::isInstance)
				.map(p -> (T) p)
				.iterator();
			var remoteIterator = Iterators.transform(cursor, s -> (T) s.getParticle());
			var result = mapper.apply(Iterators.filter(
				Iterators.concat(localIterator, remoteIterator),
				p -> index.test(serialization.serialize(p))
			));
			lowLevelBuilder.downAll(index);
			return result;
		}
	}

	public interface Mapper<T extends Particle> {
		List<Particle> map(T t) throws TxBuilderException;
	}

	public interface Replacer<T extends Particle> {
		void with(Mapper<T> mapper) throws TxBuilderException;
	}

	public <T extends Particle> Replacer<T> swap(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		String errorMessage
	) throws TxBuilderException {
		T t = down(particleClass, particlePredicate, errorMessage);
		return replacer -> replacer.map(t).forEach(this::up);
	}

	public <T extends Particle> Replacer<T> swap(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Optional<SubstateWithArg<T>> virtualParticle,
		String errorMessage
	) throws TxBuilderException {
		T t = down(particleClass, particlePredicate, virtualParticle, errorMessage);
		return replacer -> replacer.map(t).forEach(this::up);
	}

	public interface FungibleMapper<U extends Particle> {
		U map(UInt256 t) throws TxBuilderException;
	}

	public interface FungibleReplacer<U extends Particle> {
		void with(FungibleMapper<U> mapper) throws TxBuilderException;
	}

	private <T extends ResourceInBucket> UInt256 downFungible(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		UInt256 amount,
		String errorMessage
	) throws TxBuilderException {
		UInt256 spent = UInt256.ZERO;
		while (spent.compareTo(amount) < 0) {
			var substateDown = down(
				particleClass,
				particlePredicate,
				errorMessage
			);

			spent = spent.add(substateDown.getAmount());
		}

		return spent.subtract(amount);
	}

	public <T extends ResourceInBucket> void deallocateFungible(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		FungibleMapper<T> remainderMapper,
		UInt256 amount,
		String errorMessage
	) throws TxBuilderException {
		UInt256 spent = UInt256.ZERO;
		while (spent.compareTo(amount) < 0) {
			// FIXME: This is a hack due to the constraint machine not being able to
			// FIXME: handle spins of the same type one after the other yet.
			if (!spent.isZero()) {
				end();
			}

			var substateDown = down(
				particleClass,
				particlePredicate,
				errorMessage
			);

			spent = spent.add(substateDown.getAmount());
		}

		var remainder = spent.subtract(amount);
		if (!remainder.isZero()) {
			up(remainderMapper.map(remainder));
		}
	}

	public <T extends ResourceInBucket, U extends ResourceInBucket> FungibleReplacer<U> deprecatedSwapFungible(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		FungibleMapper<T> remainderMapper,
		UInt256 amount,
		String errorMessage
	) {
		return mapper -> {
			var substateUp = mapper.map(amount);
			up(substateUp);
			var remainder = downFungible(
				particleClass,
				particlePredicate.and(p -> !p.equals(substateUp)), // HACK to allow mempool filler to do it's thing
				amount,
				errorMessage
			);
			if (!remainder.isZero()) {
				up(remainderMapper.map(remainder));
			}
		};
	}

	public <T extends ResourceInBucket> void payFee(
		Predicate<TokensInAccount> particlePredicate,
		FungibleMapper<T> remainderMapper,
		UInt256 amount,
		String errorMessage
	) throws TxBuilderException {
		// Take
		var remainder = downFungible(
			TokensInAccount.class,
			particlePredicate,
			amount,
			errorMessage
		);
		lowLevelBuilder.payFee(amount);
		if (!remainder.isZero()) {
			up(remainderMapper.map(remainder));
		}
	}

	public <T extends ResourceInBucket, U extends ResourceInBucket> void downFungible(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		FungibleMapper<T> remainderMapper,
		UInt256 amount,
		String errorMessage
	) throws TxBuilderException {
		// Take
		var remainder = downFungible(
			particleClass,
			particlePredicate,
			amount,
			errorMessage
		);
		if (!remainder.isZero()) {
			up(remainderMapper.map(remainder));
		}
	}

	public <T extends ResourceInBucket, U extends ResourceInBucket> FungibleReplacer<U> swapFungible(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		FungibleMapper<T> remainderMapper,
		UInt256 amount,
		String errorMessage
	) {
		return mapper -> {
			// Take
			downFungible(
				particleClass,
				particlePredicate,
				remainderMapper,
				amount,
				errorMessage
			);

			// Put
			var substateUp = mapper.map(amount);
			up(substateUp);
		};
	}

	public TxBuilder mutex(ECPublicKey key, String id) throws TxBuilderException {
		final var addr = REAddr.ofHashedKey(key, id);
		down(
			UnclaimedREAddr.class,
			p -> p.getAddr().equals(addr),
			Optional.of(SubstateWithArg.withArg(new UnclaimedREAddr(addr), id.getBytes(StandardCharsets.UTF_8))),
			"RRI not available"
		);
		end();

		return this;
	}

	public TxBuilder message(byte[] message) {
		lowLevelBuilder.message(message);
		return this;
	}

	public Txn signAndBuild(Function<HashCode, ECDSASignature> signer) {
		var hashToSign = lowLevelBuilder.hashToSign();
		return lowLevelBuilder.sig(signer.apply(hashToSign)).build();
	}

	public Txn buildWithoutSignature() {
		return lowLevelBuilder.build();
	}

	public Pair<byte[], HashCode> buildForExternalSign() {
		return Pair.of(lowLevelBuilder.blob(), lowLevelBuilder.hashToSign());
	}
}
