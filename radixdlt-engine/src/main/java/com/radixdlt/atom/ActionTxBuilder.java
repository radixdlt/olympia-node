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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.hash.HashCode;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokDefParticleFactory;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.atommodel.validators.RegisteredValidatorParticle;
import com.radixdlt.atommodel.validators.UnregisteredValidatorParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

public final class ActionTxBuilder {
	private final AtomBuilder atomBuilder;
	private final Set<ParticleId> downParticles;
	private final RadixAddress address;
	private final Iterable<Particle> upParticles;

	// TODO: remove
	private final Random random = new Random();

	private ActionTxBuilder(
		RadixAddress address,
		Set<ParticleId> downVirtualParticles,
		Iterable<Particle> upParticles
	) {
		this.address = address;
		this.atomBuilder = Atom.newBuilder();
		this.downParticles = new HashSet<>(downVirtualParticles);
		this.upParticles = upParticles;
	}

	public static ActionTxBuilder newBuilder(RadixAddress address, Iterable<Particle> upParticles) {
		return new ActionTxBuilder(address, Set.of(), upParticles);
	}

	public static ActionTxBuilder newBuilder(RadixAddress address) {
		return new ActionTxBuilder(address, Set.of(), List.of());
	}

	public static ActionTxBuilder newSystemBuilder(Iterable<Particle> upParticleList) {
		return new ActionTxBuilder(null, Set.of(), upParticleList);
	}

	public static ActionTxBuilder newSystemBuilder() {
		return new ActionTxBuilder(null, Set.of(), List.of());
	}

	private void particleGroup() {
		atomBuilder.particleGroup();
	}

	private void up(Particle particle) {
		atomBuilder.spinUp(particle);
	}

	private void virtualDown(Particle particle) {
		atomBuilder.virtualSpinDown(particle);
		downParticles.add(ParticleId.ofVirtualParticle(particle));
	}

	private void down(ParticleId particleId) {
		atomBuilder.spinDown(particleId);
		downParticles.add(particleId);
	}

	public Iterable<Particle> upParticles() {
		return Iterables.concat(
			atomBuilder.localUpParticles(),
			Iterables.filter(upParticles, p -> !downParticles.contains(ParticleId.of(p)))
		);
	}

	private <T extends Particle> T find(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		String errorMessage
	) throws ActionTxException {
		var substateRead = StreamSupport.stream(upParticles().spliterator(), false)
			.filter(particleClass::isInstance)
			.map(particleClass::cast)
			.filter(particlePredicate)
			.findFirst();
		if (substateRead.isEmpty()) {
			throw new ActionTxException(errorMessage);
		}

		return substateRead.get();
	}

	private <T extends Particle> T down(
		Class<T> particleClass,
		Optional<T> virtualParticle,
		String errorMessage
	) throws ActionTxException {
		return down(particleClass, p -> true, virtualParticle, errorMessage);
	}

	private <T extends Particle> T down(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		String errorMessage
	) throws ActionTxException {
		return down(particleClass, particlePredicate, Optional.empty(), errorMessage);
	}

	private <T extends Particle> T down(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Optional<T> virtualParticle,
		String errorMessage
	) throws ActionTxException {
		var substateDown = StreamSupport.stream(upParticles().spliterator(), false)
			.filter(particleClass::isInstance)
			.map(particleClass::cast)
			.filter(particlePredicate)
			.peek(p -> this.down(ParticleId.of(p)))
			.findFirst()
			.or(() -> {
				if (virtualParticle.isPresent()) {
					var p = virtualParticle.get();
					this.virtualDown(p);
				}
				return virtualParticle;
			});

		if (substateDown.isEmpty()) {
			throw new ActionTxException(errorMessage);
		}

		return substateDown.get();
	}

	private interface Mapper<T extends Particle, U extends Particle> {
		U map(T t) throws ActionTxException;
	}

	private interface Replacer<T extends Particle, U extends Particle> {
		void with(Mapper<T, U> mapper) throws ActionTxException;
	}

	private <T extends Particle, U extends Particle> Replacer<T, U> swap(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		String errorMessage
	) throws ActionTxException {
		T t = down(particleClass, particlePredicate, errorMessage);
		return replacer -> {
			U u = replacer.map(t);
			up(u);
		};
	}

	private <T extends Particle, U extends Particle> Replacer<T, U> swap(
		Class<T> particleClass,
		Optional<T> virtualParticle,
		String errorMessage
	) throws ActionTxException {
		T t = down(particleClass, virtualParticle, errorMessage);
		return replacer -> {
			U u = replacer.map(t);
			up(u);
		};
	}

	private <T extends Particle, U extends Particle> Replacer<T, U> swap(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Optional<T> virtualParticle,
		String errorMessage
	) throws ActionTxException {
		T t = down(particleClass, particlePredicate, virtualParticle, errorMessage);
		return replacer -> {
			U u = replacer.map(t);
			up(u);
		};
	}

	private interface FungibleMapper<U extends Particle> {
		U map(UInt256 t) throws ActionTxException;
	}

