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

import com.radixdlt.consensus.bft.View;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.ForkManager;
import com.radixdlt.statecomputer.forks.RERules;
import org.json.JSONArray;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.atom.actions.UnregisterValidator;
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atom.actions.UpdateValidatorMetadata;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.AccountAddress;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ActionParserServiceTest {
	private final REAddr from = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
	private final REAddr to = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
	private final REAddr rri = REAddr.ofHashedKey(ECKeyPair.generateNew().getPublicKey(), "ckee");
	private final RriParser rriParser = mock(RriParser.class);
	private final ForkManager forkManager = mock(ForkManager.class);
	private final ActionParserService actionParserService = new ActionParserService(rriParser, forkManager);

	@Before
	public void setup() {
		when(rriParser.parse(any())).thenReturn(Result.ok(rri));

		final var reRules = mock(RERules.class);
		when(reRules.getMaxRounds()).thenReturn(View.of(10L));
		final var forkConfig = new ForkConfig("fork1", null, reRules);
		when(forkManager.latestKnownFork()).thenReturn(forkConfig);
	}

	@Test
	public void transferActionIsParsedCorrectly() {
		var fromAddr = AccountAddress.of(from);
		var toAddr = AccountAddress.of(to);
		var source = "[{\"type\":\"TokenTransfer\", \"from\":\"%s\", \"to\":\"%s\", \"amount\":\"%s\", \"rri\":\"%s\"}]";
		var actions = jsonArray(String.format(source, fromAddr, toAddr, UInt256.SIX, rri));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(TransferToken.class::isInstance)
					.map(TransferToken.class::cast)
					.ifPresentOrElse(
						transfer -> {
							assertEquals(UInt256.SIX, transfer.amount());
							assertEquals(rri, transfer.resourceAddr());
							assertEquals(from, transfer.from());
							assertEquals(to, transfer.to());
						},
						Assert::fail
					);
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

				parsed.get(0).toAction().findAny()
					.filter(StakeTokens.class::isInstance)
					.map(StakeTokens.class::cast)
					.ifPresentOrElse(
						stake -> {
							assertEquals(UInt256.NINE, stake.amount());
							assertEquals(from, stake.from());
							assertEquals(key, stake.to());
						},
						Assert::fail
					);
			});
	}

	@Test
	public void unstakeActionIsParsedCorrectly() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = ValidatorAddress.of(key);
		var accountAddr = AccountAddress.of(from);
		var source = "[{\"type\":\"UnstakeTokens\", \"from\":\"%s\", \"validator\":\"%s\", \"amount\":\"%s\"}]";
		var actions = jsonArray(String.format(source, accountAddr, validatorAddr, UInt256.EIGHT));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(UnstakeTokens.class::isInstance)
					.map(UnstakeTokens.class::cast)
					.ifPresentOrElse(
						unstake -> {
							assertEquals(UInt256.EIGHT, unstake.amount());
							assertEquals(from, unstake.accountAddr());
							assertEquals(key, unstake.from());
						},
						Assert::fail
					);
			});
	}

	@Test
	public void mintTokensIsParsedCorrectly() {
		var toAccount = AccountAddress.of(to);

		var source = "[{\"type\":\"MintTokens\", \"to\":\"%s\", \"amount\":\"%s\", \"rri\":\"%s\"}]";
		var actions = jsonArray(String.format(source, toAccount, UInt256.NINE, rri));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(MintToken.class::isInstance)
					.map(MintToken.class::cast)
					.ifPresentOrElse(
						mint -> {
							assertEquals(UInt256.NINE, mint.amount());
							assertEquals(to, mint.to());
							assertEquals(rri, mint.resourceAddr());
						},
						Assert::fail
					);
			});
	}

	@Test
	public void burnTokensIsParsedCorrectly() {
		var fromAddr = AccountAddress.of(from);

		var source = "[{\"type\":\"BurnTokens\", \"from\":\"%s\", \"amount\":\"%s\", \"rri\":\"%s\"}]";
		var actions = jsonArray(String.format(source, fromAddr, UInt256.FIVE, rri));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(BurnToken.class::isInstance)
					.map(BurnToken.class::cast)
					.ifPresentOrElse(
						mint -> {
							assertEquals(UInt256.FIVE, mint.amount());
							assertEquals(from, mint.from());
							assertEquals(rri, mint.resourceAddr());
						},
						Assert::fail
					);
			});
	}

	@Test
	public void registerValidatorIsParsedCorrectlyWithUrlAndName() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = ValidatorAddress.of(key);

		var source = "[{\"type\":\"RegisterValidator\", \"validator\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(RegisterValidator.class::isInstance)
					.map(RegisterValidator.class::cast)
					.ifPresentOrElse(
						register -> {
							assertEquals(key, register.validatorKey());
						},
						Assert::fail
					);
			});
	}

	@Test
	public void registerValidatorIsParsedCorrectlyWithUrl() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = ValidatorAddress.of(key);

		var source = "[{\"type\":\"RegisterValidator\", \"validator\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(RegisterValidator.class::isInstance)
					.map(RegisterValidator.class::cast)
					.ifPresentOrElse(
						register -> {
							assertEquals(key, register.validatorKey());
						},
						Assert::fail
					);
			});
	}

	@Test
	public void registerValidatorIsParsedCorrectly() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = ValidatorAddress.of(key);

		var source = "[{\"type\":\"RegisterValidator\", \"validator\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(RegisterValidator.class::isInstance)
					.map(RegisterValidator.class::cast)
					.ifPresentOrElse(
						register -> {
							assertEquals(key, register.validatorKey());
						},
						Assert::fail
					);
			});
	}

	@Test
	public void updateValidatorIsParsedCorrectlyWithUrlAndName() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = ValidatorAddress.of(key);

		var source = "[{\"type\":\"UpdateValidator\", \"validator\":\"%s\", \"name\":\"%s\", \"url\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr, "validator 1", "http://localhost/"));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(UpdateValidatorMetadata.class::isInstance)
					.map(UpdateValidatorMetadata.class::cast)
					.ifPresentOrElse(
						register -> {
							assertEquals("validator 1", register.name());
							assertEquals("http://localhost/", register.url());
							assertEquals(key, register.validatorKey());
						},
						Assert::fail
					);
			});
	}

	@Test
	public void updateValidatorIsParsedCorrectlyWithUrl() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = ValidatorAddress.of(key);

		var source = "[{\"type\":\"UpdateValidator\", \"validator\":\"%s\", \"url\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr, "http://localhost/"));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(UpdateValidatorMetadata.class::isInstance)
					.map(UpdateValidatorMetadata.class::cast)
					.ifPresentOrElse(
						register -> {
							assertNull(register.name());
							assertEquals("http://localhost/", register.url());
							assertEquals(key, register.validatorKey());
						},
						Assert::fail
					);
			});
	}

	@Test
	public void updateValidatorIsParsedCorrectly() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = ValidatorAddress.of(key);

		var source = "[{\"type\":\"UpdateValidator\", \"validator\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(UpdateValidatorMetadata.class::isInstance)
					.map(UpdateValidatorMetadata.class::cast)
					.ifPresentOrElse(
						register -> {
							assertNull(register.name());
							assertNull(register.url());
							assertEquals(key, register.validatorKey());
						},
						Assert::fail
					);
			});
	}

	@Test
	public void unregisterValidatorIsParsedCorrectlyWithUrlAndName() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = ValidatorAddress.of(key);

		var source = "[{\"type\":\"UnregisterValidator\", \"validator\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(UnregisterValidator.class::isInstance)
					.map(UnregisterValidator.class::cast)
					.ifPresentOrElse(
						register -> {
							assertEquals(key, register.validatorKey());
						},
						Assert::fail
					);
			});
	}

	@Test
	public void unregisterValidatorIsParsedCorrectlyWithUrl() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = ValidatorAddress.of(key);

		var source = "[{\"type\":\"UnregisterValidator\", \"validator\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(UnregisterValidator.class::isInstance)
					.map(UnregisterValidator.class::cast)
					.ifPresentOrElse(
						register -> {
							assertEquals(key, register.validatorKey());
						},
						Assert::fail
					);
			});
	}

	@Test
	public void unregisterValidatorIsParsedCorrectly() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = ValidatorAddress.of(key);

		var source = "[{\"type\":\"UnregisterValidator\", \"validator\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(UnregisterValidator.class::isInstance)
					.map(UnregisterValidator.class::cast)
					.ifPresentOrElse(
						register -> {
							assertEquals(key, register.validatorKey());
						},
						Assert::fail
					);
			});
	}

	@Test
	public void createFixedTokenIsParsedCorrectly() {
		var fromAddr = AccountAddress.of(from);
		var signer = ECKeyPair.generateNew().getPublicKey();

		var source = "[{\"type\":\"CreateFixedSupplyToken\", \"from\":\"%s\", \"publicKeyOfSigner\":\"%s\", \"symbol\":\"%s\", "
			+ "\"name\":\"%s\", \"description\":\"%s\", \"iconUrl\":\"%s\", \"tokenUrl\":\"%s\", \"supply\":\"%s\"}]";
		var actions = jsonArray(String.format(source, fromAddr, signer.toHex(), "symbol",
											  "name", "description", "http://icon.url/", "http://token.url/", UInt256.TEN
		));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(CreateFixedToken.class::isInstance)
					.map(CreateFixedToken.class::cast)
					.ifPresentOrElse(
						create -> {
							assertEquals(from, create.getAccountAddr());
							assertEquals(REAddr.ofPubKeyAccount(signer), create.getResourceAddr());
							assertEquals("symbol", create.getSymbol());
							assertEquals("name", create.getName());
							assertEquals("description", create.getDescription());
							assertEquals("http://icon.url/", create.getIconUrl());
							assertEquals("http://token.url/", create.getTokenUrl());
							assertEquals(UInt256.TEN, create.getSupply());
						},
						Assert::fail
					);
			});
	}

	@Test
	public void createMutableTokenIsParsedCorrectlyWithOptionalElements() {
		var publicKey = ECKeyPair.generateNew().getPublicKey();
		var fromAddr = AccountAddress.of(from);
		var source = "[{\"type\":\"CreateMutableSupplyToken\", \"from\":\"%s\", \"symbol\":\"%s\", \"name\":\"%s\", "
			+ "\"description\":\"%s\", \"iconUrl\":\"%s\", \"tokenUrl\":\"%s\", \"publicKeyOfSigner\":\"%s\"}]";
		var actions = jsonArray(String.format(source, fromAddr, "symbol", "name",
											  "description", "http://icon.url/", "http://token.url/", publicKey.toHex()
		));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(CreateMutableToken.class::isInstance)
					.map(CreateMutableToken.class::cast)
					.ifPresentOrElse(
						create -> {
							assertEquals("symbol", create.getSymbol());
							assertEquals("name", create.getName());
							assertEquals("description", create.getDescription());
							assertEquals("http://icon.url/", create.getIconUrl());
							assertEquals("http://token.url/", create.getTokenUrl());
						},
						Assert::fail
					);
			});
	}

	@Test
	public void createMutableTokenIsParsedCorrectlyWithoutOptionalElements() {
		var publicKey = ECKeyPair.generateNew().getPublicKey();
		var fromAddr = AccountAddress.of(from);
		var source = "[{\"type\":\"CreateMutableSupplyToken\", \"from\":\"%s\",  \"symbol\":\"%s\", \"name\":\"%s\", \"publicKeyOfSigner\":\"%s\"}]";
		var actions = jsonArray(String.format(source, fromAddr, "symbol", "name", publicKey.toHex()));

		actionParserService.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(CreateMutableToken.class::isInstance)
					.map(CreateMutableToken.class::cast)
					.ifPresentOrElse(
						create -> {
							assertEquals("symbol", create.getSymbol());
							assertEquals("name", create.getName());
							assertEquals("", create.getDescription());
							assertEquals("", create.getIconUrl());
							assertEquals("", create.getTokenUrl());
						},
						Assert::fail
					);
			});
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
