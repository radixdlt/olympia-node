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

package com.radixdlt.network.transport.udp;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Random;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.utils.Longs;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class for NAT handling/discovery.
 */
public final class NatHandlerRemoteImpl implements NatHandler {
	@VisibleForTesting
	static final int SECRET_LIFETIME_MS = 60_000;
	private static final Logger log = LogManager.getLogger();

	static boolean isPublicUnicastInetAddress(InetAddress address) {
		return !(address.isSiteLocalAddress() || address.isLinkLocalAddress()
			 || address.isLoopbackAddress() || address.isMulticastAddress());
	}

	static NatHandler create(InetAddress localAddress, int localPort) {
		return create(localAddress, localPort, System::currentTimeMillis);
	}

	static NatHandler create(InetAddress localAddress, int localPort, TimeSupplier timeSource) {
		return new NatHandlerRemoteImpl(localAddress, localPort, timeSource);
	}

	private final Object lock = new Object();
	private final Random prng = new Random(System.nanoTime());

	private final InetAddress localAddress;
	private final int localPort;
	private final TimeSupplier timeSource;

	private InetAddress confirmedAddress;
	private InetAddress unconfirmedAddress;
	private long secret;
	private long secretEndOfLife = Long.MIN_VALUE; // Very much expired

	NatHandlerRemoteImpl(InetAddress localAddress, int localPort, TimeSupplier timeSource) {
		this.localAddress = localAddress;
		this.localPort = localPort;
		this.timeSource = timeSource;
	}

	@Override
	public InetAddress getAddress() {
		synchronized (lock) {
			return confirmedAddress == null ? localAddress : confirmedAddress;
		}
	}

	@Override
	public void reset() {
		synchronized (lock) {
			confirmedAddress = null;
		}
	}

	@Override
	public void handleInboundPacket(ChannelHandlerContext ctx, InetAddress peerAddress, ByteBuf buf) {
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

	@Override
	public boolean endInboundValidation(ByteBuf buf) {
		// Make sure secret doesn't change mid-check
		long localSecret = this.secret;

		// quick return - in case this is not our packet, or we have not yet been set up
		if (buf == null || buf.readableBytes() != Long.BYTES) {
			return false;
		}

		// Note we don't read here, just peek
		if (buf.getLong(0) != localSecret) {
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
		log.info("public address is confirmed valid: {}", toPrint);

		// tell the caller that this packet should be ignored.
		return true;
	}

	@Override
	public int computeExtraSize(InetAddress destAddress) {
		byte[] rawSourceAddress = getAddress().getAddress();
		byte[] rawDestAddress = destAddress.getAddress();

		assert rawSourceAddress.length == 4 || rawSourceAddress.length == 16;
		assert rawDestAddress.length == 4 || rawDestAddress.length == 16;

		return 1 + rawSourceAddress.length + rawDestAddress.length;
	}

	@Override
	public void writeExtraData(ByteBuf buffer, InetAddress destAddress) {
		byte[] rawSourceAddress = getAddress().getAddress();
		byte[] rawDestAddress = destAddress.getAddress();

		assert rawSourceAddress.length == 4 || rawSourceAddress.length == 16;
		assert rawDestAddress.length == 4 || rawDestAddress.length == 16;

		buffer
			.writeByte(getAddressFormat(rawSourceAddress.length, rawDestAddress.length))
			.writeBytes(rawSourceAddress)
			.writeBytes(rawDestAddress);
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
			long now = timeSource.currentTime();
			if (address.equals(confirmedAddress) || secretEndOfLife > now) {
				return;
			}

			unconfirmedAddress = address;
			secret = prng.nextLong();
			data = secret;

			// secret is valid for a minute - plenty of time to validate the address
			// in the mean time we do not trigger of new validation - it could act as an attack vector.
			secretEndOfLife = now + SECRET_LIFETIME_MS;
		}

		log.info("validating untrusted public address: {}", address.getHostAddress());
		sendSecret(ctx, address, data);
	}

	@Override
	@JsonValue
	public String toString() {
		return getAddress().getHostAddress();
	}

	private void sendSecret(ChannelHandlerContext ctx, InetAddress address, long secret) {
		ByteBuf data = Unpooled.wrappedBuffer(Longs.toByteArray(secret));
		DatagramPacket packet = new DatagramPacket(data, new InetSocketAddress(address, this.localPort));
		ctx.writeAndFlush(packet);
	}

	@VisibleForTesting
	long secret() {
		return this.secret;
	}

	@VisibleForTesting
	long secretEndOfLife() {
		return this.secretEndOfLife;
	}

	@VisibleForTesting
	InetAddress unconfirmedAddress() {
		return this.unconfirmedAddress;
	}

	@VisibleForTesting
	void unconfirmedAddress(InetAddress address) {
		this.unconfirmedAddress = address;
	}

	@VisibleForTesting
	InetAddress confirmedAddress() {
		return this.confirmedAddress;
	}

	private byte getAddressFormat(int srclen, int dstlen) {
		// MSB: switch between old/new protocol format
		return (byte) (0x80 | (srclen != 4 ? 0x02 : 0x00) | (dstlen != 4 ? 0x01 : 0x00));
	}
}
