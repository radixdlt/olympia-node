package com.radixdlt.client.application.translate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.client.core.pow.ProofOfWork;
import com.radixdlt.client.core.pow.ProofOfWorkBuilder;
import com.radixdlt.utils.Pair;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Maps a complete list of particles ready to be submitted to a POW fee particle.
 */
public class PowFeeMapper implements FeeMapper {
	private static final int LEADING = 16;

	private final Function<Atom, Hash> hasher;
	private final ProofOfWorkBuilder powBuilder;

	public PowFeeMapper(Function<Atom, Hash> hasher, ProofOfWorkBuilder powBuilder) {
		this.hasher = Objects.requireNonNull(hasher, "hasher is required");
		this.powBuilder = Objects.requireNonNull(powBuilder, "powBuilder is required");
	}

	@Override
	public Pair<Map<String, String>, List<ParticleGroup>> map(Atom atom, RadixUniverse universe, ECPublicKey key) {
		Objects.requireNonNull(key);
		Objects.requireNonNull(universe);
		Objects.requireNonNull(atom);

		final byte[] seed = this.hasher.apply(atom).toByteArray();
		ProofOfWork pow = this.powBuilder.build(universe.getMagic(), seed, LEADING);

		return Pair.of(ImmutableMap.of(Atom.METADATA_POW_NONCE_KEY, String.valueOf(pow.getNonce())), ImmutableList.of());
	}
}
