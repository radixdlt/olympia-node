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

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.environment.EventProcessor;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class NodeEvents {
	private final Map<Class<?>, Set<BiConsumer<BFTNode, Object>>> processors;

	@Inject
	public NodeEvents(Map<Class<?>, Set<BiConsumer<BFTNode, Object>>> processors) {
		this.processors = processors;
	}

	public <T> EventProcessor<T> processor(BFTNode node, Class<T> eventClass) {
		return t -> processors.get(eventClass).forEach(c -> c.accept(node, t));
	}
}
