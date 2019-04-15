package com.radix.regression;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import io.reactivex.observers.BaseTestConsumer;
import io.reactivex.observers.TestObserver;
import org.junit.Test;

import java.math.BigDecimal;

public class MultipleSubscriptionsToSameAddress {
	@Test
	public void enter_test_name_here() {

		RadixIdentity identity = RadixIdentities.createNew();
		RadixApplicationAPI api0 = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, identity);
		RadixApplicationAPI api1 = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, identity);

		createToken(api0)
			.awaitCount(4, BaseTestConsumer.TestWaitStrategy.SLEEP_100MS, 10000)
			.assertSubscribed()
			.assertNoTimeout()
			.assertNoErrors();

		System.out.println("Create TEST with 3 supply");
		System.out.println("Subscribe api0");

		TestObserver<Object> api0Balance = TestObserver.create(Util.loggingObserver("api0"));
		api0.getMyBalance(RRI.of(api0.getMyAddress(), "TEST"))
			.subscribe(api0Balance);

		System.out.println("Mint 5 TEST");
		api0.mintTokens("TEST", BigDecimal.valueOf(5)).toCompletable().blockingAwait();

		System.out.println("Subscribe api1");
		TestObserver<Object> api1Balance = TestObserver.create(Util.loggingObserver("api1"));
		api1.getMyBalance(RRI.of(api0.getMyAddress(), "TEST"))
			.subscribe(api1Balance);

		System.out.println("Mint 6 TEST");
		api0.mintTokens("TEST", BigDecimal.valueOf(6)).toCompletable().blockingAwait();

		System.out.println("Burn 2 TEST");
		api0.burnTokens("TEST", BigDecimal.valueOf(2)).toCompletable().blockingAwait();

		api0Balance.dispose();
		api1Balance.dispose();
	}

	private static TestObserver createToken(RadixApplicationAPI api) {
		TestObserver<SubmitAtomAction> observer = new TestObserver<>();
		api.createToken(
			"TestToken",
			"TEST",
			"TestToken",
			BigDecimal.valueOf(3),
			BigDecimal.ONE,
			CreateTokenAction.TokenSupplyType.MUTABLE)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);

		return observer;
	}
}
