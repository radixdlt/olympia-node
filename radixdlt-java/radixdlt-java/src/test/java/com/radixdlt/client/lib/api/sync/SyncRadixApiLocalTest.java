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

import org.junit.Test;

import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.utils.functional.Result;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.client.lib.api.sync.RadixApi.DEFAULT_PRIMARY_PORT;
import static com.radixdlt.client.lib.api.sync.RadixApi.DEFAULT_SECONDARY_PORT;

//TODO: remaining tests
public class SyncRadixApiLocalTest {
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
	private static final String NEXT_EPOCH = "{\"result\":{\"validators\":[{\"totalDelegatedStake\":"
		+ "\"3355000000000000000000000\",\"address\":\"vb1q27acjcz0vs0dg9mwv7nwyxfxu28rcvu35zwcnn9ulu"
		+ "l25ss3kfgkue7d6p\",\"infoURL\":\"\",\"ownerDelegation\":\"0\",\"name\":\"\",\"ownerAddress\":\"brx1qsptmhz"
		+ "tqfajpa4qhden6dcseym3gu0pnjxsfmzwvhnlna2jzzxe9zc5ntj47\",\"isExternalStakeAccepted\":true},{\"totalDelegate"
		+ "dStake\":\"3354520000000000000000000\",\"address\":\"vb1q0tczj5k4n5nw7lf4prxrawja84pjtxwh68gl65hd9almsg77r8"
		+ "7zmhdqpf\",\"infoURL\":\"\",\"ownerDelegation\":\"0\",\"name\":\"\",\"ownerAddress\":\"brx1qspa0q22j6kwjdmm"
		+ "ax5yvc046t575xfve6lgarl2ja5hhlwprmcvlcg8k98kp\",\"isExternalStakeAccepted\":true}]},\"id\":\"1\",\"jsonrpc\":\"2.0\"}\n";
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
}
