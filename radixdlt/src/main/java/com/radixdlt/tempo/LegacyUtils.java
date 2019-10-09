package com.radixdlt.tempo;

import com.radixdlt.middleware.ImmutableAtom;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.middleware2.atom.EngineAtomContent;
import org.radix.atoms.Atom;

import java.util.stream.Collectors;

public final class LegacyUtils {
	private LegacyUtils() {
		throw new IllegalStateException("Can't construct");
	}

	//We are using EngineAtomContent what is part of middleware2 package. It is not good, but lets cleanup it when AtomContent will be defined
	public static TempoAtom fromLegacyAtom(Atom legacyAtom) {
		return new TempoAtom(
				new EngineAtomContent(legacyAtom.getParticleGroups(), legacyAtom.getSignatures(), legacyAtom.getMetaData()),
			legacyAtom.getAID(),
			legacyAtom.getShards()
		);
	}

	public static Atom toLegacyAtom(TempoAtom atom) {
		EngineAtomContent engineAtomContent = (EngineAtomContent)atom.getContent();
		ImmutableAtom content = new ImmutableAtom(engineAtomContent.getParticleGroups(), engineAtomContent.getSignatures(), engineAtomContent.getMetaData());
		Atom legacyAtom = new Atom(
			content.particleGroups().collect(Collectors.toList()),
			content.getSignatures(),
			content.getMetaData()
		);
		return legacyAtom;
	}

	public static Atom toLegacyAtom(SimpleRadixEngineAtom atom) {
		ImmutableAtom content = atom.getAtom();
		Atom legacyAtom = new Atom(
				content.particleGroups().collect(Collectors.toList()),
				content.getSignatures(),
				content.getMetaData()
		);
		return legacyAtom;
	}
}
