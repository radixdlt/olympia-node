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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.google.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeerPredicate;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.tcp.TCPConstants;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.universe.Universe;

public class BootstrapDiscovery
{
	// https://en.wikipedia.org/wiki/Domain_Name_System
	private static final int MAX_DNS_NAME_OCTETS = 253;

	private static final Logger log = LogManager.getLogger();
	private final RuntimeProperties properties;
	private final Universe universe;

	private Set<TransportInfo> hosts = new HashSet<>();

	/**
	 * Safely converts the data recieved by the find-nodes to a potential hostname.
	 *
	 * Potential: only limited validation (the character set) is validated by this function.
	 *
	 * Accepted chars:
	 * - contained in IPv4 addresses: [0-9.]
	 * - contained in IPv6 addresses: [a-zA-Z0-9:]
	 * - contained in non-internationalized DNS names: [a-zA-Z0-9]
	 *   https://www.icann.org/resources/pages/beginners-guides-2012-03-06-en
	 */
	@VisibleForTesting
	static String toHost(byte[] buf, int len)
	{
		for (int i = 0; i < len; i++)
		{
			if ('0' <= buf[i] && '9' >= buf[i] || 'a' <= buf[i] && 'z' >= buf[i] ||
				'A' <= buf[i] && 'Z' >= buf[i] || '.' == buf[i] || '-' == buf[i] || ':' == buf[i])
			{
				continue;
			}
			return null;
		}
		return new String(buf, 0, len, StandardCharsets.US_ASCII);
	}

	@Inject
	public BootstrapDiscovery(RuntimeProperties properties, Universe universe) {
		this.properties = Objects.requireNonNull(properties);
		this.universe = Objects.requireNonNull(universe);

		// allow nodes to connect to others, bypassing TLS handshake
		if (properties.get("network.discovery.allow_tls_bypass", 0) == 1) {
			log.info("Allowing TLS handshake bypass...");
			SSLFix.trustAllHosts();
		}

		HashSet<String> hosts = new HashSet<>();
		for (String unparsedURL : properties.get("network.discovery.urls", "").split(",")) {
			unparsedURL = unparsedURL.trim();
			if (unparsedURL.isEmpty()) {
				continue;
			}
			try
			{
				// if host is an URL - we should GET the node from the given URL
				URL url = new URL(unparsedURL);
				if (!url.getProtocol().equals("https")) {
					throw new IllegalStateException("cowardly refusing all but HTTPS network.seeds");
				}

				String host = getNextNode(url);
				if (host != null) {
					log.info("seeding from random host: {}",  host);
					hosts.add(host);
				}
			} catch (MalformedURLException ignoreConcreteHost) {
				// concrete host addresses end up here.
			}
		}

		hosts.addAll(Arrays.asList(properties.get("network.seeds", "").split(",")));

		Whitelist whitelist = Whitelist.from(properties);
		for (String host : hosts) {
			host = host.trim();
			if (host.isEmpty() || !whitelist.isWhitelisted(host)) {
				continue;
			}
			try {
				this.hosts.add(toDefaultTransportInfo(host));
			} catch (IllegalArgumentException | UnknownHostException e) {
				log.error("Host specification {} does not specify a valid host and port", host);
			}
		}
	}

	/**
	 * GET a node from the given node discovery service.
	 *
	 * The node service:
	 * - might return a stale node
	 * - might be temporary unreachable
	 * - might be compromised (don't trust it)
	 */
	@VisibleForTesting
	String getNextNode(URL nodeFinderURL)
	{
		// Default retry total time = 30 * 10 = 300 seconds = 5 minutes
		long retries = properties.get("network.discovery.connection.retries", 30);
		// NOTE: min is 10 seconds - we don't allow less
		int cooldown = properties.get("network.discovery.connection.cooldown", 1) * 10000;
		int connectionTimeout = properties.get("network.discovery.connection.timeout", 60000);
		int readTimeout = properties.get("network.discovery.read.timeout", 60000);

		long attempt = 0;
		byte[] buf = new byte[MAX_DNS_NAME_OCTETS];
		while (attempt++ != retries)
		{ // NOTE: -1 => infinite number of attempts (in practice)
			String host = null;
			BufferedInputStream input = null;
			try
			{
				// open connection
				URLConnection conn = nodeFinderURL.openConnection();
				// spoof User-Agents otherwise some CDNs do not let us through.
				conn.setRequestProperty("User-Agent", "curl/7.54.0");
				conn.setAllowUserInteraction(false); // no follow symlinks - just plain old direct links
				conn.setUseCaches(false);
				conn.setConnectTimeout(connectionTimeout);
				conn.setReadTimeout(readTimeout);
				conn.connect();

				// read data
				input = new BufferedInputStream(conn.getInputStream());
				int n = input.read(buf);
				if (n > 0)
				{
					host = toHost(buf, n);
					if (host != null)
					{
						// FIXME - Disable broken connection testing now that we no longer
						// use TCP for exchanging data.  Needs resolving when we have a
						// workable mechanism for node connectivity checking.
						//testConnection(host, checkPort, connectionTimeout);
						return host;
					}
				}
			}
			catch (IOException e)
			{
				// rejected, offline, etc. - this is expected
				log.info("host is not reachable", e);
			}
			catch (RuntimeException e)
			{
				// rejected, offline, etc. - this is expected
				log.warn("invalid host returned by node finder: " + host, e);
				break;
			}
			finally
			{
				if (input != null) {
					try {
						input.close();
					} catch (IOException ignoredExceptionOnClose) {
						// Ignored
					}
				}
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

	public Collection<TransportInfo> discover(AddressBook addressbook, PeerPredicate filter)
	{
		List<TransportInfo> results = Lists.newArrayList();

		for (TransportInfo host : hosts) {
			try {
				if (filter == null) {
					results.add(host);
				} else {
					Peer peer = addressbook.peer(host);

					if (peer != null && filter.test(peer)) {
						results.add(host);
					}
				}
			} catch (Exception ex) {
				log.error("Could not process BootstrapDiscovery for host:"+host, ex);
			}
		}

		Collections.shuffle(results);
		return results;
	}

	@VisibleForTesting
	TransportInfo toDefaultTransportInfo(String host) throws UnknownHostException {
		HostAndPort hap = HostAndPort.fromString(host).withDefaultPort(universe.getPort());
		// Resolve any names so we don't have to do it again and again, and we will also be more
		// likely to have a canonical representation.
		InetAddress resolved = InetAddress.getByName(hap.getHost());
		return TransportInfo.of(
			TCPConstants.NAME,
			StaticTransportMetadata.of(
				TCPConstants.METADATA_HOST, resolved.getHostAddress(),
				TCPConstants.METADATA_PORT, String.valueOf(hap.getPort())
			)
		);
	}

}
