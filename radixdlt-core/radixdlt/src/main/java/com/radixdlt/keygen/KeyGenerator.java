/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.keygen;

import static com.radixdlt.errors.ApiErrors.MISSING_PARAMETER;
import static com.radixdlt.errors.InternalErrors.GENERAL;
import static com.radixdlt.errors.InternalErrors.MISSING_KEYSTORE_FILE;
import static com.radixdlt.errors.InternalErrors.UNABLE_TO_LOAD_KEYSTORE;
import static com.radixdlt.errors.InternalErrors.UNABLE_TO_PARSE_COMMAND_LINE;
import static com.radixdlt.utils.functional.Failure.failure;
import static com.radixdlt.utils.functional.Result.allOf;
import static com.radixdlt.utils.functional.Result.fromOptional;
import static java.util.Optional.ofNullable;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.RadixKeyStore;
import com.radixdlt.utils.functional.Failure;
import com.radixdlt.utils.functional.Result;
import java.io.File;
import java.security.Security;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/** Command line utility for key generation. */
public class KeyGenerator {
  private static final String DEFAULT_KEYPAIR_NAME = "node";

  static {
    Security.insertProviderAt(new BouncyCastleProvider(), 1);
  }

  private final Options options;

  private KeyGenerator() {
    options =
        new Options()
            .addOption("h", "help", false, "Show usage information (this message)")
            .addOption("k", "keystore", true, "Keystore name")
            .addOption("p", "password", true, "Password for keystore")
            .addOption(
                "n", "keypair-name", true, "Key pair name (optional, default name is 'node')")
            .addOption(
                "pk",
                "show-public-key",
                false,
                "Prints the public key of an existing " + "keypair and exits");
  }

  public static void main(String[] args) {
    var rc = new KeyGenerator().run(args).fold(Failure::code, __ -> 0);
    System.exit(rc);
  }

  private Result<Void> run(String[] args) {
    return parseParameters(args)
        .filter(commandLine -> !commandLine.hasOption("h"), irrelevant())
        .filter(commandLine -> commandLine.getOptions().length != 0, irrelevant())
        .flatMap(
            cli ->
                allOf(parseKeystore(cli), parsePassword(cli), parseKeypair(cli), parseShowPk(cli))
                    .flatMap(this::generateKeypair))
        .onFailure(failure -> usage(failure.message()))
        .onSuccessDo(() -> System.out.println("Done"));
  }

  private Failure irrelevant() {
    return failure(0, "");
  }

  private void usage(String message) {
    if (!message.isEmpty()) {
      System.out.println("ERROR: " + message);
    }
    new HelpFormatter().printHelp(KeyGenerator.class.getSimpleName(), options, true);
  }

  private Result<Void> generateKeypair(
      String keystore, String password, String keypairName, boolean shouldShowPk) {
    var keystoreFile = new File(keystore);
    var newFile = !keystoreFile.canWrite();

    if (shouldShowPk) {
      return printPublicKey(keystoreFile, password, keypairName, newFile);
    }

    var keyPair = ECKeyPair.generateNew();
    var publicKey = keyPair.getPublicKey().toHex();

    System.out.printf(
        "Writing keypair '%s' [public key: %s]%ninto %s keystore %s%n",
        keypairName, publicKey, newFile ? "new" : "existing", keystore);

    return Result.wrap(
        UNABLE_TO_LOAD_KEYSTORE,
        () -> {
          RadixKeyStore.fromFile(keystoreFile, password.toCharArray(), newFile)
              .writeKeyPair(keypairName, keyPair);
          return null;
        });
  }

  private Result<Boolean> parseShowPk(CommandLine commandLine) {
    return Result.ok(commandLine.hasOption("pk"));
  }

  private Result<Void> printPublicKey(
      File keystoreFile, String password, String keypairName, boolean newFile) {
    if (!keystoreFile.exists() || !keystoreFile.canRead()) {
      return Result.fail(MISSING_KEYSTORE_FILE.with(keystoreFile));
    }

    return Result.wrap(
        GENERAL,
        () -> {
          ECKeyPair keyPair =
              RadixKeyStore.fromFile(keystoreFile, password.toCharArray(), newFile)
                  .readKeyPair(keypairName, false);
          System.out.printf(
              "Public key of keypair '%s': %s%n", keypairName, keyPair.getPublicKey().toHex());
          return null;
        });
  }

  private Result<String> parseKeystore(CommandLine commandLine) {
    return requiredString(commandLine, "k");
  }

  private Result<String> parsePassword(CommandLine commandLine) {
    return requiredString(commandLine, "p");
  }

  private Result<String> parseKeypair(CommandLine commandLine) {
    return requiredString(commandLine, "n").or(Result.ok(DEFAULT_KEYPAIR_NAME));
  }

  private Result<String> requiredString(CommandLine commandLine, String opt) {
    return fromOptional(
        () -> MISSING_PARAMETER.with(opt), ofNullable(commandLine.getOptionValue(opt)));
  }

  private Result<CommandLine> parseParameters(String[] args) {
    return Result.wrap(
        UNABLE_TO_PARSE_COMMAND_LINE, () -> new DefaultParser().parse(options, args));
  }
}
