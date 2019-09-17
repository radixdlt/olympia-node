package com.radixdlt.middleware2.converters;

import com.radixdlt.middleware.ImmutableAtom;
import com.radixdlt.tempo.TempoAtomContent;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

public class TempoAtomContentToImmutableAtomConverter implements AtomContentToImmutableAtomConverter<TempoAtomContent> {
    private static final Logger log = Logging.getLogger("TempoAtomContentToImmutableAtomConverter");

    public ImmutableAtom convert(TempoAtomContent atomContent) {
        log.info("Transformation started for TempoAtomContent: " + atomContent);
        return new ImmutableAtom(atomContent.getParticleGroups(), atomContent.getSignatures(), atomContent.getMetaData());
    }

    public TempoAtomContent convert(ImmutableAtom immutableAtom) {
        log.info("Transformation started for TempoAtomContent: " + immutableAtom);
        return new TempoAtomContent(immutableAtom.getParticleGroups(), immutableAtom.getSignatures(), immutableAtom.getMetaData());
    }
}
