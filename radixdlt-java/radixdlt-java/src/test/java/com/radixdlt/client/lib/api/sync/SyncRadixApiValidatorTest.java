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

import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.networks.Addressing;
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

	private static final String NETWORK_ID = "{\"result\":{\"networkId\":99},\"id\":\"1\",\"jsonrpc\":\"2.0\"}";
	private static final String LIST = "{\"result\":{\"cursor\":\"dv1q0llj774w40wafpqg5apgd2jxhfc9aj897zk3gvt9uzh59rq9964vjryzf9\""
		+ ",\"validators\":[{\"totalDelegatedStake\":\"100000000000000000000\",\"rakePercentage\":0,\"address\":\"dv1qfwtmurydewmf"
		+ "64rnrektuh20g8r6svm0cpnpcuuay4ammw2cnumc3jtmxl\",\"infoURL\":\"\",\"ownerDelegation\":\"100000000000000000000\",\"name\""
		+ ":\"\",\"registered\":true,\"ownerAddress\":\"ddx1qsp9e00sv3h9md825wv0xe0jafaqu02pndlqxv8rnn5jhh0detz0n0qtp2phh\",\"isExt"
		+ "ernalStakeAccepted\":true},{\"totalDelegatedStake\":\"100000000000000000000\",\"rakePercentage\":0,\"address\":\"dv1q0ll"
		+ "j774w40wafpqg5apgd2jxhfc9aj897zk3gvt9uzh59rq9964vjryzf9\",\"infoURL\":\"\",\"ownerDelegation\":\"100000000000000000000\""
		+ ",\"name\":\"\",\"registered\":true,\"ownerAddress\":\"ddx1qspll7tm6464am4yypzn59p42g6a8qhkguhc269p3vhs27s5vq5h24sfvvdfj\""
		+ ",\"isExternalStakeAccepted\":true}]},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";

	private static final String LOOKUP = "{\"result\":{\"totalDelegatedStake\":\"4754240000000000000000000\",\"rakePercentage\":0,\""
		+ "address\":\"dv1q0llj774w40wafpqg5apgd2jxhfc9aj897zk3gvt9uzh59rq9964vjryzf9\",\"infoURL\":\"\",\"ownerDelegation\":\"10000"
		+ "0000000000000000\",\"name\":\"\",\"registered\":true,\"ownerAddress\":\"ddx1qspll7tm6464am4yypzn59p42g6a8qhkguhc269p3vhs2"
		+ "7s5vq5h24sfvvdfj\",\"isExternalStakeAccepted\":true},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";

	private final OkHttpClient client = mock(OkHttpClient.class);

	@Test
	public void testList() throws IOException {
		prepareClient(LIST)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.validator().list(10, Optional.empty())
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(validatorsResponse -> assertTrue(validatorsResponse.getCursor().isPresent()))
				.onSuccess(validatorsResponse -> assertEquals(2, validatorsResponse.getValidators().size())));
	}

	@Test
	public void testLookup() throws IOException {
		var stake = UInt256.from("4754240000000000000000000");
		var address = ValidatorAddress.of(Addressing.ofNetworkId(99).forValidators()
			.parse("dv1q0llj774w40wafpqg5apgd2jxhfc9aj897zk3gvt9uzh59rq9964vjryzf9"));

		prepareClient(LOOKUP)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(
				client -> client.validator().lookup(address)
					.onFailure(failure -> fail(failure.toString()))
					.onSuccess(validatorDTO -> assertTrue(validatorDTO.isExternalStakeAccepted()))
					.onSuccess(validatorDTO -> assertEquals(stake, validatorDTO.getTotalDelegatedStake())));
	}

	private Result<RadixApi> prepareClient(String responseBody) throws IOException {
		var call = mock(Call.class);
		var response = mock(Response.class);
		var body = mock(ResponseBody.class);

		when(client.newCall(any())).thenReturn(call);
		when(call.execute()).thenReturn(response);
		when(response.body()).thenReturn(body);
		when(body.string()).thenReturn(NETWORK_ID, responseBody);

		return SyncRadixApi.connect(BASE_URL, DEFAULT_PRIMARY_PORT, DEFAULT_SECONDARY_PORT, client);
	}
}
