/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.api.service;

import com.radixdlt.api.util.ActionParser;
import com.radixdlt.atom.actions.UpdateValidatorFee;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.statecomputer.forks.Forks;
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
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Failure;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ActionParserTest {
	private final REAddr from = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
	private final REAddr to = REAddr.ofPubKeyAccount(ECKeyPair.generateNew().getPublicKey());
	private final REAddr rri = REAddr.ofHashedKey(ECKeyPair.generateNew().getPublicKey(), "ckee");
	private final Addressing addressing = Addressing.ofNetwork(Network.LOCALNET);
	private final Forks forks = mock(Forks.class);
	private final ActionParser actionParser = new ActionParser(addressing, forks);

	@Before
	public void setup() {
		final var reRules = mock(RERules.class);
		when(reRules.getMaxRounds()).thenReturn(View.of(10L));
		when(forks.getCandidateFork()).thenReturn(Optional.empty());
	}

	@Test
	public void transferActionIsParsedCorrectly() {
		var fromAddr = addressing.forAccounts().of(from);
		var toAddr = addressing.forAccounts().of(to);
		var source = "[{\"type\":\"TokenTransfer\", \"from\":\"%s\", \"to\":\"%s\", \"amount\":\"%s\", \"rri\":\"%s\"}]";
		var actions = jsonArray(String.format(source, fromAddr, toAddr, UInt256.SIX, addressing.forResources().of("ckee", rri)));

		actionParser.parse(actions)
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
		var validatorAddr = addressing.forValidators().of(key);
		var fromAddr = addressing.forAccounts().of(from);
		var source = "[{\"type\":\"StakeTokens\", \"from\":\"%s\", \"validator\":\"%s\", \"amount\":\"%s\"}]";
		var actions = jsonArray(String.format(source, fromAddr, validatorAddr, UInt256.NINE));

		actionParser.parse(actions)
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
		var validatorAddr = addressing.forValidators().of(key);
		var accountAddr = addressing.forAccounts().of(from);
		var source = "[{\"type\":\"UnstakeTokens\", \"to\":\"%s\", \"validator\":\"%s\", \"amount\":\"%s\"}]";
		var actions = jsonArray(String.format(source, accountAddr, validatorAddr, UInt256.EIGHT));

		actionParser.parse(actions)
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
		var toAccount = addressing.forAccounts().of(to);

		var source = "[{\"type\":\"MintTokens\", \"to\":\"%s\", \"amount\":\"%s\", \"rri\":\"%s\"}]";
		var actions = jsonArray(String.format(source, toAccount, UInt256.NINE, addressing.forResources().of("ckee", rri)));

		actionParser.parse(actions)
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
		var fromAddr = addressing.forAccounts().of(from);

		var source = "[{\"type\":\"BurnTokens\", \"from\":\"%s\", \"amount\":\"%s\", \"rri\":\"%s\"}]";
		var actions = jsonArray(String.format(source, fromAddr, UInt256.FIVE, addressing.forResources().of("ckee", rri)));

		actionParser.parse(actions)
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
		var validatorAddr = addressing.forValidators().of(key);

		var source = "[{\"type\":\"RegisterValidator\", \"validator\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParser.parse(actions)
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
		var validatorAddr = addressing.forValidators().of(key);

		var source = "[{\"type\":\"RegisterValidator\", \"validator\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParser.parse(actions)
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
		var validatorAddr = addressing.forValidators().of(key);

		var source = "[{\"type\":\"RegisterValidator\", \"validator\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParser.parse(actions)
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
	public void updateValidatorMetadataIsParsedCorrectlyWithUrlAndName() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = addressing.forValidators().of(key);

		var source = "[{\"type\":\"UpdateValidatorMetadata\", \"validator\":\"%s\", \"name\":\"%s\", \"url\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr, "validator 1", "http://localhost/"));

		actionParser.parse(actions)
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
	public void updateValidatorMetadataIsParsedCorrectlyWithUrl() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = addressing.forValidators().of(key);

		var source = "[{\"type\":\"UpdateValidatorMetadata\", \"validator\":\"%s\", \"url\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr, "http://localhost/"));

		actionParser.parse(actions)
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
	public void updateValidatorMetadataIsParsedCorrectly() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = addressing.forValidators().of(key);

		var source = "[{\"type\":\"UpdateValidatorMetadata\", \"validator\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParser.parse(actions)
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
	public void updateValidatorFeeIsParsedCorrectly() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = addressing.forValidators().of(key);

		var source = "[{\"type\":\"UpdateValidatorFee\", \"validator\":\"%s\", \"validatorFee\":\"1.2345\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParser.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(UpdateValidatorFee.class::isInstance)
					.map(UpdateValidatorFee.class::cast)
					.ifPresentOrElse(
						updateValidatorFee -> {
							assertEquals(key, updateValidatorFee.validatorKey());
							assertEquals(123, updateValidatorFee.getFeePercentage());
						},
						Assert::fail
					);
			});
	}

	@Test
	public void updateValidatorFeeIsCheckedForUpperBound() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = addressing.forValidators().of(key);

		var source = "[{\"type\":\"UpdateValidatorFee\", \"validator\":\"%s\", \"validatorFee\":\"100.1\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParser.parse(actions)
			.onSuccessDo(this::failureExpected);
	}

	@Test
	public void updateValidatorFeeIsCheckedForLowerBound() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = addressing.forValidators().of(key);

		var source = "[{\"type\":\"UpdateValidatorFee\", \"validator\":\"%s\", \"validatorFee\":\"-0.01\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParser.parse(actions)
			.onSuccessDo(this::failureExpected);
	}

	@Test
	public void unregisterValidatorIsParsedCorrectlyWithUrlAndName() {
		var key = ECKeyPair.generateNew().getPublicKey();
		var validatorAddr = addressing.forValidators().of(key);

		var source = "[{\"type\":\"UnregisterValidator\", \"validator\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParser.parse(actions)
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
		var validatorAddr = addressing.forValidators().of(key);

		var source = "[{\"type\":\"UnregisterValidator\", \"validator\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParser.parse(actions)
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
		var validatorAddr = addressing.forValidators().of(key);

		var source = "[{\"type\":\"UnregisterValidator\", \"validator\":\"%s\"}]";
		var actions = jsonArray(String.format(source, validatorAddr));

		actionParser.parse(actions)
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
		var fromAddr = addressing.forAccounts().of(from);
		var signer = ECKeyPair.generateNew().getPublicKey();

		var source = "[{\"type\":\"CreateFixedSupplyToken\", \"to\":\"%s\", \"publicKeyOfSigner\":\"%s\", \"symbol\":\"%s\", "
			+ "\"name\":\"%s\", \"description\":\"%s\", \"iconUrl\":\"%s\", \"tokenUrl\":\"%s\", \"supply\":\"%s\"}]";
		var actions = jsonArray(String.format(source, fromAddr, signer.toHex(), "symbol",
											  "name", "description", "http://icon.url/", "http://token.url/", UInt256.TEN
		));

		actionParser.parse(actions)
			.onFailure(this::fail)
			.onSuccess(parsed -> {
				assertEquals(1, parsed.size());

				parsed.get(0).toAction().findAny()
					.filter(CreateFixedToken.class::isInstance)
					.map(CreateFixedToken.class::cast)
					.ifPresentOrElse(
						create -> {
							assertEquals(from, create.getAccountAddr());
							assertEquals(REAddr.ofHashedKey(signer, create.getSymbol()), create.getResourceAddr());
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
		var fromAddr = addressing.forAccounts().of(from);
		var source = "[{\"type\":\"CreateMutableSupplyToken\", \"from\":\"%s\", \"symbol\":\"%s\", \"name\":\"%s\", "
			+ "\"description\":\"%s\", \"iconUrl\":\"%s\", \"tokenUrl\":\"%s\", \"publicKeyOfSigner\":\"%s\"}]";
		var actions = jsonArray(String.format(source, fromAddr, "symbol", "name",
											  "description", "http://icon.url/", "http://token.url/", publicKey.toHex()
		));

		actionParser.parse(actions)
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
		var fromAddr = addressing.forAccounts().of(from);
		var source = "[{\"type\":\"CreateMutableSupplyToken\", \"from\":\"%s\",  \"symbol\":\"%s\", \"name\":\"%s\", \"publicKeyOfSigner\":\"%s\"}]";
		var actions = jsonArray(String.format(source, fromAddr, "symbol", "name", publicKey.toHex()));

		actionParser.parse(actions)
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

		actionParser.parse(actions)
			.onFailure(System.out::println)
			.onSuccess(v -> Assert.fail("Operation succeeded, while failure is expected"));
	}

	@Test
	public void invalidAddressIsRejected() {
		var source = "[{\"type\":\"TokenTransfer\", \"from\":\"abc%s\", \"to\":\"%s\", \"amount\":\"%s\", \"rri\":\"%s\"}]";
		var actions = jsonArray(String.format(source, from, to, UInt256.NINE, rri));

		actionParser.parse(actions)
			.onFailure(System.out::println)
			.onSuccess(v -> Assert.fail("Operation succeeded, while failure is expected"));
	}

	private static JSONArray jsonArray(String format) {
		return new JSONArray(format);
	}

	private void fail(Failure failure) {
		Assert.fail(failure.message());
	}

	private void failureExpected() {
		Assert.fail("Expected failure result, got success instead");
	}
}
