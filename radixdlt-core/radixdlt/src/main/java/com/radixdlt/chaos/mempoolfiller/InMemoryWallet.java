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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.atommodel.AtomBuilder;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokDefParticleFactory;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.utils.UInt256;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Wallet where all state is kept in memory
 */
public final class InMemoryWallet {
	private final RRI tokenRRI;
	private final RadixAddress address;
	private final ImmutableList<TransferrableTokensParticle> particles;
	private final UInt256 balance;
	private final TokDefParticleFactory factory;
	private final UInt256 fee = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(50));
	private final Random random;

	private InMemoryWallet(RRI tokenRRI, RadixAddress address, Random random, ImmutableList<TransferrableTokensParticle> particles) {
		this.tokenRRI = tokenRRI;
		this.address = address;
		this.random = random;
		this.particles = particles;
		this.balance = particles.stream()
			.map(TransferrableTokensParticle::getAmount)
			.reduce(UInt256::add)
			.orElse(UInt256.ZERO);
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

		return new InMemoryWallet(tokenRRI, address, random, ImmutableList.of());
	}

	public int getNumParticles() {
		return particles.size();
	}

	public BigDecimal getBalance() {
		return TokenUnitConversions.subunitsToUnits(balance);
	}

	private static Optional<UInt256> downParticles(
		UInt256 amount,
		LinkedList<TransferrableTokensParticle> particles,
		Consumer<TransferrableTokensParticle> onDown
	) {
		UInt256 spent = UInt256.ZERO;
		while (spent.compareTo(amount) < 0 && !particles.isEmpty()) {
			TransferrableTokensParticle particle = particles.removeFirst();
			onDown.accept(particle);
			spent = spent.add(particle.getAmount());
		}

		if (spent.compareTo(amount) < 0) {
			return Optional.empty();
		}

		return Optional.of(spent.subtract(amount));
	}

	public Optional<ParticleGroup> createFeeGroup() {
		return createFeeGroup(new LinkedList<>(particles));
	}

	private Optional<ParticleGroup> createFeeGroup(LinkedList<TransferrableTokensParticle> mutableList) {
		ParticleGroup.ParticleGroupBuilder feeBuilder = ParticleGroup.builder();
		feeBuilder.addParticle(factory.createUnallocated(fee), Spin.UP);
		Optional<UInt256> remainder = downParticles(fee, mutableList, p -> feeBuilder.addParticle(p, Spin.DOWN));
		return remainder.map(r -> {
		    if (!r.isZero()) {
				TransferrableTokensParticle particle = factory.createTransferrable(address, r, random.nextLong());
				mutableList.add(particle);
				feeBuilder.addParticle(particle, Spin.UP);
		    }

		    return feeBuilder.build();
		});
	}

	private Optional<AtomBuilder> createTransaction(LinkedList<TransferrableTokensParticle> mutableList, RadixAddress to, UInt256 amount) {
		AtomBuilder atom = new AtomBuilder();
		Optional<ParticleGroup> feeGroup = createFeeGroup(mutableList);
		if (feeGroup.isEmpty()) {
			return Optional.empty();
		}
		atom.addParticleGroup(feeGroup.get());

		ParticleGroup.ParticleGroupBuilder builder = ParticleGroup.builder();
		builder.addParticle(factory.createTransferrable(to, amount, random.nextLong()), Spin.UP);
		Optional<UInt256> remainder2 = downParticles(amount, mutableList, p -> builder.addParticle(p, Spin.DOWN));
		if (remainder2.isEmpty()) {
			return Optional.empty();
		}
		remainder2.filter(r -> !r.isZero()).ifPresent(r -> {
			TransferrableTokensParticle particle = factory.createTransferrable(address, r, random.nextLong());
			mutableList.add(particle);
			builder.addParticle(particle, Spin.UP);
		});
		atom.addParticleGroup(builder.build());
		return Optional.of(atom);
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

	public InMemoryWallet addParticle(TransferrableTokensParticle p) {
		if (!p.getTokDefRef().equals(tokenRRI)) {
			return this;
		}

		if (!address.equals(p.getAddress())) {
			return this;
		}

		return new InMemoryWallet(
			tokenRRI,
			address,
			random,
			ImmutableList.<TransferrableTokensParticle>builder()
				.addAll(particles)
				.add(p)
				.build()
		);
	}

	public InMemoryWallet removeParticle(TransferrableTokensParticle p) {
		if (!p.getTokDefRef().equals(tokenRRI)) {
			return this;
		}

		if (!address.equals(p.getAddress())) {
			return this;
		}

		return new InMemoryWallet(
			tokenRRI,
			address,
			random,
			particles.stream()
				.filter(particle -> !p.equals(particle))
				.collect(ImmutableList.toImmutableList())
		);
	}
}
