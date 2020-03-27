package com.radixdlt.cli


import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

//@Grab('info.picocli:picocli:2.0.3')

@Command(name = "radix", version = "1.0",
        mixinStandardHelpOptions = true,
        description = "Radix CLI", subcommands = [
                KeyGenerator.class, GetBalance.class, GetMessage.class,
                SendMessage.class, SendTokens.class, GetDetails.class,
                GetAtomStore.class, CreateAndMintToken.class])
class RadixCLI implements Runnable {


    @Parameters(hidden = true)
    List<String> allParameters

    void run() {
        print "Running Radix CLI"
    }

    static void main(String[] args) {
        CommandLine cmd = new CommandLine(new RadixCLI())
        cmd.execute(args)
    }
}

