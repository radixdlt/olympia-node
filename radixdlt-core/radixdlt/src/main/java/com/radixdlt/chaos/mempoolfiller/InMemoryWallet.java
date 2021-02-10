package com.radixdlt.chaos.mempoolfiller;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.Atom;
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

import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class InMemoryWallet {
	private final RRI tokenRRI;
	private final RadixAddress address;
	private final ImmutableList<TransferrableTokensParticle> particles;
	private final TokDefParticleFactory factory;
	private final UInt256 fee = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 - 3).multiply(UInt256.from(40));

	private InMemoryWallet(RRI tokenRRI, RadixAddress address, ImmutableList<TransferrableTokensParticle> particles) {
		this.tokenRRI = tokenRRI;
		this.address = address;
		this.particles = particles;
		this.factory = TokDefParticleFactory.create(
			tokenRRI,
			ImmutableMap.of(
				MutableSupplyTokenDefinitionParticle.TokenTransition.BURN, TokenPermission.TOKEN_OWNER_ONLY,
				MutableSupplyTokenDefinitionParticle.TokenTransition.MINT, TokenPermission.TOKEN_OWNER_ONLY
			),
			UInt256.ONE
		);
	}

	public static InMemoryWallet create(RRI tokenRRI, RadixAddress address) {
		Objects.requireNonNull(tokenRRI);
		Objects.requireNonNull(address);

		return new InMemoryWallet(tokenRRI, address, ImmutableList.of());
	}

	private Optional<UInt256> downParticles(
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

	public Optional<Atom> createTransaction(RadixAddress to, UInt256 amount) {
		var mutableList = new LinkedList<>(particles);

	    Atom atom = new Atom();
		ParticleGroup.ParticleGroupBuilder feeBuilder = ParticleGroup.builder();
		feeBuilder.addParticle(factory.createUnallocated(fee), Spin.UP);
		Optional<UInt256> remainder = downParticles(fee, mutableList, p -> feeBuilder.addParticle(p, Spin.DOWN));
		if (remainder.isEmpty()) {
			return Optional.empty();
		}
		remainder.filter(r -> !r.isZero()).ifPresent(r -> {
			TransferrableTokensParticle particle = factory.createTransferrable(address, r);
			mutableList.add(particle);
			feeBuilder.addParticle(particle, Spin.UP);
		});
		atom.addParticleGroup(feeBuilder.build());

		ParticleGroup.ParticleGroupBuilder builder = ParticleGroup.builder();
		builder.addParticle(factory.createTransferrable(to, amount), Spin.UP);
		Optional<UInt256> remainder2 = downParticles(amount, mutableList, p -> builder.addParticle(p, Spin.DOWN));
		if (remainder2.isEmpty()) {
			return Optional.empty();
		}
		remainder2.filter(r -> !r.isZero()).ifPresent(r -> builder.addParticle(factory.createTransferrable(address, r), Spin.UP));
		atom.addParticleGroup(builder.build());

		return Optional.of(atom);
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
			particles.stream()
				.filter(particle -> !p.equals(particle))
				.collect(ImmutableList.toImmutableList())
		);
	}
}
