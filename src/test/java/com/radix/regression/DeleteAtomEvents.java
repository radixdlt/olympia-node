package com.radix.regression;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomSendAction;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.utils.UInt256;

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

	public void executeDoubleSpend() {
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixIdentities.createNew());

		TokenDefinitionReference tokenRef = TokenDefinitionReference.of(api.getMyAddress(), "JOSH");
		TestObserver<BigDecimal> myBalanceObserver = TestObserver.create(Util.loggingObserver("My Balance"));
		api.getMyBalance(tokenRef).subscribe(myBalanceObserver);

		// Given an account with a josh token with one supply
		Result result = api.createToken("Joshy Token", "JOSH", "Cool token", BigDecimal.ONE, BigDecimal.ONE, TokenSupplyType.FIXED);
		result.toCompletable().blockingAwait();

		// Wait until funds have been received
		myBalanceObserver.awaitCount(2);

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

		Observable<RadixNode> nodes = Observable.merge(twoNodes.toObservable(), oneNode.toObservable())
			.firstOrError()
			.flatMapObservable(l -> l.size() == 1 ? Observable.just(l.get(0), l.get(0)) : Observable.fromIterable(l));


		// When the account executes two transfers via two different nodes at the same time
		RadixApplicationAPI api2 = RadixApplicationAPI.create(RadixIdentities.createNew());
		Single<List<SubmitAtomSendAction>> conflictingAtoms = nodes
			.flatMapSingle(client -> {
				TransferTokensAction action = TransferTokensAction.create(api.getMyAddress(), api2.getMyAddress(), new BigDecimal("1.0"),
					tokenRef);
				return api.buildAtom(action)
					.flatMap(api.getMyIdentity()::sign)
					.map(atom -> SubmitAtomSendAction.of(UUID.randomUUID().toString(), atom, client));
			})
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

		TestObserver<BigDecimal> transferredObserver = TestObserver.create(Util.loggingObserver("Other Balance"));
		api2.getMyBalance(tokenRef)
			.subscribe(transferredObserver);

		// Wait for network to resolve conflict
		TestObserver<RadixNodeAction> lastUpdateObserver = TestObserver.create(Util.loggingObserver("Last Update"));
		RadixUniverse.getInstance().getNetworkController()
			.getActions()
			.filter(a -> a instanceof FetchAtomsObservationAction || a instanceof SubmitAtomAction)
			.debounce(10, TimeUnit.SECONDS)
			.firstOrError()
			.subscribe(lastUpdateObserver);
		lastUpdateObserver.awaitTerminalEvent();

		// Then the account balances should resolve to one transfer
		submissionObserver.awaitTerminalEvent();
		transferredObserver.assertValueAt(transferredObserver.valueCount() - 1, b -> b.compareTo(BigDecimal.ONE) == 0);
		transferredObserver.dispose();
		myBalanceObserver.assertValueAt(myBalanceObserver.valueCount() - 1, b -> b.compareTo(BigDecimal.ZERO) == 0);
		myBalanceObserver.dispose();
	}
}
