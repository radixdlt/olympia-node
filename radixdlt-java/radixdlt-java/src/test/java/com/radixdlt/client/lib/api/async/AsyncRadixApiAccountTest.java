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

import org.junit.Ignore;
import org.junit.Test;

import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.dto.TokenBalances;
import com.radixdlt.client.lib.dto.TransactionHistory;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.UInt256;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.radixdlt.client.lib.api.token.Amount.amount;

public class AsyncRadixApiAccountTest {
	private static final String BASE_URL = "http://localhost/";
	public static final ECKeyPair KEY_PAIR1 = keyPairOf(1);
	private static final AccountAddress ACCOUNT_ADDRESS1 = AccountAddress.create(KEY_PAIR1.getPublicKey());

	private static final String NETWORK_ID = "{\"result\":{\"networkId\":99},\"id\":\"1\",\"jsonrpc\":\"2.0\"}";
	private static final String TOKEN_BALANCES = "{\"result\":{\"owner\":\"ddx1qsp8n0nx0muaewav2ksx99wwsu9swq5mlndjmn3gm"
		+ "9vl9q2mzmup0xq904xyj\",\"tokenBalances\":[{\"amount\":\"1000000000000000000000000000\",\"rri\":\"xrd_dr1qyrs8"
		+ "qwl\"}]},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";
	private static final String TX_HISTORY = "{\"result\":{\"cursor\":\"1577836:800000000\",\"transactions\":[{\"fee\":"
		+ "\"0\",\"txID\":\"407074cfe7b33d7e01c317eee743d33a952360eb1c7ae64ab9caeb8d975329b3\",\"sentAt\":\"1970-01-19T"
		+ "06:17:16.800Z\",\"actions\":[{\"type\":\"Other\"},{\"type\":\"Other\"},{\"type\":\"Other\"},{\"type\":\"Othe"
		+ "r\"},{\"type\":\"Other\"},{\"type\":\"Other\"},{\"type\":\"Other\"},{\"type\":\"Other\"},{\"type\":\"Other\""
		+ "},{\"type\":\"Other\"},{\"type\":\"Other\"},{\"type\":\"Other\"},{\"type\":\"Other\"},{\"type\":\"Other\"},{"
		+ "\"amount\":\"100000000000000000000\",\"validator\":\"dv1qfwtmurydewmf64rnrektuh20g8r6svm0cpnpcuuay4ammw2cnum"
		+ "c3jtmxl\",\"from\":\"ddx1qspzsu73jt6ps6g8l0rj2yya2euunqapv7j2qemgaaujyej2tlp3lcs99m6k9\",\"type\":\"StakeTok"
		+ "ens\"},{\"amount\":\"100000000000000000000\",\"validator\":\"dv1q0llj774w40wafpqg5apgd2jxhfc9aj897zk3gvt9uzh"
		+ "59rq9964vjryzf9\",\"from\":\"ddx1qspzsu73jt6ps6g8l0rj2yya2euunqapv7j2qemgaaujyej2tlp3lcs99m6k9\",\"type\":\""
		+ "StakeTokens\"},{\"type\":\"Other\"}]}]},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";
	private static final String ERROR_RESPONSE = "{\"id\":\"2\",\"jsonrpc\":\"2.0\",\"error\":{\"code\":2523,\"data\":"
		+ "[\"0000000000000000000000000000000000000000000000000000000000000000\"],\"message\":\"Transaction with id 00"
		+ "00000000000000000000000000000000000000000000000000000000000000 not found\"}}\n";

	private static final String STAKES_RESPONSE = "{\"result\":[{\"amount\":\"2000000000000000000000\",\"validator\":"
		+ "\"dv1q0llj774w40wafpqg5apgd2jxhfc9aj897zk3gvt9uzh59rq9964vjryzf9\"}],\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";

	private static final String UNSTAKES_RESPONSE = "{\"result\":[{\"amount\":\"100000000000000000000\",\"withdrawTxID\""
		+ ":\"a8b096c07e13080299e1733a654eb60fa45014caf5d0d1d16578e8f1c3680bec\",\"epochsUntil\":147,\"validator\":"
		+ "\"dv1q0llj774w40wafpqg5apgd2jxhfc9aj897zk3gvt9uzh59rq9964vjryzf9\"}],\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";

	private final HttpClient client = mock(HttpClient.class);

