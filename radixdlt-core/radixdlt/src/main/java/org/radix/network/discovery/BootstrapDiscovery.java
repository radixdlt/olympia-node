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

package org.radix.network.discovery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;
import com.google.inject.Inject;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.tcp.TCPConstants;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.universe.Universe;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class BootstrapDiscovery {
	// https://en.wikipedia.org/wiki/Domain_Name_System
	private static final int MAX_DNS_NAME_OCTETS = 253;
	private static final Logger log = LogManager.getLogger();

	private final int defaultPort;
	private final Set<TransportInfo> hosts;

	/**
	 * Safely converts the data received by the find-nodes to a potential hostname.
	 * <p>
	 * Potential: only limited validation (the character set) is validated by this function.
	 * <p>
	 * Accepted chars:
	 * - contained in IPv4 addresses: [0-9.]
	 * - contained in IPv6 addresses: [a-zA-Z0-9:]
	 * - contained in non-internationalized DNS names: [a-zA-Z0-9]
	 * https://www.icann.org/resources/pages/beginners-guides-2012-03-06-en
	 */
	@VisibleForTesting
	static String toHost(byte[] buf, int len) {
		for (int i = 0; i < len; i++) {
			if ('0' <= buf[i] && '9' >= buf[i] || 'a' <= buf[i] && 'z' >= buf[i]
				|| 'A' <= buf[i] && 'Z' >= buf[i] || '.' == buf[i] || '-' == buf[i] || ':' == buf[i]) {
				continue;
			}
			return null;
		}
		return new String(buf, 0, len, StandardCharsets.US_ASCII);
	}

	@Inject
	public BootstrapDiscovery(RuntimeProperties properties, Universe universe) {
		// Default retry total time = 30 * 10 = 300 seconds = 5 minutes
		int retries = properties.get("network.discovery.connection.retries", 30);
		// NOTE: min is 10 seconds - we don't allow less
		int cooldown = Math.max(10_000, properties.get("network.discovery.connection.cooldown", 10_000));
		int connectionTimeout = properties.get("network.discovery.connection.timeout", 60000);
		int readTimeout = properties.get("network.discovery.read.timeout", 60000);

		this.defaultPort = universe.getPort();

		checkBypassTLSHandshake(properties);

		var hostNames = collectHostnames(properties, retries, cooldown, connectionTimeout, readTimeout);

		hostNames.addAll(List.of(properties.get("network.seeds", "").split(",")));

		var whitelist = Whitelist.from(properties);

		this.hosts = hostNames.stream()
			.map(String::trim)
			.filter(hn -> !hn.isEmpty() && whitelist.isWhitelisted(hn))
			.distinct()
			.map(this::toDefaultTransportInfo)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.collect(Collectors.toUnmodifiableSet());
	}

	private void checkBypassTLSHandshake(final RuntimeProperties properties) {
		if (properties.get("network.discovery.allow_tls_bypass", 0) != 1) {
			return;
		}

		log.info("Allowing TLS handshake bypass...");
		SSLFix.trustAllHosts();
	}

	private List<String> collectHostnames(
		final RuntimeProperties properties,
		final int retries,
		final int cooldown,
		final int connectionTimeout,
		final int readTimeout
	) {
		var hostNames = Lists.<String>newArrayList();

		for (var urlString : properties.get("network.discovery.urls", "").split(",")) {
			urlString = urlString.trim();

			if (urlString.isEmpty()) {
				continue;
			}

			try {
				// if host is an URL - we should GET the node from the given URL
				var url = toURL(urlString);
				var host = getNextNode(url, retries, cooldown, connectionTimeout, readTimeout);

				if (host == null) {
					continue;
				}

				log.info("seeding from random host: {}", host);
				hostNames.add(host);
			} catch (MalformedURLException ignoreConcreteHost) {
				// concrete host addresses end up here.
			}
		}
		return hostNames;
	}

	private URL toURL(final String urlString) throws MalformedURLException {
		var url = new URL(urlString);
		if (!url.getProtocol().equals("https")) {
			throw new IllegalStateException("cowardly refusing all but HTTPS network.seeds");
		}
		return url;
	}

	/**
	 * GET a node from the given node discovery service.
	 * <p>
	 * The node service:
	 * - might return a stale node
	 * - might be temporary unreachable
	 * - might be compromised (don't trust it)
	 */
	@VisibleForTesting
	String getNextNode(URL nodeFinderURL, int retries, int cooldown, int connectionTimeout, int readTimeout) {
		long attempt = 0;
		byte[] buf = new byte[MAX_DNS_NAME_OCTETS];

		while (attempt++ != retries) { // NOTE: -1 => infinite number of attempts (in practice)
			String host = null;

			try {
				// open connection
				var conn = nodeFinderURL.openConnection();
				// spoof User-Agents otherwise some CDNs do not let us through.
				conn.setRequestProperty("User-Agent", "curl/7.54.0");
				conn.setAllowUserInteraction(false); // no follow symlinks - just plain old direct links
				conn.setUseCaches(false);
				conn.setConnectTimeout(connectionTimeout);
				conn.setReadTimeout(readTimeout);
				conn.connect();

				// read data
				try (var input = new BufferedInputStream(conn.getInputStream())) {
					int n = input.read(buf);

					if (n > 0) {
						host = toHost(buf, n);
						if (host != null) {
							// FIXME - Disable broken connection testing now that we no longer
							// use TCP for exchanging data.  Needs resolving when we have a
							// workable mechanism for node connectivity checking.
							//testConnection(host, checkPort, connectionTimeout);
							//TODO: send PING and wait for PONG
							return host;
						}
					}
				}
			} catch (IOException e) {
				// rejected, offline, etc. - this is expected
				log.info("host is not reachable", e);
			} catch (RuntimeException e) {
				// rejected, offline, etc. - this is expected
				log.warn("invalid host returned by node finder: " + host, e);
				break;
			}

			try {
				// sleep until next attempt
				Thread.sleep(cooldown);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		return null;
	}

	/**
	 * Return a list of transports for discovery hosts.
	 *
	 * @return A collection of transports for discovery hosts
	 */
	public Collection<TransportInfo> discoveryHosts() {
		var results = new ArrayList<>(this.hosts);
		Collections.shuffle(results);
		return results;
	}

	@VisibleForTesting
	Optional<TransportInfo> toDefaultTransportInfo(String host) {
		var hap = HostAndPort.fromString(host).withDefaultPort(defaultPort);
		// Resolve any names so we don't have to do it again and again, and we will also be more
		// likely to have a canonical representation.
		try {
			var resolved = InetAddress.getByName(hap.getHost());
			return Optional.of(
				TransportInfo.of(
					TCPConstants.NAME,
					StaticTransportMetadata.of(
						TCPConstants.METADATA_HOST, resolved.getHostAddress(),
						TCPConstants.METADATA_PORT, String.valueOf(hap.getPort())
					)
				)
			);
		} catch (UnknownHostException e) {
			return Optional.empty();
		}
	}
}
