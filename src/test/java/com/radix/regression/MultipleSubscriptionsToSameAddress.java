package com.radix.regression;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import io.reactivex.Observer;
import io.reactivex.observers.BaseTestConsumer;
import io.reactivex.observers.TestObserver;
import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.utils.UInt256;

import java.math.BigDecimal;

public class MultipleSubscriptionsToSameAddress {
	@BeforeClass
	public static void setup() {
		if (!RadixUniverse.isInstantiated()) {
			RadixUniverse.bootstrap(Bootstrap.BETANET);
		}
	}

	@Test
	public void enter_test_name_here() {
		RadixIdentity identity = RadixIdentities.createNew();
		RadixApplicationAPI api0 = RadixApplicationAPI.create(identity);
		RadixApplicationAPI api1 = RadixApplicationAPI.create(identity);

		createToken(api0)
			.awaitCount(4, BaseTestConsumer.TestWaitStrategy.SLEEP_100MS, 10000)
			.assertSubscribed()
			.assertNoTimeout()
			.assertNoErrors();

		System.out.println("Create TEST with 3 supply");
		System.out.println("Subscribe api0");

		Observer<Object> api0Balance = Util.loggingObserver("api0");
		api0.getMyBalance(TokenDefinitionReference.of(api0.getMyAddress(), "TEST"))
			.subscribe(api0Balance);

		System.out.println("Mint 5 TEST");
		api0.mintTokens("TEST", UInt256.FIVE).toCompletable().blockingAwait();

		System.out.println("Subscribe api1");
		Observer<Object> api1Balance = Util.loggingObserver("api1");
		api1.getMyBalance(TokenDefinitionReference.of(api0.getMyAddress(), "TEST"))
			.subscribe(api1Balance);

		System.out.println("Mint 6 TEST");
		api0.mintTokens("TEST", UInt256.SIX).toCompletable().blockingAwait();

		System.out.println("Burn 2 TEST");
		api0.burnTokens("TEST", UInt256.TWO).toCompletable().blockingAwait();
	}

	private static TestObserver createToken(RadixApplicationAPI api) {
		TestObserver<SubmitAtomAction> observer = new TestObserver<>();
		api.createToken(
			"TestToken",
			"TEST",
			"TestToken",
			UInt256.THREE,
			UInt256.ONE,
			CreateTokenAction.TokenSupplyType.MUTABLE)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);

		return observer;
	}
}
