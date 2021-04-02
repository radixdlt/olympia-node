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
import com.google.common.collect.Streams;
import com.google.common.hash.HashCode;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
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
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Creates a transaction from high level user actions
 */
public final class TxBuilder {
	private final TxLowLevelBuilder lowLevelBuilder;
	private final Set<SubstateId> downParticles;
	private final RadixAddress address;
	private final SubstateStore remoteSubstate;

	private TxBuilder(
		RadixAddress address,
		Set<SubstateId> downVirtualParticles,
		SubstateStore remoteSubstate
	) {
		this.address = address;
		this.lowLevelBuilder = TxLowLevelBuilder.newBuilder();
		this.downParticles = new HashSet<>(downVirtualParticles);
		this.remoteSubstate = remoteSubstate;
	}

	public static TxBuilder newBuilder(RadixAddress address, SubstateStore remoteSubstate) {
		return new TxBuilder(address, Set.of(), remoteSubstate);
	}

	public static TxBuilder newBuilder(RadixAddress address) {
		return new TxBuilder(address, Set.of(), c -> List.of());
	}

	public static TxBuilder newSystemBuilder(SubstateStore remoteSubstate) {
		return new TxBuilder(null, Set.of(), remoteSubstate);
	}

	public static TxBuilder newSystemBuilder() {
		return new TxBuilder(null, Set.of(), c -> List.of());
	}

	public TxLowLevelBuilder toLowLevelBuilder() {
		return lowLevelBuilder;
	}

	private void particleGroup() {
		lowLevelBuilder.particleGroup();
	}

	private void up(Particle particle) {
		lowLevelBuilder.up(particle);
	}

	private void virtualDown(Particle particle) {
		lowLevelBuilder.virtualDown(particle);
		downParticles.add(SubstateId.ofVirtualSubstate(particle));
	}

	private void down(SubstateId substateId) {
		lowLevelBuilder.down(substateId);
		downParticles.add(substateId);
	}

	private void localDown(int index) {
		lowLevelBuilder.localDown(index);
	}

	private Iterable<Substate> remoteSubstates(Class<? extends Particle> particleClass) {
		return Iterables.filter(
			remoteSubstate.index(particleClass),
			s -> !downParticles.contains(s.getId())
		);
	}

	private Stream<Substate> remoteSubstatesStream(Class<? extends Particle> particleClass) {
		return StreamSupport.stream(remoteSubstates(particleClass).spliterator(), false);
	}

	private <T extends Particle> Substate findSubstate(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		int index,
		String errorMessage
	) throws TxBuilderException {
		var substateRead = remoteSubstatesStream(particleClass)
			.filter(s -> {
				if (!particleClass.isInstance(s.getParticle())) {
					return false;
				}

				return particlePredicate.test(particleClass.cast(s.getParticle()));
			})
			.skip(index)
			.findFirst();
		if (substateRead.isEmpty()) {
			throw new TxBuilderException(errorMessage);
		}

		return substateRead.get();
	}

	private <T extends Particle> T find(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		String errorMessage
	) throws TxBuilderException {
		var substateRead = Streams.concat(
			lowLevelBuilder.localUpSubstate().stream().map(LocalSubstate::getParticle),
			remoteSubstatesStream(particleClass).map(Substate::getParticle)
		)
			.filter(particleClass::isInstance)
			.map(particleClass::cast)
			.filter(particlePredicate)
			.findFirst();
		if (substateRead.isEmpty()) {
			throw new TxBuilderException(errorMessage);
		}

		return substateRead.get();
	}

	private <T extends Particle> T down(
		Class<T> particleClass,
		Optional<T> virtualParticle,
		String errorMessage
	) throws TxBuilderException {
		return down(particleClass, p -> true, virtualParticle, errorMessage);
	}

	private <T extends Particle> T down(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		String errorMessage
	) throws TxBuilderException {
		return down(particleClass, particlePredicate, Optional.empty(), errorMessage);
	}

	private <T extends Particle> T down(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Optional<T> virtualParticle,
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

		var substateDown = remoteSubstatesStream(particleClass)
			.filter(s -> particlePredicate.test(particleClass.cast(s.getParticle())))
			.peek(s -> this.down(s.getId()))
			.map(Substate::getParticle)
			.map(particleClass::cast)
			.findFirst()
			.or(() -> {
				if (virtualParticle.isPresent()) {
					var p = virtualParticle.get();
					this.virtualDown(p);
				}
				return virtualParticle;
			});

		if (substateDown.isEmpty()) {
			throw new TxBuilderException(errorMessage);
		}

		return substateDown.get();
	}

	private interface Mapper<T extends Particle, U extends Particle> {
		U map(T t) throws TxBuilderException;
	}

	private interface Replacer<T extends Particle, U extends Particle> {
		void with(Mapper<T, U> mapper) throws TxBuilderException;
	}

