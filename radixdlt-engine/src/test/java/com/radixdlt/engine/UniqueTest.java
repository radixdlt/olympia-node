/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.engine;

import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atommodel.unique.state.UniqueParticle;
import com.radixdlt.atommodel.unique.scrypt.UniqueParticleConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.UnclaimedREAddr;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UniqueTest {
	private ECKeyPair keyPair = ECKeyPair.generateNew();
	private RadixEngine<Void> engine;
	private EngineStore<Void> store;

	@Before
	public void setup() {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new UniqueParticleConstraintScrypt());
		var cm = new ConstraintMachine(
			cmAtomOS.virtualizedUpParticles(),
			cmAtomOS.getProcedures()
		);
		var parser = new REParser(cmAtomOS.buildSubstateDeserialization());
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(parser, ActionConstructors.newBuilder().build(), cm, store);
	}

	@Test
	public void using_own_mutex_should_work() throws Exception {
		var atom = TxBuilder.newBuilder()
			.mutex(keyPair.getPublicKey(), "np")
			.signAndBuild(keyPair::sign);
		this.engine.execute(List.of(atom));
	}

	@Test
	public void using_someone_elses_mutex_should_fail() {
		var addr = REAddr.ofHashedKey(ECKeyPair.generateNew().getPublicKey(), "smthng");
		var builder = TxBuilder.newBuilder()
			.toLowLevelBuilder()
			.virtualDown(new UnclaimedREAddr(addr), "smthng".getBytes(StandardCharsets.UTF_8))
			.up(new UniqueParticle(addr))
			.end();
		var sig = keyPair.sign(builder.hashToSign());
		var txn = builder.sig(sig).build();
		assertThatThrownBy(() -> this.engine.execute(List.of(txn)))
			.isInstanceOf(RadixEngineException.class);
	}
}
