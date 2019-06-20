package com.radixdlt.client.examples;

import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.core.atoms.particles.RRI;
import java.math.BigDecimal;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.Bootstrap;

public class TokensExample {

	private static String TO_ADDRESS_BASE58 = "JFgcgRKq6GbQqP8mZzDRhtr7K7YQM1vZiYopZLRpAeVxcnePRXX";
	private static BigDecimal AMOUNT = new BigDecimal("0.01");

	public static void main(String[] args) throws Exception {
		// Create a new public key identity
		final RadixIdentity radixIdentity = RadixIdentities.createNew();

		// Initialize api layer
		RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST, radixIdentity);

		// Sync with network
		api.pull();

		System.out.println("My address: " + api.getMyAddress());
		System.out.println("My public key: " + api.getMyPublicKey());

		// Print out all past and future transactions
		api.getTokenTransfers()
			.subscribe(System.out::println);

		// Create a token
		RRI tokenRRI = RRI.of(api.getMyAddress(), "JOSH");
		Result tokenCreation = api.createToken(
			tokenRRI,
			"Joshy Token",
			"The Best Coin Ever",
			BigDecimal.valueOf(10000.0),
			TokenUnitConversions.getMinimumGranularity(),
			TokenSupplyType.MUTABLE
		);
		tokenCreation.toObservable().blockingSubscribe(System.out::println);

		// Get token definition
		api.getTokenDef(tokenRRI)
			.subscribe(System.out::println);

		// Subscribe to current and future total balance
		api.getBalance(tokenRRI)
			.subscribe(balance -> System.out.println("My Balance: " + balance));

		// Mint tokens
		Result mint = api.mintTokens(tokenRRI, BigDecimal.valueOf(10000.0));
		mint.toObservable().blockingSubscribe(System.out::println);

		// Burn tokens
		Result burn = api.burnTokens(tokenRRI, BigDecimal.valueOf(10000.0));
		burn.toObservable().blockingSubscribe(System.out::println);

		// Send tokens
		RadixAddress toAddress = RadixAddress.from(TO_ADDRESS_BASE58);
		api.sendTokens(tokenRRI, toAddress, AMOUNT, "Test Message").toObservable()
			.subscribe(System.out::println, Throwable::printStackTrace);
	}
}
