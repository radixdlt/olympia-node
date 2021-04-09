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
import com.radixdlt.atommodel.validators.ValidatorParticle;
import com.radixdlt.identifiers.RadixAddress;
import picocli.CommandLine;

import static com.radixdlt.cli.Utils.printfln;
import static com.radixdlt.cli.Utils.println;
import static java.util.Optional.ofNullable;

/**
 * Show validator configuration
 * <br>
 * Usage:
 * <pre>
 *  $ radixdlt-cli show-validator-config [-d=<address>]
 * </pre>
 * Address is optional and if omitted then local address is used.
 */
@CommandLine.Command(name = "show-validator-config", mixinStandardHelpOptions = true,
		description = "Show Current Validator Configuration")
public class ShowValidatorConfig implements Runnable {

	@CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
	private
	Composite.IdentityInfo identityInfo;

	@CommandLine.Option(
			names = {"-d", "--address"},
			paramLabel = "ADDRESS",
			description = "Validator address to show config of",
			required = false
	)
	private
	String addressString;

	@Override
	public void run() {
		RadixApplicationAPI api = Utils.getAPI(identityInfo);

		RadixAddress address = ofNullable(addressString)
				.map(RadixAddress::from)
				.orElse(api.getAddress());

		api.pullOnce(api.getAddress()).blockingAwait();

		api.getAtomStore()
			.getUpParticles(api.getAddress(), null)
			.filter(substate -> substate.getParticle() instanceof ValidatorParticle)
			.map(substate -> (ValidatorParticle) substate.getParticle())
			.findFirst()
			.ifPresentOrElse(validator -> printDetails(validator, address),
					() -> printfln("No active validator configuration at %s", address)
			);

		println("Done");
	}

	private static void printDetails(ValidatorParticle validator, RadixAddress address) {
		printfln("Current validator configuration at %s:", address);

		var url = ofNullable(validator.getUrl())
				.orElse("<not set>");

		printfln("  url: %s", url);
	}
}
