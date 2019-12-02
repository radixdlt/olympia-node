package com.radixdlt.middleware2.processing;

import com.google.inject.Inject;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerObservation;
import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import org.json.JSONObject;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.shards.ShardSpace;
import org.radix.universe.system.LocalSystem;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class RadixEngineAtomProcessor {
	private static final Logger log = Logging.getLogger("middleware2.atomProcessor");

	private boolean interrupted;

	private final Ledger ledger;
	private final RadixEngine radixEngine;
	private final Serialization serialization;
	private final AtomToBinaryConverter atomToBinaryConverter;

	@Inject
	public RadixEngineAtomProcessor(
			Ledger ledger,
			RadixEngine radixEngine,
			Serialization serialization,
			AtomToBinaryConverter atomToBinaryConverter
			) {
		this.ledger = ledger;
		this.radixEngine = radixEngine;
		this.serialization = serialization;
		this.atomToBinaryConverter = atomToBinaryConverter;
	}

	private void process() throws InterruptedException {
		while (!interrupted) {
			LedgerObservation ledgerObservation = ledger.observe();
			if (ledgerObservation.getType() == LedgerObservation.Type.ADOPT) {
				try {
					Atom atom = atomToBinaryConverter.toAtom(ledgerObservation.getEntry().getContent());
					radixEngine.store(atom, new AtomEventListener() {
					});
				} catch (Exception e) {
					log.error("Storing atom failed", e);
				}
			} else if (ledgerObservation.getType() == LedgerObservation.Type.COMMIT) {
				try {
					Atom atom = atomToBinaryConverter.toAtom(ledgerObservation.getEntry().getContent());
					log.info("Committing to '" + ledgerObservation.getEntry().getAID());
					// TODO actual commit mechanism stub
				} catch (Exception e) {
					log.error("Committing atom failed", e);
				}
			}
		}
	}

	public AID process(JSONObject jsonAtom, Optional<ProcessorAtomEventListener> processorAtomEventListener) {
		final Atom atom = serialization.fromJsonObject(jsonAtom, Atom.class);
		processorAtomEventListener.ifPresent(listener -> listener.onDeserializationCompleted(atom.getAID()));
		ShardSpace shardsSupported = LocalSystem.getInstance().getShards();
		if (!shardsSupported.intersects(atom.getShards())) {
			throw new RuntimeException(String.format("Not a suitable submission peer: " +
				"atomShards(%s) shardsSupported(%s)", atom.getShards(), shardsSupported));
		}
		try {
			radixEngine.store(atom, new AtomEventListener() {
			});
		} catch (Exception e) {
			processorAtomEventListener.ifPresent(listener -> listener.onError(e));
			log.error("Engine processing exception ", e);
		}
		return atom.getAID();
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

	private void initGenesis() {
		try {
			LinkedList<AID> atomIds = new LinkedList<>();
			for (Atom atom : Modules.get(Universe.class).getGenesis()) {
				if (!ledger.contains(atom.getAID())) {
					radixEngine.store(atom,
							new AtomEventListener() {
								@Override
								public void onCMSuccess(Atom atom) {
									log.debug("Genesis Atom " + atom.getAID() + " stored to atom store");
								}

								@Override
								public void onCMError(Atom atom, CMError error) {
									log.fatal("Failed to process genesis Atom: " + error.getErrorCode() + " "
											+ error.getErrMsg() + " " + error.getDataPointer() + "\n"
											+ atom + "\n"
											+ error.getCmValidationState().toString());
									System.exit(-1);
								}

								@Override
								public void onVirtualStateConflict(Atom atom, DataPointer dp) {
									log.fatal("Failed to process genesis Atom: Virtual State Conflict");
									System.exit(-1);
								}

								@Override
								public void onStateConflict(Atom atom, DataPointer dp, Atom conflictAtom) {
									log.fatal("Failed to process genesis Atom: State Conflict");
									System.exit(-1);
								}

								@Override
								public void onStateMissingDependency(AID atomId, Particle particle) {
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

	public interface ProcessorAtomEventListener {
		default void onDeserializationCompleted(AID atomId) {
		}

		default void onError(Exception e) {
		}
	}
}
