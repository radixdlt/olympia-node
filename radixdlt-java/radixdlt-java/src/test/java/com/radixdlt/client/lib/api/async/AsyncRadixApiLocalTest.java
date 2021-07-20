/* Copyright 2021 Radix DLT Ltd incorporated in England.
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
package com.radixdlt.client.lib.api.async;

import org.junit.Test;

import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.AccountAddressing;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.utils.Ints;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.client.lib.api.token.Amount.amount;

//TODO: remaining tests
public class AsyncRadixApiLocalTest {
	public static final ECKeyPair KEY_PAIR1 = keyPairOf(1);
	public static final ECKeyPair KEY_PAIR2 = keyPairOf(2);
	private static final AccountAddressing ACCOUNTS = Addressing.ofNetwork(Network.LOCALNET).forAccounts();

	private static final AccountAddress ACCOUNT_ADDRESS1 = AccountAddress.create(KEY_PAIR1.getPublicKey());
	private static final AccountAddress ACCOUNT_ADDRESS2 = AccountAddress.create(KEY_PAIR2.getPublicKey());

	private static final String BASE_URL = "http://localhost/";

	private static final String NETWORK_ID = "{\"result\":{\"networkId\":99},\"id\":\"1\",\"jsonrpc\":\"2.0\"}";
	private static final String ACCOUNT_INFO = "{\"result\":{\"address\":\"ddx1qsprpeqt46q3qqmx56muck5rs9dhuz9a2x9l0g4"
		+ "addup7z2zfm4c3jqurkgjv\",\"balance\":{\"stakes\":[{\"delegate\":\"dv1qgcwgzawsygqxe4xklx94quptdlq302330m690"
		+ "tt0q0sjsjwawyvs6zsklj\",\"amount\":\"1000000000000000000000000000\"}],\"tokens\":[]}},\"id\":\"2\",\"jsonrp"
		+ "c\":\"2.0\"}\n";
	private static final String VALIDATOR_INFO = "{\"result\":{\"owner\":\"ddx1qsprpeqt46q3qqmx56muck5rs9dhuz9a2x9l0g4"
		+ "addup7z2zfm4c3jqurkgjv\",\"validatorFee\":0,\"address\":\"dv1qgcwgzawsygqxe4xklx94quptdlq302330m690tt0q0s"
		+ "jsjwawyvs6zsklj\",\"stakes\":[{\"amount\":\"1000000000000000000000000000\",\"delegator\":\"ddx1qsprpeqt46q3"
		+ "qqmx56muck5rs9dhuz9a2x9l0g4addup7z2zfm4c3jqurkgjv\"}],\"allowDelegation\":true,\"name\":\"\",\"registered\""
		+ ":true,\"totalStake\":\"1000000000000000000000000000\",\"url\":\"\"},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";
	private static final String NEXT_EPOCH = "{\"result\":{\"validators\":[{\"totalDelegatedStake\":\"1000000000000000"
		+ "000000000000\",\"validatorFee\":0,\"address\":\"dv1qtjlayqkvk234cwh5rs72uunjwgte8gnr4gp6vvgqmmdjl9fjvr5ul"
		+ "nv4zg\",\"infoURL\":\"\",\"ownerDelegation\":\"1000000000000000000000000000\",\"name\":\"\",\"registered\":"
		+ "true,\"ownerAddress\":\"ddx1qspwtl5szeje2xhp67swretnjwfep0yazvw4q8f33qr0dktu4xfswnsjjpvcy\",\"isExternalSta"
		+ "keAccepted\":true},{\"totalDelegatedStake\":\"1000000000000000000000000000\",\"validatorFee\":0,\"address"
		+ "\":\"dv1qgcwgzawsygqxe4xklx94quptdlq302330m690tt0q0sjsjwawyvs6zsklj\",\"infoURL\":\"\",\"ownerDelegation\":"
		+ "\"1000000000000000000000000000\",\"name\":\"\",\"registered\":true,\"ownerAddress\":\"ddx1qsprpeqt46q3qqmx5"
		+ "6muck5rs9dhuz9a2x9l0g4addup7z2zfm4c3jqurkgjv\",\"isExternalStakeAccepted\":true}]},\"id\":\"2\",\"jsonrpc\""
		+ ":\"2.0\"}\n";

	private static final String
		SINGLE_STEP =
		"{\"result\":{\"txID\":\"c4741a62a721885dc3523afbf0297011671d8ce8969885bc0f6a6ffde9e39235\"},\"id\":\"6\",\"jsonrpc\":\"2.0\"}";
	private static final String
		BUILD_TRANSACTION =
		"{\"result\":{\"fee\":\"74200000000000000\",\"transaction\":{\"blob\":\"06407074cfe7b33d7e01c317eee743d33a9523"
			+ "60eb1c7ae64ab9caeb8d975329b30000000501210000000000000000000000000000000000000000000000000001079c81c2558"
			+ "000020500040279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f8179801000000000000000000000000"
			+ "0000000000000000033b2e3c9ec8e3bb25aa8000000700000000020500040279be667ef9dcbbac55a06295ce870b07029bfcdb2"
			+ "dce28d959f2815b16f81798010000000000000000000000000000000000000000033b2e373301858dc29a80000205000403fff9"
			+ "7bd5755eeea420453a14355235d382f6472f8568a18b2f057a14602975560100000000000000000000000000000000000000000"
			+ "00000056bc75e2d63100000000b0e54657374206d6573736167652031\",\"hashOfBlobToSign\":\"46a20c3ddd56a0fbac76"
			+ "22c52f26753ffacc5c5bf243f901c7210394c8d55198\"}},\"id\":\"3\",\"jsonrpc\":\"2.0\"}";
	private static final String
		FINALIZE_TRANSACTION =
		"{\"result\":{\"blob\":\"06407074cfe7b33d7e01c317eee743d33a952360eb1c7ae64ab9caeb8d975329b30000000501210000000"
			+ "000000000000000000000000000000000000000000001079c81c2558000020500040279be667ef9dcbbac55a06295ce870b0702"
			+ "9bfcdb2dce28d959f2815b16f81798010000000000000000000000000000000000000000033b2e3c9ec8e3bb25aa80000007000"
			+ "00000020500040279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798010000000000000000000000"
			+ "000000000000000000033b2e373301858dc29a80000205000403fff97bd5755eeea420453a14355235d382f6472f8568a18b2f0"
			+ "57a1460297556010000000000000000000000000000000000000000000000056bc75e2d63100000000b0e54657374206d657373"
			+ "61676520310a00c07adf9012c81fed4205f14b7d7756808fecbf4615e39ad5b74c97057c532fb0006e798ed8aa457afa82908c0"
			+ "492d6e086d105374623b1ae430be39b4dd6bc96\",\"txID\":\"b3b2c41c08b4b93d533c824b015f6e11e3370f1aeafb0116ee"
			+ "44aa3f4f442f37\"},\"id\":\"4\",\"jsonrpc\":\"2.0\"}";

	//TODO: add tests for current epoch request
	private static final String CURRENT_EPOCH = "";

	private final HttpClient client = mock(HttpClient.class);

	@Test
	public void testAccountInfo() throws IOException {
		var accountAddress = AccountAddress.create(ACCOUNTS.parse("ddx1qsprpeqt46q3qqmx56muck5rs9dhuz9a2x9l0g4addup7z2zfm4c3jqurkgjv"));

		prepareClient(ACCOUNT_INFO)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.local().accountInfo().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(localAccount -> assertEquals(accountAddress, localAccount.getAddress()))
				.onSuccess(localAccount -> assertEquals(0, localAccount.getBalance().getTokens().size())));
	}

	@Test
	public void testValidatorInfo() throws IOException {
		prepareClient(VALIDATOR_INFO)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.local().validatorInfo().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(localValidatorInfo -> assertEquals(1, localValidatorInfo.getStakes().size()))
				.onSuccess(localValidatorInfo -> assertTrue(localValidatorInfo.isRegistered())));
	}

	@Test
	public void testNextEpoch() throws IOException {
		prepareClient(NEXT_EPOCH)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.local().nextEpoch().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(epochData -> assertEquals(2, epochData.getValidators().size())));
	}

	@Test
	public void testSubmitTxSingleStep() throws IOException {
		prepareClient(ACCOUNT_INFO, BUILD_TRANSACTION, FINALIZE_TRANSACTION, ACCOUNT_INFO, SINGLE_STEP)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.local().accountInfo().join().onSuccess(account -> transferFunds(client, account.getAddress())))
			.onSuccess(client -> client.local().accountInfo().join()
				.map(account -> TransactionRequest.createBuilder(account.getAddress())
					.transfer(account.getAddress(), ACCOUNT_ADDRESS2, amount(5).tokens(), "xrd_dr1qyrs8qwl")
					.message("Test message 2")
					.build())
				.flatMap(request -> client.local().submitTxSingleStep(request).join()
					.onFailure(failure -> fail(failure.toString()))
					.onSuccess(txData -> assertNotNull(txData.getTxId()))));
	}

	private void transferFunds(RadixApi client, AccountAddress address) {
		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
			.transfer(
				ACCOUNT_ADDRESS1,
				address,
				amount(100).tokens(),
				"xrd_dr1qyrs8qwl"
			)
			.message("Test message 1")
			.build();

		client.transaction().build(request).join()
			.onFailure(failure -> fail(failure.toString()))
			.map(builtTransactionDTO -> builtTransactionDTO.toFinalized(KEY_PAIR1))
			.flatMap(finalizedTransaction -> client.transaction().finalize(finalizedTransaction, true).join())
			.onSuccess(txDTO -> assertNotNull(txDTO.getTxId()));
	}

	private Promise<RadixApi> prepareClient(String... responseBodies) throws IOException {
		@SuppressWarnings("unchecked")
		var response = (HttpResponse<String>) mock(HttpResponse.class);
		var completableFuture = new CompletableFuture<HttpResponse<String>>();

		when(response.body()).thenReturn(NETWORK_ID, responseBodies);
		when(client.<String>sendAsync(any(), any())).thenReturn(completableFuture);

		completableFuture.completeAsync(() -> response);
		return AsyncRadixApi.connect(BASE_URL, RadixApi.DEFAULT_PRIMARY_PORT, RadixApi.DEFAULT_SECONDARY_PORT, client);
	}


	private static ECKeyPair keyPairOf(int pk) {
		var privateKey = new byte[ECKeyPair.BYTES];

		Ints.copyTo(pk, privateKey, ECKeyPair.BYTES - Integer.BYTES);

		try {
			return ECKeyPair.fromPrivateKey(privateKey);
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalArgumentException("Error while generating public key", e);
		}
	}
}
