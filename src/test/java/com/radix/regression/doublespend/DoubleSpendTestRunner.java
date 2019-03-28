package com.radix.regression.doublespend;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.radix.regression.Util;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.ShardedAppStateId;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.BootstrapConfig;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.address.RadixUniverseConfigs;
import com.radixdlt.client.core.ledger.AtomObservation.Type;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType;
import com.radixdlt.client.core.network.epics.DiscoverSingleNodeEpic;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.TestObserver;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.radix.common.tuples.Pair;

public final class DoubleSpendTestRunner {
	private final Function<RadixApplicationAPI, DoubleSpendTestConditions> testSupplier;
	private final BiFunction<BootstrapConfig, RadixIdentity, RadixApplicationAPI> apiSupplier;

	DoubleSpendTestRunner(
		Function<RadixApplicationAPI, DoubleSpendTestConditions> testSupplier,
		BiFunction<BootstrapConfig, RadixIdentity, RadixApplicationAPI> apiSupplier
	) {
		this.testSupplier = testSupplier;
		this.apiSupplier = apiSupplier;
	}

	DoubleSpendTestRunner(
		Function<RadixApplicationAPI, DoubleSpendTestConditions> testSupplier
	) {
		this.testSupplier = testSupplier;
		this.apiSupplier = RadixApplicationAPI::create;
	}

	public void execute(int numRounds) {
		IntStream.range(0, numRounds)
			.forEach(i -> {
				System.out.println("Round " + (i + 1));
				execute();
			});
	}

	private static class SingleNodeAPI {
		private final RadixApplicationAPI api;
		private final RadixNode node;
		private final int clientId;

		SingleNodeAPI(int clientId, RadixNode node, RadixIdentity identity, BiFunction<BootstrapConfig, RadixIdentity, RadixApplicationAPI> apiSupplier) {
			this.clientId = clientId;
			this.node = node;
			this.api = apiSupplier.apply(
				new BootstrapConfig() {
				    @Override
				    public RadixUniverseConfig getConfig() {
					    return RadixUniverseConfigs.getBetanet();
				    }

				    @Override
				    public List<RadixNetworkEpic> getDiscoveryEpics() {
					    return Collections.singletonList(new DiscoverSingleNodeEpic(node, RadixUniverseConfigs.getBetanet()));
				    }
			    },
				identity);
		}

		public String toString() {
			return "Client " + clientId + " " + node;
		}
	}

