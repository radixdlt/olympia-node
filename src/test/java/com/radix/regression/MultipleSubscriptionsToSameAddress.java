package com.radix.regression;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.BootstrapConfig;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import io.reactivex.observers.BaseTestConsumer;
import io.reactivex.observers.TestObserver;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import java.math.BigDecimal;

public class MultipleSubscriptionsToSameAddress {
	private static final BootstrapConfig BOOTSTRAP_CONFIG;
	static {
		String bootstrapConfigName = System.getenv("RADIX_BOOTSTRAP_CONFIG");
		if (bootstrapConfigName != null) {
			BOOTSTRAP_CONFIG = Bootstrap.valueOf(bootstrapConfigName);
		} else {
			BOOTSTRAP_CONFIG = Bootstrap.LOCALHOST_SINGLENODE;
		}
	}

	@Test
	public void multipleSubscriptionTest() throws Exception {

		RadixIdentity identity = RadixIdentities.createNew();
		RadixApplicationAPI api0 = RadixApplicationAPI.create(BOOTSTRAP_CONFIG, identity);
		RadixApplicationAPI api1 = RadixApplicationAPI.create(BOOTSTRAP_CONFIG, identity);

		api0.createToken(
			RRI.of(api0.getMyAddress(), "TEST"),
			"TestToken",
			"TestToken",
			BigDecimal.valueOf(3),
			BigDecimal.ONE,
			CreateTokenAction.TokenSupplyType.MUTABLE)
		.blockUntilComplete();
		TimeUnit.SECONDS.sleep(3);

		System.out.println("Create TEST with 3 supply");
		System.out.println("Subscribe api0");

		RRI token = RRI.of(api0.getMyAddress(), "TEST");

		TestObserver<Object> api0Balance = TestObserver.create(Util.loggingObserver("api0"));
		api0.getBalance(token)
			.subscribe(api0Balance);

		System.out.println("Mint 5 TEST");
		api0.mintTokens(token, BigDecimal.valueOf(5)).blockUntilComplete();
		TimeUnit.SECONDS.sleep(3);

		System.out.println("Subscribe api1");
		TestObserver<Object> api1Balance = TestObserver.create(Util.loggingObserver("api1"));
		api1.getBalance(token)
			.subscribe(api1Balance);

		System.out.println("Mint 6 TEST");
		api0.mintTokens(token, BigDecimal.valueOf(6)).blockUntilComplete();
		TimeUnit.SECONDS.sleep(3);

		System.out.println("Burn 2 TEST");
		api0.burnTokens(token, BigDecimal.valueOf(2)).blockUntilComplete();

		api0Balance.dispose();
		api1Balance.dispose();
	}
}
