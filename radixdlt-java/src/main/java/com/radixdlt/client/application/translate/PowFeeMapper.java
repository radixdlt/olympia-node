package com.radixdlt.client.application.translate;

import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.AtomFeeConsumable;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.pow.ProofOfWork;
import com.radixdlt.client.core.pow.ProofOfWorkBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class PowFeeMapper implements FeeMapper {
	private static final int LEADING = 16;

	private final Function<List<Particle>, RadixHash> hasher;
	private final ProofOfWorkBuilder powBuilder;

	public PowFeeMapper(Function<List<Particle>, RadixHash> hasher, ProofOfWorkBuilder powBuilder) {
		this.hasher = hasher;
		this.powBuilder = powBuilder;
	}

	public List<Particle> map(List<Particle> particles, RadixUniverse universe, ECPublicKey key) {
		Objects.requireNonNull(key);
		Objects.requireNonNull(universe);
		Objects.requireNonNull(particles);

		final byte[] seed = hasher.apply(particles).toByteArray();
		ProofOfWork pow = powBuilder.build(universe.getMagic(), seed, LEADING);

		Particle fee = new AtomFeeConsumable(
			pow.getNonce(),
			new AccountReference(key),
			System.nanoTime(),
			universe.getPOWToken(),
			System.currentTimeMillis() * 60000
		);

		return Collections.singletonList(fee);
	}
}
