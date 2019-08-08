package org.radix.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.function.LongSupplier;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Longs;

public final class PublicInetAddress {
	@VisibleForTesting
	static final int SECRET_LIFETIME_MS = 60_000;
	private static final Logger log = Logging.getLogger("network");

	static boolean isPublicUnicastInetAddress(InetAddress address) {
		return ! (address.isSiteLocalAddress() || address.isLinkLocalAddress() ||
				  address.isLoopbackAddress() || address.isMulticastAddress());
	}

	private static PublicInetAddress instance = null;
	private static final Object INSTANCE_LOCK = new Object();

	public static PublicInetAddress getInstance() {
		synchronized(INSTANCE_LOCK) {
			if (instance == null) {
				configure(null, Modules.get(Universe.class).getPort());
			}
			return instance;
		}
	}

	public static void configure(String localAddress, int localPort) {
		synchronized(INSTANCE_LOCK) {
			instance = new PublicInetAddress(localAddress, localPort, System::currentTimeMillis);
		}
	}

	private final Object lock = new Object();
	private final Random prng = new Random(System.nanoTime());

	private final LongSupplier timeSource;
	private final InetAddress localAddress;
	private final int localPort;

	private InetAddress confirmedAddress;
	private InetAddress unconfirmedAddress;
	private long secret;
	private long secretEndOfLife = Long.MIN_VALUE; // Very much expired

	@VisibleForTesting
	PublicInetAddress(String localAddress, int localPort, LongSupplier timeSource) {
		this.timeSource = timeSource;
		this.localPort = localPort;
		this.localAddress = getLocalAddress(localAddress);
	}

	public InetAddress get() {
		synchronized (lock) {
			return confirmedAddress == null ? localAddress : confirmedAddress;
		}
	}

	/**
	 * Sends a challenge to the given address if necessary.
	 * <p>
	 * The caller will receive a special UDP, which should be passed to the endValidation() methods.
	 *
	 * @param address untrusted address to validate
	 * @see #endValidation(DatagramPacket)
	 */
	void startValidation(InetAddress address) throws IOException {
		long data;

		// update state in a thread-safe manner
		synchronized (lock) {
			if (address == null) {
				// Reset
				confirmedAddress = null;
				return;
			}
			// If we are already matched, or our secret has not yet expired, just exit
			long now = timeSource.getAsLong();
			if (address.equals(confirmedAddress) || secretEndOfLife > now) {
				return;
			}

			unconfirmedAddress = address;
			data = secret = prng.nextLong();

			// secret is valid for a minute - plenty of time to validate the address
			// in the mean time we do not trigger of new validation - it could act as an attack vector.
			secretEndOfLife = now + SECRET_LIFETIME_MS;
		}

		log.info("validating untrusted public address: " + address);
		sendSecret(address, data);
	}

	/**
	 * The caller needs to filter all packets with this method to catch validation UDP frames.
	 *
	 * @param packet packet previously sent by start validation.
	 * @return true when packet was part of the validation process(and can be ignored by the caller) false otherwise.
	 */
	boolean endValidation(DatagramPacket packet) {
		// Make sure secret doesn't change mid-check
		long secret = this.secret;

		// quick return - in case this is not our packet, or we have not yet been set up
		if (packet == null || packet.getLength() != Long.BYTES) {
			return false;
		}

		if (Longs.fromByteArray(packet.getData()) != secret) {
			return false;
		}

		log.info("public address is confirmed valid: " + unconfirmedAddress);

		// update state in a thread-safe manner
		synchronized (lock) {
			confirmedAddress = unconfirmedAddress;
		}

		// tell the caller that this packet should be ignored.
		return true;
	}

	@Override
	@JsonValue
	public String toString() {
		return get().toString();
	}

	private void sendSecret(InetAddress address, long secret) throws IOException {
		byte[] bytes = Longs.toByteArray(secret);
		DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, localPort);
		try (DatagramSocket socket = new DatagramSocket(null)) {
			socket.send(packet);
		}
	}

	private InetAddress getLocalAddress(String localAddress) {
		try {
			if (localAddress != null) {
				return InetAddress.getByName(localAddress);
			}
		} catch (UnknownHostException e) {
			// Ignore and fall through
		}
		try {
			return InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			return InetAddress.getLoopbackAddress();
		}
	}
}
