package com.radixdlt.cli

import com.radixdlt.client.application.RadixApplicationAPI
import picocli.CommandLine

@CommandLine.Command(name = "get-message", mixinStandardHelpOptions = true,
        description = "Get Message")
class GetMessage implements Runnable {

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    Composite.IdentityInfo identityInfo

    void run() {

        RadixApplicationAPI api = Utils.getAPI(identityInfo)
        api.pull()
        api.observeMessages().subscribe({ it -> println it })
    }

}

