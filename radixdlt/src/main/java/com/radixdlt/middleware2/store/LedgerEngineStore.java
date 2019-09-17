package com.radixdlt.middleware2.store;

import com.google.inject.Inject;
import com.radixdlt.Atom;
import com.radixdlt.AtomContent;
import com.radixdlt.common.EUID;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.middleware2.converters.AtomContentToImmutableAtomConverter;
import com.radixdlt.store.EngineStore;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoAtomContent;
import org.radix.atoms.AtomStore;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class LedgerEngineStore implements EngineStore<SimpleRadixEngineAtom> {

    private Ledger ledger;
    private Map<Class<? extends AtomContent>, AtomContentToImmutableAtomConverter> contentConverters;

    @Inject
    public LedgerEngineStore(Ledger ledger, Map<Class<? extends AtomContent>, AtomContentToImmutableAtomConverter> contentConverters) {
        this.ledger = ledger;
        this.contentConverters = contentConverters;
    }

    @Override
    public void getAtomContaining(Particle particle, boolean isInput, Consumer callback) {
        Optional<Atom> atom = getAtomByParticle(particle, isInput);
        callback.accept(atom);
    }

    private Optional<Atom> getAtomByParticle(Particle particle, boolean isInput) {
        final byte[] indexableBytes = AtomStore.IDType.toByteArray(isInput ? AtomStore.IDType.PARTICLE_UP : AtomStore.IDType.PARTICLE_DOWN, particle.getHID());
        LedgerCursor cursor = ledger.search(LedgerIndex.LedgerIndexType.DUPLICATE, new LedgerIndex(indexableBytes), LedgerSearchMode.EXACT);
        return ledger.get(cursor.get());
    }

    @Override
    public void storeAtom(SimpleRadixEngineAtom atom) {
        AtomContentToImmutableAtomConverter<TempoAtomContent> atomConverter = contentConverters.get(TempoAtomContent.class);
        EngineAtomIndexer preparedAtom = new EngineAtomIndexer(atom);
        TempoAtom tempoAtom = new TempoAtom(atomConverter.convert(atom.getAtom()), atom.getAtom().getAID(), atom.getAtom().getShards());
        ledger.store(tempoAtom, preparedAtom.getUniqueIndices(), preparedAtom.getDuplicateIndices());
    }

    @Override
    public void deleteAtom(SimpleRadixEngineAtom atom) {
        throw new UnsupportedOperationException("delete operation is not supported by Tempo Ledger");
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
