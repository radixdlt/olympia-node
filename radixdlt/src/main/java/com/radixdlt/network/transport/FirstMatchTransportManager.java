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

package com.radixdlt.network.transport;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.messaging.TransportManager;
import com.radixdlt.network.transport.tcp.TCPConstants;
import com.radixdlt.network.transport.udp.UDPConstants;

/**
 * Returns the highest priority transport that we have in common
 * with the specified peer, and that can handle the specified message.
 */
public final class FirstMatchTransportManager implements TransportManager {
	private static final Logger log = LogManager.getLogger();

	private final ImmutableList<Transport> transports;
	private final Transport defaultTransport;

	@Inject
	public FirstMatchTransportManager(Set<Transport> transports) {
		this.transports = transports.stream()
			.sorted(Comparator.comparingInt(Transport::priority).reversed())
			.distinct()
			.collect(ImmutableList.toImmutableList());
		this.defaultTransport = findDefaultTransport();

		if (this.defaultTransport == null) {
			log.warn("No default transport!  Things will be quiet.");
		}
	}

	@Override
	public Collection<Transport> transports() {
		return this.transports;
	}

	@Override
	public Transport findTransport(Peer peer, byte[] bytes) {
		if (peer != null) {
			// Could probably do something a bit more efficient here with caching and such
			// once the list of transports supported gets reasonably long.

			// Check in priority order for first capable matching transport
			return this.transports.stream()
				.filter(t -> peer.supportsTransport(t.name()))
				.filter(t -> t.canHandle(bytes))
				.findFirst()
				.orElse(this.defaultTransport);
		}
		return this.defaultTransport;
	}

	@Override
	public void close() throws IOException {
		transports.forEach(this::closeSafely);
	}

	@Override
	public String toString() {
		String transportNames = transports.stream().map(Transport::name).collect(Collectors.joining(","));
		return String.format("%s[%s]", getClass().getSimpleName(), transportNames);
	}

	private Transport findDefaultTransport() {
		// Prefer TCP, as that has the widest range of acceptable messages
		Transport tcp = findTransportByName(TCPConstants.NAME);
		if (tcp != null) {
			return tcp;
		}
		// Otherwise, we would like to use UDP
		Transport udp = findTransportByName(UDPConstants.NAME);
		if (udp != null) {
			return udp;
		}
		// If neither is possible, just use whatever we have
		return this.transports.isEmpty() ? null : this.transports.get(0);
	}

	private Transport findTransportByName(String name) {
		return this.transports.stream()
			.filter(t -> name.equals(t.name()))
			.findFirst()
			.orElse(null);
	}

	private void closeSafely(Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (IOException | UncheckedIOException e) {
				log.warn("While closing " + c, e);
			}
		}
	}
}
