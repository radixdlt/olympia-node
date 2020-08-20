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

package com.radixdlt.network.hostip;

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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Streams;
import com.google.common.net.HostAndPort;

/**
 * Query for a public IP address from local interfaces.
 * Non-routable IP addresses are ignored.
 */
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
			ImmutableList<HostAndPort> addresses = addresses(interfaces)
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
				log.debug("Interface {}/{} IP {}", ni.getName(), ni.getDisplayName(), addr.getHostAddress());
			}
		}
		return addrList.stream();
	}

	@VisibleForTesting
	static boolean filter(InetAddress address) {
		return !(
			   address.isSiteLocalAddress()
			|| address.isLinkLocalAddress()
			|| address.isLoopbackAddress()
			|| address.isMulticastAddress()
		);
	}
}