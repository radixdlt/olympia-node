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

package com.radix.regression;

import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.identifiers.RRI;
import io.reactivex.observers.TestObserver;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import java.math.BigDecimal;

public class MultipleSubscriptionsToSameAddress {
	@Test
	public void multipleSubscriptionTest() throws Exception {

		RadixIdentity identity = RadixIdentities.createNew();
		RadixApplicationAPI api0 = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), identity);
		TokenUtilities.requestTokensFor(api0);
		RadixApplicationAPI api1 = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), identity);

		api0.createToken(
			RRI.of(api0.getAddress(), "TEST"),
			"TestToken",
			"TestToken",
			BigDecimal.valueOf(3),
			BigDecimal.ONE,
			CreateTokenAction.TokenSupplyType.MUTABLE)
		.blockUntilComplete();
		TimeUnit.SECONDS.sleep(3);

		System.out.println("Create TEST with 3 supply");
		System.out.println("Subscribe api0");

		RRI token = RRI.of(api0.getAddress(), "TEST");

		TestObserver<Object> api0Balance = TestObserver.create(Util.loggingObserver("api0"));
		api0.observeBalance(token)
			.subscribe(api0Balance);

		System.out.println("Mint 5 TEST");
		api0.mintTokens(token, BigDecimal.valueOf(5)).blockUntilComplete();
		TimeUnit.SECONDS.sleep(3);

		System.out.println("Subscribe api1");
		TestObserver<Object> api1Balance = TestObserver.create(Util.loggingObserver("api1"));
		api1.observeBalance(token)
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
