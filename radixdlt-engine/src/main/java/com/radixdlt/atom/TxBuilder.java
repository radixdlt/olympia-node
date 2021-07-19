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
import com.google.common.hash.HashCode;
import com.google.common.primitives.UnsignedBytes;
import com.radixdlt.application.system.scrypt.Syscall;
import com.radixdlt.application.tokens.ResourceInBucket;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.application.system.state.UnclaimedREAddr;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Iterator;
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
	private static final Logger logger = LogManager.getLogger();
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

	public void down(SubstateId substateId) {
		lowLevelBuilder.down(substateId);
	}

	private void localDown(int index) {
		lowLevelBuilder.localDown(index);
	}

	private void read(SubstateId substateId) {
		lowLevelBuilder.read(substateId);
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

	public <T extends Particle> T find(Class<T> substateClass, Object key) throws TxBuilderException {
		var keyBytes = serialization.serializeKey(substateClass, key);
		var typeByte = deserialization.classToByte(substateClass);
		var mapKey = SystemMapKey.ofSystem(typeByte, keyBytes);
		var localMaybe = lowLevelBuilder.get(mapKey);
		if (localMaybe.isPresent()) {
			return (T) localMaybe.get().getParticle();
		}
		var raw = remoteSubstate.get(mapKey);

		if (raw.isPresent()) {
			var rawSubstate = raw.get();
			try {
				return (T) deserialization.deserialize(rawSubstate.getData());
			} catch (DeserializeException e) {
				throw new IllegalStateException();
			}
		} else {
			return serialization.mapVirtual(substateClass, key);
		}
	}

	private void virtualReadDownInternal(byte typeByte, byte[] keyBytes, boolean down) {
		var parentMapKey = SystemMapKey.ofSystem(typeByte);
		var localParent = lowLevelBuilder.get(parentMapKey);
		if (localParent.isPresent()) {
			if (down) {
				lowLevelBuilder.localVirtualDown(localParent.get().getIndex(), keyBytes);
			} else {
				lowLevelBuilder.localVirtualRead(localParent.get().getIndex(), keyBytes);
			}
		} else {
			var parent = remoteSubstate.get(parentMapKey).orElseThrow();
			var substateId = SubstateId.fromBytes(parent.getId());
			if (down) {
				lowLevelBuilder.virtualDown(substateId, keyBytes);
			} else {
				lowLevelBuilder.virtualRead(substateId, keyBytes);
			}
		}
	}

	private <T extends Particle> T readDownInternal(Class<T> substateClass, Object key, boolean down) {
		var keyBytes = serialization.serializeKey(substateClass, key);
		var typeByte = deserialization.classToByte(substateClass);
		var mapKey = SystemMapKey.ofSystem(typeByte, keyBytes);
		var localMaybe = lowLevelBuilder.get(mapKey);
		if (localMaybe.isPresent()) {
			var local = localMaybe.get();
			if (down) {
				lowLevelBuilder.localDown(local.getIndex());
			} else {
				lowLevelBuilder.localRead(local.getIndex());
			}
			return (T) local.getParticle();
		}

		var raw = remoteSubstate.get(mapKey);

		if (raw.isPresent()) {
			var rawSubstate = raw.get();
			if (down) {
				down(SubstateId.fromBytes(rawSubstate.getId()));
			} else {
				read(SubstateId.fromBytes(rawSubstate.getId()));
			}
			try {
				return (T) deserialization.deserialize(rawSubstate.getData());
			} catch (DeserializeException e) {
				throw new IllegalStateException();
			}
		} else {
			this.virtualReadDownInternal(typeByte, keyBytes, down);
			return serialization.mapVirtual(substateClass, key);
		}
	}

	public <T extends Particle> T down(Class<T> substateClass, Object key) {
		return readDownInternal(substateClass, key, true);
	}

	public <T extends Particle> T read(Class<T> substateClass, Object key) {
		return readDownInternal(substateClass, key, false);
	}

	public <T extends Particle> T downSystem(Class<T> substateClass) {
		var typeByte = deserialization.classToByte(substateClass);
		var mapKey = SystemMapKey.ofSystem(typeByte);
		var localMaybe = lowLevelBuilder.get(mapKey);
		if (localMaybe.isPresent()) {
			var local = localMaybe.get();
			lowLevelBuilder.localDown(local.getIndex());
			return (T) local.getParticle();
		}

		var rawSubstate = remoteSubstate.get(mapKey).orElseThrow();
		down(SubstateId.fromBytes(rawSubstate.getId()));
		try {
			return (T) deserialization.deserialize(rawSubstate.getData());
		} catch (DeserializeException e) {
			throw new IllegalStateException();
		}
	}

	public <T extends Particle> T readSystem(Class<T> substateClass) throws TxBuilderException {
		var typeByte = deserialization.classToByte(substateClass);
		var mapKey = SystemMapKey.ofSystem(typeByte);
		var localMaybe = lowLevelBuilder.get(mapKey);
		if (localMaybe.isPresent()) {
			var local = localMaybe.get();
			lowLevelBuilder.localRead(local.getIndex());
			return (T) local.getParticle();
		}

		var rawSubstate = remoteSubstate.get(mapKey).orElseThrow();
		read(SubstateId.fromBytes(rawSubstate.getId()));
		try {
			return (T) deserialization.deserialize(rawSubstate.getData());
		} catch (DeserializeException e) {
			throw new IllegalStateException();
		}
	}

	public UnclaimedREAddr downREAddr(REAddr addr) {
		var keyBytes = serialization.serializeKey(UnclaimedREAddr.class, addr);
		var typeByte = deserialization.classToByte(UnclaimedREAddr.class);
		var mapKey = SystemMapKey.ofSystem(typeByte);
		var localParent = lowLevelBuilder.get(mapKey);
		if (localParent.isPresent()) {
			lowLevelBuilder.localVirtualDown(localParent.get().getIndex(), keyBytes);
		} else {
			var parent = remoteSubstate.get(mapKey).orElseThrow();
			var substateId = SubstateId.fromBytes(parent.getId());
			lowLevelBuilder.virtualDown(substateId, keyBytes);
		}
		return serialization.mapVirtual(UnclaimedREAddr.class, addr);
	}

	public <T extends Particle, U> U shutdownAll(
		Class<T> particleClass,
		Function<Iterator<T>, U> mapper
	) {
		var typeByte = deserialization.classToByte(particleClass);
		return shutdownAll(SubstateIndex.create(typeByte, particleClass), mapper);
	}

	// FIXME: programmedInTxn is just a hack
	public <T extends Particle> CloseableCursor<T> readIndex(SubstateIndex<T> index, boolean programmedInTxn) {
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

		if (programmedInTxn) {
			lowLevelBuilder.readIndex(index);
		}

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

	public <T extends ResourceInBucket> UInt256 downFungible(
		SubstateIndex<T> index,
		Predicate<T> particlePredicate,
		UInt256 amount,
		Supplier<TxBuilderException> exceptionSupplier
	) throws TxBuilderException {
		var spent = UInt256.ZERO;
		for (var l : lowLevelBuilder.localUpSubstate()) {
			var p = l.getParticle();
			if (!index.getSubstateClass().isInstance(p) || !particlePredicate.test((T) p)) {
				continue;
			}
			var resource = (T) p;

			spent = spent.add(resource.getAmount());
			localDown(l.getIndex());

			if (spent.compareTo(amount) >= 0) {
				return spent.subtract(amount);
			}
		}

		try (var cursor = createRemoteSubstateCursor(index)) {
			while (cursor.hasNext()) {
				var raw = cursor.next();
				try {
					var resource = (T) deserialization.deserialize(raw.getData());
					if (!particlePredicate.test(resource)) {
						continue;
					}
					spent = spent.add(resource.getAmount());
					down(SubstateId.fromBytes(raw.getId()));
					if (spent.compareTo(amount) >= 0) {
						return spent.subtract(amount);
					}

				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			}
		}

		throw exceptionSupplier.get();
	}

	public UInt256 getFeeReserve() {
		return feeReservePut;
	}

	public <T extends ResourceInBucket> void putFeeReserve(
		REAddr feePayer,
		UInt256 amount,
		Supplier<TxBuilderException> exceptionSupplier
	) throws TxBuilderException {
		var buf = ByteBuffer.allocate(2 + 1 + ECPublicKey.COMPRESSED_BYTES);
		buf.put(SubstateTypeId.TOKENS.id());
		buf.put((byte) 0);
		buf.put(feePayer.getBytes());
		var index = SubstateIndex.create(buf.array(), TokensInAccount.class);
		// Take
		var remainder = downFungible(
			index,
			p -> p.getResourceAddr().isNativeToken() && p.getHoldingAddr().equals(feePayer),
			amount,
			exceptionSupplier
		);
		lowLevelBuilder.syscall(Syscall.FEE_RESERVE_PUT, amount);
		if (!remainder.isZero()) {
			up(new TokensInAccount(feePayer, REAddr.ofNativeToken(), remainder));
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

	public TxBuilder mutex(ECPublicKey key, String id) throws TxBuilderException {
		final var addr = REAddr.ofHashedKey(key, id);

		lowLevelBuilder.syscall(Syscall.READDR_CLAIM, id.getBytes(StandardCharsets.UTF_8));
		downREAddr(addr);
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
