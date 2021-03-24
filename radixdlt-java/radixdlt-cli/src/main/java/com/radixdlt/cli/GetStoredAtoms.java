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

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.core.atoms.Atoms;
import picocli.CommandLine;

import static com.radixdlt.cli.Utils.printfln;
import static com.radixdlt.cli.Utils.println;

/**
 * This command shows stored atoms
 * <br>
 * Usage:
 * <pre>
 *  $ radixdlt-cli get-stored-atoms [-k=<keystore name>] [-p=<keystore password>]
 * </pre>
 */
@CommandLine.Command(name = "get-stored-atoms", mixinStandardHelpOptions = true,
		description = "Get stored Atoms")
public class GetStoredAtoms implements Runnable {
	@CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
	private Composite.IdentityInfo identityInfo;

	@Override
	public void run() {
		RadixApplicationAPI api = Utils.getAPI(identityInfo);

		println("Retrieving atoms...");
		api.pullOnce(api.getAddress()).blockingAwait();

		println("Atom ID's:");
		api.getAtomStore().getStoredAtoms(api.getAddress()).forEach(it -> printfln("  %s", Atoms.atomIdOf(it)));
		println("Done");
	}
}

