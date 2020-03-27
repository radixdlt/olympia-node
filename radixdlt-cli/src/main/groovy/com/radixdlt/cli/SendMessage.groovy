package com.radixdlt.cli

import com.radixdlt.client.application.RadixApplicationAPI
import com.radixdlt.client.atommodel.accounts.RadixAddress
import com.radixdlt.client.core.Bootstrap
import org.radix.utils.RadixConstants
import picocli.CommandLine

@CommandLine.Command(name = "send-message", mixinStandardHelpOptions = true,
        description = "Send Message")
class SendMessage implements Runnable {

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    Composite.IdentityInfo identityInfo

    @CommandLine.Option(names = ["-m", "--message"], paramLabel = "MESSAGE", description = "message to send", required = true)
    String messageString

    @CommandLine.Option(names = ["-d", "--address"], paramLabel = "ADDRESS", description = "Address to which message is sent", required = true)
    String addressString

    void run() {

        RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.SUNSTONE, Utils.getIdentity(identityInfo))
        RadixAddress address = RadixAddress.from(addressString)

        api.sendMessage(address, messageString.getBytes(RadixConstants.STANDARD_CHARSET), false).toCompletable().blockingAwait()

//        TODO transferTokens is not valid api method
//        api.transferTokens(address, amount, TokenDefinitionReference.of(tokenAddress, iso)).toCompletable().blockingAwait();


    }

}