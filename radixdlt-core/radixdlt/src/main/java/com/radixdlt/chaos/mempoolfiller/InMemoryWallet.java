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
 */

package com.radixdlt.chaos.mempoolfiller;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.AtomBuilder;
import com.radixdlt.atom.ParticleId;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokDefParticleFactory;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Wallet where all state is kept in memory
 */
public final class InMemoryWallet {
	private final RRI tokenRRI;
	private final RadixAddress address;
	private final Set<TransferrableTokensParticle> particles;
	private final TokDefParticleFactory factory;
	private final UInt256 fee = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(50));
	private final Random random;

	private InMemoryWallet(RRI tokenRRI, RadixAddress address, Random random, Set<TransferrableTokensParticle> particles) {
		this.tokenRRI = tokenRRI;
		this.address = address;
		this.random = random;
		this.particles = particles;

		this.factory = TokDefParticleFactory.create(
			tokenRRI,
			ImmutableMap.of(
				MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL,
				MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
			),
			UInt256.ONE
		);
	}

	public static InMemoryWallet create(RRI tokenRRI, RadixAddress address, Random random) {
		Objects.requireNonNull(tokenRRI);
		Objects.requireNonNull(address);
		Objects.requireNonNull(random);

		return new InMemoryWallet(tokenRRI, address, random, Collections.newSetFromMap(new ConcurrentHashMap<>()));
	}

	public int getNumParticles() {
		return particles.size();
	}

	public BigDecimal getBalance() {
		var balance = particles.stream()
			.map(TransferrableTokensParticle::getAmount)
			.reduce(UInt256::add)
			.orElse(UInt256.ZERO);

		return TokenUnitConversions.subunitsToUnits(balance);
	}

	private static Optional<UInt256> downParticles(
		UInt256 amount,
		LinkedList<TransferrableTokensParticle> particles,
		Consumer<ParticleId> onDown
	) {
		UInt256 spent = UInt256.ZERO;
		while (spent.compareTo(amount) < 0 && !particles.isEmpty()) {
			TransferrableTokensParticle particle = particles.removeFirst();
			onDown.accept(ParticleId.of(particle));
			spent = spent.add(particle.getAmount());
		}

		if (spent.compareTo(amount) < 0) {
			return Optional.empty();
		}

		return Optional.of(spent.subtract(amount));
	}

	public boolean createFeeGroup(AtomBuilder atomBuilder) {
		return createFeeGroup(atomBuilder, new LinkedList<>(particles));
	}

	private boolean createFeeGroup(AtomBuilder atomBuilder, LinkedList<TransferrableTokensParticle> mutableList) {
		atomBuilder.spinUp(factory.createUnallocated(fee));
		Optional<UInt256> remainder = downParticles(fee, mutableList, atomBuilder::spinDown);
		if (remainder.isEmpty()) {
			return false;
		}
		var r = remainder.get();
		if (!r.isZero()) {
			TransferrableTokensParticle particle = factory.createTransferrable(address, r, random.nextLong());
			mutableList.add(particle);
			atomBuilder.spinUp(particle);
		}

		atomBuilder.particleGroup();
		return true;
	}

	private Optional<AtomBuilder> createTransaction(LinkedList<TransferrableTokensParticle> mutableList, RadixAddress to, UInt256 amount) {
		AtomBuilder atomBuilder = Atom.newBuilder();
		boolean success = createFeeGroup(atomBuilder, mutableList);
		if (!success) {
			return Optional.empty();
		}

		atomBuilder.spinUp(factory.createTransferrable(to, amount, random.nextLong()));
		Optional<UInt256> remainder2 = downParticles(amount, mutableList, atomBuilder::spinDown);
		if (remainder2.isEmpty()) {
			return Optional.empty();
		}
		remainder2.filter(r -> !r.isZero()).ifPresent(r -> {
			TransferrableTokensParticle particle = factory.createTransferrable(address, r, random.nextLong());
			mutableList.add(particle);
			atomBuilder.spinUp(particle);
		});
		atomBuilder.particleGroup();
		return Optional.of(atomBuilder);
	}

	public Optional<AtomBuilder> createTransaction(RadixAddress to, UInt256 amount) {
		LinkedList<TransferrableTokensParticle> shuffledParticles = new LinkedList<>(particles);
		Collections.shuffle(shuffledParticles);
		return createTransaction(shuffledParticles, to, amount);
	}

	public List<AtomBuilder> createParallelTransactions(RadixAddress to, int max) {
		List<TransferrableTokensParticle> shuffledParticles = new ArrayList<>(particles);
		Collections.shuffle(shuffledParticles);
		Stream<Optional<AtomBuilder>> atoms = shuffledParticles.stream()
			.filter(t -> t.getAmount().compareTo(fee.multiply(UInt256.TWO)) > 0)
			.map(t -> {
				var mutableList = new LinkedList<TransferrableTokensParticle>();
				mutableList.add(t);
				UInt256 amount = t.getAmount().subtract(fee).divide(UInt256.TWO);
				return createTransaction(mutableList, to, amount.isZero() ? UInt256.ONE : amount);
			});

		List<TransferrableTokensParticle> dust = shuffledParticles.stream()
			.filter(t -> t.getAmount().compareTo(fee.multiply(UInt256.TWO)) <= 0)
			.collect(Collectors.toList());

		Stream<Optional<AtomBuilder>> dustAtoms = Streams.stream(Iterables.partition(dust, 3))
			.map(LinkedList::new)
			.map(mutableList -> {
				UInt256 dustAmount = mutableList.stream()
					.map(TransferrableTokensParticle::getAmount)
					.reduce(UInt256.ZERO, UInt256::add);
				return createTransaction(mutableList, to, dustAmount.subtract(fee));
			});

		return Stream.concat(atoms, dustAtoms)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.limit(max)
			.collect(Collectors.toList());
	}

	public void addParticle(TransferrableTokensParticle p) {
		if (!p.getTokDefRef().equals(tokenRRI)) {
			return;
		}

		if (!address.equals(p.getAddress())) {
			return;
		}

		particles.add(p);
	}

	public void removeParticle(TransferrableTokensParticle p) {
		if (!p.getTokDefRef().equals(tokenRRI)) {
			return;
		}

		if (!address.equals(p.getAddress())) {
			return;
		}

		particles.remove(p);
	}
}
