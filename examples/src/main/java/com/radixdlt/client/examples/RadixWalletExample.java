package com.radixdlt.client.examples;

import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.identity.RadixIdentity;
import com.radixdlt.client.core.identity.SimpleRadixIdentity;
import com.radixdlt.client.wallet.RadixWallet;

public class RadixWalletExample {

	//private static String TO_ADDRESS_BASE58 = "JGuwJVu7REeqQtx7736GB9AJ91z5xB55t8NvteaoC25AumYovjp";
	private static String TO_ADDRESS_BASE58 = null;
	private static String PAYLOAD = "A gift for you!";
	private static long AMOUNT = 1000;

	// Initialize Radix Universe
	static {
		RadixUniverse.bootstrap(Bootstrap.SUNSTONE);
	}

	public static void main(String[] args) throws Exception {
		// Network updates
		RadixUniverse.getInstance()
			.getNetwork()
			.getStatusUpdates()
			.subscribe(System.out::println);

		// Identity Manager which manages user's keys, signing, encrypting and decrypting
		final RadixIdentity radixIdentity;
		if (args.length > 0) {
			if (args[0].equals("-system")) {
				radixIdentity = RadixUniverse.getInstance().getSystemIdentity()
					.orElseThrow(() -> new IllegalStateException("System key not present"));
			} else {
				radixIdentity = new SimpleRadixIdentity(args[0]);
			}
		} else {
			radixIdentity = new SimpleRadixIdentity();
		}
		RadixAddress myAddress = RadixUniverse.getInstance().getAddressFrom(radixIdentity.getPublicKey());

		// Print out all past and future transactions
		RadixWallet.getInstance()
			.getXRDTransactions(myAddress)
			.subscribe(System.out::println);

		// Subscribe to current and future total balance
		RadixWallet.getInstance()
			.getXRDSubUnitBalance(myAddress)
			.subscribe(balance -> System.out.println("My Balance: " + ((double)balance) / Asset.XRD.getSubUnits()));


		// If specified, send money to another address
		if (TO_ADDRESS_BASE58 != null) {
			RadixAddress toAddress = RadixAddress.fromString(TO_ADDRESS_BASE58);
			RadixWallet.getInstance()
				.transferXRDWhenAvailable(AMOUNT * Asset.XRD.getSubUnits(), radixIdentity, toAddress, PAYLOAD)
				.subscribe(
					status -> System.out.println("Transaction " + status),
					Throwable::printStackTrace
				)
				;
		}
	}
}
