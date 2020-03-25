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

package org.radix.network2.transport.udp;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.function.LongSupplier;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.utils.Longs;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;

/**
 * Class for NAT handling/discovery.
 */
//FIXME: Should not be a singleton.  Fix this.
public final class PublicInetAddress {
	@VisibleForTesting
	static final int SECRET_LIFETIME_MS = 60_000;
	private static final Logger log = Logging.getLogger("transport.udp");

	public static boolean isPublicUnicastInetAddress(InetAddress address) {
		return ! (address.isSiteLocalAddress() || address.isLinkLocalAddress() ||
				  address.isLoopbackAddress() || address.isMulticastAddress());
	}

	private static PublicInetAddress instance = null;
	private static final Object INSTANCE_LOCK = new Object();

	public static PublicInetAddress getInstance() {
		synchronized(INSTANCE_LOCK) {
			if (instance == null) {
				throw new IllegalStateException("instance not configured");
			}
			return instance;
		}
	}

	public static void configure(int localPort) {
		synchronized(INSTANCE_LOCK) {
			instance = new PublicInetAddress(localPort, System::currentTimeMillis);
		}
	}

	private final Object lock = new Object();
	private final Random prng = new Random(System.nanoTime());

	private final InetAddress localAddress;
	private final int localPort;
	private final LongSupplier timeSource;

	private InetAddress confirmedAddress;
	private InetAddress unconfirmedAddress;
	private long secret;
	private long secretEndOfLife = Long.MIN_VALUE; // Very much expired

	@VisibleForTesting
	PublicInetAddress(int localPort, LongSupplier timeSource) {
		this.localAddress = getLocalAddress();
		this.localPort = localPort;
		this.timeSource = timeSource;
	}

	public InetAddress get() {
		synchronized (lock) {
			return confirmedAddress == null ? localAddress : confirmedAddress;
		}
	}

	/**
	 * Reset confirmed address.
	 */
	void reset() {
		synchronized (lock) {
			confirmedAddress = null;
		}
	}

	/**
	 * Handle an inbound packet to check if address checking/challenge is required.
	 * <p>
	 * Note that data will be consumed from {@code buf} as required to handle NAT processing.
	 *
	 * @param ctx channel context to write any required address challenges on
	 * @param peerAddress the address of the sending peer
	 * @param buf the inbound packet
	 */
	void handleInboundPacket(ChannelHandlerContext ctx, InetAddress peerAddress, ByteBuf buf) {
		int length = buf.readableBytes();
		if (length > 0) {
			byte firstByte = buf.getByte(0); // peek
			if ((firstByte & 0x80) != 0) {

				// NAT: decode the source and dest addresses (see UDPPeer for how this is encoded)
				byte[] rawLocalAddress = new byte[(firstByte & 0x01) != 0 ? 16 : 4];
				byte[] rawPeerAddress  = new byte[(firstByte & 0x02) != 0 ? 16 : 4];

				if ((rawPeerAddress.length + rawLocalAddress.length) < length) {
					buf.readByte(); // discard peeked first byte

					try {
						buf.readBytes(rawPeerAddress);
						buf.readBytes(rawLocalAddress);

						InetAddress addr = InetAddress.getByAddress(rawPeerAddress);
						// TODO: if addr is previously unknown we need to challenge it to prevent peer table poisoning:
						// See "Proposed solution for Routing Table Poisoning" in https://pdfs.semanticscholar.org/3990/e316c8ecedf8398bd6dc167d92f094525920.pdf
						if (!isPublicUnicastInetAddress(peerAddress) && isPublicUnicastInetAddress(addr)) {
							peerAddress = addr;
						}

						InetAddress localAddr = InetAddress.getByAddress(rawLocalAddress);
						if (isPublicUnicastInetAddress(localAddr)) {
							startValidation(ctx, localAddr);
						}
					} catch (UnknownHostException e) {
						log.error("While processing NAT address", e);
					}
				}
			}
		}
	}

	/**
	 * The caller needs to filter all packets with this method to catch validation UDP frames.
	 * <p>
	 * Note that no data will be consumed from {@code buf}.
	 *
	 * @param bytes packet previously sent by start validation.
	 * @return true when packet was part of the validation process(and can be ignored by the caller) false otherwise.
	 */
	boolean endValidation(ByteBuf buf) {
		// Make sure secret doesn't change mid-check
		long secret = this.secret;

		// quick return - in case this is not our packet, or we have not yet been set up
		if (buf == null || buf.readableBytes() != Long.BYTES) {
			return false;
		}

		// Note we don't read here, just peek
		if (buf.getLong(0) != secret) {
			// Check packet, but wrong data -> ignore
			return true;
		}

		final String toPrint;
		synchronized (lock) {
			if (unconfirmedAddress == null) {
				// Not set up -> can't continue, but need to ignore
				return true;
			}

			// update state in a thread-safe manner
			confirmedAddress = unconfirmedAddress;
			toPrint = confirmedAddress.getHostAddress();
		}
		log.info("public address is confirmed valid: " + toPrint);

		// tell the caller that this packet should be ignored.
		return true;
	}

	/**
	 * Sends a challenge to the given address if necessary.
	 * <p>
	 * The caller will receive a special UDP, which should be passed to the endValidation() methods.
	 *
	 * @param ctx The {@link ChannelHandlerContext} to write challenges on
	 * @param address untrusted address to validate
	 * @see #endValidation(DatagramPacket)
	 */
	private void startValidation(ChannelHandlerContext ctx, InetAddress address) {
		long data;

		// update state in a thread-safe manner
		synchronized (lock) {
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

		log.info("validating untrusted public address: " + address.getHostAddress());
		sendSecret(ctx, address, data);
	}

	@Override
	@JsonValue
	public String toString() {
		return get().getHostAddress();
	}

	private void sendSecret(ChannelHandlerContext ctx, InetAddress address, long secret) {
		ByteBuf data = Unpooled.wrappedBuffer(Longs.toByteArray(secret));
		DatagramPacket packet = new DatagramPacket(data, new InetSocketAddress(address, this.localPort));
		ctx.writeAndFlush(packet);
	}

	private InetAddress getLocalAddress() {
		try {
			return InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			return InetAddress.getLoopbackAddress();
		}
	}
}
