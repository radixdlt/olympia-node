/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */
package com.radixdlt.cli;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.security.Security;

import static com.radixdlt.cli.Utils.println;

@Command(name = "radix",
		version = "1.0",
		mixinStandardHelpOptions = true,
		subcommands = {
			KeyGenerator.class,
			GetMessages.class,
			GetDetails.class,
			GetStoredAtoms.class,
			CreateAndMintToken.class,
			RegisterValidator.class,
			UnregisterValidator.class,
			ShowValidatorConfig.class,
			ValidatorKeyGenerator.class
		})
public class RadixCLI implements Runnable {
	static {
		Security.insertProviderAt(new BouncyCastleProvider(), 1);
	}

	@Override
	public void run() {
		println("Radix Command Line Utility ");
	}

	public static void main(String[] args) {
		CommandLine cmd = new CommandLine(new RadixCLI());
		cmd.execute(args);

		if (args.length == 0) {
			cmd.printVersionHelp(System.out);
			cmd.usage(System.out);
		}
		System.exit(0);
	}
}
