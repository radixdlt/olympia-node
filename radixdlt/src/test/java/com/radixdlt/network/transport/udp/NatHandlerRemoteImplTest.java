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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;
import com.radixdlt.utils.Longs;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

public class NatHandlerRemoteImplTest {

	private NatHandlerRemoteImpl dut;
	private AtomicLong clock;

	@Before
	public void setUp() throws UnknownHostException {
		clock = new AtomicLong(0);
		InetAddress localAddress = InetAddress.getByName("127.0.0.1");
		dut = new NatHandlerRemoteImpl(localAddress, 30000, clock::get);
	}

	@Test
	public void testGet() {
		assertNotNull(dut.getAddress());
	}

	@Test
	public void testIsPublicUnicastInetAddress() throws UnknownHostException {
		assertFalse(NatHandlerRemoteImpl.isPublicUnicastInetAddress(InetAddress.getByName("172.31.0.1")));  // Site-local
		assertFalse(NatHandlerRemoteImpl.isPublicUnicastInetAddress(InetAddress.getByName("192.168.1.1"))); // Site-local
		assertFalse(NatHandlerRemoteImpl.isPublicUnicastInetAddress(InetAddress.getByName("10.10.10.10"))); // Site-local
		assertFalse(NatHandlerRemoteImpl.isPublicUnicastInetAddress(InetAddress.getByName("169.254.0.0"))); // Link-local
		assertFalse(NatHandlerRemoteImpl.isPublicUnicastInetAddress(InetAddress.getByName("127.0.0.1")));   // Localhost
		assertFalse(NatHandlerRemoteImpl.isPublicUnicastInetAddress(InetAddress.getByName("224.0.0.101"))); // Multicast
		assertFalse(NatHandlerRemoteImpl.isPublicUnicastInetAddress(InetAddress.getByName("::1")));
		assertTrue(NatHandlerRemoteImpl.isPublicUnicastInetAddress(InetAddress.getByName("1.1.1.1")));
		assertTrue(NatHandlerRemoteImpl.isPublicUnicastInetAddress(InetAddress.getByName("192.169.1.1")));
		assertTrue(NatHandlerRemoteImpl.isPublicUnicastInetAddress(InetAddress.getByName("2260::1")));
	}

	@Test
	public void testStartValidation() throws UnknownHostException {
		ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
		InetAddress from = InetAddress.getByName("127.0.0.1");

		// Reset
		dut.reset();

		// single validation until confirmed or time has elapsed
		dut.handleInboundPacket(ctx, from, packetFromTo(from, "1.1.1.1"));
		long expectedTime = dut.secretEndOfLife();
		long expectedSecret = dut.secret();

		// make sure our clock returns something new.
		clock.incrementAndGet();

		// make sure time changed
		assertNotEquals(expectedTime, clock.get());

		// try to trigger validation again with the same address
		dut.handleInboundPacket(ctx, from, packetFromTo(from, "1.1.1.1"));

		// try to trigger validation again with a different address
		dut.handleInboundPacket(ctx, from, packetFromTo(from, "2.2.2.2"));

		// make sure secretEndOfLife did not change since the first valid invocation
		assertEquals(expectedTime, dut.secretEndOfLife());
		// make sure secret did not change since the first valid invocation
		assertEquals(expectedSecret, dut.secret());

		// make sure unconfirmedAddress did not change since the first valid invocation
		assertEquals(InetAddress.getByName("1.1.1.1"), dut.unconfirmedAddress());

		// Timeout the secret
		clock.addAndGet(NatHandlerRemoteImpl.SECRET_LIFETIME_MS);

		// trigger validation again with a different address
		dut.handleInboundPacket(ctx, from, packetFromTo(from, "3.3.3.3"));

		// make sure secretEndOfLife changed
		assertNotEquals(expectedTime, dut.secretEndOfLife());
		// make sure secret changed
		assertNotEquals(expectedSecret, dut.secret());
		// make sure unconfirmedAddress changed
		assertEquals(InetAddress.getByName("3.3.3.3"), dut.unconfirmedAddress());
	}

