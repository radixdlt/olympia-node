package org.radix.network2.transport.udp;

import com.radixdlt.universe.Universe;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.DefaultEventExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.ArgumentCaptor;
import org.radix.modules.Modules;
import org.radix.network2.transport.SendResult;
import org.radix.network2.transport.TransportMetadata;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class UDPTransportOutboundConnectionTest {

	private DatagramChannel channel;
	private TransportMetadata metadata;
	private ArgumentCaptor<DatagramPacket> datagramPacketArgumentCaptor;
	private DefaultChannelPromise channelFuture;
	private String testMessage = "TestMessage";

	private String sourceAddressStr;
	private String destinationAddressStr;
	private byte addressFormatFlag;
	private Exception exception;

	@Parameters(name = "{index}: Source address: {0}, Destination address {1} points, AddressFormatFlag {2}")
	public static Iterable<Object[]> data() {
		return Arrays.asList(new Object[][]{
				//testing success
				{"192.168.1.1", "192.168.1.2", (byte) -128, null},
				{"192.168.1.1", "2001:0db8:85a3:0000:0000:8a2e:0370:7335", (byte) -127, null},
				{"2001:0db8:85a3:0000:0000:8a2e:0370:7334", "192.168.1.2", (byte) -126, null},
				{"2001:0db8:85a3:0000:0000:8a2e:0370:7334", "2001:0db8:85a3:0000:0000:8a2e:0370:7335", (byte) -125, null},

				//testing failures
				{"192.168.1.1", "192.168.1.2", (byte) -128, new Exception("Expected exception")}
		});
	}

	public UDPTransportOutboundConnectionTest(String sourceAddressStr, String destinationAddressStr, byte addressFormatFlag, Exception exception) {
		this.sourceAddressStr = sourceAddressStr;
		this.destinationAddressStr = destinationAddressStr;
		this.addressFormatFlag = addressFormatFlag;
		this.exception = exception;
	}

	@Before
	public void setUp() {

		PublicInetAddress.configure(sourceAddressStr, 12345);

		channel = mock(DatagramChannel.class);//spy(new NioDatagramChannel());
		channelFuture = spy(new DefaultChannelPromise(channel, new DefaultEventExecutor())); //new DefaultPromise(new DefaultEventExecutor());

		when(channel.alloc()).thenReturn(ByteBufAllocator.DEFAULT);
		datagramPacketArgumentCaptor = ArgumentCaptor.forClass(DatagramPacket.class);
		when(channel.writeAndFlush(datagramPacketArgumentCaptor.capture())).thenReturn(channelFuture);
		metadata = mock(TransportMetadata.class);

		when(metadata.get(UDPConstants.METADATA_UDP_PORT)).thenReturn("12345");
		when(metadata.get(UDPConstants.METADATA_UDP_HOST)).thenReturn(destinationAddressStr);

		Universe universe = mock(Universe.class);
		Modules.put(Universe.class, universe);
	}

	@After
	public void tearDown() {
		Modules.remove(Universe.class);
	}

	@Test
	public void sendTest() throws ExecutionException, InterruptedException, IOException {
		try (UDPTransportOutboundConnection udpTransportOutboundConnection = new UDPTransportOutboundConnection(channel, metadata)) {
			CompletableFuture<SendResult> completableFuture = udpTransportOutboundConnection.send(testMessage.getBytes());

			channelFuture.setSuccess();

			SendResult result = completableFuture.get();
			assertThat(result).isEqualTo(SendResult.complete());

			DatagramPacket datagramPacket = datagramPacketArgumentCaptor.getValue();
			byte[] actualMessage = new byte[datagramPacket.content().capacity()];
			datagramPacket.content().getBytes(0, actualMessage);


			byte[] sourceAddress = PublicInetAddress.getInstance().get().getAddress();
			byte[] destinationAddress = new InetSocketAddress(
				metadata.get(UDPConstants.METADATA_UDP_HOST),
				Integer.valueOf(metadata.get(UDPConstants.METADATA_UDP_PORT))
			).getAddress().getAddress();
			byte[] data = testMessage.getBytes();
			ByteBuffer expectedMessageBuffer = ByteBuffer.allocate(datagramPacket.content().capacity());
			expectedMessageBuffer.put(addressFormatFlag);
			expectedMessageBuffer.put(sourceAddress);
			expectedMessageBuffer.put(destinationAddress);
			expectedMessageBuffer.put(data);
			byte[] expectedMessage = expectedMessageBuffer.array();

			assertArrayEquals(actualMessage, expectedMessage);
		}
	}

	@Test
	public void sendFailureTest() throws ExecutionException, InterruptedException, IOException {
		assumeTrue(exception != null);
		try (UDPTransportOutboundConnection udpTransportOutboundConnection = new UDPTransportOutboundConnection(channel, metadata)) {
			CompletableFuture<SendResult> completableFuture = udpTransportOutboundConnection.send(testMessage.getBytes());
			channelFuture.setFailure(exception);
			SendResult result = completableFuture.get();
			assertThat(result.isComplete()).isEqualTo(false);
			assertThat(result.getThrowable().getMessage()).isEqualTo(exception.getMessage());
		}
	}
}