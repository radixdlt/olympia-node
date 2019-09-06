package org.radix.network2.addressbook;

import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.udp.UDPConstants;
import org.radix.serialization.SerializeMessageObject;

/**
 * Check serialization of PeerWithTransport
 */
public class PeerWithTransportSerializeTest extends SerializeMessageObject<PeerWithTransport> {
	public PeerWithTransportSerializeTest() {
		super(PeerWithTransport.class, PeerWithTransportSerializeTest::get);
	}

	private static PeerWithTransport get() {
		TransportInfo ti = TransportInfo.of(UDPConstants.UDP_NAME,
			StaticTransportMetadata.of(
				UDPConstants.METADATA_UDP_HOST, "127.0.0.1",
				UDPConstants.METADATA_UDP_PORT, "10000"
			)
		);
		return new PeerWithTransport(ti);
	}
}