	@Test
	public void testEndValidation() throws IOException {
		ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
		InetAddress from = InetAddress.getByName("127.0.0.1");

		dut.unconfirmedAddress(InetAddress.getByName("1.1.1.1"));

		// Check initial conditions
		assertNotEquals(InetAddress.getByName("1.1.1.1"), dut.confirmedAddress());

		assertFalse(dut.endInboundValidation(null));
		assertFalse(dut.endInboundValidation(byteBufFrom(new byte[] {1, 2, 3})));
		assertTrue(dut.endInboundValidation(byteBufFrom(Longs.toByteArray(-1L))));
		// make sure that confirmedAddress not set yet
		assertNull(dut.confirmedAddress());
		assertTrue(dut.endInboundValidation(byteBufFrom(Longs.toByteArray(dut.secret()))));
		// make sure that confirmedAddress got updated
		assertEquals(InetAddress.getByName("1.1.1.1"), dut.confirmedAddress());

		// get should return confirmed address
		assertEquals(InetAddress.getByName("1.1.1.1"), dut.getAddress());
		assertEquals("1.1.1.1", dut.toString());

		// no new secret now if we start again with the same address
		long oldSecret = dut.secret();
		dut.handleInboundPacket(ctx, from, packetFromTo(from, "1.1.1.1"));
		assertEquals(oldSecret, dut.secret());

		// ... but should get a new secret if we start again with a new host
		dut.handleInboundPacket(ctx, from, packetFromTo(from, "2.2.2.2"));
		assertNotEquals(oldSecret, dut.secret());
	}

	@Test
	public void testComputeSizeLocalIp4() throws UnknownHostException {
		assertEquals(1L + 4 + 4, dut.computeExtraSize(InetAddress.getByName("127.0.0.1")));
		assertEquals(1L + 4 + 16, dut.computeExtraSize(InetAddress.getByName("::1")));
	}

	@Test
	public void testComputeSizeLocalIp6() throws UnknownHostException {
		InetAddress localAddress = InetAddress.getByName("::1");
		NatHandlerRemoteImpl dut2 = new NatHandlerRemoteImpl(localAddress, 30000, clock::get);
		assertEquals(1L + 16 + 4, dut2.computeExtraSize(InetAddress.getByName("127.0.0.1")));
		assertEquals(1L + 16 + 16, dut2.computeExtraSize(InetAddress.getByName("::1")));
	}

	@Test
	public void testWriteAddressesLocalIp4() throws UnknownHostException {
		ByteBuf result1 = Unpooled.wrappedBuffer(new byte[1024]).clear();
		dut.writeExtraData(result1, InetAddress.getByName("127.0.0.1"));
		assertEquals(1L + 4 + 4, result1.writerIndex());

		ByteBuf result2 = Unpooled.wrappedBuffer(new byte[1024]).clear();
		dut.writeExtraData(result2, InetAddress.getByName("::1"));
		assertEquals(1L + 4 + 16, result2.writerIndex());
	}

	@Test
	public void testWriteAddressesLocalIp6() throws UnknownHostException {
		InetAddress localAddress = InetAddress.getByName("::1");
		NatHandlerRemoteImpl dut2 = new NatHandlerRemoteImpl(localAddress, 30000, clock::get);

		ByteBuf result1 = Unpooled.wrappedBuffer(new byte[1024]).clear();
		dut2.writeExtraData(result1, InetAddress.getByName("127.0.0.1"));
		assertEquals(1L + 16 + 4, result1.writerIndex());

		ByteBuf result2 = Unpooled.wrappedBuffer(new byte[1024]).clear();
		dut2.writeExtraData(result2, InetAddress.getByName("::1"));
		assertEquals(1L + 16 + 16, result2.writerIndex());
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
