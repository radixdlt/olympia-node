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

package com.radixdlt.api.service;

import com.radixdlt.identifiers.AccountAddress;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.utils.functional.Result;

import org.json.JSONArray;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Failure;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.api.data.ActionType.STAKE;
import static com.radixdlt.api.data.ActionType.TRANSFER;
import static com.radixdlt.api.data.ActionType.UNSTAKE;

public class ActionParserServiceTest {
	private final REAddr from = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
	private final REAddr to = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
	private final REAddr rri = REAddr.ofHashedKey(ECKeyPair.generateNew().getPublicKey(), "ckee");
	private final ClientApiStore clientApiStore = mock(ClientApiStore.class);
	private ActionParserService actionParserService = new ActionParserService(clientApiStore);

	@Before
	public void setup() {
		when(clientApiStore.parseRri(any())).thenReturn(Result.ok(rri));
	}

	@Test
	public void transferActionIsParsedCorrectly() {
		var fromAddr = AccountAddress.of(from);
		var toAddr = AccountAddress.of(to);
		var source = "[{\"type\":\"TokenTransfer\", \"from\":\"%s\", \"to\":\"%s\", \"amount\":\"%s\", \"rri\":\"%s\"}]";
		var actions = jsonArray(String.format(source, fromAddr, toAddr, UInt256.NINE, rri));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0)
					.map((type, fromAddress, toAddress, validator, amount, rriOptional) -> {
						assertEquals(TRANSFER, type);
						assertEquals(from, fromAddress);
						assertEquals(to, toAddress);
						assertEquals(amount, UInt256.NINE);
						assertEquals(rriOptional, Optional.of(rri));
						return null;
					});
			});
	}

	@Test
	public void stakeActionIsParsedCorrectly() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = ValidatorAddress.of(key);
		var fromAddr = AccountAddress.of(from);
		var source = "[{\"type\":\"StakeTokens\", \"from\":\"%s\", \"validator\":\"%s\", \"amount\":\"%s\"}]";
		var actions = jsonArray(String.format(source, fromAddr, validatorAddr, UInt256.NINE));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0)
					.map((type, fromAddress, to, validator, amount, rriOptional) -> {
						assertEquals(STAKE, type);
						assertEquals(from, fromAddress);
						assertEquals(key, validator);
						assertEquals(amount, UInt256.NINE);
						return null;
					});
			});
	}

	@Test
	public void unstakeActionIsParsedCorrectly() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = ValidatorAddress.of(key);
		var accountAddr = AccountAddress.of(from);
		var source = "[{\"type\":\"UnstakeTokens\", \"from\":\"%s\", \"validator\":\"%s\", \"amount\":\"%s\"}]";
		var actions = jsonArray(String.format(source, accountAddr, validatorAddr, UInt256.NINE));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0)
					.map((type, fromAddress, toAddress, validator, amount, rriOptional) -> {
						assertEquals(UNSTAKE, type);
						assertEquals(from, fromAddress);
						assertEquals(key, validator);
						assertEquals(amount, UInt256.NINE);
						return null;
					});
			});
	}

	@Test
	public void unsupportedActionTypeIsRejected() {
		var fromAccount = AccountAddress.of(from);
		var toAccount = AccountAddress.of(to);

		var source = "[{\"type\":\"MintTokens\", \"from\":\"%s\", \"to\":\"%s\", \"amount\":\"%s\", \"rri\":\"%s\"}]";
		var actions = jsonArray(String.format(source, fromAccount, toAccount, UInt256.NINE, rri));

		actionParserService.parse(actions)
			.onFailure(System.out::println)
			.onSuccess(v -> Assert.fail("Operation succeeded, while failure is expected"));
	}

	@Test
	public void unknownActionTypeIsRejected() {
		var source = "[{\"type\":\"CreateTokens\", \"from\":\"%s\", \"to\":\"%s\", \"amount\":\"%s\", \"rri\":\"%s\"}]";
		var actions = jsonArray(String.format(source, from, to, UInt256.NINE, rri));

		actionParserService.parse(actions)
			.onFailure(System.out::println)
			.onSuccess(v -> Assert.fail("Operation succeeded, while failure is expected"));
	}

	@Test
	public void invalidAddressIsRejected() {
		var source = "[{\"type\":\"TokenTransfer\", \"from\":\"abc%s\", \"to\":\"%s\", \"amount\":\"%s\", \"rri\":\"%s\"}]";
		var actions = jsonArray(String.format(source, from, to, UInt256.NINE, rri));

		actionParserService.parse(actions)
			.onFailure(System.out::println)
			.onSuccess(v -> Assert.fail("Operation succeeded, while failure is expected"));
	}

	private static JSONArray jsonArray(String format) {
		return new JSONArray(format);
	}

	private void fail(Failure failure) {
		Assert.fail(failure.message());
	}
}
