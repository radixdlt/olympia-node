package com.radixdlt.cli


import com.radixdlt.client.application.RadixApplicationAPI
import com.radixdlt.client.core.Bootstrap
import picocli.CommandLine

@CommandLine.Command(name = "get-message", mixinStandardHelpOptions = true,
        description = "Get Message")
class GetMessage implements Runnable {

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    Composite.IdentityInfo identityInfo

    void run() {

        RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, Utils.getIdentity(identityInfo))
        api.pull()
        api.observeMessages().subscribe({ it -> println it })
    }

}

