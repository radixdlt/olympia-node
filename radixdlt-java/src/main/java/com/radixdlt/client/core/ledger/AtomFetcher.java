package com.radixdlt.client.core.ledger;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.AtomValidationException;
import com.radixdlt.client.core.network.jsonrpc.AtomQuery;
import com.radixdlt.client.core.util.IncreasingRetryTimer;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.websocket.WebSocketException;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

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

	public Observable<AtomObservation> fetchAtoms(RadixAddress address) {
		final AtomQuery atomQuery = new AtomQuery(address);
		return Observable.fromCallable(() -> clientSelector.apply(address.getUID().getShard()))
			.flatMapSingle(c -> c)
			.flatMap(client -> client.getAtoms(atomQuery))
			.doOnError(throwable -> LOGGER.warn("Error on getAllAtoms: {}", address))
			.retryWhen(new IncreasingRetryTimer(WebSocketException.class))
			.filter(atomObservation -> {
				if (atomObservation.isStore()) {
					LOGGER.info("Received atom " + atomObservation.getAtom().hid());
					try {
						RadixAtomValidator.getInstance().validate(atomObservation.getAtom());
						return true;
					} catch (AtomValidationException e) {
						// TODO: Stop stream and mark client as untrustable
						LOGGER.error(e.toString());
						return false;
					}
				} else {
					return true;
				}
			})
			.doOnSubscribe(atoms -> LOGGER.info("Atom Query Subscribe: address({})", address));
	}
}
