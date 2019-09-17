package com.radixdlt.middleware2.store;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationUtils;
import com.radixdlt.store.SpinStateMachine;
import org.radix.atoms.AtomStore;
import org.radix.modules.Modules;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EngineAtomIndexer {

    Set<LedgerIndex> uniqueIndices = new HashSet<>();
    Set<LedgerIndex> duplicateIndices = new HashSet<>();

    public EngineAtomIndexer(SimpleRadixEngineAtom radixEngineAtom) {
        uniqueIndices.add(new LedgerIndex(AtomStore.IDType.toByteArray(AtomStore.IDType.ATOM, radixEngineAtom.getAtom().getAID())));

        Map<Particle, Spin> curSpins = radixEngineAtom.getCMInstruction().getMicroInstructions().stream()
                .filter(CMMicroInstruction::isCheckSpin)
                .collect(Collectors.toMap(
                        CMMicroInstruction::getParticle,
                        CMMicroInstruction::getCheckSpin
                ));

        radixEngineAtom.getCMInstruction().getMicroInstructions().stream()
                .filter(i -> i.getMicroOp() == CMMicroInstruction.CMMicroOp.PUSH)
                .forEach(i -> {
                    Spin curSpin = curSpins.get(i.getParticle());
                    Spin nextSpin = SpinStateMachine.next(curSpin);
                    curSpins.put(i.getParticle(), nextSpin);

                    final AtomStore.IDType idType;
                    switch (nextSpin) {
                        case UP:
                            idType = AtomStore.IDType.PARTICLE_UP;
                            break;
                        case DOWN:
                            idType = AtomStore.IDType.PARTICLE_DOWN;
                            break;
                        default:
                            throw new IllegalStateException("Unknown SPIN state for particle " + nextSpin);
                    }

                    final byte[] indexableBytes = AtomStore.IDType.toByteArray(idType, i.getParticle().getHID());
                    uniqueIndices.add(new LedgerIndex(indexableBytes));
                });


        final ImmutableSet<EUID> destinations = radixEngineAtom.getCMInstruction().getMicroInstructions().stream()
                .filter(CMMicroInstruction::isCheckSpin)
                .map(CMMicroInstruction::getParticle)
                .map(Particle::getDestinations)
                .flatMap(Set::stream)
                .collect(ImmutableSet.toImmutableSet());

        for (EUID euid : destinations) {
            duplicateIndices.add(new LedgerIndex(AtomStore.IDType.toByteArray(AtomStore.IDType.DESTINATION, euid)));
            duplicateIndices.add(new LedgerIndex(AtomStore.IDType.toByteArray(AtomStore.IDType.SHARD, euid.getShard())));
        }

        radixEngineAtom.getCMInstruction().getMicroInstructions().stream().filter(CMMicroInstruction::isCheckSpin)
                .forEach(checkSpin -> {
                    // TODO: Remove
                    // This does not handle nested particle classes.
                    // If that ever becomes a problem, this is the place to fix it.
                    final Serialization serialization = Modules.get(Serialization.class);
                    final String idForClass = serialization.getIdForClass(checkSpin.getParticle().getClass());
                    final EUID numericClassId = SerializationUtils.stringToNumericID(idForClass);
                    duplicateIndices.add(new LedgerIndex(AtomStore.IDType.toByteArray(AtomStore.IDType.PARTICLE_CLASS, numericClassId)));
                });
    }

    public Set<LedgerIndex> getUniqueIndices() {
        return uniqueIndices;
    }

    public Set<LedgerIndex> getDuplicateIndices() {
        return duplicateIndices;
    }
}
