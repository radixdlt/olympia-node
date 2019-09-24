package com.radixdlt.middleware2.store;

import com.google.inject.Inject;
import com.radixdlt.Atom;
import com.radixdlt.common.EUID;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.middleware2.converters.SimpleRadixEngineAtomToEngineAtom;
import com.radixdlt.store.EngineStore;
import org.radix.atoms.AtomStore;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class LedgerEngineStore implements EngineStore<SimpleRadixEngineAtom> {

    private Ledger ledger;
    private SimpleRadixEngineAtomToEngineAtom atomConverter;

    @Inject
    public LedgerEngineStore(Ledger ledger, SimpleRadixEngineAtomToEngineAtom atomConverter) {
        this.ledger = ledger;
        this.atomConverter = atomConverter;
    }

    @Override
    public void getAtomContaining(Particle particle, boolean isInput, Consumer<SimpleRadixEngineAtom> callback) {
        Optional<Atom> atomOptional = getAtomByParticle(particle, isInput);
        if (atomOptional.isPresent()) {
            SimpleRadixEngineAtom simpleRadixEngineAtom = atomConverter.convert(atomOptional.get());
            callback.accept(simpleRadixEngineAtom);
        }
    }

    private Optional<Atom> getAtomByParticle(Particle particle, boolean isInput) {
        final byte[] indexableBytes = AtomStore.IDType.toByteArray(isInput ? AtomStore.IDType.PARTICLE_UP : AtomStore.IDType.PARTICLE_DOWN, particle.getHID());
        LedgerCursor cursor = ledger.search(LedgerIndex.LedgerIndexType.DUPLICATE, new LedgerIndex(indexableBytes), LedgerSearchMode.EXACT);
        return ledger.get(cursor.get());
    }

    @Override
    public void storeAtom(SimpleRadixEngineAtom simpleRadixEngineAtom) {
        EngineAtomIndexer engineAtomIndexer = new EngineAtomIndexer(simpleRadixEngineAtom);
        Atom atom = atomConverter.convert(simpleRadixEngineAtom);
        ledger.store(atom, engineAtomIndexer.getUniqueIndices(), engineAtomIndexer.getDuplicateIndices());
    }

    @Override
    public void deleteAtom(SimpleRadixEngineAtom atom) {
        throw new UnsupportedOperationException("Delete operation is not supported by Ledger interface");
    }

    @Override
    public boolean supports(Set<EUID> destinations) {
        return destinations.stream()
                .map(euid -> new LedgerIndex(AtomStore.IDType.toByteArray(AtomStore.IDType.DESTINATION, euid)))
                .filter(ledgerIndex -> ledger.contains(LedgerIndex.LedgerIndexType.DUPLICATE, ledgerIndex, LedgerSearchMode.EXACT))
                .findFirst()
                .isPresent();
    }

    @Override
    public Spin getSpin(Particle particle) {
        if (getAtomByParticle(particle, true).isPresent()) {
            return Spin.UP;
        } else if (getAtomByParticle(particle, false).isPresent()) {
            return Spin.DOWN;
        }
        return Spin.NEUTRAL;
    }
}
