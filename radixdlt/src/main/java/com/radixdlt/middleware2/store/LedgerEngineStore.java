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
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.shards.ShardSpace;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class LedgerEngineStore implements EngineStore<SimpleRadixEngineAtom> {
    private static final Logger log = Logging.getLogger("middleware2.store");

    private Ledger ledger;
    private SimpleRadixEngineAtomToEngineAtom atomConverter;
    private Supplier<ShardSpace> shardSpaceSupplier;

    @Inject
    public LedgerEngineStore(Ledger ledger, SimpleRadixEngineAtomToEngineAtom atomConverter, Supplier<ShardSpace> shardSpaceSupplier) {
        this.ledger = ledger;
        this.atomConverter = atomConverter;
        this.shardSpaceSupplier = shardSpaceSupplier;
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
        final byte[] indexableBytes = EngineAtomIndices.toByteArray(isInput ? EngineAtomIndices.IndexType.PARTICLE_DOWN : EngineAtomIndices.IndexType.PARTICLE_UP, particle.getHID());
        LedgerCursor cursor = ledger.search(LedgerIndex.LedgerIndexType.UNIQUE, new LedgerIndex(indexableBytes), LedgerSearchMode.EXACT);
        if (cursor != null) {
            return ledger.get(cursor.get());
        } else {
            log.debug("getAtomByParticle returned empty result");
            return Optional.empty();
        }
    }

    @Override
    public void storeAtom(SimpleRadixEngineAtom simpleRadixEngineAtom) {
        EngineAtomIndices engineAtomIndices = EngineAtomIndices.from(simpleRadixEngineAtom);
        Atom atom = atomConverter.convert(simpleRadixEngineAtom);
        ledger.store(atom, engineAtomIndices.getUniqueIndices(), engineAtomIndices.getDuplicateIndices());
    }

    @Override
    public void deleteAtom(SimpleRadixEngineAtom atom) {
        throw new UnsupportedOperationException("Delete operation is not supported by Ledger interface");
    }

    @Override
    public boolean supports(Set<EUID> destinations) {
        return shardSpaceSupplier.get().intersects(destinations.stream().map(EUID::getShard).collect(Collectors.toSet()));
    }

    @Override
    public Spin getSpin(Particle particle) {
        if (getAtomByParticle(particle, true).isPresent()) {
            return Spin.DOWN;
        } else if (getAtomByParticle(particle, false).isPresent()) {
            return Spin.UP;
        }
        return Spin.NEUTRAL;
    }
}
