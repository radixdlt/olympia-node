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

public class AsyncRadixApiNetworkTest {
	private static final String BASE_URL = "http://localhost/";

	private static final String NETWORK_ID = "{\"result\":{\"networkId\":99},\"id\":\"2\",\"jsonrpc\":\"2.0\"}";
	private static final String DEMAND = "{\"result\":{\"tps\":5},\"id\":\"2\",\"jsonrpc\":\"2.0\"}";
	private static final String THROUGHPUT = "{\"result\":{\"tps\":283},\"id\":\"2\",\"jsonrpc\":\"2.0\"}";

	private final HttpClient client = mock(HttpClient.class);

	@Test
	public void testNetworkId() throws IOException {
		prepareClient(NETWORK_ID)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.network().id().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(networkIdDTO -> assertEquals(99, networkIdDTO.getNetworkId())));
	}

	@Test
	public void testDemand() throws IOException {
		prepareClient(DEMAND)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.network().demand().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(networkStatsDTO -> assertEquals(5L, networkStatsDTO.getTps())));
	}

	@Test
	public void testThroughput() throws IOException {
		prepareClient(THROUGHPUT)
			.map(RadixApi::withTrace)
			.join()
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.network().throughput().join()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(networkStatsDTO -> assertEquals(283L, networkStatsDTO.getTps())));
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
