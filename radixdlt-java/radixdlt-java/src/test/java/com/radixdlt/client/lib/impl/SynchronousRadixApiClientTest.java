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
package com.radixdlt.client.lib.impl;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Ignore;
import org.junit.Test;

import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.NavigationCursor;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.dto.FinalizedTransaction;
import com.radixdlt.client.lib.dto.TokenBalancesDTO;
import com.radixdlt.client.lib.dto.TransactionHistoryDTO;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SynchronousRadixApiClientTest {
	private static final String BASE_URL = "http://localhost:8080/";
	public static final ECKeyPair KEY_PAIR1 = keyPairOf(1);
	public static final ECKeyPair KEY_PAIR2 = keyPairOf(2);
	private static final AccountAddress ACCOUNT_ADDRESS1 = AccountAddress.create(KEY_PAIR1.getPublicKey());
	private static final AccountAddress ACCOUNT_ADDRESS2 = AccountAddress.create(KEY_PAIR2.getPublicKey());

	private static final String NETWORK_ID = "{\"result\":{\"networkId\":2},\"id\":\"1\",\"jsonrpc\":\"2.0\"}";
	private static final String NATIVE_TOKEN = "{\"result\":{\"tokenInfoURL\":\"https://tokens.radixdlt.com/\","
		+ "\"symbol\":\"xrd\",\"isSupplyMutable\":true,\"granularity\":\"1\",\"name\":"
		+ "\"Rads\",\"rri\":\"xrd_rb1qya85pwq\","
		+ "\"description\":\"Radix Betanet Tokens\",\"currentSupply\":"
		+ "\"8000000000000000000000000000000000000000000000\",\"iconURL\":"
		+ "\"https://assets.radixdlt.com/icons/icon-xrd-32x32.png\"},\"id\":\"2\",\"jsonrpc\":\"2.0\"}";
	private static final String TOKEN_BALANCES = "{\"result\":{\"owner\":"
		+ "\"brx1qsp8n0nx0muaewav2ksx99wwsu9swq5mlndjmn3gm9vl9q2mzmup0xqmhf7fh\",\"tokenBalances\":[{\"amount\":"
		+ "\"1000000000000000000000000000000000000000000000\",\"rri\":\"xrd_rb1qya85pwq\"}]},\"id\":\"4\","
		+ "\"jsonrpc\":\"2.0\"}";
	private static final String DEMAND = "{\"result\":{\"tps\":5},\"id\":\"5\",\"jsonrpc\":\"2.0\"}";
	private static final String THROUGHPUT = "{\"result\":{\"tps\":8},\"id\":\"6\",\"jsonrpc\":\"2.0\"}";
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
	private static final String ERROR_RESPONSE = "{\"id\":\"8\",\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32602,\"data"
		+ "\":[\"7cc3526729b27e4bdfedbb140f3a566ffc2ab582de8e1e94c2358c8466d842a3\"],"
		+ "\"message\":"
		+ "\"Unable to restore creator from transaction "
		+ "7cc3526729b27e4bdfedbb140f3a566ffc2ab582de8e1e94c2358c8466d842a3\"}}";
	private static final String BUILT_TRANSACTION = "{\"result\":{\"fee\":\"100000000000000000\",\"transaction\":{\"blob\":"
		+ "\"0103010402c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5000000000000000000000000000000000"
		+ "0000000000000000000000000000009045b83c55858c7620061d2bbc12ff86ea2ea466e576e7ea55f086901f2d9a520660000000301030"
		+ "1040279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798000000000000000000000000002cd76fe086b93ce"
		+ "2f768a00b229ffffffffff7000500000002010301040279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f817980"
		+ "00000000000000000000000002cd76fe086b93ce2f768a009bf5a87a275fff700\",\"hashOfBlobToSign\":\"5dc6006f467be27975d"
		+ "0f8f33ad94506e0df53956aae642341b12e2a094e39a2\"}},\"id\":\"1\",\"jsonrpc\":\"2.0\"}\n";
	private static final String FINALIZE_TRANSACTION = "{\"result\":{\"txID\":"
		+ "\"a41e12e424431e8f5f8b86eddc36fb84c6a1811d9005607258f799675a3bc338\"},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";

	private final OkHttpClient client = mock(OkHttpClient.class);

	@Test
	public void testNetworkId() throws IOException {
		prepareClient(NETWORK_ID)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.networkId()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(networkIdDTO -> assertEquals(2, networkIdDTO.getNetworkId())));
	}

	@Test
	public void testNativeToken() throws IOException {
		prepareClient(NATIVE_TOKEN)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.nativeToken()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(tokenInfoDTO -> assertEquals("Rads", tokenInfoDTO.getName())));
	}

	@Test
	public void testTokenInfo() throws IOException {
		prepareClient(NATIVE_TOKEN)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.tokenInfo("xrd_rb1qya85pwq")
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(tokenInfoDTO -> assertEquals("Rads", tokenInfoDTO.getName())));
	}

	@Test
	public void testTransactionHistory() throws IOException {
		prepareClient(TX_HISTORY)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(
				client -> client.transactionHistory(ACCOUNT_ADDRESS1, 5, Optional.empty())
					.onFailure(failure -> fail(failure.toString()))
					.onSuccess(transactionHistoryDTO -> assertNotNull(transactionHistoryDTO.getCursor()))
					.onSuccess(transactionHistoryDTO -> assertNotNull(transactionHistoryDTO.getTransactions()))
					.map(TransactionHistoryDTO::getTransactions)
					.onSuccess(txs -> assertEquals(1, txs.size()))
					.map(txs -> txs.get(0).getActions())
					.onSuccess(actions -> assertEquals(23, actions.size()))
			);
	}

	@Test
	public void testTransactionHistory2() throws IOException {
		SynchronousRadixApiClient.connect(BASE_URL)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(
				client -> {
					var cursorHolder = new AtomicReference<NavigationCursor>();

					client.transactionHistory(ACCOUNT_ADDRESS1, 3, Optional.empty())
						.onFailure(failure -> fail(failure.toString()))
						.onSuccess(v -> v.getCursor().ifPresent(System.out::println))
						.onSuccess(v -> v.getCursor().ifPresent(cursorHolder::set))
						.map(TransactionHistoryDTO::getTransactions)
						.map(t -> t.stream().map(v -> String.format(
							"%s - %s%n",
							v.getTxID(),
							v.getSentAt().getInstant()
						)).collect(Collectors.toList()))
						.onSuccess(System.out::println);

					client.transactionHistory(ACCOUNT_ADDRESS1, 3, Optional.ofNullable(cursorHolder.get()))
						.onFailure(failure -> fail(failure.toString()))
						.onSuccess(v -> v.getCursor().ifPresent(System.out::println))
						.onSuccess(v -> v.getCursor().ifPresent(cursorHolder::set))
						.map(TransactionHistoryDTO::getTransactions)
						.map(t -> t.stream().map(v -> String.format(
							"%s - %s%n",
							v.getTxID(),
							v.getSentAt().getInstant()
						)).collect(Collectors.toList()))
						.onSuccess(System.out::println);

					client.transactionHistory(ACCOUNT_ADDRESS1, 3, Optional.ofNullable(cursorHolder.get()))
						.onFailure(failure -> fail(failure.toString()))
						.onSuccess(v -> v.getCursor().ifPresent(System.out::println))
						.onSuccess(v -> v.getCursor().ifPresent(cursorHolder::set))
						.map(TransactionHistoryDTO::getTransactions)
						.map(t -> t.stream().map(v -> String.format(
							"%s - %s%n",
							v.getTxID(),
							v.getSentAt().getInstant()
						)).collect(Collectors.toList()))
						.onSuccess(System.out::println);
				}
			);
	}

	@Test
	public void testTokenBalances() throws IOException {
		prepareClient(TOKEN_BALANCES)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.tokenBalances(ACCOUNT_ADDRESS1)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(tokenBalancesDTO -> assertEquals(ACCOUNT_ADDRESS1, tokenBalancesDTO.getOwner()))
				.map(TokenBalancesDTO::getTokenBalances)
				.onSuccess(balances -> assertEquals(1, balances.size())));
	}

	@Test
	public void testErrorResponse() throws IOException {
		prepareClient(ERROR_RESPONSE)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.lookupTransaction(AID.ZERO)
				.onFailure(failure -> assertEquals(-32602, failure.code()))
				.onSuccess(__ -> fail()));
	}

	@Test
	public void testDemand() throws IOException {
		prepareClient(DEMAND)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.networkTransactionDemand()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(networkStatsDTO -> assertEquals(5L, networkStatsDTO.getTps())));
	}

	@Test
	public void testThroughput() throws IOException {
		prepareClient(THROUGHPUT)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.networkTransactionThroughput()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(networkStatsDTO -> assertEquals(8L, networkStatsDTO.getTps())));
	}

	@Test
	public void testBuildTransaction() throws IOException {
		var hash = Hex.decode("5dc6006f467be27975d0f8f33ad94506e0df53956aae642341b12e2a094e39a2");

		var request = TransactionRequest.createBuilder()
			.transfer(
				ACCOUNT_ADDRESS1,
				ACCOUNT_ADDRESS2,
				UInt256.NINE,
				"xrd_rb1qya85pwq"
			)
			.build();

		prepareClient(BUILT_TRANSACTION)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.buildTransaction(request)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(dto -> assertEquals(UInt256.from(100000000000000000L), dto.getFee()))
				.onSuccess(dto -> assertArrayEquals(hash, dto.getTransaction().getHashToSign()))
			);
	}

	@Test
	@Ignore //Useful testbed for experiments testing
	public void testBuildTransactionWithMessage() {
		var request = TransactionRequest.createBuilder()
			.transfer(
				ACCOUNT_ADDRESS1,
				ACCOUNT_ADDRESS2,
				UInt256.NINE,
				"xrd_rb1qya85pwq"
			)
			.message("Test message")
			.build();

		SynchronousRadixApiClient.connect(BASE_URL)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.buildTransaction(request)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(builtTransactionDTO -> assertEquals(UInt256.from(100000000000000000L), builtTransactionDTO.getFee()))
				.map(builtTransactionDTO -> builtTransactionDTO.toFinalized(KEY_PAIR1))
				.onSuccess(finalizedTransaction -> client.finalizeTransaction(finalizedTransaction)
					.onSuccess(txDTO -> assertNotNull(txDTO.getTxId()))
					.map(txDTO -> finalizedTransaction.withTxId(txDTO.getTxId()))
					.onSuccess(submittableTransaction -> client.submitTransaction(submittableTransaction)
						.onFailure(failure -> fail(failure.toString()))
						.onSuccess(txDTO -> submittableTransaction.rawTxId()
							.ifPresentOrElse(aid -> assertEquals(aid, txDTO.getTxId()), () -> fail("Should not happen")))))
			);
	}

	@Test
	public void addManyTransactions() throws InterruptedException {
		for (int i = 0; i < 20; i++) {
			addTransaction(UInt256.from(i + 10));
			Thread.sleep(1000);
		}
	}

	private void addTransaction(UInt256 amount) {
		var request = TransactionRequest.createBuilder()
			.transfer(
				ACCOUNT_ADDRESS1,
				ACCOUNT_ADDRESS2,
				amount,
				"xrd_rb1qya85pwq"
			)
			.message("Test message")
			.build();

		SynchronousRadixApiClient.connect(BASE_URL)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.buildTransaction(request)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(builtTransactionDTO -> assertEquals(UInt256.from(100000000000000000L), builtTransactionDTO.getFee()))
				.map(builtTransactionDTO -> builtTransactionDTO.toFinalized(KEY_PAIR1))
				.onSuccess(finalizedTransaction -> client.finalizeTransaction(finalizedTransaction)
					.onSuccess(txDTO -> assertNotNull(txDTO.getTxId()))
					.map(txDTO -> finalizedTransaction.withTxId(txDTO.getTxId()))
					.onSuccess(submittableTransaction -> client.submitTransaction(submittableTransaction)
						.onFailure(failure -> fail(failure.toString()))
						.onSuccess(txDTO -> submittableTransaction.rawTxId()
							.ifPresentOrElse(aid -> assertEquals(aid, txDTO.getTxId()), () -> fail("Should not happen")))))
			);
	}

	@Test
	public void testFinalizeTransaction() throws Exception {
		var request = buildFinalizedTransaction();
		var txId = AID.from("a41e12e424431e8f5f8b86eddc36fb84c6a1811d9005607258f799675a3bc338");

		prepareClient(FINALIZE_TRANSACTION)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.finalizeTransaction(request)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(dto -> assertEquals(txId, dto.getTxId()))
			);
	}

	@Test
	public void testSubmitTransaction() throws Exception {
		var txId = AID.from("a41e12e424431e8f5f8b86eddc36fb84c6a1811d9005607258f799675a3bc338");
		var request = buildFinalizedTransaction().withTxId(txId);

		prepareClient(FINALIZE_TRANSACTION)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.submitTransaction(request)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(dto -> assertEquals(txId, dto.getTxId()))
			);
	}

	private FinalizedTransaction buildFinalizedTransaction() throws PublicKeyException {
		var blob = Hex.decode("0103010402c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee"
								  + "5000000000000000000000000000000000000000000000000000000000000000904fcdcdd43e66c"
								  + "ff732ba9a0cbd484cdd9fa9579388b67e3878fd981280a48372e00000003010301040279be667ef"
								  + "9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798000000000000000000000000"
								  + "002cd76fe086b93ce2f768a00b229ffffffffff7000500000002010301040279be667ef9dcbbac5"
								  + "5a06295ce870b07029bfcdb2dce28d959f2815b16f81798000000000000000000000000002cd76f"
								  + "e086b93ce2f768a009bf5a87a275fff700");
		var sig = ECDSASignature.decodeFromHexDer("30440220768a67a36549e11f19ddb6e2c172c3"
													  + "f2f2996600413f1d2f246667ab2de81ddf0220"
													  + "70f3bb613bcba2704728b99fad91668e2d6759"
													  + "3f73b7c3567eae61596242f64c");
		var pubkey = ECPublicKey.fromHex("0479be667ef9dcbbac55a06295ce870b07029bfcdb2dce"
											 + "28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8"
											 + "fd17b448a68554199c47d08ffb10d4b8");
		return FinalizedTransaction.create(blob, sig, pubkey, null);
	}

	private Result<SynchronousRadixApiClient> prepareClient(String responseBody) throws IOException {
		var call = mock(Call.class);
		var response = mock(Response.class);
		var body = mock(ResponseBody.class);

		when(client.newCall(any())).thenReturn(call);
		when(call.execute()).thenReturn(response);
		when(response.body()).thenReturn(body);
		when(body.string()).thenReturn(responseBody);

		return SynchronousRadixApiClient.connect(BASE_URL, client);
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
