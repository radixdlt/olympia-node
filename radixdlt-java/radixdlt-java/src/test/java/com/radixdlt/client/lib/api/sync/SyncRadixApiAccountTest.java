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
package com.radixdlt.client.lib.api.sync;

import org.junit.Ignore;
import org.junit.Test;

import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.client.lib.dto.TokenBalances;
import com.radixdlt.client.lib.dto.TransactionHistory;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.io.IOException;
import java.util.Optional;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.client.lib.api.sync.RadixApi.DEFAULT_PRIMARY_PORT;
import static com.radixdlt.client.lib.api.sync.RadixApi.DEFAULT_SECONDARY_PORT;

public class SyncRadixApiAccountTest {
	private static final String BASE_URL = "http://localhost/";
	public static final ECKeyPair KEY_PAIR1 = keyPairOf(1);
	public static final ECKeyPair KEY_PAIR2 = keyPairOf(2);
	private static final AccountAddress ACCOUNT_ADDRESS1 = AccountAddress.create(KEY_PAIR1.getPublicKey());
	private static final AccountAddress ACCOUNT_ADDRESS2 = AccountAddress.create(KEY_PAIR2.getPublicKey());

	private static final String TOKEN_BALANCES = "{\"result\":{\"owner\":"
		+ "\"brx1qsp8n0nx0muaewav2ksx99wwsu9swq5mlndjmn3gm9vl9q2mzmup0xqmhf7fh\",\"tokenBalances\":[{\"amount\":"
		+ "\"1000000000000000000000000000000000000000000000\",\"rri\":\"xrd_rb1qya85pwq\"}]},\"id\":\"4\","
		+ "\"jsonrpc\":\"2.0\"}";
	private static final String TX_HISTORY = "{\"result\":{\"cursor\":\"1577836800000:0\",\"transactions\":[{"
		+ "\"fee\":\"0\",\"txID\":\"7cc3526729b27e4bdfedbb140f3a566ffc2ab582de8e1e94c2358c8466d842a3\",\"sentAt\":"
		+ "\"+51969-08-29T00:00:00Z\",\"actions\":[{\"type\":\"Other\"},{\"type\":\"Other\"},{\"type\":\"Other\"},"
		+ "{\"type\":\"Other\"},{\"type\":\"Other\"},{\"type\":\"Other\"},{\"type\":\"Other\"},{\"type\":\"Other\"},"
		+ "{\"type\":\"Other\"},{\"type\":\"Other\"},{\"type\":\"Other\"},"
		+ "{\"amount\":\"1000000000000000000000000\",\"validator\":"
		+ "\"vb1q27acjcz0vs0dg9mwv7nwyxfxu28rcvu35zwcnn9ulul25ss3kfgkue7d6p\",\"from\":"
		+ "\"brx1qspll7tm6464am4yypzn59p42g6a8qhkguhc269p3vhs27s5vq5h24sh5s4yh\",\"type\":\"StakeTokens\"},"
		+ "{\"amount\":\"1000000000000000000000000\",\"validator\":"
		+ "\"vb1q0tczj5k4n5nw7lf4prxrawja84pjtxwh68gl65hd9almsg77r87zmhdqpf\",\"from\":"
		+ "\"brx1qspll7tm6464am4yypzn59p42g6a8qhkguhc269p3vhs27s5vq5h24sh5s4yh\",\"type\":\"StakeTokens\"},"
		+ "{\"type\":\"Other\"},{\"type\":\"Other\"},{\"type\":\"Other\"},"
		+ "{\"amount\":\"1000000000000000000000000\",\"rri\":"
		+ "\"gum_rb1qvnrj7v43s875nuq7lv2hlghmydvz3udnv3kwssy0stqang8k7\",\"from\":"
		+ "\"brx1qspll7tm6464am4yypzn59p42g6a8qhkguhc269p3vhs27s5vq5h24sh5s4yh\",\"to\":"
		+ "\"brx1qsptmhztqfajpa4qhden6dcseym3gu0pnjxsfmzwvhnlna2jzzxe9zc5ntj47\",\"type\":\"TokenTransfer\"},"
		+ "{\"amount\":\"1000000000000000000000000\",\"rri\":"
		+ "\"cerb_rb1qdaezx9damhh9nv3kp4gl5a58ch59yspal6gr8c63xmskrtk96\",\"from\":"
		+ "\"brx1qspll7tm6464am4yypzn59p42g6a8qhkguhc269p3vhs27s5vq5h24sh5s4yh\",\"to\":"
		+ "\"brx1qsptmhztqfajpa4qhden6dcseym3gu0pnjxsfmzwvhnlna2jzzxe9zc5ntj47\",\"type\":\"TokenTransfer\"},"
		+ "{\"amount\":\"1000000000000000000000000\",\"rri\":"
		+ "\"emunie_rb1q0amnvsa09rxfz83xny849cyg39v3qu9taxcra5p7hxqnn6afk\",\"from\":"
		+ "\"brx1qspll7tm6464am4yypzn59p42g6a8qhkguhc269p3vhs27s5vq5h24sh5s4yh\",\"to\":"
		+ "\"brx1qsptmhztqfajpa4qhden6dcseym3gu0pnjxsfmzwvhnlna2jzzxe9zc5ntj47\",\"type\":\"TokenTransfer\"},"
		+ "{\"amount\":\"1000000000000000000000000\",\"rri\":"
		+ "\"gum_rb1qvnrj7v43s875nuq7lv2hlghmydvz3udnv3kwssy0stqang8k7\",\"from\":"
		+ "\"brx1qspll7tm6464am4yypzn59p42g6a8qhkguhc269p3vhs27s5vq5h24sh5s4yh\",\"to\":"
		+ "\"brx1qspa0q22j6kwjdmmax5yvc046t575xfve6lgarl2ja5hhlwprmcvlcg8k98kp\",\"type\":\"TokenTransfer\"},"
		+ "{\"amount\":\"1000000000000000000000000\",\"rri\":"
		+ "\"cerb_rb1qdaezx9damhh9nv3kp4gl5a58ch59yspal6gr8c63xmskrtk96\",\"from\":"
		+ "\"brx1qspll7tm6464am4yypzn59p42g6a8qhkguhc269p3vhs27s5vq5h24sh5s4yh\",\"to\":"
		+ "\"brx1qspa0q22j6kwjdmmax5yvc046t575xfve6lgarl2ja5hhlwprmcvlcg8k98kp\",\"type\":\"TokenTransfer\"},"
		+ "{\"amount\":\"1000000000000000000000000\",\"rri\":"
		+ "\"emunie_rb1q0amnvsa09rxfz83xny849cyg39v3qu9taxcra5p7hxqnn6afk\",\"from\":"
		+ "\"brx1qspll7tm6464am4yypzn59p42g6a8qhkguhc269p3vhs27s5vq5h24sh5s4yh\",\"to\":"
		+ "\"brx1qspa0q22j6kwjdmmax5yvc046t575xfve6lgarl2ja5hhlwprmcvlcg8k98kp\",\"type\":\"TokenTransfer\"},"
		+ "{\"type\":\"Other\"}]}]},\"id\":\"7\",\"jsonrpc\":\"2.0\"}";
	private static final String ERROR_RESPONSE = "{\"id\":\"8\",\"jsonrpc\":\"2.0\",\"error\":{\"code\":2523,\"data"
		+ "\":[\"7cc3526729b27e4bdfedbb140f3a566ffc2ab582de8e1e94c2358c8466d842a3\"],"
		+ "\"message\":"
		+ "\"Unable to restore creator from transaction "
		+ "7cc3526729b27e4bdfedbb140f3a566ffc2ab582de8e1e94c2358c8466d842a3\"}}";

