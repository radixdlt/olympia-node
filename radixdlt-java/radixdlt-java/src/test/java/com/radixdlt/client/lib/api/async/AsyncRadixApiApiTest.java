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

import org.junit.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AsyncRadixApiApiTest {
	private static final String BASE_URL = "http://localhost/";

	private static final String NETWORK_ID = "{\"result\":{\"networkId\":99},\"id\":\"1\",\"jsonrpc\":\"2.0\"}";
	private static final String CONFIGURATION = "{\"result\":{\"endpoints\":[\"/metrics\",\"/system\",\"/account\","
		+ "\"/validation\",\"/universe\",\"/faucet\",\"/chaos\",\"/health\",\"/version\",\"/developer\"]},\"id\":"
		+ "\"2\",\"jsonrpc\":\"2.0\"}\n";
	private static final String DATA = "{\"result\":{\"elapsed\":{\"apidb\":{\"balance\":{\"read\":1672,\""
		+ "write\":4790},\"flush\":{\"time\":630722},\"transaction\":{\"read\":0,\"write\":1453},\"token\":"
		+ "{\"read\":134,\"write\":842}}},\"count\":{\"apidb\":{\"flush\":{\"count\":1627},\"balance\":{\"t"
		+ "otal\":50,\"read\":26,\"bytes\":{\"read\":1532,\"write\":4263},\"write\":24},\"queue\":{\"size\""
		+ ":6},\"transaction\":{\"total\":7,\"read\":0,\"bytes\":{\"read\":0,\"write\":13923},\"write\":7},"
		+ "\"token\":{\"total\":2,\"read\":1,\"bytes\":{\"read\":245,\"write\":245},\"write\":1}}}},\"id\":"
		+ "\"2\",\"jsonrpc\":\"2.0\"}\n";

	private final HttpClient client = mock(HttpClient.class);

	@Test
	public void testConfiguration() throws IOException {
		prepareClient(CONFIGURATION)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.api().configuration().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(configurationDTO -> assertEquals(10, configurationDTO.getEndpoints().size())));
	}

	@Test
	public void testData() throws IOException {
		prepareClient(DATA)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.api().data().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(data -> assertNotNull(data.getCount()))
				.onSuccess(data -> assertNotNull(data.getElapsed()))
				.onSuccess(data -> assertEquals(630722, data.getElapsed().getApiDb().getFlush().getTime()))
				.onSuccess(data -> assertEquals(1672, data.getElapsed().getApiDb().getBalance().getRead()))
				.onSuccess(data -> assertEquals(6, data.getCount().getApiDb().getQueue().getSize())));
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
}
