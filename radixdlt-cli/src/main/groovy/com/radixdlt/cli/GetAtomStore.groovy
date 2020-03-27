package com.radixdlt.cli

import com.radixdlt.client.application.RadixApplicationAPI
import com.radixdlt.client.core.Bootstrap
import picocli.CommandLine

@CommandLine.Command(name = "get-atoms", mixinStandardHelpOptions = true,
        description = "Get Atoms")
class GetAtomStore implements Runnable {

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    Composite.IdentityInfo identityInfo

    void run() {

        RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, Utils.getIdentity(identityInfo))
        api.pull()

        def atomStore = api.getAtomStore()
        def observations = atomStore.getAtomObservations(api.getAddress())
        observations.filter({ it -> return it.isHead() }).blockingFirst()
        atomStore.getStoredAtoms(api.getAddress()).each { it -> println it.getAid() }
        System.exit(0)
    }

}

