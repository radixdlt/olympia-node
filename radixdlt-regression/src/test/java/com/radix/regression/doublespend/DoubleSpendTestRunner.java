/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radix.regression.doublespend;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radix.regression.Util;
import com.radix.regression.doublespend.DoubleSpendTestConditions.BatchedActions;
import com.radix.test.utils.TokenUtilities;
import com.radixdlt.atom.ClientAtom;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.ShardedAppStateId;
import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.core.BootstrapConfig;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.atom.AtomBuilder;
import com.radixdlt.client.core.atoms.AtomStatus;
import com.radixdlt.client.core.ledger.AtomObservation.Type;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomStatusAction;
import io.reactivex.Completable;
import io.reactivex.Observable;
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

import com.radixdlt.utils.Pair;

import static org.junit.Assume.assumeTrue;

// FIXME: Too many warnings here to fix for checkstyle right now.
//CHECKSTYLE:OFF

public final class DoubleSpendTestRunner {
	private final Function<RadixApplicationAPI, DoubleSpendTestConditions> testSupplier;
	private final BiFunction<BootstrapConfig, RadixIdentity, RadixApplicationAPI> apiSupplier;

	DoubleSpendTestRunner(
		Function<RadixApplicationAPI, DoubleSpendTestConditions> testSupplier,
		BiFunction<BootstrapConfig, RadixIdentity, RadixApplicationAPI> apiSupplier
	) {
		this.testSupplier = testSupplier;
		this.apiSupplier = apiSupplier.andThen(this::withTokens);
	}