	private static final String STAKES_RESPONSE = "{\"result\":[{\"amount\":\"18\","
		+ "\"validator\":\"vb1qtrqglu5g8kh6mfsg4qxa9wq0nv9cauwfwxw70984wkqnw2uwz0w2p0mkqq\"}],"
		+ "\"id\":\"1\",\"jsonrpc\":\"2.0\"}\n";

	private static final String UNSTAKES_RESPONSE = "{\"result\":[{\"amount\":\"5\","
		+ "\"withdrawTxID\":\"13a2465719885944f8ed12d881522f083e6cb323a164ed9d0d470b8ad5b8abc9\","
		+ "\"epochsUntil\":0,\"validator\":\"vb1qtrqglu5g8kh6mfsg4qxa9wq0nv9cauwfwxw70984wkqnw2uwz0w2p0mkqq\"}],"
		+ "\"id\":\"1\",\"jsonrpc\":\"2.0\"}";

	private final OkHttpClient client = mock(OkHttpClient.class);

	@Test
	public void testTransactionHistory() throws IOException {
		prepareClient(TX_HISTORY)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(
				client -> client.account().history(ACCOUNT_ADDRESS1, 5, Optional.empty())
					.onFailure(failure -> fail(failure.toString()))
					.onSuccess(transactionHistoryDTO -> assertNotNull(transactionHistoryDTO.getCursor()))
					.onSuccess(transactionHistoryDTO -> assertNotNull(transactionHistoryDTO.getTransactions()))
					.map(TransactionHistory::getTransactions)
					.onSuccess(txs -> assertEquals(1, txs.size()))
					.map(txs -> txs.get(0).getActions())
					.onSuccess(actions -> assertEquals(23, actions.size()))
			);
	}

