package org.radix.network.discovery;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network.Network;
import org.radix.network.SSLFix;
import org.radix.network.peers.Peer;
import org.radix.network.peers.PeerStore;
import org.radix.network.peers.filters.PeerFilter;
import org.radix.properties.RuntimeProperties;
import com.radixdlt.universe.Universe;

public class BootstrapDiscovery implements Discovery
{
	// https://en.wikipedia.org/wiki/Domain_Name_System
	private static final int MAX_DNS_NAME_OCTETS = 253;

	private static final Logger log = Logging.getLogger();

	public static synchronized BootstrapDiscovery getInstance()
	{
		if (instance == null)
			instance = new BootstrapDiscovery();

		return instance;
	}

	private static BootstrapDiscovery instance = null;

	private Set<URI> hosts = new HashSet<URI>();

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
	private static String toHost(byte[] buf, int len)
	{
		for (int i = 0; i < len; i++)
		{
			if ('0' <= buf[i] && '9' >= buf[i] || 'a' <= buf[i] && 'z' >= buf[i] ||
				'A' <= buf[i] && 'Z' >= buf[i] || '.' == buf[i] || '-' == buf[i])
			{
				continue;
			}
			return null;
		}
		return new String(buf, 0, len, StandardCharsets.US_ASCII);
	}

	/**
	 * Tries to determine if a host is reachable by connecting (TCP) to the specified port.
	 */
	private static void testConnection(String host, int port, int timeout) throws IOException
	{
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), timeout);
		}
	}

	/**
	 * GET a node from the given node discovery service.
	 *
	 * The node service:
	 * - might return a stale node
	 * - might be temporary unreachable
	 * - might be compromized (don't trust it)
	 */
	private static String getNextNode(URL nodeFinderURL)
	{
		RuntimeProperties cfg = Modules.get(RuntimeProperties.class);
		// Default retry total time = 30 * 10 = 300 seconds = 5 minutes
		long retries = cfg.get("network.discovery.connection.retries", 30);
		// NOTE: min is 10 seconds - we don't allow less
		int cooldown = cfg.get("network.discovery.connection.cooldown", 1) * 10000;
		int connectionTimeout = cfg.get("network.discovery.connection.timeout", 60000);
		int readTimeout = cfg.get("network.discovery.read.timeout", 60000);

		// Wallets (and other apps) that rely on the API can obviously check port the API port (8443).
		int checkPort = Modules.get(Universe.class).getPort();

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
				log.warn("invalid host returned by node finder: "+host, e);
				break;
			}
			finally
			{
				if (input != null)
					try { input.close(); } catch (IOException ignoredExceptionOnClose) { }
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

	private BootstrapDiscovery()
	{
		RuntimeProperties cfg = Modules.get(RuntimeProperties.class);
		HashSet<String> hosts = new HashSet<String>();

		// allow nodes to connect to others, bypassing TLS handshake
		if(cfg.get("network.discovery.allow_tls_bypass", 0) == 1) {
			log.info("Allowing TLS handshake bypass...");
			SSLFix.trustAllHosts();
		}

		for (String unparsedURL : cfg.get("network.discovery.urls", "").split(","))
		{
			unparsedURL = unparsedURL.trim();
			if (unparsedURL.isEmpty())
				continue;
			try
			{
				// if host is an URL - we should GET the node from the given URL
				URL url = new URL(unparsedURL);
				if (!url.getProtocol().equals("https"))
					throw new IllegalStateException("cowardly refusing all but HTTPS network.seeds");

				String host = getNextNode(url);
				if (host != null)
				{
					log.info("seeding from random host: "+host);
					hosts.add(host);
				}
			}
			catch (MalformedURLException ignoreConcreteHost)
			{
				// concrete host addresses end up here.
			}
		}

		for (String host : cfg.get("network.seeds", "").split(","))
		{
			hosts.add(host);
		}

		for (String host : hosts)
		{
			host = host.trim();
			if (host.isEmpty())
				continue;
			try
			{
				if (!Network.getInstance().isWhitelisted(Network.getURI(host)))
					continue;

				this.hosts.add(Network.getURI(host.trim()));
			}
			catch (Exception ex)
			{
				log.error("Could not add bootstrap "+host.trim(), ex);
			}
		}
	}

	@Override
	public Collection<URI> discover(PeerFilter filter)
	{
		List<URI> results = new ArrayList<URI>();

		for (URI host : hosts)
		{
			try
			{
				if (filter == null)
					results.add(host);
				else
				{
					Peer peer = Modules.get(PeerStore.class).getPeer(host);

					if (peer == null || !filter.filter(peer))
						results.add(host);
				}
			}
			catch (Exception ex)
			{
				log.error("Could not process BootstrapDiscovery for host:"+host, ex);
			}
		}

		Collections.shuffle(results);

		return results;
	}
}
