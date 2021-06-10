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
package com.radixdlt.client.lib.api;

import org.junit.Test;

import com.radixdlt.utils.functional.Result;

import java.io.IOException;

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

import static com.radixdlt.client.lib.api.RadixApi.DEFAULT_PRIMARY_PORT;
import static com.radixdlt.client.lib.api.RadixApi.DEFAULT_SECONDARY_PORT;

public class DefaultRadixApiApiTest {
	private static final String BASE_URL = "http://localhost/";

	private static final String CONFIGURATION = "{\"result\":{\"endpoints\":"
		+ "[\"/system\",\"/account\",\"/universe\",\"/faucet\",\"/chaos\","
		+ "\"/health\",\"/version\",\"/archive\",\"/construction\"]},\"id\":\"1\",\"jsonrpc\":\"2.0\"}\n";
	private static final String DATA = "{\"result\":{\"elapsed\":{\"apidb\":{\"balance\":"
		+ "{\"read\":8121,\"write\":31650},\"flush\":{\"time\":4522593},\"transaction\":"
		+ "{\"read\":0,\"write\":1340},\"token\":{\"read\":365,\"write\":1011}}},\"count\":"
		+ "{\"apidb\":{\"flush\":{\"count\":13422},\"balance\":{\"total\":696,\"read\":349,\"bytes\":"
		+ "{\"read\":53876,\"write\":59412},\"write\":347},\"queue\":{\"size\":29},\"transaction\":"
		+ "{\"total\":9,\"read\":0,\"bytes\":{\"read\":0,\"write\":35483},\"write\":9},\"token\":"
		+ "{\"total\":8,\"read\":4,\"bytes\":{\"read\":740,\"write\":740},\"write\":4}}}},"
		+ "\"id\":\"1\",\"jsonrpc\":\"2.0\"}\n";

	private final OkHttpClient client = mock(OkHttpClient.class);

	@Test
	public void testConfiguration() throws IOException {
		prepareClient(CONFIGURATION)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.api().configuration()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(configurationDTO -> assertEquals(9, configurationDTO.getEndpoints().size())));
	}

	@Test
	public void testData() throws IOException {
		prepareClient(DATA)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(
				client -> client.api().data()
					.onFailure(failure -> fail(failure.toString()))
					.onSuccess(data -> assertNotNull(data.getCount()))
					.onSuccess(data -> assertNotNull(data.getElapsed()))
					.onSuccess(data -> assertEquals(4522593, data.getElapsed().getApiDb().getFlush().getTime()))
					.onSuccess(data -> assertEquals(8121, data.getElapsed().getApiDb().getBalance().getRead()))
					.onSuccess(data -> assertEquals(29, data.getCount().getApiDb().getQueue().getSize()))
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

		return DefaultRadixApi.connect(BASE_URL, DEFAULT_PRIMARY_PORT, DEFAULT_SECONDARY_PORT, client);
	}
}
