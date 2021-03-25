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

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.atom.ActionTxBuilder;
import com.radixdlt.atom.ActionTxException;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Wallet where all state is kept in memory
 */
public final class InMemoryWallet {
	private final RRI tokenRRI;
	private final RadixAddress address;
	private final Set<TransferrableTokensParticle> particles;
	private final UInt256 fee = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(50));

	private InMemoryWallet(RRI tokenRRI, RadixAddress address, Set<TransferrableTokensParticle> particles) {
		this.tokenRRI = tokenRRI;
		this.address = address;
		this.particles = particles;
	}

	public List<Particle> particleList() {
		return new ArrayList<>(particles);
	}

	public Set<TransferrableTokensParticle> particles() {
		return particles;
	}

	public static InMemoryWallet create(RRI tokenRRI, RadixAddress address, Random random) {
		Objects.requireNonNull(tokenRRI);
		Objects.requireNonNull(address);
		Objects.requireNonNull(random);

		return new InMemoryWallet(tokenRRI, address, Collections.newSetFromMap(new ConcurrentHashMap<>()));
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

	public List<ActionTxBuilder> createParallelTransactions(RadixAddress to, int max) {
		List<TransferrableTokensParticle> shuffledParticles = new ArrayList<>(particles);
		Collections.shuffle(shuffledParticles);
		Stream<ActionTxBuilder> builders = shuffledParticles.stream()
			.filter(t -> t.getAmount().compareTo(fee.multiply(UInt256.TWO)) > 0)
			.flatMap(t -> {
				UInt256 amount = t.getAmount().subtract(fee).divide(UInt256.TWO);
				try {
					var builder = ActionTxBuilder.newBuilder(address, List.of(t))
						.transferNative(tokenRRI, to, amount)
						.burnForFee(tokenRRI, fee);
					return Stream.of(builder);
				} catch (ActionTxException e) {
					return Stream.of();
				}
			});

		List<TransferrableTokensParticle> dust = shuffledParticles.stream()
			.filter(t -> t.getAmount().compareTo(fee.multiply(UInt256.TWO)) <= 0)
			.collect(Collectors.toList());

		Stream<ActionTxBuilder> dustAtoms = Streams.stream(Iterables.partition(dust, 3))
			.flatMap(mutableList -> {
				UInt256 dustAmount = mutableList.stream()
					.map(TransferrableTokensParticle::getAmount)
					.reduce(UInt256.ZERO, UInt256::add);
				var particles = mutableList.stream().map(p -> (Particle) p).collect(Collectors.toList());

				try {
					var builder = ActionTxBuilder.newBuilder(address, particles)
						.transferNative(tokenRRI, to, dustAmount.subtract(fee))
						.burnForFee(tokenRRI, fee);
					return Stream.of(builder);
				} catch (ActionTxException e) {
					return Stream.of();
				}
			});

		return Stream.concat(builders, dustAtoms)
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
