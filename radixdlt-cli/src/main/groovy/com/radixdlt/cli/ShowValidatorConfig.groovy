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

package com.radixdlt.cli

import com.radixdlt.client.application.RadixApplicationAPI
import com.radixdlt.client.atommodel.validators.RegisteredValidatorParticle
import com.radixdlt.identifiers.RadixAddress
import picocli.CommandLine

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
class ShowValidatorConfig implements Runnable {

	@CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
	Composite.IdentityInfo identityInfo

	@CommandLine.Option(names = ["-d", "--address"], paramLabel = "ADDRESS", description = "Validator address to show config of", required = false)
	String addressString

	@Override
	void run() {
		RadixApplicationAPI api = Utils.getAPI(identityInfo)
		RadixAddress address = addressString == null ? api.getAddress() : RadixAddress.from(addressString)
		api.pullOnce(api.getAddress()).blockingAwait()
		def latestValidatorRegistration = api.getAtomStore().getUpParticles(api.getAddress(), null)
				.filter({ particle -> particle instanceof RegisteredValidatorParticle })
				.findFirst()
		if (latestValidatorRegistration.isPresent()) {
			println "Current validator configuration at ${address}:"
			def validator = latestValidatorRegistration.get()
			def url = validator.getUrl()
			def allowedDelegators = validator.getAllowedDelegators()
			printf("  url: %s%n", url == null ? "<not set>" : url)
			printf("  allowedDelegators: %s%n", allowedDelegators == null ? "<not set, allows any>" : allowedDelegators)
		} else {
			println "No active validator configuration at ${address}"
		}
	}
}
