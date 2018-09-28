package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.IncreasingRetryTimer;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observables.ConnectableObservable;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Module responsible for a node request and then submission of an atom and retry
 * mechanism if it fails.
 */
public class RadixAtomSubmitter implements AtomSubmitter {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixAtomSubmitter.class);

	private final Function<Set<Long>, Single<RadixJsonRpcClient>> clientSelector;

	public RadixAtomSubmitter(Function<Set<Long>, Single<RadixJsonRpcClient>> clientSelector) {
		this.clientSelector = clientSelector;
	}

	/**
	 * Immediately submits an atom into the ledger without waiting for subscription. The returned
	 * observable is a full replay of the status of the atom, from submission to acceptance by
	 * the network.
	 *
	 * @param atom atom to submit into the ledger
	 * @return Observable emitting status updates to submission
	 */
	public Observable<AtomSubmissionUpdate> submitAtom(Atom atom) {
		Observable<AtomSubmissionUpdate> status = clientSelector.apply(atom.getRequiredFirstShard())
			.doOnSuccess(client -> LOGGER.info("Found client to submit atom {}: {}", atom.getHid(), client.getLocation()))
			.doOnError(throwable -> {
				LOGGER.warn("Error on submitAtom {} {}", atom.getHid(), throwable.getMessage());
			})
			.flatMapObservable(client -> client.submitAtom(atom))
			.doOnError(Throwable::printStackTrace)
			.retryWhen(new IncreasingRetryTimer());

		ConnectableObservable<AtomSubmissionUpdate> replay = status.replay();
		replay.connect();

		return replay;
	}

}
