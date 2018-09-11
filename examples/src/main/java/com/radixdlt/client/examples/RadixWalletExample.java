package com.radixdlt.client.examples;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.dapps.wallet.RadixWallet;

public class RadixWalletExample {

	private static String TO_ADDRESS_BASE58 = "JGuwJVu7REeqQtx7736GB9AJ91z5xB55t8NvteaoC25AumYovjp";
	//private static String TO_ADDRESS_BASE58 = null;
	private static long AMOUNT = 1;
	private static String MESSAGE = "A gift!";

	// Initialize Radix Universe
	static {
		RadixUniverse.bootstrap(Bootstrap.SUNSTONE);
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
		RadixWallet wallet = new RadixWallet(api);

		// Print out all past and future transactions
		wallet.getXRDTransactions()
			.subscribe(System.out::println);

		// Subscribe to current and future total balance
		wallet.getXRDBalance()
			.subscribe(balance -> System.out.println("My Balance: " + balance));

		// If specified, send money to another address
		if (TO_ADDRESS_BASE58 != null) {
			RadixAddress toAddress = RadixAddress.fromString(TO_ADDRESS_BASE58);
			wallet.transferXRDWhenAvailable(AMOUNT * Asset.TEST.getSubUnits(), toAddress, MESSAGE)
				.toObservable()
				.subscribe(System.out::println, Throwable::printStackTrace);
		}
	}
}
