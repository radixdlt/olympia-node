package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomValidationException;
import com.radixdlt.client.core.network.AtomQuery;
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

	public Observable<Atom> fetchAtoms(EUID destination) {
		final AtomQuery<Atom> atomQuery = new AtomQuery<>(destination, Atom.class);
		return Observable.fromCallable(() -> clientSelector.apply(destination.getShard()))
			.flatMapSingle(c -> c)
			.flatMap(client -> client.getAtoms(atomQuery))
			.doOnError(throwable -> {
				LOGGER.warn("Error on getAllAtoms: {}", destination);
			})
			.retryWhen(new IncreasingRetryTimer())
			.filter(atom -> {
				try {
					RadixAtomValidator.getInstance().validate(atom);
					return true;
				} catch (AtomValidationException e) {
					// TODO: Stop stream and mark client as untrustable
					LOGGER.error(e.toString());
					return false;
				}
			})
			.doOnSubscribe(atoms -> LOGGER.info("Atom Query Subscribe: destination({})", destination));
	}
}