	private interface FungibleReplacer<U extends Particle> {
		void with(FungibleMapper<U> mapper) throws ActionTxException;
	}

	private <T extends Particle> UInt256 downFungible(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Function<T, UInt256> amountMapper,
		UInt256 amount,
		String errorMessage
	) throws ActionTxException {
		UInt256 spent = UInt256.ZERO;
		while (spent.compareTo(amount) < 0) {
			var substateDown = down(
				particleClass,
				particlePredicate,
				errorMessage
			);

			spent = spent.add(amountMapper.apply(substateDown));
		}

		return spent.subtract(amount);
	}

	private <T extends Particle, U extends Particle> FungibleReplacer<U> swapFungible(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Function<T, UInt256> amountMapper,
		FungibleMapper<T> remainderMapper,
		UInt256 amount,
		String errorMessage
	) {
		return mapper -> {
			up(mapper.map(amount));
			var remainder = downFungible(particleClass, particlePredicate, amountMapper, amount, errorMessage);
			if (!remainder.isZero()) {
				up(remainderMapper.map(remainder));
			}
		};
	}


	private void assertHasAddress(String message) throws ActionTxException {
		if (address == null) {
			throw new ActionTxException(message);
		}
	}

	private void assertIsSystem(String message) throws ActionTxException {
		if (address != null) {
			throw new ActionTxException(message);
		}
	}

	public ActionTxBuilder systemNextView(long view, long timestamp) throws ActionTxException {
		assertIsSystem("Not permitted as user to execute system next view");

		swap(
			SystemParticle.class,
			Optional.of(new SystemParticle(0, 0, 0)),
			"No System particle available"
		).with(substateDown -> {
			if (view <= substateDown.getView()) {
				throw new ActionTxException("Next view isn't higher than current view.");
			}
			return new SystemParticle(substateDown.getEpoch(), view, timestamp);
		});

		particleGroup();

		return this;
	}

	public ActionTxBuilder systemNextEpoch(long timestamp) throws ActionTxException {
		assertIsSystem("Not permitted as user to execute system next epoch");

		swap(
			SystemParticle.class,
			Optional.of(new SystemParticle(0, 0, 0)),
			"No System particle available"
		).with(substateDown -> new SystemParticle(substateDown.getEpoch() + 1, 0, timestamp));
		particleGroup();

		return this;
	}

	public ActionTxBuilder registerAsValidator() throws ActionTxException {
		assertHasAddress("Must have address");

		swap(
			UnregisteredValidatorParticle.class,
			p -> p.getAddress().equals(address),
			Optional.of(new UnregisteredValidatorParticle(address, 0L)),
			"Already a validator"
		).with(
			substateDown -> new RegisteredValidatorParticle(address, ImmutableSet.of(), substateDown.getNonce() + 1)
		);

		particleGroup();
		return this;
	}

	public ActionTxBuilder unregisterAsValidator() throws ActionTxException {
		assertHasAddress("Must have address");

		swap(
			RegisteredValidatorParticle.class,
			p -> p.getAddress().equals(address),
			"Already unregistered."
		).with(
			substateDown -> new UnregisteredValidatorParticle(address, substateDown.getNonce() + 1)
		);
		particleGroup();

		return this;
	}



	public ActionTxBuilder mutex(String id) throws ActionTxException {
		assertHasAddress("Must have address");

		final var tokenRRI = RRI.of(address, id);
		swap(
			RRIParticle.class,
			p -> p.getRri().equals(tokenRRI),
			Optional.of(new RRIParticle(tokenRRI)),
			"RRI not available"
		).with(rri -> new UniqueParticle(id, address, 1));

		particleGroup();

		return this;
	}

	public ActionTxBuilder createFixedToken(FixedTokenDefinition tokenDefinition) throws ActionTxException {
		assertHasAddress("Must have address");

		final var tokenRRI = RRI.of(address, tokenDefinition.getSymbol());

		down(
			RRIParticle.class,
			p -> p.getRri().equals(tokenRRI),
			Optional.of(new RRIParticle(tokenRRI)),
			"RRI not available"
		);

		up(new TransferrableTokensParticle(
			address,
			tokenDefinition.getSupply(),
			tokenDefinition.getGranularity(),
			tokenRRI,
			ImmutableMap.of())
		);

		up(new FixedSupplyTokenDefinitionParticle(
			tokenRRI,
			tokenDefinition.getName(),
			tokenDefinition.getDescription(),
			tokenDefinition.getSupply(),
			tokenDefinition.getGranularity(),
			tokenDefinition.getIconUrl(),
			tokenDefinition.getTokenUrl()
		));

		particleGroup();

		return this;
	}

