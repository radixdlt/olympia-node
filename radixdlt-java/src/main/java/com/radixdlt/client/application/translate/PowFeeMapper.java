package com.radixdlt.client.application.translate;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.radixdlt.client.core.atoms.particles.SpunParticle;
import org.radix.utils.Int128;
import org.radix.utils.UInt256;

import com.radixdlt.client.atommodel.tokens.FeeParticle;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.pow.ProofOfWork;
import com.radixdlt.client.core.pow.ProofOfWorkBuilder;

/**
 * Maps a complete list of particles ready to be submitted to a POW fee particle.
 */
public class PowFeeMapper implements FeeMapper {
	private static final int LEADING = 16;

	private final Function<List<ParticleGroup>, RadixHash> hasher;
	private final ProofOfWorkBuilder powBuilder;

	public PowFeeMapper(Function<List<ParticleGroup>, RadixHash> hasher, ProofOfWorkBuilder powBuilder) {
		this.hasher = Objects.requireNonNull(hasher, "hasher is required");
		this.powBuilder = Objects.requireNonNull(powBuilder, "powBuilder is required");
	}

	@Override
	public List<ParticleGroup> map(List<ParticleGroup> particleGroups, RadixUniverse universe, ECPublicKey key) {
		Objects.requireNonNull(key);
		Objects.requireNonNull(universe);
		Objects.requireNonNull(particleGroups);

		final byte[] seed = this.hasher.apply(particleGroups).toByteArray();
		ProofOfWork pow = this.powBuilder.build(universe.getMagic(), seed, LEADING);

		Particle fee = new FeeParticle(
				fromNonce(pow.getNonce()),
				universe.getAddressFrom(key),
				System.nanoTime(),
				universe.getPOWToken(),
				System.currentTimeMillis() / 60000L + 60000L
		);

		return Collections.singletonList(ParticleGroup.of(SpunParticle.up(fee)));
	}

	private static UInt256 fromNonce(long nonce) {
		return UInt256.from(Int128.from(0L, nonce));
	}
}
