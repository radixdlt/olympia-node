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

package com.radixdlt.network.transport.udp;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import javax.inject.Singleton;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.radixdlt.network.hostip.HostIp;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.Transport;
import com.radixdlt.network.transport.TransportMetadata;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.universe.Universe;

/**
 * Guice configuration for the UDP transport subsystem.
 */
public class UDPTransportModule extends AbstractModule {
	private final UDPConfiguration config;

	public UDPTransportModule(RuntimeProperties properties) {
		this(UDPConfiguration.fromRuntimeProperties(properties));
	}

	public UDPTransportModule(UDPConfiguration config) {
		this.config = Objects.requireNonNull(config);
	}

	@Override
	protected void configure() {
		// UDPOnlyConnectionManager dependencies
	     Multibinder<Transport> transportMultibinder = Multibinder.newSetBinder(binder(), Transport.class);
	     transportMultibinder.addBinding().to(NettyUDPTransportImpl.class);

		// NettyUDPTransportImpl dependencies
		bind(UDPConfiguration.class).toInstance(config);
		bind(TransportMetadata.class).annotatedWith(Names.named("local")).toInstance(StaticTransportMetadata.empty()); // Use defaults for now
		bind(UDPTransportControlFactory.class).toProvider(this::udpTransportControlFactoryProvider);
		bind(UDPTransportOutboundConnectionFactory.class).toProvider(this::udpTransportOutboundConnectionFactoryProvider);
	}

	@Provides
	@Singleton
	private NatHandler natHandlerProvider(HostIp hostip, Universe universe) {
		return hostip.hostIp()
			.map(hostName -> {
				try {
					return InetAddress.getByName(hostName);
				} catch (UnknownHostException ex) {
					throw new IllegalStateException("Could not determine host IP address", ex);
				}
			})
			.map(hostAddress -> NatHandlerRemoteImpl.create(hostAddress, universe.getPort(), System::currentTimeMillis))
			.orElseThrow(() -> new IllegalStateException("Could not determine host IP address"));
	}

	private UDPTransportControlFactory udpTransportControlFactoryProvider() {
		return UDPTransportControlImpl::new;
	}

	private UDPTransportOutboundConnectionFactory udpTransportOutboundConnectionFactoryProvider() {
		return UDPTransportOutboundConnection::new;
	}
}
