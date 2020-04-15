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

import java.util.Objects;

import com.google.inject.AbstractModule;
import com.radixdlt.properties.RuntimeProperties;

/**
 * Guice configuration for {@link HostIp}.
 */
public final class HostIpModule extends AbstractModule {

	private final RuntimeProperties properties;

	public HostIpModule(RuntimeProperties properties) {
		this.properties = Objects.requireNonNull(properties);
	}

	@Override
	protected void configure() {
		// The main target
		bind(HostIp.class).toInstance(StandardHostIp.defaultHostIp(properties));
	}
}
