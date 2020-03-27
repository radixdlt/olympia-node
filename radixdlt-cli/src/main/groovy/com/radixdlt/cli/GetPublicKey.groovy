package com.radixdlt.cli

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.radixdlt.client.application.RadixApplicationAPI
import com.radixdlt.client.atommodel.accounts.RadixAddress
import com.radixdlt.client.core.Bootstrap
import picocli.CommandLine

@CommandLine.Command(name = "get-public-key", mixinStandardHelpOptions = true,
        description = "Get Public key")
class GetPublicKey implements Runnable {

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    Composite.IdentityInfo identityInfo

    void run() {

        RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.LOCALHOST_SINGLENODE, Utils.getIdentity(identityInfo))
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(RadixAddress.class, new RadixAddressTypeAdapter())
                .excludeFieldsWithoutExposeAnnotation()
                .create()

        def key = Utils.getIdentity(identityInfo).getPublicKey()
        System.out.println(key.toString())
        System.exit(0)

    }

}