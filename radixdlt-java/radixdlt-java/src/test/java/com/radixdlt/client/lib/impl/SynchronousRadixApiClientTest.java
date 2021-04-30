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

import org.junit.Assert;
import org.junit.Test;

import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.dto.TransactionHistoryDTO;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.utils.Ints;
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

public class SynchronousRadixApiClientTest {
	private static final String BASE_URL = "http://localhost:8080/";
	private static final AccountAddress ACCOUNT_ADDRESS = AccountAddress.create(pubkeyOf(1));

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

	private final OkHttpClient client = mock(OkHttpClient.class);

	@Test
	public void testNetworkId() throws IOException {
		prepareClient(NETWORK_ID)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.networkId()
				.onFailureDo(Assert::fail)
				.onSuccess(networkIdDTO -> assertEquals(2, networkIdDTO.getNetworkId())));
	}

	@Test
	public void testNativeToken() throws IOException {
		prepareClient(NATIVE_TOKEN)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.nativeToken()
				.onFailureDo(Assert::fail)
				.onSuccess(tokenInfoDTO -> assertEquals("Rads", tokenInfoDTO.getName())));
	}

	@Test
	public void testTokenInfo() throws IOException {
		prepareClient(NATIVE_TOKEN)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.tokenInfo("xrd_rb1qya85pwq")
				.onFailureDo(Assert::fail)
				.onSuccess(tokenInfoDTO -> assertEquals("Rads", tokenInfoDTO.getName())));
	}

	@Test
	public void testTransactionHistory() throws IOException {
		prepareClient(TX_HISTORY)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(
				client -> client.transactionHistory(ACCOUNT_ADDRESS, 10, Optional.empty())
					.onFailureDo(Assert::fail)
					.onSuccess(transactionHistoryDTO -> assertNotNull(transactionHistoryDTO.getCursor()))
					.onSuccess(transactionHistoryDTO -> assertNotNull(transactionHistoryDTO.getTransactions()))
					.map(TransactionHistoryDTO::getTransactions)
					.onSuccess(txs -> assertEquals(1, txs.size()))
					.map(txs -> txs.get(0).getActions())
					.onSuccess(actions -> assertEquals(23, actions.size()))
			);
	}

	@Test
	public void testDemand() throws IOException {
		prepareClient(DEMAND)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.networkTransactionDemand()
				.onFailureDo(Assert::fail)
				.onSuccess(networkStatsDTO -> assertEquals(5L, networkStatsDTO.getTps())));
	}

	@Test
	public void testThroughput() throws IOException {
		prepareClient(THROUGHPUT)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.networkTransactionThroughput()
				.onFailureDo(Assert::fail)
				.onSuccess(networkStatsDTO -> assertEquals(8L, networkStatsDTO.getTps())));
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

	private static ECPublicKey pubkeyOf(int pk) {
		final byte[] privateKey = new byte[ECKeyPair.BYTES];
		Ints.copyTo(pk, privateKey, ECKeyPair.BYTES - Integer.BYTES);
		ECKeyPair kp;
		try {
			kp = ECKeyPair.fromPrivateKey(privateKey);
		} catch (PrivateKeyException | PublicKeyException e) {
			throw new IllegalArgumentException("Error while generating public key", e);
		}
		return kp.getPublicKey();
	}
}