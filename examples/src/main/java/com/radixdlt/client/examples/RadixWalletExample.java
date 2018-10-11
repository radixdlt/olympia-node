package com.radixdlt.client.examples;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.dapps.wallet.RadixWallet;
import java.math.BigDecimal;

public class RadixWalletExample {

	private static String TO_ADDRESS_BASE58 = "JFgcgRKq6GbQqP8mZzDRhtr7K7YQM1vZiYopZLRpAeVxcnePRXX";
	//private static String TO_ADDRESS_BASE58 = null;
	private static String MESSAGE = "A gift for you!";
	private static BigDecimal AMOUNT = new BigDecimal("100.0");

	// Initialize Radix Universe
	static {
		RadixUniverse.bootstrap(Bootstrap.BETANET);
	}

	public static void main(String[] args) throws Exception {
		// Identity Manager which manages user's keys, signing, encrypting and decrypting
		final RadixIdentity radixIdentity;
		if (args.length > 0) {
			radixIdentity = RadixIdentities.loadOrCreateFile(args[0]);
		} else {
			radixIdentity = RadixIdentities.loadOrCreateFile("my.key");
		}

		// Network updates
		RadixUniverse.getInstance()
			.getNetwork()
			.getStatusUpdates()
			.subscribe(System.out::println);

		RadixApplicationAPI api = RadixApplicationAPI.create(radixIdentity);
		api.pull();

		RadixWallet wallet = new RadixWallet(api);

		System.out.println("My address: " + api.getMyAddress());
		System.out.println("My public key: " + api.getMyPublicKey());

		// Print out all past and future transactions
		wallet.getTransactions()
			.subscribe(System.out::println);

		// Subscribe to current and future total balance
		api.getBalance(api.getMyAddress())
			.subscribe(balance -> System.out.println("My Balance:\n" + balance));

		/*
		Result result = api.createToken("Test", "Josh", "Just for kicks", 1);
		result.toObservable().subscribe(System.out::println);
		*/


		// If specified, send money to another address

			/*
		if (TO_ADDRESS_BASE58 != null) {
			RadixAddress toAddress = RadixAddress.fromString(TO_ADDRESS_BASE58);
			api.sendTokens(toAddress, Amount.)
			wallet.sendWhenAvailable(AMOUNT, MESSAGE, toAddress)
				.toObservable()
				.subscribe(System.out::println, Throwable::printStackTrace);
		}
				*/
	}
}
