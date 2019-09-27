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
import org.radix.modules.Modules;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EngineAtomIndices {
    private enum IndexType {
        PARTICLE_UP((byte)3), PARTICLE_DOWN((byte)4), PARTICLE_CLASS((byte)5), UID((byte)6), DESTINATION((byte)7);
        byte value;

        IndexType(byte value) {
            this.value = value;
        }
    }
    private final Set<LedgerIndex> uniqueIndices;
    private final Set<LedgerIndex> duplicateIndices;

    public EngineAtomIndices(Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
        this.uniqueIndices = uniqueIndices;
        this.duplicateIndices = duplicateIndices;
    }

    public static EngineAtomIndices from(SimpleRadixEngineAtom radixEngineAtom) {
        ImmutableSet.Builder<LedgerIndex> uniqueIndices = ImmutableSet.builder();
        ImmutableSet.Builder<LedgerIndex> duplicateIndices = ImmutableSet.builder();

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

                    final IndexType indexType;
                    switch (nextSpin) {
                        case UP:
                            indexType = IndexType.PARTICLE_UP;
                            break;
                        case DOWN:
                            indexType = IndexType.PARTICLE_DOWN;
                            break;
                        default:
                            throw new IllegalStateException("Unknown SPIN state for particle " + nextSpin);
                    }

                    final byte[] indexableBytes = toByteArray(indexType, i.getParticle().getHID());
                    uniqueIndices.add(new LedgerIndex(indexableBytes));
                });


        final ImmutableSet<EUID> destinations = radixEngineAtom.getCMInstruction().getMicroInstructions().stream()
                .filter(CMMicroInstruction::isCheckSpin)
                .map(CMMicroInstruction::getParticle)
                .map(Particle::getDestinations)
                .flatMap(Set::stream)
                .collect(ImmutableSet.toImmutableSet());

        for (EUID euid : destinations) {
            duplicateIndices.add(new LedgerIndex(toByteArray(IndexType.DESTINATION, euid)));
        }

        radixEngineAtom.getCMInstruction().getMicroInstructions().stream().filter(CMMicroInstruction::isCheckSpin)
                .forEach(checkSpin -> {
                    // TODO: Remove
                    // This does not handle nested particle classes.
                    // If that ever becomes a problem, this is the place to fix it.
                    final Serialization serialization = Modules.get(Serialization.class);
                    final String idForClass = serialization.getIdForClass(checkSpin.getParticle().getClass());
                    final EUID numericClassId = SerializationUtils.stringToNumericID(idForClass);
                    duplicateIndices.add(new LedgerIndex((byte)4, toByteArray(IndexType.PARTICLE_CLASS, numericClassId)));
                });
        return new EngineAtomIndices(uniqueIndices.build(), duplicateIndices.build());
    }

    public Set<LedgerIndex> getUniqueIndices() {
        return uniqueIndices;
    }

    public Set<LedgerIndex> getDuplicateIndices() {
        return duplicateIndices;
    }

    private static byte[] toByteArray(IndexType type, EUID id) {
        if (id == null) {
            throw new IllegalArgumentException("EUID is null");
        }

        byte[] idBytes = id.toByteArray();
        byte[] typeBytes = new byte[idBytes.length + 1];
        typeBytes[0] = type.value;
        System.arraycopy(idBytes, 0, typeBytes, 1, idBytes.length);
        return typeBytes;
    }

}
