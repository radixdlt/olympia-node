package com.radixdlt.client.core.network;

import static org.junit.Assert.assertTrue;

import okhttp3.OkHttpClient;
import org.junit.Test;

public class HttpClientsTest {
	@Test
	public void testClientCreation() {
		OkHttpClient client = HttpClients.getSslAllTrustingClient();
		for (int i = 0; i < 10; i++) {
			assertTrue(client == HttpClients.getSslAllTrustingClient());
		}
	}
}