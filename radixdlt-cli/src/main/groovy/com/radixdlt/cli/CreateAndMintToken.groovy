package com.radixdlt.cli

import com.radixdlt.client.application.RadixApplicationAPI
import com.radixdlt.client.application.translate.tokens.CreateTokenAction
import com.radixdlt.client.application.translate.tokens.MintTokensAction
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions
import com.radixdlt.client.core.Bootstrap
import com.radixdlt.client.core.atoms.particles.RRI
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

        RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, Utils.getIdentity(identityInfo))
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
    }
}