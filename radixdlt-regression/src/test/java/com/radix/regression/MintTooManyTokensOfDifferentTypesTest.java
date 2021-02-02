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
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import io.reactivex.observers.BaseTestConsumer.TestWaitStrategy;
import io.reactivex.observers.TestObserver;
import java.math.BigDecimal;
import org.junit.Test;

public class MintTooManyTokensOfDifferentTypesTest {
	@Test
	public void given_a_token_with_max_supply_created_in_one_account__when_another_token_with_max_supply_created() {
		RadixApplicationAPI api0 = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		TokenUtilities.requestTokensFor(api0);
		RadixApplicationAPI api1 = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		TokenUtilities.requestTokensFor(api1);

		createToken(api0)
			.awaitCount(4, TestWaitStrategy.SLEEP_100MS, 10000)
			.assertSubscribed()
			.assertNoTimeout()
			.assertNoErrors();

		createToken(api1)
			.awaitCount(4, TestWaitStrategy.SLEEP_100MS, 10000)
			.assertSubscribed()
			.assertNoTimeout()
			.assertNoErrors();
	}

	private static TestObserver<SubmitAtomAction> createToken(RadixApplicationAPI api) {
		TestObserver<SubmitAtomAction> observer = new TestObserver<>();
		api.createToken(
			RRI.of(api.getAddress(), "TEST"),
			"TestToken",
			"TestToken",
			BigDecimal.valueOf(2).pow(256).subtract(BigDecimal.ONE).scaleByPowerOfTen(-18),
			TokenUnitConversions.getMinimumGranularity(),
			CreateTokenAction.TokenSupplyType.MUTABLE
		)
			.toObservable()
			.doOnNext(System.out::println)
			.subscribe(observer);

		return observer;
	}
}
