/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.network.hostip;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

import com.radixdlt.properties.RuntimeProperties;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NetworkQueryHostIpTest {

	@Test
	public void testPropertyNull() {
		RuntimeProperties properties = mock(RuntimeProperties.class);
		when(properties.get(eq(NetworkQueryHostIp.QUERY_URLS_PROPERTY), any())).thenReturn(null);
		NetworkQueryHostIp nqhip = (NetworkQueryHostIp) NetworkQueryHostIp.create(properties);
		assertEquals(NetworkQueryHostIp.DEFAULT_QUERY_URLS.size(), nqhip.count());
	}

	@Test
	public void testPropertyEmpty() {
		RuntimeProperties properties = mock(RuntimeProperties.class);
		when(properties.get(eq(NetworkQueryHostIp.QUERY_URLS_PROPERTY), any())).thenReturn("");
		NetworkQueryHostIp nqhip = (NetworkQueryHostIp) NetworkQueryHostIp.create(properties);
		assertEquals(NetworkQueryHostIp.DEFAULT_QUERY_URLS.size(), nqhip.count());
	}

	@Test
	public void testPropertyNotEmpty() {
		RuntimeProperties properties = mock(RuntimeProperties.class);
		when(properties.get(eq(NetworkQueryHostIp.QUERY_URLS_PROPERTY), any()))
			.thenReturn("http://localhost,http://8.8.8.8");
		NetworkQueryHostIp nqhip = (NetworkQueryHostIp) NetworkQueryHostIp.create(properties);
		assertEquals(2, nqhip.count());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCollectionEmpty() {
		NetworkQueryHostIp.create(Collections.emptyList());
	}

	@Test
	public void testCollectionNotEmptyQueryFailed() throws IOException {
		HttpURLConnection conn = mock(HttpURLConnection.class);
		doReturn(404).when(conn).getResponseCode();
		URL url = mock(URL.class);
		doReturn(conn).when(url).openConnection();
		NetworkQueryHostIp nqhip = (NetworkQueryHostIp) NetworkQueryHostIp.create(Arrays.asList(url));
		Optional<String> host = nqhip.hostIp();
		assertFalse(host.isPresent());
	}

	@Test
	public void testCollectionNotEmptyQueryFailed2() throws IOException {
		URL url = mock(URL.class);
		doThrow(new IOException("test exception")).when(url).openConnection();
		NetworkQueryHostIp nqhip = (NetworkQueryHostIp) NetworkQueryHostIp.create(Arrays.asList(url));
		Optional<String> host = nqhip.hostIp();
		assertFalse(host.isPresent());
	}

	@Test
	public void testCollectionNotEmpty() throws IOException {
		String data = "127.0.0.1";
		try (ByteArrayInputStream input = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))) {
			HttpURLConnection conn = mock(HttpURLConnection.class);
			doReturn(input).when(conn).getInputStream();
			URL url = mock(URL.class);
			doReturn(conn).when(url).openConnection();
			NetworkQueryHostIp nqhip = (NetworkQueryHostIp) NetworkQueryHostIp.create(Arrays.asList(url));
			Optional<String> host = nqhip.hostIp();
			assertTrue(host.isPresent());
			assertEquals(data, host.get());
		}
	}

	@Test
	public void testCollectionAllDifferent() throws IOException {
		List<URL> urls = Arrays.asList(
			makeUrl("127.0.0.1"),
			makeUrl("127.0.0.2"),
			makeUrl("127.0.0.3"),
			makeUrl("127.0.0.4")
		);
		NetworkQueryHostIp nqhip = (NetworkQueryHostIp) NetworkQueryHostIp.create(urls);
		Optional<String> host = nqhip.hostIp();
		assertFalse(host.isPresent());
	}

	private static URL makeUrl(String data) throws IOException {
		ByteArrayInputStream input = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
		HttpURLConnection conn = mock(HttpURLConnection.class);
		doReturn(input).when(conn).getInputStream();
		URL url = mock(URL.class);
		doReturn(conn).when(url).openConnection();
		return url;
	}
}
