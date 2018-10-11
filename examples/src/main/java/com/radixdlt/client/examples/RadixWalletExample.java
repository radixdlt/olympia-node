package com.radixdlt.client.examples;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.objects.Amount;
import com.radixdlt.client.core.atoms.Token;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.application.identity.RadixIdentity;
import java.math.BigDecimal;

public class RadixWalletExample {

	private static String TO_ADDRESS_BASE58 = "JFgcgRKq6GbQqP8mZzDRhtr7K7YQM1vZiYopZLRpAeVxcnePRXX";
	//private static String TO_ADDRESS_BASE58 = null;
	private static String MESSAGE = "A gift for you!";
	private static BigDecimal AMOUNT = new BigDecimal("0.01");

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

		System.out.println("My address: " + api.getMyAddress());
		System.out.println("My public key: " + api.getMyPublicKey());

		// Print out all past and future transactions
		api.getMyTokenTransfers()
			.subscribe(System.out::println);

		// Subscribe to current and future total balance
		api.getBalance(api.getMyAddress())
			.subscribe(balance -> System.out.println("My Balance:\n" + balance));

		// If specified, send money to another address
		if (TO_ADDRESS_BASE58 != null) {
			RadixAddress toAddress = RadixAddress.fromString(TO_ADDRESS_BASE58);
			api.sendTokens(toAddress, Amount.of(AMOUNT, Token.of("JOSH"))).toObservable()
				.subscribe(System.out::println, Throwable::printStackTrace);
		}
	}
}
