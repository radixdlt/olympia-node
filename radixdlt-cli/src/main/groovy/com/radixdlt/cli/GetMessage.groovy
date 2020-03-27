package com.radixdlt.cli

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.radixdlt.client.application.RadixApplicationAPI
import com.radixdlt.client.atommodel.accounts.RadixAddress
import com.radixdlt.client.core.Bootstrap
import picocli.CommandLine

@CommandLine.Command(name = "get-message", mixinStandardHelpOptions = true,
        description = "Get Message")
class GetMessage implements Runnable {

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    Composite.IdentityInfo identityInfo

    void run() {

        RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.SUNSTONE, Utils.getIdentity(identityInfo))
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(RadixAddress.class, new RadixAddressTypeAdapter())
                .excludeFieldsWithoutExposeAnnotation()
                .create()

//        TODO Below api doesn't have getMessages
//        api.pull();
//        api.getMessages()
//                .map(gson::toJson)
//                .subscribe(a -> {
//                    System.out.println(a);
//                    System.out.flush();
//                });
//        TimeUnit.SECONDS.sleep(3);
    }

}

