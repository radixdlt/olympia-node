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

import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.identifiers.RadixAddress;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static com.radixdlt.cli.Utils.printf;
import static com.radixdlt.cli.Utils.println;

/**
 * Register the validator
 * <br>
 * Usage:
 * <pre>
 *  $ radixdlt-cli register-validator -k=<keystore name> -p=<keystore password> [-i=<info URL>] [-a=<delegator address>]
 * </pre>
 * The {@code -i=<info URL>} parameter is optional.<br>
 * The {@code -a=<delegator address>} is optional and can be specified multiple times. Every occurence adds one
 * delegator.
 */
@CommandLine.Command(name = "register-validator", mixinStandardHelpOptions = true,
		description = "Register as a Validator")
public class RegisterValidator implements Runnable {

	@CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
	private Composite.IdentityInfo identityInfo;

	@CommandLine.Option(names = {"-i", "--info-url"}, paramLabel = "INFO_URL", description = "info url", required = false)
	private String infoUrl;

	@CommandLine.Option(
			names = {"-a", "--allow-delegators"},
			paramLabel = "ALLOWED_DELEGATORS",
			description = "addresses to allow as delegators to this validator", required = false
	)
	private String[] allowedDelegators;

	@Override
	public void run() {
		Set<RadixAddress> delegators = allowedDelegators == null ? ImmutableSet.of() : Arrays.stream(allowedDelegators)
				.map(RadixAddress::from)
				.collect(Collectors.toSet());

		RadixApplicationAPI api = Utils.getAPI(identityInfo);
		api.pullOnce(api.getAddress()).blockingAwait();
		api.registerValidator(api.getAddress(), delegators, infoUrl).blockUntilComplete();
		printf("Registered %s as a validator:\n", api.getAddress());
		printf("  url: %s%n\n", infoUrl == null ? "<not set>" : infoUrl);
		printf("  allowedDelegators: %s%n\n", delegators.isEmpty() ? "<not set, allows any>" : allowedDelegators);
		println("Done");
	}
}
