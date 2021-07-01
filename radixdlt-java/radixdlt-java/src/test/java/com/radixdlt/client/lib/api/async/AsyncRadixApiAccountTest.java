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
	public static final ECKeyPair KEY_PAIR2 = keyPairOf(2);
	private static final AccountAddress ACCOUNT_ADDRESS1 = AccountAddress.create(KEY_PAIR1.getPublicKey());
	private static final AccountAddress ACCOUNT_ADDRESS2 = AccountAddress.create(KEY_PAIR2.getPublicKey());

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

		try {
			return AsyncRadixApi.connect(BASE_URL, RadixApi.DEFAULT_PRIMARY_PORT, RadixApi.DEFAULT_SECONDARY_PORT, client);
		} finally {
			completableFuture.completeAsync(() -> response);
		}
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
