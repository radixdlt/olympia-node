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

package com.radixdlt.client.handler;

import org.junit.Assert;
import org.junit.Test;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Failure;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonArray;

import static com.radixdlt.client.api.ActionType.STAKE;
import static com.radixdlt.client.api.ActionType.TRANSFER;
import static com.radixdlt.client.api.ActionType.UNSTAKE;

public class ActionParserTest {
	private static final byte MAGIC = (byte) 0;
	private final RadixAddress from = new RadixAddress(MAGIC, ECKeyPair.generateNew().getPublicKey());
	private final RadixAddress to = new RadixAddress(MAGIC, ECKeyPair.generateNew().getPublicKey());
	private final RRI rri = RRI.of(new RadixAddress(MAGIC, ECKeyPair.generateNew().getPublicKey()), "COOKIE");

	@Test
	public void transferActionIsParsedCorrectly() {
		var source = "[{\"type\":\"TokenTransfer\", \"from\":\"%s\", \"to\":\"%s\", \"amount\":\"%s\", \"rri\":\"%s\"}]";
		var actions = jsonArray(String.format(source, from, to, UInt256.NINE, rri)).orElseThrow();

		ActionParser.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0)
					.map((type, fromAddress, toAddress, amount, rriOptional) -> {
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
		var source = "[{\"type\":\"StakeTokens\", \"from\":\"%s\", \"validator\":\"%s\", \"amount\":\"%s\"}]";
		var actions = jsonArray(String.format(source, from, to, UInt256.NINE)).orElseThrow();

		ActionParser.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0)
					.map((type, fromAddress, validatorAddress, amount, rriOptional) -> {
						assertEquals(STAKE, type);
						assertEquals(from, fromAddress);
						assertEquals(to, validatorAddress);
						assertEquals(amount, UInt256.NINE);
						return null;
					});
			});
	}

	@Test
	public void unstakeActionIsParsedCorrectly() {
		var source = "[{\"type\":\"UnstakeTokens\", \"from\":\"%s\", \"validator\":\"%s\", \"amount\":\"%s\"}]";
		var actions = jsonArray(String.format(source, from, to, UInt256.NINE)).orElseThrow();

		ActionParser.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0)
					.map((type, fromAddress, validatorAddress, amount, rriOptional) -> {
						assertEquals(UNSTAKE, type);
						assertEquals(from, fromAddress);
						assertEquals(to, validatorAddress);
						assertEquals(amount, UInt256.NINE);
						return null;
					});
			});
	}

	@Test
	public void unsupportedActionTypeIsRejected() {
		var source = "[{\"type\":\"MintTokens\", \"from\":\"%s\", \"to\":\"%s\", \"amount\":\"%s\", \"rri\":\"%s\"}]";
		var actions = jsonArray(String.format(source, from, to, UInt256.NINE, rri)).orElseThrow();

		ActionParser.parse(actions)
			.onFailure(System.out::println)
			.onSuccess(v -> Assert.fail("Operation succeeded, while failure is expected"));
	}

	@Test
	public void unknownActionTypeIsRejected() {
		var source = "[{\"type\":\"CreateTokens\", \"from\":\"%s\", \"to\":\"%s\", \"amount\":\"%s\", \"rri\":\"%s\"}]";
		var actions = jsonArray(String.format(source, from, to, UInt256.NINE, rri)).orElseThrow();

		ActionParser.parse(actions)
			.onFailure(System.out::println)
			.onSuccess(v -> Assert.fail("Operation succeeded, while failure is expected"));
	}

	@Test
	public void invalidAddressIsRejected() {
		var source = "[{\"type\":\"TokenTransfer\", \"from\":\"abc%s\", \"to\":\"%s\", \"amount\":\"%s\", \"rri\":\"%s\"}]";
		var actions = jsonArray(String.format(source, from, to, UInt256.NINE, rri)).orElseThrow();

		ActionParser.parse(actions)
			.onFailure(System.out::println)
			.onSuccess(v -> Assert.fail("Operation succeeded, while failure is expected"));
	}

	private void fail(Failure failure) {
		Assert.fail(failure.message());
	}
}
