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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.radix.network2.transport.udp.PublicInetAddress;

import com.radixdlt.utils.Longs;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

public class PublicInetAddressTest {

	private PublicInetAddress dut;
	private AtomicLong clock;

	static class FakeDatagramSocket extends DatagramSocket {
		FakeDatagramSocket(SocketAddress address) throws SocketException {
			super((SocketAddress) null); // ensure created unbound
		}

		@Override
		public void send(DatagramPacket p) throws IOException {
			// Do nothing
		}

		@Override
		public void close() {
			// Do nothing
		}
	}

	@Before
	public void setUp() {
		clock = new AtomicLong(0);
		dut = new PublicInetAddress(30000, clock::get);
	}

	@Test
	public void testGet() {
		assertNotNull(dut.get());
	}

	@Test
	public void testIsPublicUnicastInetAddress() throws UnknownHostException {
		assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("172.31.0.1")));  // Site-local
		assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("192.168.1.1"))); // Site-local
		assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("10.10.10.10"))); // Site-local
		assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("169.254.0.0"))); // Link-local
		assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("127.0.0.1")));   // Localhost
		assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("224.0.0.101"))); // Multicast
		assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("::1")));
		assertTrue(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("1.1.1.1")));
		assertTrue(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("192.169.1.1")));
		assertTrue(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("2260::1")));
	}

	@Test
	public void testStartValidation() throws Exception {
		ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
		InetAddress from = InetAddress.getByName("127.0.0.1");

		// Reset
		dut.reset();

		// single validation until confirmed or time has elapsed
		dut.handleInboundPacket(ctx, from, packetFromTo(from, "1.1.1.1"));
		long expectedTime = (long) Whitebox.getField(PublicInetAddress.class, "secretEndOfLife").get(dut);
		long expectedSecret = (long) Whitebox.getField(PublicInetAddress.class, "secret").get(dut);

		// make sure our clock returns something new.
		clock.incrementAndGet();

		// make sure time changed
		assertNotEquals(expectedTime, clock.get());

		// try to trigger validation again with the same address
		dut.handleInboundPacket(ctx, from, packetFromTo(from, "1.1.1.1"));

		// try to trigger validation again with a different address
		dut.handleInboundPacket(ctx, from, packetFromTo(from, "2.2.2.2"));

		// make sure secretEndOfLife did not change since the first valid invocation
		assertEquals(expectedTime, Whitebox.getField(PublicInetAddress.class, "secretEndOfLife").get(dut));
		// make sure secret did not change since the first valid invocation
		assertEquals(expectedSecret, Whitebox.getField(PublicInetAddress.class, "secret").get(dut));

		// make sure unconfirmedAddress did not change since the first valid invocation
		assertEquals(InetAddress.getByName("1.1.1.1"), Whitebox.getField(PublicInetAddress.class, "unconfirmedAddress").get(dut));

		// Timeout the secret
		clock.addAndGet(PublicInetAddress.SECRET_LIFETIME_MS);

		// trigger validation again with a different address
		dut.handleInboundPacket(ctx, from, packetFromTo(from, "3.3.3.3"));

		// make sure secretEndOfLife changed
		assertNotEquals(expectedTime, Whitebox.getField(PublicInetAddress.class, "secretEndOfLife").get(dut));
		// make sure secret changed
		assertNotEquals(expectedSecret, Whitebox.getField(PublicInetAddress.class, "secret").get(dut));
		// make sure unconfirmedAddress changed
		assertEquals(InetAddress.getByName("3.3.3.3"), Whitebox.getField(PublicInetAddress.class, "unconfirmedAddress").get(dut));
	}

	@Test
	public void testEndValidation() throws IllegalAccessException, IOException {
		ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
		InetAddress from = InetAddress.getByName("127.0.0.1");

		long secret = -1L;
		Whitebox.getField(PublicInetAddress.class, "secret").set(dut, secret);
		Whitebox.getField(PublicInetAddress.class, "unconfirmedAddress").set(dut, InetAddress.getByName("1.1.1.1"));

		// Check initial conditions
		assertNotEquals(InetAddress.getByName("1.1.1.1"), Whitebox.getField(PublicInetAddress.class, "confirmedAddress").get(dut));

		assertFalse(dut.endValidation(null));
		assertFalse(dut.endValidation(byteBufFrom(new byte[] {1, 2, 3})));
		assertTrue(dut.endValidation(byteBufFrom(Longs.toByteArray(0L))));
		// make sure that confirmedAddress not set yet
		assertNull(Whitebox.getField(PublicInetAddress.class, "confirmedAddress").get(dut));
		assertTrue(dut.endValidation(byteBufFrom(Longs.toByteArray(secret))));
		// make sure that confirmedAddress got updated
		assertEquals(InetAddress.getByName("1.1.1.1"), Whitebox.getField(PublicInetAddress.class, "confirmedAddress").get(dut));

		// get should return confirmed address
		assertEquals(InetAddress.getByName("1.1.1.1"), dut.get());
		assertEquals("1.1.1.1", dut.toString());

		// no new secret now if we start again with the same address
		long oldSecret = Whitebox.getField(PublicInetAddress.class, "secret").getLong(dut);
		dut.handleInboundPacket(ctx, from, packetFromTo(from, "1.1.1.1"));
		assertEquals(oldSecret, Whitebox.getField(PublicInetAddress.class, "secret").getLong(dut));

		// ... but should get a new secret if we start again with a new host
		dut.handleInboundPacket(ctx, from, packetFromTo(from, "2.2.2.2"));
		assertNotEquals(oldSecret, Whitebox.getField(PublicInetAddress.class, "secret").getLong(dut));
	}

	private ByteBuf byteBufFrom(byte[] bs) {
		return Unpooled.wrappedBuffer(bs);
	}

	private ByteBuf packetFromTo(InetAddress from, String to) throws UnknownHostException {
		// NAT: encode source and dest address to work behind NAT and userland proxies (Docker for Windows/Mac)
		byte[] rawFromAddress = from.getAddress();
		byte[] rawToAddress = InetAddress.getByName(to).getAddress();

		int totalSize = rawFromAddress.length + rawToAddress.length + 1;

		byte[] data = new byte[totalSize];
		data[0] = getAddressFormat(rawFromAddress.length, rawToAddress.length);
		System.arraycopy(rawFromAddress, 0, data, 1, rawFromAddress.length);
		System.arraycopy(rawToAddress, 0, data, 1 + rawFromAddress.length, rawToAddress.length);

		return Unpooled.wrappedBuffer(data);
	}

	private byte getAddressFormat(int srcAddrLen, int dstAddrLen) {
		return (byte) (0x80 | (srcAddrLen == 4 ? 0x00 : 0x02) | (dstAddrLen == 4 ? 0x00 : 0x01));
	}
}
