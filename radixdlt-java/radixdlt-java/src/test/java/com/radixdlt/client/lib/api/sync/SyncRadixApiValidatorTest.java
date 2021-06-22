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

import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.io.IOException;
import java.util.Optional;

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

public class SyncRadixApiValidatorTest {
	private static final String BASE_URL = "http://localhost/";

	private static final String LIST = "{\"result\":{\"cursor\":\"vb1q0tczj5k4n5nw7lf4"
		+ "prxrawja84pjtxwh68gl65hd9almsg77r87zmhdqpf\",\"validators\":[{\"totalDelegatedStake\":"
		+ "\"4505000000000000000000000\",\"address\":\"vb1q27acjcz0vs0dg9mwv7nwyxfxu28rcvu35zwcnn"
		+ "9ulul25ss3kfgkue7d6p\",\"infoURL\":\"\",\"ownerDelegation\":\"0\",\"name\":\"\",\"owne"
		+ "rAddress\":\"brx1qsptmhztqfajpa4qhden6dcseym3gu0pnjxsfmzwvhnlna2jzzxe9zc5ntj47\",\"isE"
		+ "xternalStakeAccepted\":true},{\"totalDelegatedStake\":\"4504290000000000000000000\","
		+ "\"address\":\"vb1q0tczj5k4n5nw7lf4prxrawja84pjtxwh68gl65hd9almsg77r87zmhdqpf\","
		+ "\"infoURL\":\"\",\"ownerDelegation\":\"0\",\"name\":\"\",\"ownerAddress\":\"brx1qspa0q"
		+ "22j6kwjdmmax5yvc046t575xfve6lgarl2ja5hhlwprmcvlcg8k98kp\",\"isExternalStakeAccepted"
		+ "\":true}]},\"id\":\"1\",\"jsonrpc\":\"2.0\"}\n";

	private static final String LOOKUP = "{\"result\":{\"totalDelegatedStake\":\"47542400000000000"
		+ "00000000\",\"address\":\"vb1q0tczj5k4n5nw7lf4prxrawja84pjtxwh68gl65hd9almsg77r87zmhdqpf\","
		+ "\"infoURL\":\"\",\"ownerDelegation\":\"0\",\"name\":\"\",\"ownerAddress\":\"brx1qspa0q22j6"
		+ "kwjdmmax5yvc046t575xfve6lgarl2ja5hhlwprmcvlcg8k98kp\",\"isExternalStakeAccepted\":true},"
		+ "\"id\":\"1\",\"jsonrpc\":\"2.0\"}\n";

	private final OkHttpClient client = mock(OkHttpClient.class);

	@Test
	public void testList() throws IOException {
		prepareClient(LIST)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.validator().list(10, Optional.empty())
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(validatorsResponse -> assertTrue(validatorsResponse.getCursor().isPresent()))
				.onSuccess(validatorsResponse -> assertEquals(2, validatorsResponse.getValidators().size()))
			);
	}

	@Test
	public void testData() throws IOException {
		var stake = UInt256.from("4754240000000000000000000");
		var address = "vb1q0tczj5k4n5nw7lf4prxrawja84pjtxwh68gl65hd9almsg77r87zmhdqpf";

		prepareClient(LOOKUP)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(
				client -> client.validator().lookup(address)
					.onFailure(failure -> fail(failure.toString()))
					.onSuccess(validatorDTO -> assertTrue(validatorDTO.isExternalStakeAccepted()))
					.onSuccess(validatorDTO -> assertEquals(stake, validatorDTO.getTotalDelegatedStake()))
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
