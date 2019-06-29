package com.radix.regression.doublespend;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radix.regression.Util;
import com.radix.regression.doublespend.DoubleSpendTestConditions.BatchedActions;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.ShardedAppStateId;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.BootstrapConfig;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.address.RadixUniverseConfigs;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.ledger.AtomObservation.Type;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.TestObserver;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.radix.common.tuples.Pair;

public final class DoubleSpendTestRunner {
	private static final BootstrapConfig BOOTSTRAP_CONFIG;
	static {
		String bootstrapConfigName = System.getenv("RADIX_BOOTSTRAP_CONFIG");
		if (bootstrapConfigName != null) {
			BOOTSTRAP_CONFIG = Bootstrap.valueOf(bootstrapConfigName);
		} else {
			BOOTSTRAP_CONFIG = Bootstrap.LOCALHOST;
		}
	}

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
				System.out.println("================================================================");
				System.out.println("Round " + (i + 1));
				System.out.println("================================================================");

				final ImmutableMap<ShardedAppStateId, ApplicationState> finalState = execute();

				System.out.println();
				System.out.println("Final State:");
				System.out.println(finalState);
				System.out.println();
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
					    return RadixUniverseConfigs.getLocalnet();
				    }

				    @Override
					public List<RadixNetworkEpic> getDiscoveryEpics() {
				    	return Collections.emptyList();
					}

				    @Override
				    public Set<RadixNode> getInitialNetwork() {
				    	return ImmutableSet.of(node);
					}
			    },
				identity);
		}

		Observable<SubmitAtomAction> executeSequentially(List<List<Action>> actions) {
			return Observable.fromIterable(actions)
				.concatMap(transaction ->
					api.buildAtom(transaction)
						.flatMap(api.getMyIdentity()::sign)
						.flatMapObservable(a -> api.submitAtom(a, true).toObservable())
				);
		}

		@Override
		public String toString() {
			return "Client " + clientId + " " + node;
		}
	}


	ImmutableMap<ShardedAppStateId, ApplicationState> execute() {
		RadixApplicationAPI api = apiSupplier.apply(BOOTSTRAP_CONFIG, RadixIdentities.createNew());
		DoubleSpendTestConditions doubleSpendTestConditions = testSupplier.apply(api);

		List<BatchedActions> initialActions = doubleSpendTestConditions.initialActions();
		initialActions.stream()
			.map(batched -> {
				Transaction transaction = api.createTransaction();
				for (Action action : batched.getActions()) {
					transaction.execute(action);
				}
				return transaction.commit();
			})
			.map(Result::toCompletable)
			.forEach(Completable::blockingAwait);

		// Wait for network to sync
		// TODO: implement faster mechanism for this
		try {
			TimeUnit.SECONDS.sleep(5);
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
			.debounce(3, TimeUnit.SECONDS)
			.firstOrError()
			.map(state -> state.getNodes().entrySet().stream()
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
		Observable<Pair<SingleNodeAPI, List<List<Action>>>> conflictingAtoms =
			Observable.zip(
				singleNodeApis,
				Observable.fromIterable(doubleSpendTestConditions.conflictingActions())
					.map(l -> l.stream().map(BatchedActions::getActions).collect(Collectors.toList())),
				Pair::of
			);

		List<TestObserver<SubmitAtomAction>> submissionObservers = conflictingAtoms.map(a -> {
			TestObserver<SubmitAtomAction> submissionObserver =
				TestObserver.create(Util.loggingObserver("Client " + a.getFirst().clientId + " Submission" ));
			a.getFirst().executeSequentially(a.getSecond()).subscribe(submissionObserver);
			return submissionObserver;
		}).toList().blockingGet();

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
							+ f.getObservation().getAtom().getAid());
					}
				} else if (a instanceof SubmitAtomStatusAction) {
					SubmitAtomStatusAction r = (SubmitAtomStatusAction) a;
					if (r.getStatusNotification().getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION) {
						System.out.println(System.currentTimeMillis() + " " + singleNodeApi + " VALIDATION_ERROR: " + r.getAtom().getAid());
					}
				}
			})
			.filter(a -> a instanceof FetchAtomsObservationAction || a instanceof SubmitAtomAction)
		)
			.debounce(20, TimeUnit.SECONDS)
			.firstOrError()
			.subscribe(lastUpdateObserver);
		lastUpdateObserver.awaitTerminalEvent();
		submissionObservers.forEach(TestObserver::dispose);

		final long startTime = System.currentTimeMillis();
		final CompositeDisposable compositeDisposable = new CompositeDisposable();
		singleNodeApis.map(singleNodeAPI -> doubleSpendTestConditions.postConsensusCondition().getStateRequired().stream()
			.map(p -> singleNodeAPI.api.pull(p.getSecond().address())))
			.subscribe(s -> s.forEach(compositeDisposable::add));

		try {
			while (true) {
				Map<String, Set<Atom>> lastAtomState = singleNodeApis.map(
					singleNodeAPI -> {
						Set<Atom> particles = doubleSpendTestConditions.postConsensusCondition().getStateRequired().stream()
							.flatMap(p -> singleNodeAPI.api.getAtomStore().getStoredAtoms(p.getSecond().address()))
							.collect(Collectors.toSet());

						return Pair.of("Client " + singleNodeAPI.clientId, particles);
					}).toMap(Pair::getFirst, Pair::getSecond).blockingGet();

				List<ImmutableMap<ShardedAppStateId, ApplicationState>> states = testObserversPerApi.stream().map(testObservers -> {
					ImmutableMap<ShardedAppStateId, ApplicationState> state = testObservers.entrySet().stream()
						.collect(ImmutableMap.toImmutableMap(Entry::getKey, e -> {
							List<ApplicationState> values = e.getValue().values();
							if (values.isEmpty()) {
								throw new IllegalStateException("Application state for " + e.getKey() + " is empty");
							}

							return values.get(values.size() - 1);
						}));
					return state;
				}).collect(Collectors.toList());

				// TODO: Remove 160 seconds when atom sync speed is fixed
				final long cur = System.currentTimeMillis();
				final long timeUntilResolved = startTime + TimeUnit.SECONDS.toMillis(1000) - cur;

				if (timeUntilResolved > 0) {
					if (states.stream().allMatch(s -> doubleSpendTestConditions.postConsensusCondition().getCondition().matches(s))
							&& states.stream().allMatch(s0 -> states.stream().allMatch(s1 -> s1.equals(s0)))
							&& lastAtomState.entrySet().stream().map(Entry::getValue)
								.allMatch(s0 -> lastAtomState.entrySet().stream().map(Entry::getValue).allMatch(s1 -> s1.equals(s0))
					)) {
						return states.iterator().next();
					} else {
						try {
							if (lastAtomState.entrySet().stream().map(Entry::getValue)
								.allMatch(s0 -> lastAtomState.entrySet().stream().map(Entry::getValue).allMatch(s1 -> s1.equals(s0)))) {
								System.out.println(cur + " States match but not expected retrying 5 seconds...Time until resolved: " + (timeUntilResolved / 1000));
								if (!states.isEmpty()) {
									System.out.println(states.iterator().next());
								}
							} else {
								System.out.println(cur + " States don't match retrying 5 seconds...Time until resolved: " + (timeUntilResolved / 1000));
							}

							for (Entry<String, Set<Atom>> e : lastAtomState.entrySet()) {
								System.out.println(e.getKey() + ": " + e.getValue().stream().map(Atom::getAid).map(Object::toString).collect(Collectors.toSet()));
							}

							TimeUnit.SECONDS.sleep(5);
							System.out.println("Retrying...");

						} catch (InterruptedException e) {
						}
					}
				} else {
					states.forEach(s -> assertThat(s)
						.is(doubleSpendTestConditions.postConsensusCondition().getCondition()));
						//.as(doubleSpendTestConditions.postConsensusCondition().getCondition().description().toString())));

					// All clients should see the same app state
					for (ImmutableMap<ShardedAppStateId, ApplicationState> state0 : states) {
						for (ImmutableMap<ShardedAppStateId, ApplicationState> state1 : states) {
							assertThat(state0).isEqualTo(state1);
						}
					}

					// All clients should see the same atom state
					for (Entry<String, Set<Atom>> state0 : lastAtomState.entrySet()) {
						for (Entry<String, Set<Atom>> state1 : lastAtomState.entrySet()) {
							assertThat(state0.getValue()).isEqualTo(state1.getValue());
						}
					}

					break;
				}
			}

		} finally {
			compositeDisposable.dispose();
			testObserversPerApi.forEach(testObservers -> testObservers.forEach((k,v) -> v.dispose()));
		}

		throw new IllegalStateException();
	}
}
