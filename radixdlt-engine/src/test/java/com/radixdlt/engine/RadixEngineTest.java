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

package com.radixdlt.engine;

import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV1;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.store.InMemoryEngineStore;

import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RadixEngineTest {
	@Test
	public void empty_particle_group_should_throw_error() {
		// Arrange
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new SystemConstraintScryptV1());
		var cm = new ConstraintMachine(
			cmAtomOS.virtualizedUpParticles(),
			cmAtomOS.getProcedures()
		);
		var parser = new REParser(cmAtomOS.buildSubstateDeserialization());
		var actionConstructors = ActionConstructors.newBuilder().build();
		RadixEngine<Void> engine = new RadixEngine<>(parser, actionConstructors, cm, new InMemoryEngineStore<>());

		// Act
		// Assert
		var atom = TxLowLevelBuilder.newBuilder()
			.end()
			.build();
		assertThatThrownBy(() -> engine.execute(List.of(atom)))
			.isInstanceOf(RadixEngineException.class);
	}
}