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
import com.google.common.primitives.UnsignedBytes;
import com.radixdlt.application.system.scrypt.Syscall;
import com.radixdlt.application.tokens.ResourceInBucket;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.atomos.UnclaimedREAddr;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
	private UInt256 feeReservePut;
	private UInt256 feeReserveTake = UInt256.ZERO;
	private int numResourcesCreated = 0;

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
		if (particle instanceof TokenResource) {
			numResourcesCreated++;
		}
	}

	public int getNumResourcesCreated() {
		return numResourcesCreated;
	}

	private <T extends Particle> T virtualDown(Class<T> substateClass, Object key) {
		var pair = serialization.serializeVirtual(substateClass, key);
		lowLevelBuilder.virtualDown(pair.getSecond());
		return pair.getFirst();
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

	private <T extends Particle> T virtualRead(Class<T> substateClass, Object key) {
		var pair = serialization.serializeVirtual(substateClass, key);
		lowLevelBuilder.virtualRead(pair.getSecond());
		return pair.getFirst();
	}

	private CloseableCursor<RawSubstateBytes> createRemoteSubstateCursor(SubstateIndex<?> index) {
		return remoteSubstate.openIndexedCursor(index)
			.filter(s -> !lowLevelBuilder.remoteDownSubstate().contains(SubstateId.fromBytes(s.getId())));
	}

	private CloseableCursor<RawSubstateBytes> createRemoteSubstateCursor(Class<? extends Particle> c) {
		var b = deserialization.classToByte(c);
		return createRemoteSubstateCursor(SubstateIndex.create(new byte[] {b}, c));
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

	private Substate deserialize(RawSubstateBytes rawSubstateBytes) {
		try {
			var raw = deserialization.deserialize(rawSubstateBytes.getData());
			return Substate.create(raw, SubstateId.fromBytes(rawSubstateBytes.getId()));
		} catch (DeserializeException e) {
			throw new IllegalStateException(e);
		}
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
				.map(this::deserialize)
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
				iteratorToStream(cursor).map(this::deserialize).map(Substate::getParticle)
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
		Supplier<TxBuilderException> exceptionSupplier
	) throws TxBuilderException {
		return down(particleClass, particlePredicate, Optional.empty(), exceptionSupplier);
	}

	public <T extends Particle> T down(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Optional<Object> keyToVirtual,
		Supplier<TxBuilderException> exceptionSupplier
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
				.map(this::deserialize)
				.filter(s -> particlePredicate.test(particleClass.cast(s.getParticle())))
				.peek(s -> this.down(s.getId()))
				.map(Substate::getParticle)
				.map(particleClass::cast)
				.findFirst()
				.or(() -> keyToVirtual.map(k -> this.virtualDown(particleClass, k)));

			if (substateDown.isEmpty()) {
				throw exceptionSupplier.get();
			}

			return substateDown.get();
		}
	}

	public <T extends Particle> T read(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Optional<Object> keyToVirtual,
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
				.map(this::deserialize)
				.filter(s -> particlePredicate.test(particleClass.cast(s.getParticle())))
				.peek(s -> this.read(s.getId()))
				.map(Substate::getParticle)
				.map(particleClass::cast)
				.findFirst()
				.or(() -> keyToVirtual.map(k -> this.virtualRead(particleClass, k)));

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
		var typeByte = deserialization.classToByte(particleClass);
		return shutdownAll(SubstateIndex.create(typeByte, particleClass), mapper);
	}

	public <T extends Particle> CloseableCursor<T> readIndex(SubstateIndex index) {
		var comparator = UnsignedBytes.lexicographicalComparator().reversed();
		var cursor = createRemoteSubstateCursor(index);
		var localIterator = lowLevelBuilder.localUpSubstate().stream()
			.map(LocalSubstate::getParticle)
			.filter(index.getSubstateClass()::isInstance)
			.map(p -> (T) p)
			.map(p -> Pair.of(p, serialization.serialize(p)))
			.filter(p -> index.test(p.getSecond()))
			.sorted(Comparator.comparing(Pair::getSecond, comparator))
			.iterator();

		lowLevelBuilder.readIndex(index);

		return new CloseableCursor<T>() {
			private RawSubstateBytes nextRemote = cursor.hasNext() ? cursor.next() : null;
			private Pair<T, byte[]> nextLocal = localIterator.hasNext() ? localIterator.next() : null;

			private T nextRemote() {
				var next = nextRemote;
				nextRemote = cursor.hasNext() ? cursor.next() : null;
				try {
					return (T) deserialization.deserialize(next.getData());
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			}

			private T nextLocal() {
				var next = nextLocal;
				nextLocal = localIterator.hasNext() ? localIterator.next() : null;
				return next.getFirst();
			}

			@Override
			public void close() {
				cursor.close();
			}

			@Override
			public boolean hasNext() {
				return nextRemote != null || nextLocal != null;
			}

			@Override
			public T next() {
				if (nextRemote != null && nextLocal != null) {
					var compare = comparator.compare(nextRemote.getData(), nextLocal.getSecond());
					return compare <= 0 ? nextRemote() : nextLocal();
				} else if (nextRemote != null) {
					return nextRemote();
				} else if (nextLocal != null) {
					return nextLocal();
				} else {
					throw new NoSuchElementException();
				}
			}
		};
	}

	public <T extends Particle, U> U shutdownAll(
		SubstateIndex<T> index,
		Function<Iterator<T>, U> mapper
	) {
		try (var cursor = createRemoteSubstateCursor(index)) {
			var localIterator = lowLevelBuilder.localUpSubstate().stream()
				.map(LocalSubstate::getParticle)
				.filter(index.getSubstateClass()::isInstance)
				.map(p -> (T) p)
				.filter(p -> index.test(serialization.serialize(p)))
				.iterator();
			var remoteIterator = Iterators.transform(cursor, s -> (T) this.deserialize(s).getParticle());
			var result = mapper.apply(Iterators.concat(localIterator, remoteIterator));
			lowLevelBuilder.downIndex(index);
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
		Supplier<TxBuilderException> exceptionSupplier
	) throws TxBuilderException {
		T t = down(particleClass, particlePredicate, exceptionSupplier);
		return replacer -> replacer.map(t).forEach(this::up);
	}

	public <T extends Particle> Replacer<T> swap(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Optional<Object> virtualKey,
		Supplier<TxBuilderException> exceptionSupplier
	) throws TxBuilderException {
		T t = down(particleClass, particlePredicate, virtualKey, exceptionSupplier);
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
		Supplier<TxBuilderException> exceptionSupplier
	) throws TxBuilderException {
		UInt256 spent = UInt256.ZERO;
		while (spent.compareTo(amount) < 0) {
			var substateDown = down(
				particleClass,
				particlePredicate,
				exceptionSupplier
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
		Supplier<TxBuilderException> exceptionSupplier
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
				exceptionSupplier
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
		Supplier<TxBuilderException> exceptionSupplier
	) {
		return mapper -> {
			var substateUp = mapper.map(amount);
			up(substateUp);
			var remainder = downFungible(
				particleClass,
				particlePredicate.and(p -> !p.equals(substateUp)), // HACK to allow mempool filler to do it's thing
				amount,
				exceptionSupplier
			);
			if (!remainder.isZero()) {
				up(remainderMapper.map(remainder));
			}
		};
	}

	public UInt256 getFeeReserve() {
		return feeReservePut;
	}

	public <T extends ResourceInBucket> void putFeeReserve(
		Predicate<TokensInAccount> particlePredicate,
		FungibleMapper<T> remainderMapper,
		UInt256 amount,
		Supplier<TxBuilderException> exceptionSupplier
	) throws TxBuilderException {
		// Take
		var remainder = downFungible(
			TokensInAccount.class,
			particlePredicate,
			amount,
			exceptionSupplier
		);
		lowLevelBuilder.syscall(Syscall.FEE_RESERVE_PUT, amount);
		if (!remainder.isZero()) {
			up(remainderMapper.map(remainder));
		}
		this.feeReservePut = amount;
	}

	public void takeFeeReserve(
		REAddr addr,
		UInt256 amount
	) {
		lowLevelBuilder.syscall(Syscall.FEE_RESERVE_TAKE, amount);
		if (!amount.isZero()) {
			up(new TokensInAccount(addr, REAddr.ofNativeToken(), amount));
		}
		this.feeReserveTake = this.feeReserveTake.add(amount);
	}

	public <T extends ResourceInBucket, U extends ResourceInBucket> void downFungible(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		FungibleMapper<T> remainderMapper,
		UInt256 amount,
		Supplier<TxBuilderException> exceptionSupplier
	) throws TxBuilderException {
		// Take
		var remainder = downFungible(
			particleClass,
			particlePredicate,
			amount,
			exceptionSupplier
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
		Supplier<TxBuilderException> exceptionSupplier
	) {
		return mapper -> {
			// Take
			downFungible(
				particleClass,
				particlePredicate,
				remainderMapper,
				amount,
				exceptionSupplier
			);

			// Put
			var substateUp = mapper.map(amount);
			up(substateUp);
		};
	}

	public TxBuilder mutex(ECPublicKey key, String id) throws TxBuilderException {
		final var addr = REAddr.ofHashedKey(key, id);

		lowLevelBuilder.syscall(Syscall.READDR_CLAIM, id.getBytes(StandardCharsets.UTF_8));
		down(
			UnclaimedREAddr.class,
			p -> p.getAddr().equals(addr),
			Optional.of(addr),
			() -> new TxBuilderException("Address already claimed")
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

	public UnsignedTxnData buildForExternalSign() {
		var put = Optional.ofNullable(feeReservePut).orElse(UInt256.ZERO);
		var take = feeReserveTake;
		if (put.compareTo(take) < 0) {
			throw new IllegalStateException("Should not get to this state.");
		}
		var feesPaid = put.subtract(take);

		return new UnsignedTxnData(lowLevelBuilder.blob(), lowLevelBuilder.hashToSign(), feesPaid);
	}
}
