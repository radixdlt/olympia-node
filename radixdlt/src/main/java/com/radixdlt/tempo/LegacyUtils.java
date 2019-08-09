package com.radixdlt.tempo;

import com.radixdlt.atoms.ImmutableAtom;
import org.radix.atoms.Atom;

import java.util.stream.Collectors;

public final class LegacyUtils {
	private LegacyUtils() {
		throw new IllegalStateException("Can't construct");
	}

	public static TempoAtom fromLegacyAtom(Atom legacyAtom) {
		return new TempoAtom(
			(ImmutableAtom) legacyAtom,
			legacyAtom.getAID(),
			legacyAtom.getTimestamp(),
			legacyAtom.getShards(),
			legacyAtom.getTemporalProof()
		);
	}

	public static Atom toLegacyAtom(TempoAtom atom) {
		ImmutableAtom content = (ImmutableAtom) atom.getContent();
		Atom legacyAtom = new Atom(
			content.particleGroups().collect(Collectors.toList()),
			content.getSignatures(),
			content.getMetaData()
		);
		legacyAtom.setTemporalProof(atom.getTemporalProof());
		return legacyAtom;
	}
}
