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

package com.radixdlt.network.p2p.discovery;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.NetworkId;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Pair;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

// TODO: move to PeerDiscovery
public final class SeedNodesConfigParser {
  private final int defaultPort;
  private final Set<String> unresolvedUris = new HashSet<>();
  private final Set<RadixNodeUri> resolvedSeedNodes = new HashSet<>();
  private final Addressing addressing;
  private final int networkId;

  @Inject
  public SeedNodesConfigParser(P2PConfig config, @NetworkId int networkId, Addressing addressing) {
    this.networkId = networkId;
    this.addressing = addressing;
    this.defaultPort = config.defaultPort();
    this.unresolvedUris.addAll(config.seedNodes());
    this.resolveHostNames();
  }

  public Set<RadixNodeUri> getResolvedSeedNodes() {
    this.resolveHostNames();
    return this.resolvedSeedNodes;
  }

  private void resolveHostNames() {
    if (this.unresolvedUris.isEmpty()) {
      return;
    }

    final var newlyResolvedHosts =
        this.unresolvedUris.stream()
            .map(host -> Pair.of(host, resolveRadixNodeUri(host)))
            .filter(p -> p.getSecond().isPresent())
            .collect(ImmutableSet.toImmutableSet());

    final var newlyResolvedHostsNames =
        newlyResolvedHosts.stream().map(Pair::getFirst).collect(ImmutableSet.toImmutableSet());

    this.unresolvedUris.removeAll(newlyResolvedHostsNames);

    this.resolvedSeedNodes.addAll(
        newlyResolvedHosts.stream().map(p -> p.getSecond().get()).toList());
  }

  private Optional<RadixNodeUri> resolveRadixNodeUri(String rawUri) {
    try {
      final var parsedUri = new URI(rawUri);
      final var resolved = InetAddress.getByName(parsedUri.getHost());
      // FIXME: This is a bit messy, should have clearer logic on the checks
      return Optional.of(
          RadixNodeUri.fromPubKeyAndAddress(
              networkId,
              addressing.forNodes().parse(parsedUri.getUserInfo()),
              resolved.getHostAddress(),
              parsedUri.getPort() > 0 ? parsedUri.getPort() : defaultPort));
    } catch (UnknownHostException | URISyntaxException | DeserializeException e) {
      return Optional.empty();
    }
  }
}
