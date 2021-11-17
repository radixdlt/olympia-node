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

package com.radixdlt.store.tree;

import java.nio.ByteBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

public class PMTTest {

  private static final Logger logger = LogManager.getLogger();

  @Test
  public void serializationPrefix() {

    var p1 = ByteBuffer.allocate(8).putInt(2).get();
    // var p2 = ByteBuffer.allocate(4).putInt(3).get();

    logger.info("8 bit even prefix {}", p1);
    // logger.info("4 bit even prefix {}", p2);

    int high1 = (p1 & 0xf0) >> 4;
    int low1 = p1 & 0xf;

    // int high2 = (p2 & 0xf0) >> 4;
    // int low2 = p2 & 0xf;

    logger.info("8 bit even prefix in nibbles {} {}", high1, low1);
    // logger.info("4 bit even prefix in nibbles {} {}", high2, low2);
  }

  @Test
  public void commonPrefix() {

    // example Substates
    var path1 =
        "300126433882d547b3fbb20ca1935879e03a4f75b474546ccf39b4cd03edbe1600000000".getBytes();
    var path2 =
        "300126433882d547b3fbb20ca1935879e03a4f75b474546ccf39b4cd03edbe1600000001".getBytes();

    logger.info("path1: {}", path1);
    logger.info("paxth2xxxx: {}", path2);

    var lengthP1 = path1.length;
    var lengthP2 = path2.length;

    var nibsPath1 = new int[lengthP1 * 2];
    var nibsIndex = 0;
    for (byte b : path1) {
      logger.info("path1: {}", b);
      int high = (b & 0xf0) >> 4;
      int low = b & 0xf;

      nibsPath1[nibsIndex] = high;
      nibsPath1[nibsIndex + 1] = low;

      logger.info("h: {} {} ", high);
      logger.info("l: {}", low);
      System.out.println("..");

      nibsIndex = nibsIndex + 2;

      logger.info("p1: index: {} cur: {}", nibsIndex, nibsPath1);
    }

    var nibsPath2 = new int[lengthP2 * 2];
    nibsIndex = 0;
    for (byte b : path2) {
      logger.info("path2: {}", b);
      int high = (b & 0xf0) >> 4;
      int low = b & 0xf;

      nibsPath2[nibsIndex] = high;
      nibsPath2[nibsIndex + 1] = low;

      logger.info("h: {}", high);
      logger.info("l: {}", low);
      System.out.println("..");

      nibsIndex = nibsIndex + 2;
    }

    logger.info("p1: {}", nibsPath1);
    logger.info("p2: {}", nibsPath2);

    var smaller = Math.min(nibsPath1.length, nibsPath2.length);
    for (int i = 0; i < smaller; i++) {
      logger.info("index: {}, val: {} {}", i, nibsPath1[i], nibsPath2[i]);
      if (nibsPath1[i] == nibsPath2[i]) {
        logger.info("YES");
      } else {
        logger.info("NO");
        break;
      }
    }
  }
}
