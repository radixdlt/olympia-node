package com.radixdlt.middleware2.converters;

import com.radixdlt.Atom;
import com.radixdlt.middleware.ImmutableAtom;
import com.radixdlt.middleware.RadixEngineUtils;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.middleware2.atom.EngineAtom;
import com.radixdlt.middleware2.atom.EngineAtomContent;
import com.radixdlt.tempo.TempoAtom;
import org.radix.atoms.events.AtomExceptionEvent;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.validation.ConstraintMachineValidationException;

public class SimpleRadixEngineAtomToEngineAtom {

    private static final Logger log = Logging.getLogger("SimpleRadixEngineAtomToTempoAtom");

    public EngineAtom convert(SimpleRadixEngineAtom simpleRadixEngineAtom) {
        ImmutableAtom immutableAtom = simpleRadixEngineAtom.getAtom();
        EngineAtomContent engineAtomContent = new EngineAtomContent(immutableAtom.getParticleGroups(), immutableAtom.getSignatures(), immutableAtom.getMetaData());
        EngineAtom engineAtom = new EngineAtom(engineAtomContent, immutableAtom.getAID(), immutableAtom.getShards());
        return engineAtom;
    }

    public SimpleRadixEngineAtom convert(Atom atom) {
        ImmutableAtom immutableAtom;
        //I do not like this approach, but at the moment we decided that it is ok
        if (atom instanceof TempoAtom || atom instanceof EngineAtom) {
            EngineAtomContent atomContent = (EngineAtomContent) atom.getContent();
            immutableAtom = new ImmutableAtom(atomContent.getParticleGroups(), atomContent.getSignatures(), atomContent.getMetaData());
        } else {
            throw new UnsupportedOperationException("Atom type is not supported: " + atom.getClass());
        }

        SimpleRadixEngineAtom simpleRadixEngineAtom;
        try {
            simpleRadixEngineAtom = RadixEngineUtils.toCMAtom(immutableAtom);
        } catch (RadixEngineUtils.CMAtomConversionException e) {
            org.radix.atoms.Atom legacyAtom = new org.radix.atoms.Atom(immutableAtom.getParticleGroups(), immutableAtom.getSignatures(), immutableAtom.getMetaData());
            ConstraintMachineValidationException validationException = new ConstraintMachineValidationException(immutableAtom,e.getMessage(), e.getDataPointer() );
            AtomExceptionEvent atomExceptionEvent = new AtomExceptionEvent(validationException, legacyAtom);
            Events.getInstance().broadcast(atomExceptionEvent);
            log.error("Atom to SimpleRadixEngineAtom conversion failed", e);
            throw new RuntimeException(e);
        }
        return simpleRadixEngineAtom;
    }
}
