package org.radix.network2.transport.udp;

/**
 * Various constants used by the UDP transport.
 */
public class UDPConstants {
	private UDPConstants() {
		throw new IllegalStateException("Can't construct");
	}

	/**
	 * The UDP transport name.
	 */
	public static final String UDP_NAME = "UDP";

	/**
	 * The "host" property for the UDP transport.
	 * Can be an IPv4/IPv6 raw address or host name.
	 */
	public static final String METADATA_UDP_HOST = "host";
	/**
	 * The "port" property for the UDP transport.
	 */
	public static final String METADATA_UDP_PORT = "port";

	/**
	 * Maximum packet size that we will attempt to send.
	 * Note that we have given ourselves some headroom here.
	 */
	static final int MAX_PACKET_LENGTH = 64_000;
}