	@Test
	public void testTokenBalances() throws IOException {
		prepareClient(TOKEN_BALANCES)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.account().balances(ACCOUNT_ADDRESS1)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(tokenBalancesDTO -> assertEquals(ACCOUNT_ADDRESS1, tokenBalancesDTO.getOwner()))
				.map(TokenBalances::getTokenBalances)
				.onSuccess(balances -> assertEquals(1, balances.size())));
	}

	@Test
	public void testErrorResponse() throws IOException {
		prepareClient(ERROR_RESPONSE)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.transaction().lookup(AID.ZERO)
				.onFailure(failure -> assertEquals(2523, failure.code()))
				.onSuccess(__ -> fail()));
	}

	@Test
	public void listStakes() throws IOException {
		prepareClient(STAKES_RESPONSE)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.account().stakes(ACCOUNT_ADDRESS1)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(stakePositionsDTOS -> assertEquals(1, stakePositionsDTOS.size()))
				.onSuccess(stakePositionsDTOS -> assertEquals(UInt256.from(18L), stakePositionsDTOS.get(0).getAmount()))
			);
	}

	@Test
	public void listUnStakes() throws IOException {
		prepareClient(UNSTAKES_RESPONSE)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.account().unstakes(ACCOUNT_ADDRESS1)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(unstakePositionsDTOS -> assertEquals(1, unstakePositionsDTOS.size()))
				.onSuccess(unstakePositionsDTOS -> assertEquals(UInt256.FIVE, unstakePositionsDTOS.get(0).getAmount()))
			);
	}

	@Test
	@Ignore
	public void makeStake() {
		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> makeStake(client, UInt256.NINE));
	}

	@Test
	@Ignore
	public void makeUnStake() {
		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> makeUnStake(client, UInt256.FIVE));
	}

	@Test
	@Ignore
	public void transferUnStake() {
		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> transferUnStake(client, UInt256.NINE));
	}

	private void transferUnStake(RadixApi client, UInt256 amount) {
		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS2)
			.transfer(
				ACCOUNT_ADDRESS2,
				ACCOUNT_ADDRESS1,
				amount,
				"xrd_rb1qya85pwq"
			)
			.message("Test message")
			.build();

		client.transaction().build(request)
			.onFailure(failure -> fail(failure.toString()))
			.map(builtTransactionDTO -> builtTransactionDTO.toFinalized(KEY_PAIR2))
			.onSuccess(finalizedTransaction -> client.transaction().finalize(finalizedTransaction)
				.onSuccess(submittableTransaction -> client.transaction().submit(submittableTransaction)
					.onFailure(failure -> fail(failure.toString()))
					.onSuccess(txDTO -> assertEquals(submittableTransaction.getTxId(), txDTO.getTxId()))));
	}

	private void makeStake(RadixApi client, UInt256 amount) {
		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
			.stake(ACCOUNT_ADDRESS1, ValidatorAddress.of(KEY_PAIR2.getPublicKey()), amount)
			.build();

		client.transaction().build(request)
			.onFailure(failure -> fail(failure.toString()))
			.map(builtTransactionDTO -> builtTransactionDTO.toFinalized(KEY_PAIR1))
			.onSuccess(finalizedTransaction -> client.transaction().finalize(finalizedTransaction)
				.onSuccess(submittableTransaction -> client.transaction().submit(submittableTransaction)
					.onFailure(failure -> fail(failure.toString()))
					.onSuccess(txDTO -> assertEquals(submittableTransaction.getTxId(), txDTO.getTxId()))));
	}

	private void makeUnStake(RadixApi client, UInt256 amount) {
		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
			.unstake(ACCOUNT_ADDRESS1, ValidatorAddress.of(KEY_PAIR2.getPublicKey()), amount)
			.build();

		client.transaction().build(request)
			.onFailure(failure -> fail(failure.toString()))
			.map(builtTransactionDTO -> builtTransactionDTO.toFinalized(KEY_PAIR1))
			.onSuccess(finalizedTransaction -> client.transaction().finalize(finalizedTransaction)
				.onSuccess(submittableTransaction -> client.transaction().submit(submittableTransaction)
					.onFailure(failure -> fail(failure.toString()))
					.onSuccess(txDTO -> assertEquals(submittableTransaction.getTxId(), txDTO.getTxId()))));
	}

	private Result<RadixApi> prepareClient(String responseBody) throws IOException {
		var call = mock(Call.class);
		var response = mock(Response.class);
		var body = mock(ResponseBody.class);

		when(client.newCall(any())).thenReturn(call);
		when(call.execute()).thenReturn(response);
		when(response.body()).thenReturn(body);
		when(body.string()).thenReturn(responseBody);

		return SyncRadixApi.connect(BASE_URL, DEFAULT_PRIMARY_PORT, DEFAULT_SECONDARY_PORT, client);
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
