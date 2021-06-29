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
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.client.lib.api.sync.RadixApi.DEFAULT_PRIMARY_PORT;
import static com.radixdlt.client.lib.api.sync.RadixApi.DEFAULT_SECONDARY_PORT;

public class SyncRadixApiLocalTest {
	public static final ECKeyPair KEY_PAIR1 = keyPairOf(1);
	public static final ECKeyPair KEY_PAIR2 = keyPairOf(2);
	public static final ECKeyPair KEY_PAIR3 = keyPairOf(3);
	private static final AccountAddress ACCOUNT_ADDRESS1 = AccountAddress.create(KEY_PAIR1.getPublicKey());
	private static final AccountAddress ACCOUNT_ADDRESS2 = AccountAddress.create(KEY_PAIR2.getPublicKey());
	private static final ValidatorAddress VALIDATOR_ADDRESS = ValidatorAddress.of(KEY_PAIR3.getPublicKey());

	private static final String BASE_URL = "http://localhost/";

	private static final String ACCOUNT_INFO = "{\"result\":{\"address\":\"brx1qsptmhztqfajpa4qhden6dcseym3gu0pnjxsfmzw"
		+ "vhnlna2jzzxe9zc5ntj47\",\"balance\":{\"stakes\":[],\"tokens\":[{\"amount\":\"1000000000000000000000000000000"
		+ "000000000000000\",\"rri\":\"xrd_rb1qya85pwq\"},{\"amount\":\"1000000000000000000000000\",\"rri\":\"cerb_rb1q"
		+ "daezx9damhh9nv3kp4gl5a58ch59yspal6gr8c63xmskrtk96\"},{\"amount\":\"1000000000000000000000000\",\"rri\":\"emu"
		+ "nie_rb1q0amnvsa09rxfz83xny849cyg39v3qu9taxcra5p7hxqnn6afk\"},{\"amount\":\"1000000000000000000000000\",\"r"
		+ "ri\":\"gum_rb1qvnrj7v43s875nuq7lv2hlghmydvz3udnv3kwssy0stqang8k7\"}]}},\"id\":\"1\",\"jsonrpc\":\"2.0\"}\n";
	private static final String VALIDATOR_INFO = "{\"result\":{\"address\":\"vb1q27acjcz0vs0dg9mwv7nwyxfxu28rcvu35zwcn"
		+ "n9ulul25ss3kfgkue7d6p\",\"stakes\":[{\"amount\":\"1005000000000000000000000\",\"delegator\":\"brx1qspll7tm64"
		+ "64am4yypzn59p42g6a8qhkguhc269p3vhs27s5vq5h24sh5s4yh\"}],\"name\":\"\",\"registered\":true,\"totalStake\":"
		+ "\"1005000000000000000000000\",\"url\":\"\"},\"id\":\"1\",\"jsonrpc\":\"2.0\"}\n";
	private static final String NEXT_EPOCH = "{\"result\":{\"validators\":[{\"totalDelegatedStake\":\"1000000000000000"
		+ "000000000\",\"address\":\"vb1q27acjcz0vs0dg9mwv7nwyxfxu28rcvu35zwcnn9ulul25ss3kfgkue7d6p\",\"infoURL\":\"\""
		+ ",\"ownerDelegation\":\"1000000000000000000000000\",\"percentage\":0,\"name\":\"\",\"registered\":true,\"own"
		+ "erAddress\":\"brx1qsptmhztqfajpa4qhden6dcseym3gu0pnjxsfmzwvhnlna2jzzxe9zc5ntj47\",\"isExternalStakeAccepted"
		+ "\":true},{\"totalDelegatedStake\":\"1000000000000000000000000\",\"address\":\"vb1q0tczj5k4n5nw7lf4prxrawja8"
		+ "4pjtxwh68gl65hd9almsg77r87zmhdqpf\",\"infoURL\":\"\",\"ownerDelegation\":\"1000000000000000000000000\",\"pe"
		+ "rcentage\":0,\"name\":\"\",\"registered\":true,\"ownerAddress\":\"brx1qspa0q22j6kwjdmmax5yvc046t575xfve6lga"
		+ "rl2ja5hhlwprmcvlcg8k98kp\",\"isExternalStakeAccepted\":true}]},\"id\":\"1\",\"jsonrpc\":\"2.0\"}\n";
	private static final String SINGLE_STEP = "";
	private static final String CURRENT_EPOCH = "";

	private final OkHttpClient client = mock(OkHttpClient.class);

	@Test
	public void testAccountInfo() throws IOException {
		var accountAddress = AccountAddress.create("brx1qsptmhztqfajpa4qhden6dcseym3gu0pnjxsfmzwvhnlna2jzzxe9zc5ntj47");

		prepareClient(ACCOUNT_INFO)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.local().accountInfo()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(localAccount -> assertEquals(accountAddress, localAccount.getAddress()))
				.onSuccess(localAccount -> assertEquals(4, localAccount.getBalance().getTokens().size()))
			);
	}

	@Test
	public void testValidatorInfo() throws IOException {
		prepareClient(VALIDATOR_INFO)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.local().validatorInfo()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(localValidatorInfo -> assertEquals(1, localValidatorInfo.getStakes().size()))
				.onSuccess(localValidatorInfo -> assertTrue(localValidatorInfo.isRegistered()))
			);
	}

	@Test
	public void testNextEpoch() throws IOException {
		prepareClient(NEXT_EPOCH)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.local().nextEpoch()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(epochData -> assertEquals(2, epochData.getValidators().size()))
			);
	}

	@Test
	@Ignore //FIXME: Does not work for now as accounts don't match the address of node we're talking to
	public void testSubmitTxSingleStep() throws IOException {
		var request = TransactionRequest.createBuilder(ACCOUNT_ADDRESS1)
			.transfer(
				ACCOUNT_ADDRESS1,
				ACCOUNT_ADDRESS2,
				UInt256.NINE,
				"xrd_rb1qya85pwq"
			)
			.message("Test message")
			.build();

		RadixApi.connect(BASE_URL)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.local().submitTxSingleStep(request)
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(txData -> assertNotNull(txData.getTxId()))
			);
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