	private <T extends Particle, U extends Particle> Replacer<T, U> swap(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		String errorMessage
	) throws TxBuilderException {
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
	) throws TxBuilderException {
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
	) throws TxBuilderException {
		T t = down(particleClass, particlePredicate, virtualParticle, errorMessage);
		return replacer -> {
			U u = replacer.map(t);
			up(u);
		};
	}

	private interface FungibleMapper<U extends Particle> {
		U map(UInt256 t) throws TxBuilderException;
	}

	private interface FungibleReplacer<U extends Particle> {
		void with(FungibleMapper<U> mapper) throws TxBuilderException;
	}

	private <T extends Particle> UInt256 downFungible(
		Class<T> particleClass,
		Predicate<T> particlePredicate,
		Function<T, UInt256> amountMapper,
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
			var substateUp = mapper.map(amount);
			up(substateUp);
			var remainder = downFungible(
				particleClass,
				particlePredicate.and(p -> !p.equals(substateUp)), // HACK to allow mempool filler to do it's thing
				amountMapper,
				amount,
				errorMessage
			);
			if (!remainder.isZero()) {
				up(remainderMapper.map(remainder));
			}
		};
	}


	private void assertHasAddress(String message) throws TxBuilderException {
		if (address == null) {
			throw new TxBuilderException(message);
		}
	}

	private void assertIsSystem(String message) throws TxBuilderException {
		if (address != null) {
			throw new TxBuilderException(message);
		}
	}

	public TxBuilder systemNextView(long view, long timestamp, long currentEpoch) throws TxBuilderException {
		assertIsSystem("Not permitted as user to execute system next view");

		swap(
			SystemParticle.class,
			p -> p.getEpoch() == currentEpoch,
			currentEpoch == 0 ? Optional.of(new SystemParticle(0, 0, 0)) : Optional.empty(),
			"No System particle available"
		).with(substateDown -> {
			if (view <= substateDown.getView()) {
				throw new TxBuilderException("Next view isn't higher than current view.");
			}
			return new SystemParticle(substateDown.getEpoch(), view, timestamp);
		});

		particleGroup();

		return this;
	}

	public TxBuilder systemNextEpoch(long timestamp, long currentEpoch) throws TxBuilderException {
		assertIsSystem("Not permitted as user to execute system next epoch");

		swap(
			SystemParticle.class,
			p -> p.getEpoch() == currentEpoch,
			currentEpoch == 0 ? Optional.of(new SystemParticle(0, 0, 0)) : Optional.empty(),
			"No System particle available"
		).with(substateDown -> new SystemParticle(substateDown.getEpoch() + 1, 0, timestamp));
		particleGroup();

		return this;
	}

	public TxBuilder registerAsValidator() throws TxBuilderException {
		assertHasAddress("Must have address");

		swap(
			UnregisteredValidatorParticle.class,
			p -> p.getAddress().equals(address),
			Optional.of(new UnregisteredValidatorParticle(address)),
			"Already a validator"
		).with(
			substateDown -> new RegisteredValidatorParticle(address, ImmutableSet.of())
		);

		particleGroup();
		return this;
	}

	public TxBuilder unregisterAsValidator() throws TxBuilderException {
		assertHasAddress("Must have address");

		swap(
			RegisteredValidatorParticle.class,
			p -> p.getAddress().equals(address),
			"Already unregistered."
		).with(
			substateDown -> new UnregisteredValidatorParticle(address)
		);
		particleGroup();

		return this;
	}

	public TxBuilder mutex(String id) throws TxBuilderException {
		assertHasAddress("Must have address");

		final var tokenRRI = RRI.of(address, id);
		swap(
			RRIParticle.class,
			p -> p.getRri().equals(tokenRRI),
			Optional.of(new RRIParticle(tokenRRI)),
			"RRI not available"
		).with(rri -> new UniqueParticle(id, address));

		particleGroup();

		return this;
	}

	public TxBuilder createFixedToken(FixedTokenDefinition tokenDefinition) throws TxBuilderException {
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

	public TxBuilder createMutableToken(MutableTokenDefinition tokenDefinition) throws TxBuilderException {
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

	public TxBuilder mint(RRI rri, RadixAddress to, UInt256 amount) throws TxBuilderException {
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
		).with(amt -> factory.createTransferrable(to, amt));

		particleGroup();

		return this;
	}

	// For mempool filler
	public TxBuilder splitNative(RRI rri, UInt256 minSize, int index) throws TxBuilderException {
		assertHasAddress("Must have address");

		// HACK
		var factory = TokDefParticleFactory.create(
			rri,
			ImmutableMap.of(
				MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL,
				MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
			),
			UInt256.ONE
		);

		var substate = findSubstate(
			TransferrableTokensParticle.class,
			p -> p.getTokDefRef().equals(rri)
				&& p.getAddress().equals(address)
				&& p.getAmount().compareTo(minSize) > 0,
			index,
			"Could not find large particle greater than " + minSize
		);

		down(substate.getId());
		var particle = (TransferrableTokensParticle) substate.getParticle();
		var amt1 = particle.getAmount().divide(UInt256.TWO);
		var amt2 = particle.getAmount().subtract(amt1);
		up(factory.createTransferrable(address, amt1));
		up(factory.createTransferrable(address, amt2));
		particleGroup();

		return this;
	}

	public TxBuilder transferNative(RRI rri, RadixAddress to, UInt256 amount) throws TxBuilderException {
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
			amt -> factory.createTransferrable(address, amt),
			amount,
			"Not enough balance for transfer."
		).with(amt -> factory.createTransferrable(to, amount));

		particleGroup();

		return this;
	}

	public TxBuilder transfer(RRI rri, RadixAddress to, UInt256 amount) throws TxBuilderException {
		// TODO: Need to include mutable supply
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
			amt -> factory.createTransferrable(address, amt),
			amount,
			"Not enough balance for transfer."
		).with(amt -> factory.createTransferrable(to, amount));

		particleGroup();

		return this;
	}

	public TxBuilder burn(RRI rri, UInt256 amount) throws TxBuilderException {
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
			amt -> factory.createTransferrable(address, amt),
			amount,
			"Not enough balance for burn."
		).with(amt -> factory.createUnallocated(amount));

		particleGroup();

		return this;
	}

	public TxBuilder stakeTo(RRI rri, RadixAddress delegateAddress, UInt256 amount) throws TxBuilderException {
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
		).with(RegisteredValidatorParticle::copy);

		swapFungible(
			TransferrableTokensParticle.class,
			p -> p.getTokDefRef().equals(rri) && p.getAddress().equals(address),
			TransferrableTokensParticle::getAmount,
			amt -> factory.createTransferrable(address, amt),
			amount,
			"Not enough balance for staking."
		).with(amt -> factory.createStaked(delegateAddress, address, amt));

		particleGroup();

		return this;
	}

	public TxBuilder unstakeFrom(RRI rri, RadixAddress delegateAddress, UInt256 amount) throws TxBuilderException {
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

		swapFungible(
			StakedTokensParticle.class,
			p -> p.getTokDefRef().equals(rri) && p.getAddress().equals(address),
			StakedTokensParticle::getAmount,
			amt -> factory.createStaked(delegateAddress, address, amt),
			amount,
			"Not enough staked."
		).with(amt -> factory.createTransferrable(address, amt));

		particleGroup();

		return this;
	}

	// FIXME: This is broken as can move stake to delegate who doesn't approve of you
	public TxBuilder moveStake(RRI rri, RadixAddress from, RadixAddress to, UInt256 amount) throws TxBuilderException {
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

		swapFungible(
			StakedTokensParticle.class,
			p -> p.getTokDefRef().equals(rri) && p.getAddress().equals(address),
			StakedTokensParticle::getAmount,
			amt -> factory.createStaked(from, address, amt),
			amount,
			"Not enough staked."
		).with(amt -> factory.createStaked(to, address, amt));

		particleGroup();

		return this;
	}

	public TxBuilder burnForFee(RRI rri, UInt256 amount) throws TxBuilderException {
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
			amt -> factory.createTransferrable(address, amt),
			amount,
			"Not enough balance to for fee burn."
		).with(factory::createUnallocated);

		particleGroup();
		return this;
	}

	public Atom signAndBuildRemoteSubstateOnly(
		Function<HashCode, ECDSASignature> signer,
		Consumer<SubstateStore> upSubstateConsumer
	) {
		var atom = lowLevelBuilder.signAndBuild(signer);
		upSubstateConsumer.accept(this::remoteSubstates);
		return atom;
	}

	public Atom signAndBuild(
		Function<HashCode, ECDSASignature> signer,
		Consumer<SubstateStore> upSubstateConsumer
	) {
		var atom = lowLevelBuilder.signAndBuild(signer);
		SubstateStore upSubstate = c -> Iterables.concat(
			lowLevelBuilder.localUpSubstate().stream()
				.filter(l -> c.isInstance(l.getParticle()))
				.map(l -> Substate.create(l.getParticle(), SubstateId.ofSubstate(atom, l.getIndex())))
				.collect(Collectors.toList()),
			remoteSubstates(c)
		);
		upSubstateConsumer.accept(upSubstate);
		return atom;
	}

	public Atom signAndBuild(Function<HashCode, ECDSASignature> signer) {
		return lowLevelBuilder.signAndBuild(signer);
	}

	public Atom buildWithoutSignature() {
		return lowLevelBuilder.buildWithoutSignature();
	}
}
