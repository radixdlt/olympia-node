package com.radixdlt.chaos.mempoolfiller;

import com.google.inject.Inject;
import com.radixdlt.consensus.Command;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.UInt256;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MempoolFiller {
	private static final Logger logger = LogManager.getLogger();
	private final Serialization serialization;
	private final RadixEngine<LedgerAtom> radixEngine;
	private final Hasher hasher;
	private final Universe universe;
	private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;
	private final ScheduledEventDispatcher<ScheduledMempoolFill> mempoolFillDispatcher;
	private final ECKeyPair keyPair;
	private RadixAddress to = null;

	@Inject
	public MempoolFiller(
		@MempoolFillerKey ECKeyPair keyPair,
		Serialization serialization,
		Hasher hasher,
		Universe universe,
		RadixEngine<LedgerAtom> radixEngine,
		EventDispatcher<MempoolAdd> mempoolAddEventDispatcher,
		ScheduledEventDispatcher<ScheduledMempoolFill> mempoolFillDispatcher
	) {
	    this.keyPair = keyPair;
		this.serialization = serialization;
		this.hasher = hasher;
		this.universe = universe;
		this.radixEngine = radixEngine;
		this.mempoolAddEventDispatcher = mempoolAddEventDispatcher;
		this.mempoolFillDispatcher = mempoolFillDispatcher;
	}

	public EventProcessor<MempoolFillerUpdate> messageFloodUpdateProcessor() {
	    return u -> {
	    	if (u.enabled() == (to != null)) {
	    		logger.info("Mempool Filler: not updating");
	    		return;
			}

			logger.info("Mempool Filler: Updating " + u.enabled());

			if (u.enabled()) {
	    		to = new RadixAddress((byte) universe.getMagic(), keyPair.getPublicKey());
	    		mempoolFillDispatcher.dispatch(ScheduledMempoolFill.create(), 50);
			} else {
	    		to = null;
			}
		};
    }

	public EventProcessor<ScheduledMempoolFill> scheduledMempoolFillEventProcessor() {
		return p -> {
		    if (to == null) {
		    	return;
			}

			logger.info("Mempool Filler: Filling");

			InMemoryWallet wallet = radixEngine.getComputedState(InMemoryWallet.class);
			wallet.createTransaction(to, UInt256.ONE)
				.ifPresentOrElse(
					atom -> {
						atom.sign(keyPair, hasher);
						ClientAtom clientAtom = ClientAtom.convertFromApiAtom(atom, hasher);
						byte[] payload = serialization.toDson(clientAtom, DsonOutput.Output.ALL);
						Command command = new Command(payload);
						this.mempoolAddEventDispatcher.dispatch(MempoolAdd.create(command));
					},
					() -> logger.warn("Unable to create atom")
				);

			mempoolFillDispatcher.dispatch(ScheduledMempoolFill.create(), 1000);
		};
	}
}
