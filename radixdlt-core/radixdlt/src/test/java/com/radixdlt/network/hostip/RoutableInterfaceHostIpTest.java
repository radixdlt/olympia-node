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

package com.radixdlt.network.hostip;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.Iterators;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Vector;
import org.junit.Test;

public class RoutableInterfaceHostIpTest {

  @Test
  public void testEmpty() {
    RoutableInterfaceHostIp rihip = make();
    assertFalse(rihip.hostIp(Collections.emptyIterator()).isPresent());
  }

  @Test
  public void testLoopback() throws UnknownHostException {
    RoutableInterfaceHostIp rihip = make();

    InetAddress loopback = InetAddress.getByName("127.0.0.1");
    Vector<InetAddress> addresses = new Vector<>(List.of(loopback));

    NetworkInterface ni = mock(NetworkInterface.class);
    when(ni.getInetAddresses()).thenReturn(addresses.elements());

    assertFalse(rihip.hostIp(Iterators.forArray(ni)).isPresent());
  }

  @Test
  public void testLinkLocal() throws UnknownHostException {
    RoutableInterfaceHostIp rihip = make();

    InetAddress linklocal = InetAddress.getByName("169.254.0.0");
    Vector<InetAddress> addresses = new Vector<>(List.of(linklocal));

    NetworkInterface ni = mock(NetworkInterface.class);
    when(ni.getInetAddresses()).thenReturn(addresses.elements());

    assertFalse(rihip.hostIp(Iterators.forArray(ni)).isPresent());
  }

  @Test
  public void testMulticast() throws UnknownHostException {
    RoutableInterfaceHostIp rihip = make();

    InetAddress linklocal = InetAddress.getByName("224.0.0.1");
    Vector<InetAddress> addresses = new Vector<>(List.of(linklocal));

    NetworkInterface ni = mock(NetworkInterface.class);
    when(ni.getInetAddresses()).thenReturn(addresses.elements());

    assertFalse(rihip.hostIp(Iterators.forArray(ni)).isPresent());
  }

  @Test
  public void testNonRoutable() throws UnknownHostException {
    RoutableInterfaceHostIp rihip = make();

    InetAddress nonroutable = InetAddress.getByName("192.168.0.1");
    Vector<InetAddress> addresses = new Vector<>(List.of(nonroutable));

    NetworkInterface ni = mock(NetworkInterface.class);
    when(ni.getInetAddresses()).thenReturn(addresses.elements());

    Optional<String> result = rihip.hostIp(Iterators.forArray(ni));
    assertFalse(result.isPresent());
  }

  @Test
  public void testRoutable() throws UnknownHostException {
    RoutableInterfaceHostIp rihip = make();

    InetAddress routable = InetAddress.getByName("8.8.8.8");
    Vector<InetAddress> addresses = new Vector<>(List.of(routable));

    NetworkInterface ni = mock(NetworkInterface.class);
    when(ni.getInetAddresses()).thenReturn(addresses.elements());

    Optional<String> result = rihip.hostIp(Iterators.forArray(ni));
    assertTrue(result.isPresent());
    assertEquals("8.8.8.8", result.get());
  }

  @Test
  public void testTooManyAddresses() throws UnknownHostException {
    RoutableInterfaceHostIp rihip = make();

    InetAddress addr1 = InetAddress.getByName("8.8.8.8");
    InetAddress addr2 = InetAddress.getByName("8.8.4.4");
    Vector<InetAddress> addresses = new Vector<>(List.of(addr1, addr2));

    NetworkInterface ni = mock(NetworkInterface.class);
    when(ni.getInetAddresses()).thenReturn(addresses.elements());

    Optional<String> result = rihip.hostIp(Iterators.forArray(ni));
    assertFalse(result.isPresent());
  }

  @Test
  public void testInvalidAddress() {
    // Not sure how this can really happen without a O/S fail
    RoutableInterfaceHostIp rihip = make();

    InetAddress badAddr = mock(InetAddress.class);
    when(badAddr.getHostAddress()).thenReturn("a:b");
    Vector<InetAddress> addresses = new Vector<>(List.of(badAddr));

    NetworkInterface ni = mock(NetworkInterface.class);
    when(ni.getInetAddresses()).thenReturn(addresses.elements());

    Optional<String> result = rihip.hostIp(Iterators.forArray(ni));
    assertFalse(result.isPresent());
  }

  private static RoutableInterfaceHostIp make() {
    return (RoutableInterfaceHostIp) RoutableInterfaceHostIp.create();
  }
}