	public ActionTxBuilder createMutableToken(MutableTokenDefinition tokenDefinition) throws ActionTxException {
		assertHasAddress("Must have address");

		final var tokenRRI = RRI.of(address, tokenDefinition.getSymbol());
		down(
			RRIParticle.class,
			p -> p.getRri().equals(tokenRRI),
			Optional.of(new RRIParticle(tokenRRI)),
			"RRI not available"
		);
		final var factory = TokDefParticleFactory.create(
			tokenRRI, tokenDefinition.getTokenPermissions(), UInt256.ONE
		);
		up(factory.createUnallocated(UInt256.MAX_VALUE));
		up(new MutableSupplyTokenDefinitionParticle(
			tokenRRI,
			tokenDefinition.getName(),
			tokenDefinition.getDescription(),
			tokenDefinition.getGranularity(),
			tokenDefinition.getIconUrl(),
			tokenDefinition.getTokenUrl(),
			tokenDefinition.getTokenPermissions()
		));
		particleGroup();

		return this;
	}

	public ActionTxBuilder mint(RRI rri, RadixAddress to, UInt256 amount) throws ActionTxException {
		var tokenDefSubstate = find(
			MutableSupplyTokenDefinitionParticle.class,
			p -> p.getRRI().equals(rri),
			"Could not find token rri " + rri
		);

		final var factory = TokDefParticleFactory.create(
			rri, tokenDefSubstate.getTokenPermissions(), tokenDefSubstate.getGranularity()
		);

		swapFungible(
			UnallocatedTokensParticle.class,
			p -> p.getTokDefRef().equals(rri),
			UnallocatedTokensParticle::getAmount,
			factory::createUnallocated,
			amount,
			"Not enough balance to for minting."
		).with(amt -> factory.createTransferrable(to, amt, random.nextLong()));

		particleGroup();

		return this;
	}

	public ActionTxBuilder transferNative(RRI rri, RadixAddress to, UInt256 amount) throws ActionTxException {
		// HACK
		var factory = TokDefParticleFactory.create(
			rri,
			ImmutableMap.of(
				MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL,
				MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
			),
			UInt256.ONE
		);

		swapFungible(
			TransferrableTokensParticle.class,
			p -> p.getTokDefRef().equals(rri) && p.getAddress().equals(address),
			TransferrableTokensParticle::getAmount,
			amt -> factory.createTransferrable(address, amt, random.nextLong()),
			amount,
			"Not enough balance for transfer."
		).with(amt -> factory.createTransferrable(to, amount, random.nextLong()));

		particleGroup();

		return this;
	}

	public ActionTxBuilder transfer(RRI rri, RadixAddress to, UInt256 amount) throws ActionTxException {
		var tokenDefSubstate = find(
			MutableSupplyTokenDefinitionParticle.class,
			p -> p.getRRI().equals(rri),
			"Could not find token rri " + rri
		);
		final var factory = TokDefParticleFactory.create(
			rri, tokenDefSubstate.getTokenPermissions(), tokenDefSubstate.getGranularity()
		);
		swapFungible(
			TransferrableTokensParticle.class,
			p -> p.getTokDefRef().equals(rri) && p.getAddress().equals(address),
			TransferrableTokensParticle::getAmount,
			amt -> factory.createTransferrable(address, amt, random.nextLong()),
			amount,
			"Not enough balance for transfer."
		).with(amt -> factory.createTransferrable(to, amount, random.nextLong()));

		particleGroup();

		return this;
	}

	public ActionTxBuilder stakeTo(RRI rri, RadixAddress delegateAddress, UInt256 amount) throws ActionTxException {
		assertHasAddress("Must have an address.");
		// HACK
		var factory = TokDefParticleFactory.create(
			rri,
			ImmutableMap.of(
				MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL,
				MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
			),
			UInt256.ONE
		);

		swap(
			RegisteredValidatorParticle.class,
			p -> p.getAddress().equals(delegateAddress) && p.allowsDelegator(address),
			"Cannot delegate to " + delegateAddress
		).with(substateDown -> substateDown.copyWithNonce(substateDown.getNonce() + 1));

		swapFungible(
			TransferrableTokensParticle.class,
			p -> p.getTokDefRef().equals(rri) && p.getAddress().equals(address),
			TransferrableTokensParticle::getAmount,
			amt -> factory.createTransferrable(address, amt, random.nextLong()),
			amount,
			"Not enough balance for staking."
		).with(amt -> factory.createStaked(delegateAddress, address, amount, random.nextLong()));

		particleGroup();

		return this;
	}

	public ActionTxBuilder burnForFee(RRI rri, UInt256 amount) throws ActionTxException {
		// HACK
		var factory = TokDefParticleFactory.create(
			rri,
			ImmutableMap.of(
				MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL,
				MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
			),
			UInt256.ONE
		);
		swapFungible(
			TransferrableTokensParticle.class,
			p -> p.getTokDefRef().equals(rri) && p.getAddress().equals(address),
			TransferrableTokensParticle::getAmount,
			amt -> factory.createTransferrable(address, amt, random.nextLong()),
			amount,
			"Not enough balance to for fee burn."
		).with(factory::createUnallocated);

		particleGroup();
		return this;
	}

	public Atom signAndBuild(Function<HashCode, ECDSASignature> signer) {
		return atomBuilder.signAndBuild(signer);
	}

	public Atom buildWithoutSignature() {
		return atomBuilder.buildWithoutSignature();
	}
}
