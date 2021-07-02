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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AsyncRadixApiRadixEngineTest {
	private static final String BASE_URL = "http://localhost/";

	private static final String NETWORK_ID = "{\"result\":{\"networkId\":99},\"id\":\"1\",\"jsonrpc\":\"2.0\"}";
	private static final String CONFIGURATION = "{\"result\":[{\"maxSigsPerRound\":50,\"maxValidators\":100,"
		+ "\"name\":\"olympia-first-epoch\",\"maxRounds\":1500000,\"epoch\":0,\"version\":\"olympia_v1\"},"
		+ "{\"maxSigsPerRound\":50,\"maxValidators\":100,\"name\":\"olympia\",\"maxRounds\":10000,\"epoch\":2,"
		+ "\"version\":\"olympia_v1\"}],\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";
	private static final String DATA = "{\"result\":{\"systemTransactions\":37884,\"invalidProposedCommands\":1,"
		+ "\"userTransactions\":2016},\"id\":\"2\",\"jsonrpc\":\"2.0\"}\n";

	private final HttpClient client = mock(HttpClient.class);

	@Test
	public void testConfiguration() throws IOException {
		prepareClient(CONFIGURATION)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.radixEngine().configuration().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(configuration -> assertEquals(2, configuration.size()))
				.onSuccess(configuration -> assertEquals("olympia", configuration.get(1).getName())));
	}

	@Test
	public void testData() throws IOException {
		prepareClient(DATA)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(
				client -> client.radixEngine().data().join()
					.onFailure(failure -> fail(failure.toString()))
					.onSuccess(data -> assertEquals(37884L, data.getSystemTransactions()))
					.onSuccess(data -> assertEquals(2016L, data.getUserTransactions()))
					.onSuccess(data -> assertEquals(1L, data.getInvalidProposedCommands()))
			);
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
}
