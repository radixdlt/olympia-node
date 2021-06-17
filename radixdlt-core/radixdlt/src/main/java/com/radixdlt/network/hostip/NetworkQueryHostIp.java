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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import com.google.common.net.HostAndPort;
import com.radixdlt.properties.RuntimeProperties;

/**
 * Query for a public IP address using an oracle.
 * This class can be used to query a single oracle, or if a number
 * of oracles are provided, a simple majority vote is used.
 */
final class NetworkQueryHostIp implements HostIp {
	private static final Logger log = LogManager.getLogger();

	@VisibleForTesting
	static final String QUERY_URLS_PROPERTY = "network.host_ip_query_urls";

	@VisibleForTesting
	static final ImmutableList<URL> DEFAULT_QUERY_URLS = ImmutableList.of(
		makeurl("https://checkip.amazonaws.com/"),
		makeurl("https://ipv4.icanhazip.com/"),
		makeurl("https://myexternalip.com/raw"),
		makeurl("https://ipecho.net/plain"),
		makeurl("https://bot.whatismyipaddress.com/"),
		makeurl("https://www.trackip.net/ip"),
		makeurl("https://ifconfig.co/ip")
	);

	static HostIp create(Collection<URL> urls) {
		return new NetworkQueryHostIp(urls);
	}

	static HostIp create(RuntimeProperties properties) {
		String urlsProperty = properties.get(QUERY_URLS_PROPERTY, "");
		if (urlsProperty == null || urlsProperty.trim().isEmpty()) {
			return create(DEFAULT_QUERY_URLS);
		}
		ImmutableList<URL> urls = Arrays.asList(urlsProperty.split(","))
			.stream()
			.map(NetworkQueryHostIp::makeurl)
			.collect(ImmutableList.toImmutableList());
		return create(urls);
	}

	private final List<URL> hosts;
	private final Supplier<Optional<String>> result = Suppliers.memoize(this::get);

	NetworkQueryHostIp(Collection<URL> urls) {
		if (urls.isEmpty()) {
			throw new IllegalArgumentException("At least one URL must be specified");
		}
		this.hosts = new ArrayList<>(urls);
	}

	int count() {
		return this.hosts.size();
	}

	@Override
	public Optional<String> hostIp() {
		return result.get();
	}

	Optional<String> get() {
		return publicIp((count() + 1) / 2); // Round up
	}

	Optional<String> publicIp(int threshold) {
		// Make sure we don't DoS the first one on the list
		Collections.shuffle(this.hosts);
		log.debug("Using hosts {}", this.hosts);
		final Map<HostAndPort, AtomicInteger> ips = Maps.newHashMap();
		for (URL url : this.hosts) {
			HostAndPort q = query(url);
			if (q != null) {
				int newValue = ips.computeIfAbsent(q, k -> new AtomicInteger()).incrementAndGet();
				if (newValue >= threshold) {
					log.info("Found address {}", q);
					return Optional.of(q.getHost());
				}
			}
		}
		log.info("No suitable address found");
		return Optional.empty();
	}

	@VisibleForTesting
	static HostAndPort query(URL url) {
		try {
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");
			// Pretty much required by shared hosting
			con.setRequestProperty("Host", url.getHost());

			// A user agent is required by some hosts
			con.setRequestProperty("User-Agent", "curl/7.58.0");

			// Some don't like it unless an accept is set
			con.setRequestProperty("Accept", "*/*");

			int status = con.getResponseCode();
			if (status > 299) {
				log.debug("Host {} failed with status {}", url, status);
				return null;
			}

			try (
				InputStream is = con.getInputStream();
				InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)
			) {
				String result = CharStreams.toString(isr).trim();
				log.debug("Host {} returned {}", url, result);
				return HostAndPort.fromHost(result);
			}
		} catch (IOException | IllegalArgumentException ex) {
			// Ignored
			if (log.isDebugEnabled()) {
				log.debug(String.format("Host %s failed with exception", url), ex);
			}
			return null;
		}
	}

	private static URL makeurl(String s) {
		try {
			return new URL(s);
		} catch (MalformedURLException ex) {
			throw new IllegalStateException("While constructing URL for " + s, ex);
		}
	}
}