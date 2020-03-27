package com.radixdlt.cli

import com.radixdlt.client.application.RadixApplicationAPI
import com.radixdlt.client.core.Bootstrap
import picocli.CommandLine

//@Grab('info.picocli:picocli:2.0.3')

import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

@Command(name = "radix", version = "1.0",
        mixinStandardHelpOptions = true,
        description = "Radix CLI", subcommands = [
                KeyGenerator.class, GetBalance.class, GetMessage.class, SendMessage.class, SendTokens.class])
class RadixCLI implements Runnable {

//    @Option(names = ["-k", "--keyfile"], paramLabel = "KEYFILE", description = "location of keyfile.")
//    String keyFile
//
//    @Option(names = ["-u", "--unencryptedkeyfile"], paramLabel = "UNENCRYPTED_KEYFILE", description = "location of unencrypted keyfile.")
//    String unEncryptedKeyFile


    @Parameters(hidden = true)
    // "hidden": don't show this parameter in usage help message
    List<String> allParameters

    void run() {
        print "Running Radix CLI"
    }

    @Command(name = "myaddr",mixinStandardHelpOptions = true, description = "Get My address")
    void getAddress(@ArgGroup(exclusive = true, multiplicity = "0..1") Composite.IdentityInfo info) {
        RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.SUNSTONE, Utils.getIdentity(info))
        api.getAddress().toString()
        System.out.println(api.getAddress().toString())
        System.exit(0)
    }

    @Command(name = "native-token",mixinStandardHelpOptions = true, description = "Native tokens")
    void nativeTokens(@ArgGroup(exclusive = true, multiplicity = "0..1") Composite.IdentityInfo info) {
        RadixApplicationAPI api = RadixApplicationAPI.create(Bootstrap.SUNSTONE, Utils.getIdentity(info))
        System.out.println('${api.getNativeTokenRef().getAddress()} + "/tokens/"')

//        TODO getSymbol is not valid method
//        System.out.println(api.getNativeTokenRef().getAddress() + "/tokens/" + api.getNativeTokenRef().getSymbol());
    }

    static void main(String[] args) {
        CommandLine cmd = new CommandLine(new RadixCLI())
        cmd.execute(args)
    }
}

