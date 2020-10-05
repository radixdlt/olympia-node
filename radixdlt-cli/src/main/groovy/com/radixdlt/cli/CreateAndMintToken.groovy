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

package com.radixdlt.cli

import com.radixdlt.client.application.RadixApplicationAPI
import com.radixdlt.client.application.translate.tokens.CreateTokenAction
import com.radixdlt.client.application.translate.tokens.MintTokensAction
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions
import com.radixdlt.identifiers.RRI
import picocli.CommandLine

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
class CreateAndMintToken implements Runnable {
    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    Composite.IdentityInfo identityInfo

    @CommandLine.Option(names = ["-a", "--amount"], paramLabel = "AMOUNT", description = " Amount to send")
    BigDecimal amount

    @CommandLine.Option(names = ["-t", "--token-name"], paramLabel = "TOKEN_NAME", description = " Unique name of the token", required = true)
    String tokenName

    @CommandLine.Option(names = ["-d", "--token-desc"], paramLabel = "TOKEN_DESCRIPTION", description = "Description of token")
    String tokenDescription = "(no description provided)"

    @CommandLine.Option(names = ["-r", "--use-existing"], paramLabel = "USE_EXISTING_TOKEN", description = " Use existing token instead of creating new")
    boolean useExisting;

    @Override
    void run() {
        if (tokenName.isBlank()) {
            println "Token name must not be empty"
            System.exit(-1)
        }

        if (useExisting && amount != null && amount <= BigDecimal.ZERO) {
            println "If existing token is used, then greater than zero amount must be provided"
            System.exit(-1)
        }

        RadixApplicationAPI api = Utils.getAPI(identityInfo)
        RRI tokenRRI = RRI.of(api.getAddress(), tokenName)
        RadixApplicationAPI.Transaction transaction = api.createTransaction()

        if (!useExisting) {
            println "Creating token ${tokenName} with ${tokenDescription}"

            transaction.stage(CreateTokenAction.create(
                    tokenRRI,
                    "${tokenName}",
                    "${tokenDescription}",
                    BigDecimal.ZERO,
                    TokenUnitConversions.getMinimumGranularity(),
                    CreateTokenAction.TokenSupplyType.MUTABLE
            ))
        } else {
            println "Using existing token ${tokenName}"
        }

        if (amount != null && amount > BigDecimal.ZERO) {
            println "Minting token for ${amount}"
            transaction.stage(MintTokensAction.create(tokenRRI, api.getAddress(), BigDecimal.valueOf(amount)))
        }

        println "Committing transaction..."
        transaction.commitAndPush()
            .toObservable()
            .blockingSubscribe({ it -> println it })
        println "Done"
        System.exit(0)
    }
}