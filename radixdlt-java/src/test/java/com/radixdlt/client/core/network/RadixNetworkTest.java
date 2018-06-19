package com.radixdlt.client.core.network;

import static org.junit.Assert.*;

import io.reactivex.Observable;
import java.util.stream.IntStream;
import org.junit.Test;

public class RadixNetworkTest {

	@Test
	public void testGetClientsMultipleTimes() {

		RadixNetwork network = new RadixNetwork(() -> Observable.just(
			new RadixPeer("1", false, 8080),
			new RadixPeer("2", false, 8080),
			new RadixPeer("3", false, 8080)
		));

		IntStream.range(0,10).forEach(i ->
			network.getRadixClients()
				.map(RadixClient::getLocation)
				.test()
				.assertValueAt(0, "ws://1:8080/rpc")
				.assertValueAt(1, "ws://2:8080/rpc")
				.assertValueAt(2, "ws://3:8080/rpc")
		);
	}
}