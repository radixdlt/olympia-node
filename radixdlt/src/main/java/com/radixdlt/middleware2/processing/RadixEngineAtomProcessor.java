package com.radixdlt.middleware2.processing;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.consensus.tempo.Application;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.consensus.Consensus;
import com.radixdlt.consensus.ConsensusObservation;
import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.LedgerEntry;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.LedgerEntryStoreView;
import com.radixdlt.universe.Universe;
import org.json.JSONObject;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.shards.ShardSpace;
import org.radix.universe.system.LocalSystem;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class RadixEngineAtomProcessor implements Application {
	private static final Logger log = Logging.getLogger("middleware2.atomProcessor");

	private boolean interrupted;

	private final Consensus consensus;
	private final LedgerEntryStore store;
	private final RadixEngine radixEngine;
	private final Serialization serialization;
	private final AtomToBinaryConverter atomToBinaryConverter;
	private final BlockingDeque<Atom> parkedAtoms;
	private final Map<Atom, ProcessorAtomEventListener> parkedListeners;

	@Inject
	public RadixEngineAtomProcessor(
		Consensus consensus,
		LedgerEntryStore store,
		RadixEngine radixEngine,
		Serialization serialization,
		AtomToBinaryConverter atomToBinaryConverter
	) {
		this.consensus = consensus;
		this.store = store;
		this.radixEngine = radixEngine;
		this.serialization = serialization;
		this.atomToBinaryConverter = atomToBinaryConverter;
		this.parkedAtoms = new LinkedBlockingDeque<>();
		this.parkedListeners = new ConcurrentHashMap<>();
	}

	@Override
	public LedgerEntry takeNextEntry() throws InterruptedException {
		Atom atom = parkedAtoms.take();
		return new LedgerEntry(atomToBinaryConverter.toLedgerEntryContent(atom), atom.getAID());
	}

	private void process() throws InterruptedException {
		while (!interrupted) {
			ConsensusObservation consensusObservation = consensus.observe();
			if (consensusObservation.getType() == ConsensusObservation.Type.COMMIT) {
				Atom atom = atomToBinaryConverter.toAtom(consensusObservation.getEntry().getContent());
				try {
					radixEngine.store(atom, new AtomEventListener() {
					});
					parkedAtoms.remove(atom);
				} catch (Exception e) {
					parkedListeners.computeIfPresent(atom, (a, listener) -> {
						listener.onError(e);
						return null;
					});
					log.error("Storing atom failed", e);
				}

				log.info("Committing to '" + consensusObservation.getEntry().getAID());
				// TODO actual commit mechanism stub
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
			processorAtomEventListener.ifPresent(listener -> parkedListeners.put(atom, listener));
			parkedAtoms.add(atom);
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
		this.store.close();
		interrupted = true;
	}

	private void initGenesis() {
		try {
			LinkedList<AID> atomIds = new LinkedList<>();
			for (Atom atom : Modules.get(Universe.class).getGenesis()) {
				if (!store.contains(atom.getAID())) {
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
			while (!store.contains(atomID)) {
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
