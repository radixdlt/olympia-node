package com.radixdlt.cli

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.radixdlt.client.application.RadixApplicationAPI
import com.radixdlt.client.atommodel.accounts.RadixAddress
import com.radixdlt.client.core.Bootstrap
import picocli.CommandLine

@CommandLine.Command(name = "get-balance", mixinStandardHelpOptions = true,
        description = "Get Balance")
class GetBalance implements Runnable {

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    Composite.IdentityInfo identityInfo

    void run() {

        RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.SUNSTONE, Utils.getIdentity(identityInfo))
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(RadixAddress.class, new RadixAddressTypeAdapter())
                .excludeFieldsWithoutExposeAnnotation()
                .create()
//        TODO api.getState is not valid method
//        TokenBalanceState tokenBalanceState = api.getState(TokenBalanceState.class, api.getMyAddress()).blockingFirst();
//        System.out.println(gson.toJson(tokenBalanceState));
//        System.exit(0);
    }

}