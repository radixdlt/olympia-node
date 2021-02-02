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

package com.radix.acceptance.balance;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.radix.test.utils.TokenUtilities;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.LocalRadixIdentity;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.identifiers.RRI;

import static org.junit.Assert.assertTrue;

import io.reactivex.disposables.Disposable;

public class MultipleTransfers {

	@Test
	public void testBalanceWithMultipleInputs() throws InterruptedException {
		LocalRadixIdentity identity1 = RadixIdentities.createNew();
		LocalRadixIdentity identity2 = RadixIdentities.createNew();
		RadixApplicationAPI api1 = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), identity1);
		TokenUtilities.requestTokensFor(api1);
		RRI tokenRRI = RRI.of(api1.getAddress(), "MBALANCETEST");
		api1.createFixedSupplyToken(tokenRRI, "MBALANCETEST", "TEST", new BigDecimal(100000)).blockUntilComplete();
		Disposable d1 = api1.pull();
		RadixApplicationAPI api2 = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), identity2);
		TokenUtilities.requestTokensFor(api2);
		Disposable d2 = api2.pull();
		try {
			CountDownLatch cld3 = new CountDownLatch(1);
			api2.observeBalance(api2.getAddress(), tokenRRI).subscribe(b -> {
				if (b.intValue() == 3) {
					cld3.countDown();
				}
			});
			api1.sendTokens(tokenRRI, api2.getAddress(), new BigDecimal(1)).blockUntilComplete();
			api1.sendTokens(tokenRRI, api2.getAddress(), new BigDecimal(2)).blockUntilComplete();
			assertTrue(cld3.await(10, TimeUnit.SECONDS));

			CountDownLatch cld0 = new CountDownLatch(1);
			api2.observeBalance(api2.getAddress(), tokenRRI).subscribe(b -> {
				if (b.intValue() == 0) {
					cld0.countDown();
				}
			});
			api2.sendTokens(tokenRRI, api1.getAddress(), new BigDecimal(3)).blockUntilComplete();
			assertTrue(cld0.await(10, TimeUnit.SECONDS));
		} finally {
			d1.dispose();
			d2.dispose();
		}
	}
}
