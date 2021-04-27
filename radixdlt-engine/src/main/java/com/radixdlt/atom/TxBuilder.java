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

import com.google.common.collect.Streams;
import com.google.common.hash.HashCode;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.atomos.REAddrParticle;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
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
	private final ECPublicKey user;
	private final SubstateStore remoteSubstate;

	private TxBuilder(
		ECPublicKey user,
		SubstateStore remoteSubstate
	) {
		this.user = user;
		this.lowLevelBuilder = TxLowLevelBuilder.newBuilder();
		this.remoteSubstate = remoteSubstate;
	}

	public static TxBuilder newBuilder(ECPublicKey user, SubstateStore remoteSubstate) {
		return new TxBuilder(user, remoteSubstate);
	}

	public static TxBuilder newBuilder(ECPublicKey user) {
		return new TxBuilder(user, SubstateStore.empty());
	}

	public static TxBuilder newBuilder(SubstateStore remoteSubstate) {
		return new TxBuilder(null, remoteSubstate);
	}

	public static TxBuilder newBuilder() {
		return new TxBuilder(null, SubstateStore.empty());
	}

	public TxLowLevelBuilder toLowLevelBuilder() {
		return lowLevelBuilder;
	}

	public void particleGroup() {
		lowLevelBuilder.particleGroup();
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

	private SubstateCursor createRemoteSubstateCursor(Class<? extends Particle> particleClass) {
		return SubstateCursor.filter(
			remoteSubstate.openIndexedCursor(particleClass),
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
	public <T extends Particle> Substate findSubstate(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		TxErrorCode errorCode,
		String errorMessage
	) throws TxBuilderException {
		try (var cursor = createRemoteSubstateCursor(particleClass)) {
			var substateRead = iteratorToStream(cursor)
				.filter(s -> particlePredicate.test(particleClass.cast(s.getParticle())))
				.findFirst();

			if (substateRead.isEmpty()) {
				throw new TxBuilderException(errorCode, errorMessage);
			}

			return substateRead.get();
		}
	}

	public <T extends Particle> T find(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		TxErrorCode errorCode,
		String errorMessage
	) throws TxBuilderException {
		try (var cursor = createRemoteSubstateCursor(particleClass)) {
			var substateRead = Streams.concat(
				lowLevelBuilder.localUpSubstate().stream().map(LocalSubstate::getParticle),
				iteratorToStream(cursor)
			)
				.filter(particleClass::isInstance)
				.map(particleClass::cast)
				.filter(particlePredicate)
				.findFirst();
			if (substateRead.isEmpty()) {
				throw new TxBuilderException(errorCode, errorMessage);
			}

			return substateRead.get();
		}
	}

	private <T extends Particle> T down(
		Class<T> particleClass,
		Optional<SubstateWithArg<T>> virtualParticle,
		TxErrorCode errorCode,
		String errorMessage
	) throws TxBuilderException {
		return down(particleClass, p -> true, virtualParticle, errorCode, errorMessage);
	}

	private <T extends Particle> T down(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		TxErrorCode errorCode,
		String errorMessage
	) throws TxBuilderException {
		return down(particleClass, particlePredicate, Optional.empty(), errorCode, errorMessage);
	}

	public <T extends Particle> T down(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Optional<SubstateWithArg<T>> virtualParticle,
		TxErrorCode errorCode,
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
				throw new TxBuilderException(errorCode, errorMessage + " (Substate not found)");
			}

			return substateDown.get();
		}
	}

	public interface Mapper<T extends Particle, U extends Particle> {
		U map(T t) throws TxBuilderException;
	}

	public interface Replacer<T extends Particle, U extends Particle> {
		void with(Mapper<T, U> mapper) throws TxBuilderException;
	}

	public <T extends Particle, U extends Particle> Replacer<T, U> swap(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		TxErrorCode errorCode,
		String errorMessage
	) throws TxBuilderException {
		T t = down(particleClass, particlePredicate, errorCode, errorMessage);
		return replacer -> {
			U u = replacer.map(t);
			up(u);
		};
	}

	private <T extends Particle, U extends Particle> Replacer<T, U> swap(
		Class<T> particleClass,
		Optional<SubstateWithArg<T>> virtualParticle,
		TxErrorCode errorCode,
		String errorMessage
	) throws TxBuilderException {
		T t = down(particleClass, virtualParticle, errorCode, errorMessage);
		return replacer -> {
			U u = replacer.map(t);
			up(u);
		};
	}

	public <T extends Particle, U extends Particle> Replacer<T, U> swap(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Optional<SubstateWithArg<T>> virtualParticle,
		TxErrorCode errorCode,
		String errorMessage
	) throws TxBuilderException {
		T t = down(particleClass, particlePredicate, virtualParticle, errorCode, errorMessage);
		return replacer -> {
			U u = replacer.map(t);
			up(u);
		};
	}

	public interface FungibleMapper<U extends Particle> {
		U map(UInt256 t) throws TxBuilderException;
	}

	public interface FungibleReplacer<U extends Particle> {
		void with(FungibleMapper<U> mapper) throws TxBuilderException;
	}

	private <T extends Particle> UInt256 downFungible(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Function<T, UInt256> amountMapper,
		UInt256 amount,
		TxErrorCode errorCode,
		String errorMessage
	) throws TxBuilderException {
		UInt256 spent = UInt256.ZERO;
		while (spent.compareTo(amount) < 0) {
			var substateDown = down(
				particleClass,
				particlePredicate,
				errorCode,
				errorMessage
			);

			spent = spent.add(amountMapper.apply(substateDown));
		}

		return spent.subtract(amount);
	}

	public <T extends Particle> void deallocateFungible(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Function<T, UInt256> amountMapper,
		FungibleMapper<T> remainderMapper,
		UInt256 amount,
		TxErrorCode errorCode,
		String errorMessage
	) throws TxBuilderException {
		UInt256 spent = UInt256.ZERO;
		while (spent.compareTo(amount) < 0) {
			// FIXME: This is a hack due to the constraint machine not being able to
			// FIXME: handle spins of the same type one after the other yet.
			if (!spent.isZero()) {
				particleGroup();
			}

			var substateDown = down(
				particleClass,
				particlePredicate,
				errorCode,
				errorMessage
			);

			spent = spent.add(amountMapper.apply(substateDown));
		}

		var remainder = spent.subtract(amount);
		if (!remainder.isZero()) {
			up(remainderMapper.map(remainder));
		}
	}

	public <T extends Particle, U extends Particle> FungibleReplacer<U> swapFungible(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Function<T, UInt256> amountMapper,
		FungibleMapper<T> remainderMapper,
		UInt256 amount,
		TxErrorCode errorCode,
		String errorMessage
	) {
		return mapper -> {
			var substateUp = mapper.map(amount);
			up(substateUp);
			var remainder = downFungible(
				particleClass,
				particlePredicate.and(p -> !p.equals(substateUp)), // HACK to allow mempool filler to do it's thing
				amountMapper,
				amount,
				errorCode,
				errorMessage
			);
			if (!remainder.isZero()) {
				up(remainderMapper.map(remainder));
			}
		};
	}


	public Optional<ECPublicKey> getUser() {
		return Optional.ofNullable(user);
	}

	public ECPublicKey getUserOrFail(String errorMessage) throws TxBuilderException {
		if (user == null) {
			throw new TxBuilderException(TxErrorCode.ADDRESS_IS_MISSING, errorMessage);
		}
		return user;
	}

	public void assertHasAddress(String message) throws TxBuilderException {
		if (user == null) {
			throw new TxBuilderException(TxErrorCode.ADDRESS_IS_MISSING, message);
		}
	}

	public void assertIsSystem(String message) throws TxBuilderException {
		if (user != null) {
			throw new TxBuilderException(TxErrorCode.NOT_A_SYSTEM, message);
		}
	}

	public TxBuilder mutex(String id) throws TxBuilderException {
		assertHasAddress("Must have address");

		final var addr = REAddr.ofHashedKey(user, id);
		swap(
			REAddrParticle.class,
			p -> p.getAddr().equals(addr),
			Optional.of(SubstateWithArg.withArg(new REAddrParticle(addr), id.getBytes(StandardCharsets.UTF_8))),
			TxErrorCode.RRI_NOT_AVAILABLE,
			"RRI not available"
		).with(r -> new UniqueParticle(addr));

		particleGroup();

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
