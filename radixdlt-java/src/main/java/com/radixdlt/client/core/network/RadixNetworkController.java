package com.radixdlt.client.core.network;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.AtomValidationException;
import com.radixdlt.client.core.ledger.AtomSubmitter;
import com.radixdlt.client.core.ledger.RadixAtomValidator;
import io.reactivex.Observable;
import io.reactivex.observables.ConnectableObservable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RadixNetworkController implements AtomSubmitter {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixNetworkController.class);

	public static class RadixNetworkControllerBuilder {
		private RadixNetwork network;
		private RadixUniverseConfig config;
		private Observable<RadixPeer> seeds;

		public RadixNetworkControllerBuilder() {
		}

		public RadixNetworkControllerBuilder seeds(Observable<RadixPeer> seeds) {
			this.seeds = seeds;
			return this;
		}

		public RadixNetworkControllerBuilder network(RadixNetwork network) {
			this.network = network;
			return this;
		}

		public RadixNetworkControllerBuilder checkUniverse(RadixUniverseConfig config) {
			this.config = config;
			return this;
		}

		public RadixNetworkController build() {
			Objects.requireNonNull(network);
			Objects.requireNonNull(seeds);

			final RadixClientSupplier clientSupplier = config == null
				? new RadixClientSupplier(network)
				: new RadixClientSupplier(network, config);

			return new RadixNetworkController(clientSupplier, network, seeds);
		}
	}

	private final RadixClientSupplier clientSupplier;
	private final RadixNetwork network;
	private final Observable<RadixPeer> seeds;

	private RadixNetworkController(RadixClientSupplier clientSupplier, RadixNetwork network, Observable<RadixPeer> seeds) {
		this.clientSupplier = clientSupplier;
		this.network = network;
		this.seeds = seeds;

		// TODO: move this to more appropriate place
		seeds.subscribe(network::addPeer);

		// TODO: Cleanup discovery
		network.getNetworkState().debounce(1, TimeUnit.SECONDS).subscribe(this::discover);
	}

	private void discover(RadixNetworkState state) {
		final Map<RadixClientStatus,List<RadixPeer>> statusMap = Arrays.stream(RadixClientStatus.values())
			.collect(Collectors.toMap(
				Function.identity(),
				s -> state.getPeers().entrySet().stream().filter(e -> e.getValue().equals(s)).map(Entry::getKey).collect(Collectors.toList())
			));
		final long nodeCount = state.getPeers().size();
		if (nodeCount == 1 && !statusMap.get(RadixClientStatus.CONNECTED).isEmpty()) {
			RadixPeer singleNode = statusMap.get(RadixClientStatus.CONNECTED).get(0);
			RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(network.getWsChannel(singleNode));
			jsonRpcClient.getLivePeers()
				.toObservable()
				.flatMapIterable(i -> i)
				.map(data -> new RadixPeer(data.getIp(), singleNode.isSsl(), singleNode.getPort()))
				.subscribe(network::addPeer);
		}
	}


	public RadixNetwork getNetwork() {
		return network;
	}

	/**
	 * Immediately submits an atom into the ledger without waiting for subscription. The returned
	 * observable is a full replay of the status of the atom, from submission to acceptance by
	 * the network.
	 *
	 * @param atom atom to submit into the ledger
	 * @return Observable emitting status updates to submission
	 */
	@Override
	public Observable<AtomSubmissionUpdate> submitAtom(Atom atom) {
		Observable<AtomSubmissionUpdate> status = clientSupplier.getRadixClient(atom.getRequiredFirstShard())
			.map(network::getWsChannel)
			.flatMapObservable(c ->
				new RadixJsonRpcClient(c).submitAtom(atom)
					.doOnError(Throwable::printStackTrace)
					.retryWhen(new IncreasingRetryTimer(WebSocketException.class))
					// TODO: Better way of cleanup?
					.doFinally(() -> Observable.timer(2, TimeUnit.SECONDS).subscribe(i -> c.close()))
			);

		ConnectableObservable<AtomSubmissionUpdate> replay = status.replay();
		replay.connect();

		return replay;
	}

	public Observable<AtomObservation> fetchAtoms(RadixAddress address) {
		final AtomQuery atomQuery = new AtomQuery(address);

		return clientSupplier.getRadixClient(address.getUID().getShard())
			.map(network::getWsChannel)
			.flatMapObservable(c ->
				new RadixJsonRpcClient(c).getAtoms(atomQuery)
					.doOnError(throwable -> LOGGER.warn("Error on getAllAtoms: {}", address))
					.retryWhen(new IncreasingRetryTimer(WebSocketException.class))
					.filter(atomObservation -> {
						if (atomObservation.isStore()) {
							LOGGER.info("Received atom " + atomObservation.getAtom().getHid());
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
					.doOnSubscribe(atoms -> LOGGER.info("Atom Query Subscribe: address({})", address))
					// TODO: Better way of cleanup?
					.doFinally(() -> Observable.timer(2, TimeUnit.SECONDS).subscribe(i -> c.close()))
			);
	}
}
