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

@CommandLine.Command(name = "show-validator-config", mixinStandardHelpOptions = true,
		description = "Show Current Validator Configuration")
class ShowValidatorConfig implements Runnable {

	@CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
	Composite.IdentityInfo identityInfo

	@CommandLine.Option(names = ["-d", "--address"], paramLabel = "ADDRESS", description = "Address to which message is sent", required = true)
	String addressString

	void run() {
		RadixApplicationAPI api = Utils.getAPI(identityInfo)
		RadixAddress address = addressString == null ? api.getAddress() : RadixAddress.from(addressString)
		api.pullOnce(api.getAddress())
		def latestValidatorRegistration = api.getAtomStore().getUpParticles(api.getAddress(), null)
				.filter({ particle -> particle instanceof RegisteredValidatorParticle })
				.map(RegisteredValidatorParticle.class.&cast)
				.findFirst()
		if (latestValidatorRegistration.isPresent()) {
			println "current validator configuration at ${address}:"
			def validator = latestValidatorRegistration.get()
			def url = validator.getUrl()
			def allowedDelegators = validator.getAllowedDelegators()
			printf("url: %s", url == null ? "<not set>" : url)
			printf("allowedDelegators: %s", allowedDelegators == null ? "<not set, allows any>" : allowedDelegators)
		} else {
			println "no active validator configuration at ${address}"
		}
	}
}