	@Test
	public void testTransactionHistory() throws IOException {
		prepareClient(TX_HISTORY)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.account().history(ACCOUNT_ADDRESS1, 5, Optional.empty()).join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(transactionHistoryDTO -> assertNotNull(transactionHistoryDTO.getCursor()))
				.onSuccess(transactionHistoryDTO -> assertNotNull(transactionHistoryDTO.getTransactions()))
				.map(TransactionHistory::getTransactions)
				.onSuccess(txs -> assertEquals(1, txs.size()))
				.map(txs -> txs.get(0).getActions())
				.onSuccess(actions -> assertEquals(17, actions.size())));
	}

	@Test
	public void testTokenBalances() throws IOException {
		prepareClient(TOKEN_BALANCES)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.account().balances(ACCOUNT_ADDRESS1).join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(tokenBalancesDTO -> assertEquals(ACCOUNT_ADDRESS1, tokenBalancesDTO.getOwner()))
				.map(TokenBalances::getTokenBalances)
				.onSuccess(balances -> assertEquals(1, balances.size())));
	}

	@Test
	public void testErrorResponse() throws IOException {
		prepareClient(ERROR_RESPONSE)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.transaction().lookup(AID.ZERO).join()
				.onFailure(failure -> assertEquals(2523, failure.code()))
				.onSuccess(__ -> fail()));
	}

	@Test
	public void listStakes() throws IOException {
		prepareClient(STAKES_RESPONSE)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.account().stakes(ACCOUNT_ADDRESS1).join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(stakePositionsDTOS -> assertEquals(1, stakePositionsDTOS.size()))
				.onSuccess(stakePositionsDTOS -> assertEquals(amount(2000).tokens(), stakePositionsDTOS.get(0).getAmount())));
	}

	@Test
	public void listUnStakes() throws IOException {
		prepareClient(UNSTAKES_RESPONSE)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.account().unstakes(ACCOUNT_ADDRESS1).join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(unstakePositionsDTOS -> assertEquals(1, unstakePositionsDTOS.size()))
				.onSuccess(unstakePositionsDTOS -> assertEquals(amount(100).tokens(), unstakePositionsDTOS.get(0).getAmount())));
	}

	@Test
	@Ignore
	public void makeStake() {
		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> makeStake(client, amount(1000).tokens()));
	}

	@Test
	@Ignore
	public void makeUnStake() {
		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> makeUnStake(client, amount(100).tokens()));
	}

	@Test
	@Ignore
	public void transferUnStake() {
		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> transferUnStake(client, amount(100).tokens()));
	}

	private void transferUnStake(RadixApi client, UInt256 amount) {
		client.local().accountInfo()
			.map(account -> TransactionRequest.createBuilder(account.getAddress())
				.transfer(account.getAddress(), ACCOUNT_ADDRESS1, amount, "xrd_dr1qyrs8qwl")
				.build())
			.flatMap(request -> client.local().submitTxSingleStep(request)
				.onFailure(failure -> fail(failure.toString())))
			.join();
	}

	private void makeStake(RadixApi client, UInt256 amount) {
		client.local().validatorInfo()
			.map(account -> TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
				.stake(ACCOUNT_ADDRESS1, account.getAddress(), amount)
				.build())
			.flatMap(request -> client.transaction().build(request)
				.onFailure(failure -> fail(failure.toString()))
				.map(builtTransaction -> builtTransaction.toFinalized(KEY_PAIR1))
				.onSuccess(transaction -> client.transaction().finalize(transaction, true)))
			.join();
	}

	private void makeUnStake(RadixApi client, UInt256 amount) {
		client.local().validatorInfo()
			.map(account -> TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
				.unstake(ACCOUNT_ADDRESS1, account.getAddress(), amount)
				.build())
			.flatMap(request -> client.transaction().build(request)
				.onFailure(failure -> fail(failure.toString()))
				.map(builtTransaction -> builtTransaction.toFinalized(KEY_PAIR1))
				.onSuccess(transaction -> client.transaction().finalize(transaction, true)))
			.join();
	}

	private Promise<RadixApi> prepareClient(String responseBody) throws IOException {
		@SuppressWarnings("unchecked")
		var response = (HttpResponse<String>) mock(HttpResponse.class);
		var completableFuture = new CompletableFuture<HttpResponse<String>>();

		when(response.body()).thenReturn(NETWORK_ID, responseBody);
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
