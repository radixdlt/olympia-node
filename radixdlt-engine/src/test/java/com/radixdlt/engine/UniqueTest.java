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

import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atommodel.unique.UniqueParticle;
import com.radixdlt.atommodel.unique.UniqueParticleConstraintScrypt;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.Rri;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UniqueTest {
	private ECKeyPair keyPair = ECKeyPair.generateNew();
	private RadixAddress address = new RadixAddress((byte) 0, this.keyPair.getPublicKey());
	private RadixEngine<Void> engine;
	private EngineStore<Void> store;

	@Before
	public void setup() {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new UniqueParticleConstraintScrypt());
		var cm = new ConstraintMachine.Builder()
			.setVirtualStoreLayer(cmAtomOS.virtualizedUpParticles())
			.setParticleStaticCheck(cmAtomOS.buildParticleStaticCheck())
			.setParticleTransitionProcedures(cmAtomOS.buildTransitionProcedures())
			.build();
		this.store = new InMemoryEngineStore<>();
		this.engine = new RadixEngine<>(cm, store);
	}

	@Test
	public void using_own_mutex_should_work() throws Exception {
		var atom = TxBuilder.newBuilder(address)
			.mutex("np")
			.signAndBuild(keyPair::sign);
		this.engine.execute(List.of(atom));
	}

	@Test
	public void using_someone_elses_mutex_should_fail() {
		var rri = Rri.of(ECKeyPair.generateNew().getPublicKey(), "smthng");
		var builder = TxBuilder.newBuilder(address)
			.toLowLevelBuilder()
			.virtualDown(new RRIParticle(rri))
			.up(new UniqueParticle(rri))
			.particleGroup();
		var sig = keyPair.sign(builder.hashToSign());
		var txn = builder.sig(sig).build();
		assertThatThrownBy(() -> this.engine.execute(List.of(txn)))
			.isInstanceOf(RadixEngineException.class);
	}
}
