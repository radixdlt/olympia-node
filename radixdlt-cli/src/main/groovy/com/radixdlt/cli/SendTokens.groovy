package com.radixdlt.cli

import com.radixdlt.client.application.RadixApplicationAPI
import com.radixdlt.client.atommodel.accounts.RadixAddress
import picocli.CommandLine

@CommandLine.Command(name = "send-token", mixinStandardHelpOptions = true,
        description = "Send Token")
class SendTokens implements Runnable {

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    Composite.IdentityInfo identityInfo

    @CommandLine.Option(names = ["-a", "--amount"], paramLabel = "AMOUNT", description = " Amount to send", required = true)
    BigDecimal amount

    @CommandLine.Option(names = ["-t", "--token"], paramLabel = "TOKEN", description = "Token to send", required = true)
    String tokenString

    @CommandLine.Option(names = ["-d", "--address"], paramLabel = "ADDRESS", description = "Address to which token is sent", required = true)
    String addressString


    void run() {

        RadixApplicationAPI api = Utils.getAPI(identityInfo)
        String[] ref = tokenString.split("/")
        RadixAddress tokenAddress = RadixAddress.from(ref[0])
        String iso = ref[2]
        RadixAddress address = RadixAddress.from(addressString)

//        TODO transferTokens is not valid api method
//        api.transferTokens(address, amount, TokenDefinitionReference.of(tokenAddress, iso)).toCompletable().blockingAwait();


    }

}