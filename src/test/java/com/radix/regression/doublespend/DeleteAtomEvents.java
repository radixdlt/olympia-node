package com.radix.regression.doublespend;

import com.radix.regression.Util;
import com.radix.regression.doublespend.DoubleSpendTestCreator.DoubleSpendTest;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ApplicationState;
import com.radixdlt.client.application.translate.ShardedAppStateId;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.TokenBalanceState;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.TestObserver;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.Condition;
import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.common.tuples.Pair;

import static org.assertj.core.api.Assertions.assertThat;

public class DeleteAtomEvents {
	@BeforeClass
	public static void setup() {
		if (!RadixUniverse.isInstantiated()) {
			RadixUniverse.bootstrap(Bootstrap.BETANET);
		}
	}

	@Test
	public void given_an_account_with_a_josh_token_with_one_supply__when_the_account_executes_two_transfers_via_two_different_nodes_at_the_same_time__then_the_account_balances_should_resolve_to_only_one_transfer() {
		IntStream.range(0, 10)
			.forEach(i -> {
				System.out.println("Round " + (i + 1));
				executeDoubleSpend();
			});
	}

	private static class DoubleTransferTestCreator implements DoubleSpendTestCreator {

		@Override
		public DoubleSpendTest create(final RadixAddress apiAddress) {
			final RadixAddress toAddress = RadixUniverse.getInstance().getAddressFrom(RadixIdentities.createNew().getPublicKey());
			final TokenDefinitionReference tokenRef = TokenDefinitionReference.of(apiAddress, "JOSH");

			return new DoubleSpendTest() {
				@Override
				public List<Action> initialActions() {
					return Collections.singletonList(
						CreateTokenAction.create(
							apiAddress,
							"Joshy Token",
							"JOSH",
							"Cool Token",
							BigDecimal.ONE,
							BigDecimal.ONE,
							TokenSupplyType.FIXED
						)
					);
				}

				@Override
				public List<Action> concurrentDoubleSpendActions() {
					TransferTokensAction action = TransferTokensAction.create(apiAddress, toAddress, BigDecimal.ONE, tokenRef);
					return Arrays.asList(action, action);
				}

				@Override
				public PostConsensusCondition postConsensusCondition() {
					Set<Pair<String, ShardedAppStateId>> stateRequired = new HashSet<>();
					stateRequired.add(Pair.of("Balance 1", ShardedAppStateId.of(TokenBalanceState.class, apiAddress)));
					stateRequired.add(Pair.of("Balance 2", ShardedAppStateId.of(TokenBalanceState.class, toAddress)));

					return new PostConsensusCondition(
						stateRequired,
						new Condition<>(map -> {
							TokenBalanceState tokenBalanceState1 = (TokenBalanceState) map.get(ShardedAppStateId.of(TokenBalanceState.class, apiAddress));
							TokenBalanceState tokenBalanceState2 = (TokenBalanceState) map.get(ShardedAppStateId.of(TokenBalanceState.class, toAddress));
							return tokenBalanceState1.getBalance().get(tokenRef).getAmount().compareTo(BigDecimal.ZERO) == 0 &&
									tokenBalanceState2.getBalance().get(tokenRef).getAmount().compareTo(BigDecimal.ONE) == 0;
						}, "")
					);
				}
			};
		}
	}

	public void executeDoubleSpend() {
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixIdentities.createNew());

		DoubleSpendTestCreator creator = new DoubleTransferTestCreator();
		DoubleSpendTest doubleSpendTest = creator.create(api.getMyAddress());

		List<Action> initialActions = doubleSpendTest.initialActions();
		Disposable d = api.pull();
		initialActions.stream()
			.map(api::execute)
			.map(Result::toCompletable)
			.forEach(Completable::blockingAwait);
		d.dispose();

		// Retrieve two nodes in the network
		Single<List<RadixNode>> twoNodes = RadixUniverse.getInstance().getNetworkController().getNetwork()
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
		Single<List<RadixNode>> oneNode = RadixUniverse.getInstance().getNetworkController().getNetwork()
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

		Observable<RadixNode> nodes = Observable.merge(twoNodes.toObservable(), oneNode.toObservable())
			.firstOrError()
			.flatMapObservable(l -> l.size() == 1 ? Observable.just(l.get(0), l.get(0)) : Observable.fromIterable(l));


		// When the account executes two transfers via two different nodes at the same time
		Single<List<SubmitAtomSendAction>> conflictingAtoms =
			Observable.zip(
				nodes,
				Observable.fromIterable(doubleSpendTest.concurrentDoubleSpendActions()),
				(client, action) ->
					api.buildAtom(action)
						.flatMap(api.getMyIdentity()::sign)
						.map(atom -> SubmitAtomSendAction.of(UUID.randomUUID().toString(), atom, client))
			)
			.flatMapSingle(a -> a)
			.toList();

		TestObserver<SubmitAtomResultAction> submissionObserver = TestObserver.create(Util.loggingObserver("Submission"));
		conflictingAtoms
			.flattenAsObservable(l -> l)
			.doAfterNext(a -> RadixUniverse.getInstance().getNetworkController().dispatch(a))
			.flatMap(a ->
				RadixUniverse.getInstance().getNetworkController()
					.getActions()
					.ofType(SubmitAtomResultAction.class)
					.filter(action -> action.getUuid().equals(a.getUuid()))
					.take(1)
			)
			.subscribe(submissionObserver);

		Map<ShardedAppStateId, TestObserver<ApplicationState>> testObservers = doubleSpendTest.postConsensusCondition().getStateRequired().stream()
			.collect(Collectors.toMap(
				Pair::getSecond,
				pair -> {
					final String name = pair.getFirst();
					final ShardedAppStateId id = pair.getSecond();
					final RadixApplicationAPI newApi = RadixApplicationAPI.create(RadixIdentities.createNew());
					final TestObserver<ApplicationState> testObserver = TestObserver.create(Util.loggingObserver(name));
					newApi.getState(id.stateClass(), id.address()).subscribe(testObserver);
					return testObserver;
				}
			));

		// Wait for network to resolve conflict
		TestObserver<RadixNodeAction> lastUpdateObserver = TestObserver.create(Util.loggingObserver("Last Update"));
		RadixUniverse.getInstance().getNetworkController()
			.getActions()
			.filter(a -> a instanceof FetchAtomsObservationAction || a instanceof SubmitAtomAction)
			.debounce(10, TimeUnit.SECONDS)
			.firstOrError()
			.subscribe(lastUpdateObserver);
		lastUpdateObserver.awaitTerminalEvent();
		submissionObserver.awaitTerminalEvent();

		Map<ShardedAppStateId, ApplicationState> state = testObservers.entrySet().stream()
			.collect(Collectors.toMap(
				Entry::getKey,
				e -> {
					List<ApplicationState> values = e.getValue().values();
					return values.get(values.size() - 1);
				}
			));
		testObservers.forEach((k,v) -> v.dispose());

		assertThat(state).is(doubleSpendTest.postConsensusCondition().getCondition());
	}
}
