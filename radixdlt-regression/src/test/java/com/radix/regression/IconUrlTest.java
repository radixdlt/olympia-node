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

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionsState;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.identifiers.Rri;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.reactivex.observers.TestObserver;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

public class IconUrlTest {
	@Test
	@Ignore
	public void when_creating_multi_issuance_token_with_icon_url__then_icon_url_should_be_included() throws Exception {
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		TestObserver<TokenDefinitionsState> testObserver = TestObserver.create(Util.loggingObserver("TokenDefinitionsState"));
		api.observeTokenDefs(api.getAddress())
			.filter(td -> !td.getState().isEmpty())
			.firstOrError()
			.subscribe(testObserver);

		Rri token = Rri.of(api.getAddress(), "RLAU");
		Result result0 = api.createMultiIssuanceToken(
			token,
			"RLAU Token Name",
			"RLAU Token Description",
			"https://robohash.org/Radix-DLT-Ltd-RLAU-Token.png",
			null
		);
		result0.toObservable().subscribe(System.out::println);
		result0.blockUntilComplete();

		testObserver.awaitTerminalEvent();
		List<TokenDefinitionsState> values = testObserver.values();
		assertEquals(1, values.size());
		TokenDefinitionsState value = values.get(0);
		assertNotNull(value);
		assertNotNull(value.getState());
		assertNotNull(value.getState().get(token));
		assertEquals("https://robohash.org/Radix-DLT-Ltd-RLAU-Token.png", value.getState().get(token).getIconUrl());
	}

	@Test
	@Ignore
	public void when_creating_fixed_supply_token_with_icon_url__then_icon_url_should_be_included() throws Exception {
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		TestObserver<TokenDefinitionsState> testObserver = TestObserver.create(Util.loggingObserver("TokenDefinitionsState"));
		api.observeTokenDefs(api.getAddress())
			.filter(td -> !td.getState().isEmpty())
			.firstOrError()
			.subscribe(testObserver);

		Rri token = Rri.of(api.getAddress(), "RLAU");
		Result result0 = api.createFixedSupplyToken(
			token,
			"RLAU Token Name",
			"RLAU Token Description",
			"https://robohash.org/Radix-DLT-Ltd-RLAU-Token.png",
			null,
			BigDecimal.valueOf(1)
		);
		result0.toObservable().subscribe(System.out::println);
		result0.blockUntilComplete();

		testObserver.awaitTerminalEvent();
		List<TokenDefinitionsState> values = testObserver.values();
		assertEquals(1, values.size());
		TokenDefinitionsState value = values.get(0);
		System.err.println(value);
		assertNotNull(value);
		assertNotNull(value.getState());
		assertNotNull(value.getState().get(token));
		assertEquals("https://robohash.org/Radix-DLT-Ltd-RLAU-Token.png", value.getState().get(token).getIconUrl());
	}

	@Test
	@Ignore
	public void when_creating_token_with_icon_url__then_icon_url_should_be_included() throws Exception {
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		TestObserver<TokenDefinitionsState> testObserver = TestObserver.create(Util.loggingObserver("TokenDefinitionsState"));
		api.observeTokenDefs(api.getAddress())
			.filter(td -> !td.getState().isEmpty())
			.firstOrError()
			.subscribe(testObserver);

		Rri token = Rri.of(api.getAddress(), "RLAU");
		Result result0 = api.createToken(
			token,
			"RLAU Token Name",
			"RLAU Token Description",
			"https://robohash.org/Radix-DLT-Ltd-RLAU-Token.png",
			null,
			BigDecimal.valueOf(1),
			TokenUnitConversions.getMinimumGranularity(),
			TokenSupplyType.FIXED
		);
		result0.toObservable().subscribe(System.out::println);
		result0.blockUntilComplete();

		testObserver.awaitTerminalEvent();
		List<TokenDefinitionsState> values = testObserver.values();
		assertEquals(1, values.size());
		TokenDefinitionsState value = values.get(0);
		assertNotNull(value);
		assertNotNull(value.getState());
		assertNotNull(value.getState().get(token));
		assertEquals("https://robohash.org/Radix-DLT-Ltd-RLAU-Token.png", value.getState().get(token).getIconUrl());
	}
}
