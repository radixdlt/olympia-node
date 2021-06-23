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

public class AsyncRadixApiSyncTest {
	private static final String BASE_URL = "http://localhost/";

	private static final String CONFIGURATION = "{\"result\":{\"maxLedgerUpdatesRate\":50,\"syncCheckMaxPeers\":10,"
		+ "\"ledgerStatusUpdateMaxPeersToNotify\":10,\"syncCheckInterval\":3000,\"requestTimeout\":5000},\"id\":\"1\","
		+ "\"jsonrpc\":\"2.0\"}\n";
	private static final String DATA = "{\"result\":{\"processed\":207,\"invalidCommandsReceived\":0,"
		+ "\"targetCurrentDiff\":0,\"remoteRequestsProcessed\":10773,\"lastReadMillis\":0,"
		+ "\"targetStateVersion\":229529},\"id\":\"1\",\"jsonrpc\":\"2.0\"}\n";

	private final HttpClient client = mock(HttpClient.class);

	@Test
	public void testConfiguration() throws IOException {
		prepareClient(CONFIGURATION)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.sync().configuration()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(configuration -> assertEquals(50L, configuration.getMaxLedgerUpdatesRate()))
				.onSuccess(configuration -> assertEquals(3000, configuration.getSyncCheckInterval()))
				.join())
			.join();
	}

	@Test
	public void testData() throws IOException {
		prepareClient(DATA)
			.map(RadixApi::withTrace)
			.onFailure(failure -> fail(failure.toString()))
			.onSuccess(client -> client.sync().data()
				.onFailure(failure -> fail(failure.toString()))
				.onSuccess(data -> assertEquals(207L, data.getProcessed()))
				.onSuccess(data -> assertEquals(229529L, data.getTargetStateVersion()))
				.join())
			.join();
	}

	private Promise<RadixApi> prepareClient(String responseBody) throws IOException {
		@SuppressWarnings("unchecked")
		var response = (HttpResponse<String>) mock(HttpResponse.class);
		var completableFuture = new CompletableFuture<HttpResponse<String>>();

		when(response.body()).thenReturn(responseBody);
		when(client.<String>sendAsync(any(), any())).thenReturn(completableFuture);

		try {
			return AsyncRadixApi.connect(BASE_URL, RadixApi.DEFAULT_PRIMARY_PORT, RadixApi.DEFAULT_SECONDARY_PORT, client);
		} finally {
			completableFuture.completeAsync(() -> response);
		}
	}
}
