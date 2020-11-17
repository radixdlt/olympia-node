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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.integration.distributed.simulation.TestInvariant.TestInvariantError;
import com.radixdlt.integration.invariants.SafetyChecker;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class SafetyCheckerModule extends AbstractModule {

	@Override
	public void configure() {
		bind(NodeEvents.class).in(Scopes.SINGLETON);
	}

	@ProvidesIntoSet
	public BiConsumer<BFTNode, BFTCommittedUpdate> safetyCheckProcessor(SafetyChecker safetyChecker) {
		return (node, update) -> {
			Optional<TestInvariantError> maybeError = safetyChecker.process(node, update);
			assertThat(maybeError).isEmpty();
		};
	}

	@Provides
	public Map<Class<?>, Set<BiConsumer<BFTNode, Object>>> safetyCheckProcessor(Set<BiConsumer<BFTNode, BFTCommittedUpdate>> processors) {
		return ImmutableMap.of(
			BFTCommittedUpdate.class, processors.stream().<BiConsumer<BFTNode, Object>>map(c -> (node, o) -> {
				BFTCommittedUpdate update = (BFTCommittedUpdate) o;
				c.accept(node, update);
			})
				.collect(Collectors.toSet())
		);
	}
}
