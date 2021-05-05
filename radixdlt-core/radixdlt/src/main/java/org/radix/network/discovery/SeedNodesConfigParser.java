/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.network.discovery;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import com.google.inject.Inject;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.utils.Pair;
import com.google.common.collect.ImmutableSet;

// TODO: move to PeerDiscovery
public final class SeedNodesConfigParser {
	private final int defaultPort;
	private final Set<String> unresolvedUris = new HashSet<>();
	private final Set<RadixNodeUri> resolvedSeedNodes = new HashSet<>();

	@Inject
	public SeedNodesConfigParser(P2PConfig config) {
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

		final var newlyResolvedHosts = this.unresolvedUris.stream()
			.map(host -> Pair.of(host, resolveRadixNodeUri(host)))
			.filter(p -> p.getSecond().isPresent())
			.collect(ImmutableSet.toImmutableSet());

		final var newlyResolvedHostsNames = newlyResolvedHosts.stream().map(Pair::getFirst)
			.collect(ImmutableSet.toImmutableSet());

		this.unresolvedUris.removeAll(newlyResolvedHostsNames);

		this.resolvedSeedNodes.addAll(
			newlyResolvedHosts.stream()
				.map(p -> p.getSecond().get())
				.collect(Collectors.toList())
		);
	}

	private Optional<RadixNodeUri> resolveRadixNodeUri(String rawUri) {
		try {
			final var parsedUri = new URI(rawUri);
			final var resolved = InetAddress.getByName(parsedUri.getHost());
			final var newUri = new URI(String.format(
				"radix://%s@%s:%s",
				parsedUri.getUserInfo(),
				resolved.getHostAddress(),
				(parsedUri.getPort() > 0 ? parsedUri.getPort() : defaultPort)
			));
			return Optional.of(RadixNodeUri.fromUri(newUri));
		} catch (UnknownHostException | URISyntaxException | PublicKeyException e) {
			return Optional.empty();
		}
	}
}
