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

package com.radixdlt.network.p2p;

import static com.radixdlt.network.p2p.Subnet.fromString;
import static org.junit.Assert.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.Assert;
import org.junit.Test;

public class SubnetTest {
  @Test
  public void validSubnetsAreParsed() {
    fromString("0.0.0.0/0").ifPresentOrElse(v -> validate(v, "0.0.0.0", 0), Assert::fail);
    fromString("1.1.1.1/16").ifPresentOrElse(v -> validate(v, "1.1.1.1", 16), Assert::fail);
    fromString("123.132.34.128/25")
        .ifPresentOrElse(v -> validate(v, "123.132.34.128", 25), Assert::fail);
    fromString("10.0.0.0/8").ifPresentOrElse(v -> validate(v, "10.0.0.0", 8), Assert::fail);
    fromString("172.16.0.0/12").ifPresentOrElse(v -> validate(v, "172.16.0.0", 12), Assert::fail);
    fromString("192.168.0.0/16").ifPresentOrElse(v -> validate(v, "192.168.0.0", 16), Assert::fail);
  }

  @Test
  public void inValidSubnetsAreRejected() {
    // Missing components
    fromString("10.0.0.0/").ifPresent(this::fail);
    fromString("10.0.0.0").ifPresent(this::fail);
    fromString("/8").ifPresent(this::fail);
    fromString("8").ifPresent(this::fail);

    // Incomplete address
    fromString("1.0.0./0").ifPresent(this::fail);
    fromString("1.0.0/0").ifPresent(this::fail);

    // Address component starting from zero
    fromString("01.0.0.0/0").ifPresent(this::fail);
    fromString("1.01.1.1/16").ifPresent(this::fail);
    fromString("1.1.01.1/16").ifPresent(this::fail);
    fromString("1.1.1.01/16").ifPresent(this::fail);

    // Address component above 255
    fromString("256.0.0.0/0").ifPresent(this::fail);
    fromString("1.256.1.1/16").ifPresent(this::fail);
    fromString("1.1.256.1/16").ifPresent(this::fail);
    fromString("1.1.1.257/16").ifPresent(this::fail);

    // Negative bits
    fromString("172.16.0.0/-1").ifPresent(this::fail);
    // Bits above 32
    fromString("192.168.0.0/33").ifPresent(this::fail);
  }

  @Test
  public void maskIsCalculatedProperly() {
    fromString("0.0.0.0/0").ifPresentOrElse(v -> assertEquals(0L, v.bitMask()), Assert::fail);
    fromString("0.0.0.0/1")
        .ifPresentOrElse(v -> assertEquals(0x080000000L, v.bitMask()), Assert::fail);
    fromString("0.0.0.0/8")
        .ifPresentOrElse(v -> assertEquals(0x0FF000000L, v.bitMask()), Assert::fail);
    fromString("0.0.0.0/12")
        .ifPresentOrElse(v -> assertEquals(0x0FFF00000L, v.bitMask()), Assert::fail);
    fromString("0.0.0.0/16")
        .ifPresentOrElse(v -> assertEquals(0x0FFFF0000L, v.bitMask()), Assert::fail);
    fromString("0.0.0.0/24")
        .ifPresentOrElse(v -> assertEquals(0x0FFFFFF00L, v.bitMask()), Assert::fail);
    fromString("0.0.0.0/32")
        .ifPresentOrElse(v -> assertEquals(0x0FFFFFFFFL, v.bitMask()), Assert::fail);
  }

  @Test
  public void subnetMatchesAddressesWithinSubnet() {
    fromString("0.0.0.0/0")
        .ifPresentOrElse(subnet -> validateSubnetMatch(subnet, "127.0.0.1"), Assert::fail);
    fromString("192.168.0.0/16")
        .ifPresentOrElse(subnet -> validateSubnetMatch(subnet, "192.168.1.1"), Assert::fail);
    fromString("192.168.1.0/24")
        .ifPresentOrElse(subnet -> validateSubnetMatch(subnet, "192.168.1.1"), Assert::fail);
    fromString("192.168.1.10/32")
        .ifPresentOrElse(subnet -> validateSubnetMatch(subnet, "192.168.1.10"), Assert::fail);
  }

  @Test
  public void subnetRejectsAddressesOutsideSubnet() {
    fromString("192.168.1.0/24")
        .ifPresentOrElse(subnet -> validateNoSubnetMatch(subnet, "192.168.2.1"), Assert::fail);
    fromString("192.168.1.10/32")
        .ifPresentOrElse(subnet -> validateNoSubnetMatch(subnet, "192.168.1.11"), Assert::fail);
    fromString("192.168.1.10/16")
        .ifPresentOrElse(subnet -> validateNoSubnetMatch(subnet, "192.169.1.10"), Assert::fail);
    fromString("128.0.0.0/31")
        .ifPresentOrElse(subnet -> validateNoSubnetMatch(subnet, "127.0.0.1"), Assert::fail);
  }

  private void validateSubnetMatch(Subnet subnet, String host) {
    try {
      assertTrue(subnet.matches(InetAddress.getByName(host)));
    } catch (UnknownHostException e) {
      Assert.fail("Invalid host: " + host);
    }
  }

  private void validateNoSubnetMatch(Subnet subnet, String host) {
    try {
      assertFalse(subnet.matches(InetAddress.getByName(host)));
    } catch (UnknownHostException e) {
      Assert.fail("Invalid host: " + host);
    }
  }

  private void fail(Subnet subnet) {
    Assert.fail("Subnet " + subnet + ", should not have been accepted");
  }

  private void validate(Subnet subnet, String net, int numBits) {
    assertEquals(net, subnet.address().toString().substring(1));
    assertEquals(numBits, subnet.numBits());
  }
}
