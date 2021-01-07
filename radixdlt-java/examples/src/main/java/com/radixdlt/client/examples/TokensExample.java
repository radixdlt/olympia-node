/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.examples;

import com.radixdlt.client.application.RadixApplicationAPI.Result;
import com.radixdlt.client.application.RadixApplicationAPI.Transaction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.MintTokensAction;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.identifiers.RRI;
import java.math.BigDecimal;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.Bootstrap;

public class TokensExample {
	private TokensExample() { }

	public static void main(String[] args) {
		// Create a new public key identity
		final RadixIdentity radixIdentity = RadixIdentities.createNew();

		// Initialize api layer
		RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST, radixIdentity);

		// Constantly sync account with network
		api.pull();

		System.out.println("My address: " + api.getAddress());
		System.out.println("My public key: " + api.getPublicKey());

		// Create a unique identifier for the token
		RRI tokenRRI = RRI.of(api.getAddress(), "JOSH");

		// Observe all past and future transactions
		api.observeTokenTransfers()
			.subscribe(System.out::println);

		// Observe current and future total balance
		api.observeBalance(tokenRRI)
			.subscribe(balance -> System.out.println("My Balance: " + balance));

		// Create token and mint
		Transaction transaction = api.createTransaction();
		transaction.stage(CreateTokenAction.create(
			tokenRRI,
			"Joshy Token",
			"The Best Coin Ever",
			BigDecimal.ZERO,
			TokenUnitConversions.getMinimumGranularity(),
			TokenSupplyType.MUTABLE
		));
		transaction.stage(MintTokensAction.create(tokenRRI, api.getAddress(), BigDecimal.valueOf(1000000.0)));
		Result createTokenAndMint = transaction.commitAndPush();
		createTokenAndMint.toObservable().blockingSubscribe(System.out::println);

		// Get token definition
		System.out.println(api.getTokenDef(tokenRRI));

		// Mint tokens
		Result mint = api.mintTokens(tokenRRI, BigDecimal.valueOf(10000.0));
		mint.toObservable().blockingSubscribe(System.out::println);

		// Burn tokens
		Result burn = api.burnTokens(tokenRRI, BigDecimal.valueOf(10000.0));
		burn.toObservable().blockingSubscribe(System.out::println);

		// Send tokens
		RadixAddress toAddress = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");
		api.sendTokens(tokenRRI, toAddress, BigDecimal.valueOf(10000.0), "Test Message").toObservable()
			.subscribe(System.out::println, Throwable::printStackTrace);
	}
}
