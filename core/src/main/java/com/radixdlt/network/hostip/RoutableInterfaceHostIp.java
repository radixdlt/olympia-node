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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Streams;
import com.google.common.net.HostAndPort;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Query for a public IP address from local interfaces. Non-routable IP addresses are ignored. */
public class RoutableInterfaceHostIp implements HostIp {
  private static final Logger log = LogManager.getLogger();

  static HostIp create() {
    return new RoutableInterfaceHostIp();
  }

  private final Supplier<Optional<String>> result = Suppliers.memoize(this::get);

  @Override
  public Optional<String> hostIp() {
    return result.get();
  }

  private Optional<String> get() {
    try {
      return hostIp(Iterators.forEnumeration(NetworkInterface.getNetworkInterfaces()));
    } catch (SocketException e) {
      log.warn("Exception while retrieving network interfaces", e);
    }
    return Optional.empty();
  }

  @VisibleForTesting
  Optional<String> hostIp(Iterator<NetworkInterface> interfaces) {
    try {
      ImmutableList<HostAndPort> addresses =
          addresses(interfaces)
              .map(addr -> HostAndPort.fromHost(addr.getHostAddress()))
              .collect(ImmutableList.toImmutableList());
      if (addresses.isEmpty()) {
        log.debug("No addresses found");
      } else if (addresses.size() > 1) {
        log.warn("Too many addresses {}", addresses);
      } else {
        HostAndPort hap = addresses.get(0);
        log.debug("Found address {}", hap);
        return Optional.of(hap.getHost());
      }
    } catch (IllegalArgumentException e) {
      log.warn("Exception while retrieving interface address: {}", e.getMessage());
    }
    return Optional.empty();
  }

  private Stream<InetAddress> addresses(Iterator<NetworkInterface> networkInterfaces) {
    return Streams.stream(networkInterfaces)
        .flatMap(this::interfaceAddresses)
        .filter(RoutableInterfaceHostIp::filter);
  }

  private Stream<InetAddress> interfaceAddresses(NetworkInterface ni) {
    Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
    List<InetAddress> addrList = Collections.list(inetAddresses);
    if (log.isDebugEnabled()) {
      for (InetAddress addr : addrList) {
        log.debug(
            "Interface {}/{} IP {}", ni.getName(), ni.getDisplayName(), addr.getHostAddress());
      }
    }
    return addrList.stream();
  }

  @VisibleForTesting
  static boolean filter(InetAddress address) {
    return !(address.isSiteLocalAddress()
        || address.isLinkLocalAddress()
        || address.isLoopbackAddress()
        || address.isMulticastAddress());
  }
}
