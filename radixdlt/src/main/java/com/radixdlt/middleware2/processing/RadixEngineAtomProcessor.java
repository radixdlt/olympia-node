package com.radixdlt.middleware2.processing;

import com.google.inject.Inject;
import com.radixdlt.common.AID;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerObservation;
import com.radixdlt.middleware.ImmutableAtom;
import com.radixdlt.middleware.RadixEngineUtils;
import com.radixdlt.middleware.SimpleRadixEngineAtom;
import com.radixdlt.middleware2.converters.SimpleRadixEngineAtomToEngineAtom;
import com.radixdlt.tempo.LegacyUtils;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.universe.Universe;
import org.radix.atoms.Atom;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.validation.ConstraintMachineValidationException;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RadixEngineAtomProcessor {
	private static final Logger log = Logging.getLogger("middleware2.atomProcessor");

	private boolean interrupted;

	private Ledger ledger;
	private RadixEngine<SimpleRadixEngineAtom> radixEngine;
	private SimpleRadixEngineAtomToEngineAtom atomConverter;

	@Inject
	public RadixEngineAtomProcessor(
			Ledger ledger,
			RadixEngine<SimpleRadixEngineAtom> radixEngine,
			SimpleRadixEngineAtomToEngineAtom atomConverter
			) {
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
					simpleRadixEngineAtom = getSimpleRadixEngineAtomWithLegacyAtom(simpleRadixEngineAtom);
					radixEngine.store(simpleRadixEngineAtom, new AtomEventListener() {
					});
				} catch (Exception e) {
					log.error("Atom processing failed", e);
				}
			}
		}
	}

	public void process(Atom atom) {
		//We have 2 conversion operation. Should be fixed when we will define clear Atom model
		try {
			TempoAtom tempoAtom = LegacyUtils.fromLegacyAtom(atom);
			SimpleRadixEngineAtom simpleRadixEngineAtom = atomConverter.convert(tempoAtom);
			simpleRadixEngineAtom = getSimpleRadixEngineAtomWithLegacyAtom(simpleRadixEngineAtom);
			radixEngine.store(simpleRadixEngineAtom, new AtomEventListener() {
			});
		} catch (Exception e) {
			log.error("Engine processing exception ", e);
		}
	}

	public void start() {
		initGenesis();
		new Thread(() -> {
			try {
				process();
			} catch (InterruptedException e) {
				log.error("Starting of RadixEngineAtomProcessor failed", e);
			}
		}).start();
	}

	public void stop() {
		interrupted = true;
	}

	private static SimpleRadixEngineAtom getSimpleRadixEngineAtomWithLegacyAtom(SimpleRadixEngineAtom cmAtom) {
		ImmutableAtom immutableAtom = cmAtom.getAtom();
		Atom atom = new Atom(immutableAtom.getParticleGroups(), immutableAtom.getSignatures(), immutableAtom.getMetaData());
		return new SimpleRadixEngineAtom(atom, cmAtom.getCMInstruction());
	}

	private void initGenesis() {
		try {
			LinkedList<AID> atomIds = new LinkedList<>();
			for (ImmutableAtom immutableAtom : Modules.get(Universe.class).getGenesis()) {
				if (!ledger.contains(immutableAtom.getAID())) {
					final SimpleRadixEngineAtom cmAtom;
					try {
						cmAtom = RadixEngineUtils.toCMAtom(immutableAtom);
					} catch (RadixEngineUtils.CMAtomConversionException e) {
						throw new ConstraintMachineValidationException(immutableAtom, e.getMessage(), e.getDataPointer());
					}

					radixEngine.store(cmAtom,
							new AtomEventListener<SimpleRadixEngineAtom>() {
								@Override
								public void onCMSuccess(SimpleRadixEngineAtom cmAtom) {
									log.debug("Genesis Atom " + cmAtom.getAtom().getAID() + " stored to atom store");
								}

								@Override
								public void onCMError(SimpleRadixEngineAtom cmAtom, CMError error) {
									log.fatal("Failed to process genesis Atom: " + error.getErrorCode() + " "
											+ error.getErrMsg() + " " + error.getDataPointer() + "\n"
											+ cmAtom.getAtom() + "\n"
											+ error.getCmValidationState().toString());
									System.exit(-1);
								}

								@Override
								public void onVirtualStateConflict(SimpleRadixEngineAtom cmAtom, DataPointer dp) {
									log.fatal("Failed to process genesis Atom: Virtual State Conflict");
									System.exit(-1);
								}

								@Override
								public void onStateConflict(SimpleRadixEngineAtom cmAtom, DataPointer dp, SimpleRadixEngineAtom conflictAtom) {
									log.fatal("Failed to process genesis Atom: State Conflict");
									System.exit(-1);
								}

								@Override
								public void onStateMissingDependency(SimpleRadixEngineAtom cmAtom, DataPointer dp) {
									log.fatal("Failed to process genesis Atom: Missing Dependency");
									System.exit(-1);
								}
							});
				}
			}
			waitForAtoms(atomIds);
		} catch (Exception ex) {
			log.fatal("Failed to process genesis Atom", ex);
			System.exit(-1);
		}
	}

	private void waitForAtoms(List<AID> atomHashes) throws InterruptedException {
		for (AID atomID : atomHashes) {
			while (!ledger.contains(atomID)) {
				TimeUnit.MILLISECONDS.sleep(100);
			}
		}
	}
}
