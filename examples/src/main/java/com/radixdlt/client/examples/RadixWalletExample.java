package com.radixdlt.client.examples;

import com.radixdlt.client.application.translate.data.SendMessageAction;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import java.math.BigDecimal;

import java.util.concurrent.TimeUnit;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.client.Serialize;
import org.radix.utils.UInt256;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokenclasses.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.application.translate.tokens.TokenClassReference;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;

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
//		RadixUniverse.getInstance()
//			.getNetwork()
//			.getStatusUpdates()
//			.subscribe(System.out::println);

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

		/*
		api.createToken(
			"Joshy Token",
			"JOSH",
			"The Best Coin Ever",
			TokenClassReference.unitsToSubunits(10000),
			UInt256.ONE,
			TokenSupplyType.MUTABLE
		).toObservable().subscribe(System.out::println);
		*/

		api.getTokenClass(TokenClassReference.of(api.getMyAddress(), "JOSH"))
			.subscribe(System.out::println);

		TimeUnit.SECONDS.sleep(4);

		api.buildAtom(TransferTokensAction.create(api.getMyAddress(), api.getMyAddress(), AMOUNT, TokenClassReference.of(api.getMyAddress(), "JOSH")))
		.flatMap(api.getMyIdentity()::sign)
		.subscribe(atom -> System.out.println(Serialize.getInstance().toJson(atom, Output.ALL)));

		// If specified, send money to another address
		if (TO_ADDRESS_BASE58 != null) {
			RadixAddress toAddress = RadixAddress.from(TO_ADDRESS_BASE58);
			api.sendTokens(toAddress, AMOUNT, api.getNativeTokenRef(), "Test Message").toObservable()
				.subscribe(System.out::println, Throwable::printStackTrace);
		}
	}
}
