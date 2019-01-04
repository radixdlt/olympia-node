package com.radixdlt.client.core.network;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.AtomValidationException;
import com.radixdlt.client.core.ledger.AtomSubmitter;
import com.radixdlt.client.core.ledger.RadixAtomValidator;
import com.radixdlt.client.core.ledger.selector.RadixPeerSelector;
import com.radixdlt.client.core.ledger.selector.RandomSelector;
import com.radixdlt.client.core.network.AtomSubmissionUpdate.AtomSubmissionState;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.subjects.PublishSubject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
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
		private Observable<RadixPeer> seeds = Observable.empty();
		private RadixPeerSelector selector = new RandomSelector();
		private List<AtomSubmissionEpic> epics = new ArrayList<>();

		public RadixNetworkControllerBuilder() {
		}

		public RadixNetworkControllerBuilder seeds(Observable<RadixPeer> seeds) {
			Objects.requireNonNull(seeds);
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

		public RadixNetworkControllerBuilder selector(RadixPeerSelector selector) {
			Objects.requireNonNull(selector);
			this.selector = selector;
			return this;
		}

		public RadixNetworkControllerBuilder addEpic(AtomSubmissionEpic epic) {
			this.epics.add(epic);
			return this;
		}


		public RadixNetworkController build() {
			Objects.requireNonNull(network);

			return new RadixNetworkController(selector, network, seeds, epics);
		}
	}

	/**
	 * The selector to use to decide between a list of viable peers
	 */
	private final RadixPeerSelector selector;
	private final RadixNetwork network;
	private final Observable<RadixPeer> seeds;
	private final Observable<RadixNetworkState> networkState;

	private final PublishSubject<AtomSubmissionUpdate> submissionUpdates = PublishSubject.create();

	private RadixNetworkController(RadixPeerSelector selector, RadixNetwork network, Observable<RadixPeer> seeds, List<AtomSubmissionEpic> epics) {
		this.selector = selector;
		this.network = network;
		this.seeds = seeds;

		this.networkState = this.network.getNetworkState().replay(1).autoConnect(3);

		this.networkState.debounce(1, TimeUnit.SECONDS).subscribe(this::discover);
		this.networkState.map(RadixNetworkState::getPeers)
			.takeUntil(i -> !i.isEmpty())
			.flatMap(i -> seeds)
			.subscribe(seed -> {
				LOGGER.info("Adding seed: " + seed);
				network.addPeer(seed);
			});

		Set<Observable<AtomSubmissionUpdate>> updates = epics.stream()
			.map(epic -> epic.epic(submissionUpdates, this.networkState))
			.collect(Collectors.toSet());

		Observable.merge(updates).subscribe(submissionUpdates::onNext);
	}

	// TODO: Cleanup discovery
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

	private static List<RadixPeer> getConnectedNodes(RadixNetworkState state) {
		return state.getPeers().entrySet().stream()
			.filter(entry -> entry.getValue().equals(RadixClientStatus.CONNECTED))
			.map(Map.Entry::getKey)
			.collect(Collectors.toList());
	}

	private void findConnection(RadixNetworkState state) {
		final Map<RadixClientStatus,List<RadixPeer>> statusMap = Arrays.stream(RadixClientStatus.values())
			.collect(Collectors.toMap(
				Function.identity(),
				s -> state.getPeers().entrySet().stream().filter(e -> e.getValue().equals(s)).map(Entry::getKey).collect(Collectors.toList())
			));

		final long activeNodeCount =
			statusMap.get(RadixClientStatus.CONNECTED).size()
			+ statusMap.get(RadixClientStatus.CONNECTING).size();

		if (activeNodeCount < 1) {
			LOGGER.info(String.format("Requesting more node connections, want %d but have %d active nodes", 1, activeNodeCount));

			List<RadixPeer> disconnectedPeers = statusMap.get(RadixClientStatus.DISCONNECTED);
			if (disconnectedPeers.isEmpty()) {
				LOGGER.info("Could not connect to new peer, don't have any.");
			} else {
				network.connect(disconnectedPeers.get(0));
			}
		}
	}

	// TODO: add sharding check
	private Single<RadixPeer> connectToNode(Set<Long> shard, Observable<RadixNetworkState> networkState) {
		Observable<RadixNetworkState> syncNetState = networkState
			.replay(1)
			.autoConnect(2);

		Observable<List<RadixPeer>> connectedNodes = syncNetState
			.map(RadixNetworkController::getConnectedNodes)
			.publish()
			.autoConnect(2);

		// Try and connect if there are no nodes
		syncNetState.zipWith(connectedNodes.takeWhile(List::isEmpty), (s, n) -> s)
			.subscribe(this::findConnection);

		Single<RadixPeer> selectedNode = connectedNodes
			.filter(viablePeerList -> !viablePeerList.isEmpty())
			.firstOrError()
			.map(selector::apply);

		return selectedNode;
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
		Observable<AtomSubmissionUpdate> status = submissionUpdates.serialize()
			.filter(u -> u.getAtom().equals(atom))
			.takeUntil(AtomSubmissionUpdate::isComplete);
		ConnectableObservable<AtomSubmissionUpdate> replay = status.replay();
		replay.connect();

		submissionUpdates.onNext(AtomSubmissionUpdate.create(atom, AtomSubmissionState.SEARCHING_FOR_NODE));

		return replay;
	}

	public Observable<AtomObservation> fetchAtoms(RadixAddress address) {
		final AtomQuery atomQuery = new AtomQuery(address);

		return this.connectToNode(Collections.singleton(address.getUID().getShard()), this.networkState)
			.flatMapObservable(c -> {
				WebSocketClient ws = network.getWsChannel(c);
				return new RadixJsonRpcClient(ws).getAtoms(atomQuery).doOnError(throwable -> LOGGER.warn("Error on getAllAtoms: {}", address))
					.retryWhen(new IncreasingRetryTimer(WebSocketException.class)).filter(atomObservation -> {
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
				}).doOnSubscribe(atoms -> LOGGER.info("Atom Query Subscribe: address({})", address))
					// TODO: Better way of cleanup?
				.doFinally(() -> Observable.timer(2, TimeUnit.SECONDS).subscribe(i -> ws.close()));
			});
	}
}
