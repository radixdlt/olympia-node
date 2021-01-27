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

import org.junit.Test;

import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.identifiers.RRI;
import java.math.BigDecimal;

public class CreateTokenWithoutDescriptionTest {

	@Test
	public void createMultiIssuanceTokenWithoutDescription() {
		RadixIdentity testIdentity = RadixIdentities.createNew();
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), testIdentity);
		TokenUtilities.requestTokensFor(api);
		RRI tokenRRI1 = RRI.of(api.getAddress(), "TESTTOKEN1");
		api.createMultiIssuanceToken(tokenRRI1, "TESTTOKEN1").blockUntilComplete();
		RRI tokenRRI2 = RRI.of(api.getAddress(), "TESTTOKEN2");
		api.createMultiIssuanceToken(tokenRRI2, "TESTTOKEN2", null).blockUntilComplete();
	}

	@Test
	public void createFixedIssuanceTokenWithoutDescription() {
		RadixIdentity testIdentity = RadixIdentities.createNew();
		RadixApplicationAPI api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), testIdentity);
		TokenUtilities.requestTokensFor(api);
		RRI tokenRRI1 = RRI.of(api.getAddress(), "TESTTOKEN3XX");
		api.createFixedSupplyToken(tokenRRI1, "TESTTOKEN3XX", null, BigDecimal.ONE).blockUntilComplete();
	}
}
