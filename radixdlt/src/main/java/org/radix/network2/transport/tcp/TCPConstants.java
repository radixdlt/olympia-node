package org.radix.network2.transport.tcp;

/**
 * Various constants used by the TCP transport.
 */
public class TCPConstants {
	private TCPConstants() {
		throw new IllegalStateException("Can't construct");
	}

	/**
	 * The TCP transport name.
	 */
	public static final String TCP_NAME = "TCP";

	/**
	 * The "host" property for the TCP transport.
	 * Can be an IPv4/IPv6 raw address or host name.
	 */
	public static final String METADATA_TCP_HOST = "host";
	/**
	 * The "port" property for the TCP transport.
	 */
	public static final String METADATA_TCP_PORT = "port";

	/**
	 * Maximum packet size that we will attempt to send.
	 */
	static final int MAX_PACKET_LENGTH = 1024 * 1024;

	/**
	 * Size of prepended/decoded frame length.
	 */
	static final int LENGTH_HEADER = Integer.BYTES;
}