	DoubleSpendTestRunner(
		Function<RadixApplicationAPI, DoubleSpendTestConditions> testSupplier
	) {
		this.testSupplier = testSupplier;
		this.apiSupplier = this::createWithTokens;
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

		SingleNodeAPI(
			int clientId,
			RadixNode node,
			RadixIdentity identity,
			BiFunction<BootstrapConfig, RadixIdentity, RadixApplicationAPI> apiSupplier
		) {
			this.clientId = clientId;
			this.node = node;
			this.api = apiSupplier.apply(
				new BootstrapConfig() {
				    @Override
				    public RadixUniverseConfig getConfig() {
					    return RadixEnv.getBootstrapConfig().getConfig();
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

		Observable<SubmitAtomAction> executeSequentially(List<BatchedActions> actions) {
			return Observable.fromIterable(actions)
				.concatMap(batched -> {
					Transaction transaction = api.createTransaction();
					for (Action action : batched.getActions()) {
						transaction.addToWorkingArea(action);
					}
					transaction.getWorkingAreaRequirements().stream()
						.map(ShardedParticleStateId::address)
						.distinct()
						.map(api::pullOnce)
						.forEach(Completable::blockingAwait);
					transaction.stageWorkingArea();
					AtomBuilder unsignedAtom = transaction.buildAtom();

					return api.getIdentity().addSignature(unsignedAtom)
						.map(AtomBuilder::buildAtom)
						.flatMapObservable(a -> api.submitAtom(a, true).toObservable());
				});
		}

		@Override
		public String toString() {
			return "Client " + clientId + " " + node;
		}
	}


	ImmutableMap<ShardedAppStateId, ApplicationState> execute() {
		RadixApplicationAPI api = apiSupplier.apply(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		DoubleSpendTestConditions doubleSpendTestConditions = testSupplier.apply(api);

		List<BatchedActions> initialActions = doubleSpendTestConditions.initialActions();
		initialActions.stream()
			.map(batched -> {
				Transaction transaction = api.createTransaction();
				for (Action action : batched.getActions()) {
					transaction.stage(action);
				}
				transaction.stageWorkingArea();
				return transaction.commitAndPush();
			})
			.map(Result::toCompletable)
			.forEach(Completable::blockingAwait);

		// Wait for network to sync while retrieving two nodes in the network
		List<RadixNode> nodes = api.getNetworkState()
			.flatMapIterable(RadixNetworkState::getNodes)
			.distinct()
			.take(10, TimeUnit.SECONDS)
			.toList()
			.blockingGet();
		assumeTrue(nodes.size() >= 2);

		AtomicInteger clientId = new AtomicInteger(1);
		Observable<SingleNodeAPI> singleNodeApis = Observable.just(nodes.get(0), nodes.get(1))
			.map(node -> new SingleNodeAPI(clientId.getAndIncrement(), node, api.getIdentity(), apiSupplier)).cache();

		// When the account executes two transfers via two different nodes at the same time
		Observable<Pair<SingleNodeAPI, List<BatchedActions>>> conflictingAtoms =
			Observable.zip(
				singleNodeApis,
				Observable.fromIterable(doubleSpendTestConditions.conflictingActions()),
				Pair::of
			);

		List<TestObserver<SubmitAtomAction>> submissionObservers = conflictingAtoms.map(a -> {
			TestObserver<SubmitAtomAction> submissionObserver =
				TestObserver.create(Util.loggingObserver("Client " + a.getFirst().clientId + " Submission"));
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

							singleNodeApi.api.observeState(id.stateClass(), id.address()).subscribe(testObserver);
							return testObserver;
						}
					))
			)
			.toList()
			.blockingGet();

		final long startTime = System.currentTimeMillis();
		final CompositeDisposable compositeDisposable = new CompositeDisposable();
		singleNodeApis.map(singleNodeAPI -> doubleSpendTestConditions.postConsensusCondition().getStateRequired().stream()
			.map(p -> singleNodeAPI.api.pull(p.getSecond().address())))
			.subscribe(s -> s.forEach(compositeDisposable::add));

		// Wait for network to resolve conflict
		TestObserver<RadixNodeAction> lastUpdateObserver = TestObserver.create(Util.loggingObserver("Last Update"));
		singleNodeApis.flatMap(singleNodeApi ->
			singleNodeApi.api.getNetworkActions()
			.doOnNext(a -> {
				if (a instanceof FetchAtomsObservationAction) {
					FetchAtomsObservationAction f = (FetchAtomsObservationAction) a;
					if (f.getObservation().getType() == Type.DELETE || f.getObservation().getType() == Type.STORE) {
						System.out.println(
							System.currentTimeMillis()
							+ " "
							+ singleNodeApi
							+ " "
							+ f.getObservation().getType()
							+ ": "
							+ f.getObservation().getAtom().getAID()
						);
					}
				} else if (a instanceof SubmitAtomStatusAction) {
					SubmitAtomStatusAction r = (SubmitAtomStatusAction) a;
					if (r.getStatusNotification().getAtomStatus() == AtomStatus.EVICTED_FAILED_CM_VERIFICATION) {
						System.out.println(
							System.currentTimeMillis()
							+ " "
							+ singleNodeApi
							+ " VALIDATION_ERROR: "
							+ r.getAtom().getAID()
						);
					}
				}
			})
			.filter(a -> a instanceof FetchAtomsObservationAction || a instanceof SubmitAtomAction)
		)
			.timeout(30, TimeUnit.SECONDS)
			.debounce(20, TimeUnit.SECONDS)
			.firstOrError()
			.subscribe(lastUpdateObserver);
		lastUpdateObserver.awaitTerminalEvent();
		submissionObservers.forEach(TestObserver::dispose);

		try {
			while (true) {
				var lastAtomState = singleNodeApis.map(
					singleNodeAPI -> {
						var atoms = doubleSpendTestConditions.postConsensusCondition().getStateRequired().stream()
							.flatMap(p -> singleNodeAPI.api.getAtomStore().getStoredAtoms(p.getSecond().address()))
							.collect(Collectors.toSet());

						return Pair.of("Client " + singleNodeAPI.clientId, atoms);
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
					if (states.stream().allMatch(s -> doubleSpendTestConditions.postConsensusCondition()
						.getCondition().matches(s))
						&& states.stream().allMatch(s0 -> states.stream().allMatch(s1 -> s1.equals(s0)))
						&& lastAtomState.values().stream()
							.allMatch(s0 -> lastAtomState.values().stream().allMatch(s1 -> s1.equals(s0))
					)) {
						return states.iterator().next();
					} else {
						try {
							if (lastAtomState.values().stream()
								.allMatch(s0 -> lastAtomState.values().stream().allMatch(s1 -> s1.equals(s0)))) {
								System.out.println(
									cur
									+ " States match but not expected retrying 5 seconds...Time until resolved: "
									+ (timeUntilResolved / 1000)
								);
								if (!states.isEmpty()) {
									System.out.println(states.iterator().next());
								}
							} else {
								System.out.println(
									cur
									+ " States don't match retrying 5 seconds...Time until resolved: "
									+ (timeUntilResolved / 1000)
								);
							}

							for (var e : lastAtomState.entrySet()) {
								System.out.println(
									e.getKey()
									+ ": "
									+ e.getValue().stream()
										.map(ClientAtom::getAID)
										.map(Object::toString)
										.collect(Collectors.toSet())
								);
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
					for (var state0 : lastAtomState.entrySet()) {
						for (var state1 : lastAtomState.entrySet()) {
							assertThat(state0.getValue()).isEqualTo(state1.getValue());
						}
					}

					break;
				}
			}

		} finally {
			compositeDisposable.dispose();
			testObserversPerApi.forEach(testObservers -> testObservers.forEach((k, v) -> v.dispose()));
		}

		throw new IllegalStateException();
	}

	private RadixApplicationAPI withTokens(RadixApplicationAPI api) {
		TokenUtilities.requestTokensFor(api);
		return api;
	}

	private RadixApplicationAPI createWithTokens(BootstrapConfig bootstrap, RadixIdentity identity) {
		return withTokens(RadixApplicationAPI.create(bootstrap, identity));
	}
}
