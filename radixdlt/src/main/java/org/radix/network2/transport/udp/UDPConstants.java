package org.radix.network2.transport.udp;

public class UDPConstants {
	private UDPConstants() {
		throw new IllegalStateException("Can't construct");
	}

	public static final String UDP_NAME = "UDP";

	public static final String METADATA_UDP_HOST = "host";
	public static final String METADATA_UDP_PORT = "port";

	static final int MAX_PACKET_LENGTH = 64_000;
}
