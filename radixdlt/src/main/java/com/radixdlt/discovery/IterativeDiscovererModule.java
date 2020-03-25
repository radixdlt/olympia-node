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

package com.radixdlt.discovery;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.properties.RuntimeProperties;

public class IterativeDiscovererModule extends AbstractModule {
	private final IterativeDiscovererConfiguration configuration;

	public IterativeDiscovererModule(RuntimeProperties properties) {
		this(IterativeDiscovererConfiguration.fromRuntimeProperties(properties));
	}

	public IterativeDiscovererModule(IterativeDiscovererConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	protected void configure() {
		// main target
		Multibinder<AtomDiscoverer> discovererMultibinder = Multibinder.newSetBinder(binder(), AtomDiscoverer.class);
		discovererMultibinder.addBinding().to(IterativeDiscoverer.class);

		// dependencies
		bind(IterativeDiscovererConfiguration.class).toInstance(configuration);
	}
}
