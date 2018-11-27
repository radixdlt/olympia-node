package com.radixdlt.client.examples;

import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;

/**
 * The simplest Radix program you can write:
 * Connect to a Radix Universe and display nodes you can connect to
 */
public class HelloWorld {
	public static void main(String[] args) {

		// Setup the universe you want to connect to.
		// Sunstone is the Testnet for Alpha release
		RadixUniverse.bootstrap(Bootstrap.BETANET);

		// Connect to the network and retrieve list of
		// nodes you can connect to
		RadixUniverse.getInstance()
			.getNetwork()
			.connectAndGetStatusUpdates()
			.subscribe(System.out::println);
	}
}
