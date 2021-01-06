/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.integration.distributed.deterministic;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.radixdlt.integration.distributed.deterministic.NodeEvents.NodeEventProcessor;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages events for a node network
 */
public class NodeEventsModule extends AbstractModule {
	@Override
	public void configure() {
		bind(NodeEvents.class).in(Scopes.SINGLETON);
	}

	@Provides
	public Map<Class<?>, Set<NodeEventProcessor<?>>> safetyCheckProcessor(Set<NodeEventProcessor<?>> processors) {
		return processors.stream()
			.collect(Collectors.groupingBy(NodeEventProcessor::getEventClass, Collectors.toSet()));
	}
}
