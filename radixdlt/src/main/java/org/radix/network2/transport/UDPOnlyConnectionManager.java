package org.radix.network2.transport;

import java.io.IOException;
import java.util.HashMap;

import org.radix.network.peers.Peer;
import org.radix.network2.IOFunction;
import org.radix.network2.messaging.ConnectionManager;
import org.radix.network2.transport.udp.UDPConstants;
import org.radix.utils.IOUtils;

import com.google.common.annotations.VisibleForTesting;

// FIXME: Remove this when network2 complete
// Needs replacing with a connection manager that handles other transports
public class UDPOnlyConnectionManager implements ConnectionManager {

	private final Object lock = new Object();
	private final HashMap<TransportMetadata, Transport> connections = new HashMap<>();
	private TransportFactory transportFactory;

	public UDPOnlyConnectionManager(TransportFactory transportFactory) {
		this.transportFactory = transportFactory;
	}

	@Override
	public Transport findTransport(Peer peer, byte[] bytes) {
		TransportMetadata metadata = peer.connectionData(UDPConstants.UDP_NAME);
		// TODO: Check if this locking style is too slow
		synchronized (lock) {
			return connections.computeIfAbsent(metadata, IOFunction.unchecked(this::createUDPTransportImpl));
		}
	}

	@Override
	public void close() throws IOException {
		synchronized (lock) {
			connections.values().forEach(IOUtils::closeSafely);
			connections.clear();
		}
	}

	@VisibleForTesting
	int size() {
		synchronized (lock) {
			return this.connections.size();
		}
	}

	private Transport createUDPTransportImpl(TransportMetadata metadata) throws IOException {
		return this.transportFactory.create(metadata);
	}

	@Override
	public String toString() {
		return String.format("%s[connections=%s]", getClass().getSimpleName(), connections.size());
	}
}
