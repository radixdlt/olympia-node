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

import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identity.RadixIdentities;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.GeneralSecurityException;

/**
 * This command generates new private key and prints it to standard output.
 * <br>
 * Usage:
 * <pre>
 *  $ radixdlt-cli generate-key -p=<password>
 * </pre>
 * The password is required and it should not be empty.
 */
@Command(name = "generate-key", mixinStandardHelpOptions = true,
        description = "Generate key")
public class KeyGenerator implements Runnable {

    @Option(names = {"-p", "--password"}, paramLabel = "PASSWORD", description = "password", required = true)
    private String password;

    @Override
    public void run() {
        if (password == null || password.isBlank()) {
            System.out.println("The password must be provided");
            return;
        }

        try (var writer = new PrintWriter(System.out)) {
            RadixIdentities.createNewEncryptedIdentity(writer, password);
            writer.flush();
        } catch (IOException | GeneralSecurityException | PrivateKeyException | PublicKeyException e) {
            System.out.println("Unable to generate keys due to following error:\n" + e.getMessage());
            return;
        }
        System.out.println("Done");
    }
}
