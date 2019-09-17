package com.radixdlt.middleware2.processing;

import com.google.inject.Inject;
import com.radixdlt.AtomContent;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerObservation;
import com.radixdlt.middleware.ImmutableAtom;
import com.radixdlt.middleware.RadixEngineUtils;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.middleware2.converters.AtomContentToImmutableAtomConverter;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.Map;

public class RadixEngineAtomProcessor implements AtomProcessor {
    private static final Logger log = Logging.getLogger("RadixEngineAtomProcessor");

    private boolean interrupted;

    private Ledger ledger;
    private RadixEngine radixEngine;
    private Map<Class<? extends AtomContent>, AtomContentToImmutableAtomConverter> contentConverters;

    @Inject
    public RadixEngineAtomProcessor(Ledger ledger, RadixEngine<SimpleRadixEngineAtom> radixEngine,
                                    Map<Class<? extends AtomContent>, AtomContentToImmutableAtomConverter> contentConverters) {
        this.ledger = ledger;
        this.radixEngine = radixEngine;
        this.contentConverters = contentConverters;
    }

    private void process() throws InterruptedException {
        while (!interrupted) {
            LedgerObservation ledgerObservation = ledger.observe();
            if (LedgerObservation.Type.ADOPT == ledgerObservation.getType()) {
                log.info("Middleware received ADOPT event");
                AtomContent atomContent = ledgerObservation.getAtom().getContent();
                AtomContentToImmutableAtomConverter contentConverter = contentConverters.get(atomContent.getClass());
                if (contentConverter == null) {
                    throw new RuntimeException("Unsupported atom content: " + atomContent.getClass());
                }
                ImmutableAtom immutableAtom = contentConverter.convert(atomContent);
                SimpleRadixEngineAtom simpleRadixEngineAtom;
                try {
                    simpleRadixEngineAtom = RadixEngineUtils.toCMAtom(immutableAtom);
                } catch (RadixEngineUtils.CMAtomConversionException e) {
                    log.error("Atom processing failed", e);
                    throw new RuntimeException(e);
                }
                radixEngine.store(simpleRadixEngineAtom, new AtomEventListener() {
                });
            }
        }
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
