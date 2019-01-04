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
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate.AtomSubmissionState;
import com.radixdlt.client.core.network.actions.AtomsFetchUpdate;
import com.radixdlt.client.core.network.actions.AtomsFetchUpdate.AtomsFetchState;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
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
		private List<RadixNetworkEpic> epics = new ArrayList<>();

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

		public RadixNetworkControllerBuilder addEpic(RadixNetworkEpic epic) {
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

	private final Subject<RadixNodeAction> nodeActions = PublishSubject.<RadixNodeAction>create().toSerialized();

	private RadixNetworkController(RadixPeerSelector selector, RadixNetwork network, Observable<RadixPeer> seeds, List<RadixNetworkEpic> epics) {
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

		Set<Observable<RadixNodeAction>> updates = epics.stream()
			.map(epic -> epic.epic(nodeActions, this.networkState))
			.collect(Collectors.toSet());

		Observable.merge(updates).subscribe(nodeActions::onNext);
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
		AtomSubmissionUpdate initialAction = AtomSubmissionUpdate.searchForNode(atom);

		Observable<AtomSubmissionUpdate> status = nodeActions
			.filter(a -> a instanceof AtomSubmissionUpdate)
			.map(AtomSubmissionUpdate.class::cast)
			.filter(u -> u.getUuid().equals(initialAction.getUuid()))
			.takeUntil(AtomSubmissionUpdate::isComplete);
		ConnectableObservable<AtomSubmissionUpdate> replay = status.replay();
		replay.connect();

		nodeActions.onNext(initialAction);

		return replay;
	}

	public Observable<AtomObservation> fetchAtoms(RadixAddress address) {
		return Observable.create(emitter -> {
			AtomsFetchUpdate initialAction = AtomsFetchUpdate.searchForNode(address);

			Disposable d = nodeActions
				.filter(a -> a instanceof AtomsFetchUpdate)
				.map(AtomsFetchUpdate.class::cast)
				.filter(a -> a.getUuid().equals(initialAction.getUuid()))
				.filter(a -> a.getState().equals(AtomsFetchState.ATOM_OBSERVATION))
				.map(AtomsFetchUpdate::getObservation)
				.subscribe(emitter::onNext, emitter::onError, emitter::onComplete);

			emitter.setCancellable(() -> {
				d.dispose();
				nodeActions.onNext(AtomsFetchUpdate.cancel(initialAction.getUuid(), initialAction.getAddress()));
			});

			nodeActions.onNext(initialAction);
		});
	}
}
