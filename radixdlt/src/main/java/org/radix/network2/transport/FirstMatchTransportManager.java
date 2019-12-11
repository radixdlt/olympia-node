package org.radix.network2.transport;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network2.messaging.TransportManager;
import org.radix.network2.transport.tcp.TCPConstants;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

// Needs replacing with a transport manager that does a better job of
// selecting a match.
public class FirstMatchTransportManager implements TransportManager {
	private static final Logger log = Logging.getLogger("transport");

	private ImmutableMap<String, Transport> transports;

	@Inject
	public FirstMatchTransportManager(Set<Transport> transports) {
		this.transports = transports.stream().collect(ImmutableMap.toImmutableMap(Transport::name, Function.identity()));
		if (defaultTransport() == null) {
			log.warn("No default transport!  Things will be quiet.");
		}
	}

	@Override
	public Collection<Transport> transports() {
		return transports.values();
	}

	@Override
	public Transport findTransport(Stream<TransportInfo> peerTransports, byte[] bytes) {
		if (peerTransports != null) {
			// First that matches for now.  Later we can check against message size etc
			return peerTransports
				.map(TransportInfo::name)
				.map(transports::get)
				.filter(Objects::nonNull)
				.filter(t -> t.canHandle(bytes))
				.findFirst()
				.orElseGet(this::defaultTransport);
		}
		return defaultTransport();
	}

	@Override
	public void close() throws IOException {
		transports.values().forEach(this::closeSafely);
	}

	@Override
	public String toString() {
		String transportNames = transports.keySet().stream().collect(Collectors.joining(","));
		return String.format("%s[%s]", getClass().getSimpleName(), transportNames);
	}

	private Transport defaultTransport() {
		return transports.get(TCPConstants.TCP_NAME);
	}

	private void closeSafely(Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (IOException | UncheckedIOException e) {
				log.warn("While closing " + c, e);
			}
		}
	}
}
