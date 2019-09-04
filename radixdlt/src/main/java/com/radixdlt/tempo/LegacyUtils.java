package com.radixdlt.tempo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.AtomContent;
import com.radixdlt.middleware.ImmutableAtom;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import org.radix.atoms.Atom;

import java.util.stream.Collectors;

public final class LegacyUtils {
	private LegacyUtils() {
		throw new IllegalStateException("Can't construct");
	}

	@SerializerId2("tempo.legacy.atom.content")
	private static class LegacyAtomContentWrapper extends AtomContent {
		@JsonProperty("content")
		@DsonOutput(DsonOutput.Output.ALL)
		private ImmutableAtom content;

		private LegacyAtomContentWrapper() {
		}

		private LegacyAtomContentWrapper(ImmutableAtom content) {
			this.content = content;
		}

		private ImmutableAtom getContent() {
			return content;
		}
	}

	public static TempoAtom fromLegacyAtom(Atom legacyAtom) {
		return new TempoAtom(
			new LegacyAtomContentWrapper(legacyAtom),
			legacyAtom.getAID(),
			legacyAtom.getShards(),
			legacyAtom.getTemporalProof()
		);
	}

	public static Atom toLegacyAtom(TempoAtom atom) {
		ImmutableAtom content = ((LegacyAtomContentWrapper) atom.getContent()).getContent();
		Atom legacyAtom = new Atom(
			content.particleGroups().collect(Collectors.toList()),
			content.getSignatures(),
			content.getMetaData()
		);
		legacyAtom.setTemporalProof(atom.getTemporalProof());
		return legacyAtom;
	}
}
