package com.radixdlt.middleware2.processing;

import com.google.inject.Inject;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerObservation;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.middleware2.converters.SimpleRadixEngineAtomToEngineAtom;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

public class RadixEngineAtomProcessor {
    private static final Logger log = Logging.getLogger("RadixEngineAtomProcessor");

    private boolean interrupted;

    private Ledger ledger;
    private RadixEngine radixEngine;
    private SimpleRadixEngineAtomToEngineAtom atomConverter;

    @Inject
    public RadixEngineAtomProcessor(Ledger ledger, RadixEngine<SimpleRadixEngineAtom> radixEngine,
                                    SimpleRadixEngineAtomToEngineAtom atomConverter) {
        this.ledger = ledger;
        this.radixEngine = radixEngine;
        this.atomConverter = atomConverter;
    }

    private void process() throws InterruptedException {
        while (!interrupted) {
            LedgerObservation ledgerObservation = ledger.observe();
            if (ledgerObservation.getType() == LedgerObservation.Type.ADOPT) {
                log.info("Middleware received ADOPT event");
                try {
                    SimpleRadixEngineAtom simpleRadixEngineAtom = atomConverter.convert(ledgerObservation.getAtom());
                    log.debug("Store received atom to engine");
                    radixEngine.store(simpleRadixEngineAtom, new AtomEventListener() {
                    });
                } catch (Exception e) {
                    log.error("Atom processing failed", e);
                }
            }
        }
        log.info("Processing stopped");
    }

    public void start() {
        log.info("RadixEngineAtomProcessor starting");
        new Thread(() -> {
            try {
                process();
            } catch (InterruptedException e) {
                log.error("Starting of RadixEngineAtomProcessor failed");
            }
        }).start();
    }

    public void stop() {
        interrupted = true;
    }
}