	void execute() {
		RadixApplicationAPI api = apiSupplier.apply(Bootstrap.LOCALHOST, RadixIdentities.createNew());
		DoubleSpendTestConditions doubleSpendTestConditions = testSupplier.apply(api);

		List<Action> initialActions = doubleSpendTestConditions.initialActions();
		Disposable d = api.pull();
		initialActions.stream()
			.map(api::execute)
			.map(Result::toCompletable)
			.forEach(Completable::blockingAwait);
		d.dispose();

		// Wait for network to sync
		// TODO: implement faster mechanism for this
		try {
			TimeUnit.SECONDS.sleep(2);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		// Retrieve two nodes in the network
		Single<List<RadixNode>> twoNodes = api.getNetworkState()
			.filter(network -> network.getNodes().entrySet().stream()
				.filter(e -> e.getValue().getData().isPresent() && e.getValue().getUniverseConfig().isPresent())
				.count() >= 2)
			.firstOrError()
			.map(state ->
				state.getNodes().entrySet().stream()
					.filter(e -> e.getValue().getUniverseConfig().isPresent())
					.map(Entry::getKey)
					.collect(Collectors.toList())
			);

		// If two nodes don't exist in the network just use one node
		Single<List<RadixNode>> oneNode = api.getNetworkState()
			.filter(network -> network.getNodes().entrySet().stream()
				.filter(e -> e.getValue().getData().isPresent() && e.getValue().getUniverseConfig().isPresent())
				.count() == 1)
			.debounce(3, TimeUnit.SECONDS)
			.firstOrError()
			.map(state ->
				state.getNodes().entrySet().stream()
					.filter(e -> e.getValue().getUniverseConfig().isPresent())
					.map(Entry::getKey)
					.collect(Collectors.toList())
			);

		AtomicInteger clientId = new AtomicInteger(1);

		Observable<SingleNodeAPI> singleNodeApis = Observable.merge(twoNodes.toObservable(), oneNode.toObservable())
			.firstOrError()
			.flatMapObservable(l -> l.size() == 1 ? Observable.just(l.get(0), l.get(0)) : Observable.fromIterable(l))
			.map(node -> new SingleNodeAPI(clientId.getAndIncrement(), node, api.getMyIdentity(), apiSupplier))
			.cache();


		// When the account executes two transfers via two different nodes at the same time
		Observable<Pair<SingleNodeAPI, List<Action>>> conflictingAtoms =
			Observable.zip(
				singleNodeApis,
				Observable.fromIterable(doubleSpendTestConditions.conflictingActions()),
				Pair::of
			);

		TestObserver<SubmitAtomAction> submissionObserver = TestObserver.create(Util.loggingObserver("Submission"));
		conflictingAtoms
			.flatMap(a -> Observable.fromIterable(a.getFirst().api.executeSequentially(a.getSecond()))
				.flatMap(Result::toObservable)
				.takeUntil(s -> {
					if (s instanceof SubmitAtomResultAction) {
						SubmitAtomResultAction submitAtomResultAction = (SubmitAtomResultAction) s;
						return submitAtomResultAction.getType() != SubmitAtomResultActionType.STORED;
					}
					return false;
				})
			)
			.subscribe(submissionObserver);

		List<Map<ShardedAppStateId, TestObserver<ApplicationState>>> testObserversPerApi = singleNodeApis
			.map(singleNodeApi ->
				doubleSpendTestConditions.postConsensusCondition().getStateRequired().stream()
					.collect(Collectors.toMap(
						Pair::getSecond,
						pair -> {
							final String name = pair.getFirst();
							final ShardedAppStateId id = pair.getSecond();
							final TestObserver<ApplicationState> testObserver =
								TestObserver.create(Util.loggingObserver(singleNodeApi + " " + name));

							singleNodeApi.api.getState(id.stateClass(), id.address()).subscribe(testObserver);
							return testObserver;
						}
					))
			)
			.toList()
			.blockingGet();

		// Wait for network to resolve conflict
		TestObserver<RadixNodeAction> lastUpdateObserver = TestObserver.create(Util.loggingObserver("Last Update"));
		singleNodeApis.flatMap(singleNodeApi ->
			singleNodeApi.api.getNetworkController()
			.getActions()
			.doOnNext(a -> {
				if (a instanceof FetchAtomsObservationAction) {
					FetchAtomsObservationAction f = (FetchAtomsObservationAction) a;
					if (f.getObservation().getType() == Type.DELETE || f.getObservation().getType() == Type.STORE) {
						System.out.println(System.currentTimeMillis() + " " + singleNodeApi + " " + f.getObservation().getType() + ": "
							+ f.getObservation().getAtom().getHid());
					}
				} else if (a instanceof SubmitAtomResultAction) {
					SubmitAtomResultAction r = (SubmitAtomResultAction) a;
					if (r.getType() == SubmitAtomResultActionType.VALIDATION_ERROR) {
						System.out.println(System.currentTimeMillis() + " " + singleNodeApi + " VALIDATION_ERROR: " + r.getAtom().getHid());
					}
				}
			})
			.filter(a -> a instanceof FetchAtomsObservationAction || a instanceof SubmitAtomAction)
		)
			.debounce(10, TimeUnit.SECONDS)
			.firstOrError()
			.subscribe(lastUpdateObserver);
		lastUpdateObserver.awaitTerminalEvent();
		submissionObserver.awaitTerminalEvent();

		List<ImmutableMap<ShardedAppStateId, ApplicationState>> states = testObserversPerApi.stream().map(testObservers -> {
			ImmutableMap<ShardedAppStateId, ApplicationState> state = testObservers.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(
					Entry::getKey,
					e -> {
						List<ApplicationState> values = e.getValue().values();
						return values.get(values.size() - 1);
					}
				));
			testObservers.forEach((k,v) -> v.dispose());
			return state;
		}).collect(Collectors.toList());

		states.forEach(s -> assertThat(s).is(doubleSpendTestConditions.postConsensusCondition().getCondition()));

		// All clients should see the same state
		for (ImmutableMap<ShardedAppStateId, ApplicationState> state0 : states) {
			for (ImmutableMap<ShardedAppStateId, ApplicationState> state1 : states) {
				assertThat(state0).isEqualTo(state1);
			}
		}

	}
}
