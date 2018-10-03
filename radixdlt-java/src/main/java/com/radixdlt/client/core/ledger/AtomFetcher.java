package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.network.IncreasingRetryTimer;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Module responsible for selecting a node and fetching atoms and retrying if necessary.
 */
public class AtomFetcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(AtomFetcher.class);

	/**
	 * Mechanism by which to get a valid client to connect to.
	 */
	private final Function<Long, Single<RadixJsonRpcClient>> clientSelector;

	public AtomFetcher(Function<Long, Single<RadixJsonRpcClient>> clientSelector) {
		this.clientSelector = clientSelector;
	}

	public Observable<AtomObservation> fetchAtoms(EUID destination) {
		return clientSelector.apply(destination.getShard())
			.flatMapObservable(client -> client.getAtoms(destination))
			.doOnError(throwable -> {
				LOGGER.warn("Error on getAllAtoms: {}", destination);
			})
			.retryWhen(new IncreasingRetryTimer())
			.doOnSubscribe(atoms -> LOGGER.info("Atom Query Subscribe: destination({})", destination));
	}
}
