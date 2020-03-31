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

@CommandLine.Command(name = "create-mint-token", mixinStandardHelpOptions = true,
        description = "Create new token and mint tokens")
class CreateAndMintToken implements Runnable {

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    Composite.IdentityInfo identityInfo

    @CommandLine.Option(names = ["-a", "--amount"], paramLabel = "AMOUNT", description = " Amount to send", required = true)
    BigDecimal amount

    @CommandLine.Option(names = ["-n", "--token-name"], paramLabel = "TOKEN_NAME", description = " Unique name of the token", required = true)
    String tokenName

    @CommandLine.Option(names = ["-d", "--token-desc"], paramLabel = "TOKEN_DESCRIPTION", description = "Description of token")
    String tokenDescription = "Token description not provided"


    void run() {

        RadixApplicationAPI api = Utils.getAPI(identityInfo)
        RRI tokenRRI = RRI.of(api.getAddress(), tokenName)
        RadixApplicationAPI.Transaction transaction = api.createTransaction()
        transaction.stage(CreateTokenAction.create(
                tokenRRI,
                "${tokenName}",
                "${tokenDescription}",
                BigDecimal.ZERO,
                TokenUnitConversions.getMinimumGranularity(),
                CreateTokenAction.TokenSupplyType.MUTABLE
        ))
        transaction.stage(MintTokensAction.create(tokenRRI, api.getAddress(), BigDecimal.valueOf(amount)))
        RadixApplicationAPI.Result createTokenAndMint = transaction.commitAndPush()
        createTokenAndMint.toObservable().blockingSubscribe({ it -> println it })
        System.exit(0)
    }
}