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
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.RadixConstants;
import picocli.CommandLine;

import static com.radixdlt.cli.Utils.printfln;
import static com.radixdlt.cli.Utils.println;

/**
 * Send message to specified address
 * <br>
 * Usage:
 * <pre>
 *  $ radixdlt-cli send-message -k=<keystore name> -p=<keystore password> -m=<message> -d=<destination address>
 * </pre>
 */
@CommandLine.Command(name = "send-message", mixinStandardHelpOptions = true,
		description = "Send Message")
public class SendMessage implements Runnable {

	@CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
	private
	Composite.IdentityInfo identityInfo;

	@CommandLine.Option(names = {"-m", "--message"}, paramLabel = "MESSAGE", description = "message to send", required = true)
	private
	String messageString;

	@CommandLine.Option(names = {"-d", "--address"}, paramLabel = "ADDRESS", description = "Address to which message is sent", required = true)
	private
	String addressString;

	@Override
	public void run() {
		RadixApplicationAPI api = Utils.getAPI(identityInfo);
		RadixAddress address = RadixAddress.from(addressString);

		printfln("Sending message '%s' to address %s", messageString, address);
		RadixApplicationAPI.Result result = api.sendMessage(address, messageString.getBytes(RadixConstants.STANDARD_CHARSET), true);
		result.blockUntilComplete();
		printfln("Message sent successfully. AtomID of resulting atom : %s", result.getAtom().getAid());
		println("Done");
	}
}