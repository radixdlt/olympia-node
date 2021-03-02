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

package com.radixdlt.cli;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.MintTokensAction;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.identifiers.RRI;
import picocli.CommandLine;

import java.math.BigDecimal;

import static com.radixdlt.cli.Utils.printfln;
import static com.radixdlt.cli.Utils.println;

/**
 * This command allows creation and minting of tokens
 * <br>
 * Usage:
 * <pre>
 *  $ radixdlt-cli create-mint-token [-k=<keystore name>] [-p=<keystore password>] [-a=<amount>] -n=<token name> [-d=<token description>] [-r]
 * </pre>
 * There are following modes of operation for this command, depending on specified parameters. All modes require token name.
 * <ul>
 *     <li>Create token without minting it - Do not specify amount. In this mode {@code -r} option is not allowed</li>
 *     <li>Mint existing token - Specify amount and provide {@code -r} option.</li>
 *     <li>Create and immediately mint token - Specify amount (and omit {@code -r} option)</li>
 * </ul>
 */
@CommandLine.Command(name = "create-mint-token", mixinStandardHelpOptions = true,
		description = "Create or Mint or Create and Mint token")
public class CreateAndMintToken implements Runnable {
	@CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
	private Composite.IdentityInfo identityInfo;

	@CommandLine.Option(names = {"-a", "--amount"}, paramLabel = "AMOUNT", description = " Amount to send")
	private BigDecimal amount;

	@CommandLine.Option(names = {"-t", "--token-name"}, paramLabel = "TOKEN_NAME", description = " Unique name of the token", required = true)
	private String tokenName;

	@CommandLine.Option(names = {"-d", "--token-desc"}, paramLabel = "TOKEN_DESCRIPTION", description = "Description of token")
	private String tokenDescription = "(no description provided)";

	@CommandLine.Option(
			names = {"-r", "--use-existing"},
			paramLabel = "USE_EXISTING_TOKEN",
			description = " Use existing token instead of creating new"
	)
	private boolean useExisting;

	@Override
	public void run() {
		if (tokenName.isBlank()) {
			println("Token name must not be empty");
			return;
		}

		if (useExisting && amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
			println("If existing token is used, then greater than zero amount must be provided");
			return;
		}

		RadixApplicationAPI api = Utils.getAPI(identityInfo);
		RRI tokenRRI = RRI.of(api.getAddress(), tokenName);
		RadixApplicationAPI.Transaction transaction = api.createTransaction();

		if (!useExisting) {
			printfln("Creating token %s with %s", tokenName, tokenDescription);

			transaction.stage(CreateTokenAction.create(
					tokenRRI,
					tokenName,
					tokenDescription,
					BigDecimal.ZERO,
					TokenUnitConversions.getMinimumGranularity(),
					CreateTokenAction.TokenSupplyType.MUTABLE
			));
		} else {
			printfln("Using existing token %s", tokenName);
		}

		if (amount != null && amount.compareTo(BigDecimal.ZERO) >= 0) {
			printfln("Minting token for %f", amount);
			transaction.stage(MintTokensAction.create(tokenRRI, api.getAddress(), amount));
		}

		println("Committing transaction...");
		transaction.commitAndPush()
				.toObservable()
				.blockingSubscribe(it -> println(it.toString()));
		println("Done");
	}
}