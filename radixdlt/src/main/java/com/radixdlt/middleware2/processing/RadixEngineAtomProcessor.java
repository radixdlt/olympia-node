package com.radixdlt.middleware2.processing;

import com.google.inject.Inject;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerObservation;
import com.radixdlt.middleware.ImmutableAtom;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.middleware2.converters.SimpleRadixEngineAtomToEngineAtom;
import org.radix.atoms.Atom;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

public class RadixEngineAtomProcessor {
	private static final Logger log = Logging.getLogger(RadixEngineAtomProcessor.class.getSimpleName());

	private boolean interrupted;

	private Ledger ledger;
	private RadixEngine<SimpleRadixEngineAtom> radixEngine;
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
				try {
					SimpleRadixEngineAtom simpleRadixEngineAtom = atomConverter.convert(ledgerObservation.getAtom());
					simpleRadixEngineAtom  = getSimpleRadixEngineAtomWithLegacyAtom(simpleRadixEngineAtom);
					radixEngine.store(simpleRadixEngineAtom, new AtomEventListener() {
					});
				} catch (Exception e) {
					log.error("Atom processing failed", e);
				}
			}
		}
	}

	public void start() {
		new Thread(() -> {
			try {
				process();
			} catch (InterruptedException e) {
				log.error("Starting of RadixEngineAtomProcessor failed", e);
			}
		}).start();
	}

	private static SimpleRadixEngineAtom getSimpleRadixEngineAtomWithLegacyAtom(SimpleRadixEngineAtom cmAtom) {
		ImmutableAtom immutableAtom = cmAtom.getAtom();
		Atom atom = new Atom(immutableAtom.getParticleGroups(), immutableAtom.getSignatures(), immutableAtom.getMetaData());
		return new SimpleRadixEngineAtom(atom, cmAtom.getCMInstruction());
	}

	public void stop() {
		interrupted = true;
	}
}
