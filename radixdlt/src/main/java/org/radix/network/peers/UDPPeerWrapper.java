package org.radix.network.peers;

import java.net.URI;
import java.util.stream.Stream;

import org.radix.network2.addressbook.PeerTimestamps;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportException;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.udp.UDPConstants;
import org.radix.time.Timestamps;
import org.radix.universe.system.RadixSystem;

import com.radixdlt.common.EUID;

// FIXME: Temporary shim for connecting UDPPeer to MessageCentralImpl
final class UDPPeerWrapper implements org.radix.network2.addressbook.Peer {

	private final TransportMetadata transportMetadata;
	private final TransportInfo transportInfo;
	private final EUID nid;
	private final PeerTimestamps timestamps;

	UDPPeerWrapper(UDPPeer peer) {
		URI uri = peer.getURI();
		String host = uri.getHost();
		String port = String.valueOf(uri.getPort());

		this.transportMetadata = StaticTransportMetadata.of(
			UDPConstants.METADATA_UDP_HOST, host,
			UDPConstants.METADATA_UDP_PORT, port
		);
		this.transportInfo = TransportInfo.of(UDPConstants.UDP_NAME, this.transportMetadata);
		RadixSystem peerSystem = peer.getSystem();
		this.nid = peerSystem == null ? null : peerSystem.getNID();

		// Not right, but going with this for now as the wrapper will not be long lived
		// and will also be removed when everything hooked up correctly
		this.timestamps = PeerTimestamps.of(
			peer.getTimestamp(Timestamps.ACTIVE),
			peer.getTimestamp(Timestamps.BANNED)
		);
	}

	@Override
	public EUID getNID() {
		return this.nid;
	}

	@Override
	public PeerTimestamps getTimestamps() {
		return this.timestamps;
	}

	@Override
	public boolean supportsTransport(String transportName) {
		return UDPConstants.UDP_NAME.equals(transportName);
	}

	@Override
	public Stream<TransportInfo> supportedTransports() {
		return Stream.of(transportInfo);
	}

	@Override
	public TransportMetadata connectionData(String transportName) {
		if (UDPConstants.UDP_NAME.equals(transportName)) {
			return transportMetadata;
		}
		throw new TransportException("Unsupported transport: " + transportName);
	}
}
