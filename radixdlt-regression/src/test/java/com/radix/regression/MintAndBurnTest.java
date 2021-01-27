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
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.translate.tokens.BurnTokensAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.MintTokensAction;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import java.math.BigDecimal;
import org.junit.Test;
import com.radixdlt.utils.UInt256;

public class MintAndBurnTest {
	@Test
	public void given_an_account_owner_who_created_a_token__when_the_owner_mints_max_then_burns_max_then_mints_max__then_it_should_all_be_successful() throws Exception {
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), RadixIdentities.createNew());
		TokenUtilities.requestTokensFor(api);
		api.discoverNodes();
		RadixNode originNode = api.getNetworkState()
			.map(RadixNetworkState::getNodes)
			.filter(s -> !s.isEmpty())
			.map(s -> s.iterator().next())
			.firstOrError()
			.blockingGet();
		RRI token = RRI.of(api.getAddress(), "JOSH");

		CreateTokenAction createTokenAction = CreateTokenAction.create(
			token,
			"Joshy Token",
			"Best token",
			BigDecimal.ZERO,
			TokenUnitConversions.subunitsToUnits(UInt256.ONE),
			TokenSupplyType.MUTABLE);
		Result result0 = api.execute(createTokenAction, originNode);
		result0.toObservable().subscribe(System.out::println);
		result0.blockUntilComplete();

		MintTokensAction mintTokensAction = MintTokensAction.create(token, api.getAddress(), TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE));
		Result result1 = api.execute(mintTokensAction, originNode);
		result1.toObservable().subscribe(System.out::println);
		result1.blockUntilComplete();

		BurnTokensAction burnTokensAction = BurnTokensAction.create(token, api.getAddress(), TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE));
		Result result2 = api.execute(burnTokensAction, originNode);
		result2.toObservable().subscribe(System.out::println);
		result2.blockUntilComplete();

		MintTokensAction mintTokensAction2 = MintTokensAction.create(token, api.getAddress(), TokenUnitConversions.subunitsToUnits(UInt256.MAX_VALUE));
		Result result3 = api.execute(mintTokensAction2, originNode);
		result3.toObservable().subscribe(System.out::println);
		result3.blockUntilComplete();
	}
}
