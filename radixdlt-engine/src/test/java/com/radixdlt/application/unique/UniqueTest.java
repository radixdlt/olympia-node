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

package com.radixdlt.application.unique;

import com.radixdlt.application.system.scrypt.Syscall;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.Txn;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.application.system.construction.CreateSystemConstructorV2;
import com.radixdlt.application.system.scrypt.SystemConstraintScrypt;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.application.unique.scrypt.MutexConstraintScrypt;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.CreateSystem;
import com.radixdlt.atomos.CMAtomOS;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.InMemoryEngineStore;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UniqueTest {
	private ECKeyPair keyPair = ECKeyPair.generateNew();
	private RadixEngine<Void> sut;
	private EngineStore<Void> store;
	private REParser parser;
	private SubstateSerialization serialization;
	private Txn genesis;

	@Before
	public void setup() throws Exception {
		var cmAtomOS = new CMAtomOS();
		cmAtomOS.load(new MutexConstraintScrypt());
		cmAtomOS.load(new SystemConstraintScrypt(Set.of()));
		var cm = new ConstraintMachine(
			cmAtomOS.getProcedures(),
			cmAtomOS.buildSubstateDeserialization(),
			cmAtomOS.buildVirtualSubstateDeserialization()
		);
		this.parser = new REParser(cmAtomOS.buildSubstateDeserialization());
		this.serialization = cmAtomOS.buildSubstateSerialization();
		this.store = new InMemoryEngineStore<>();
		this.sut = new RadixEngine<>(
			parser,
			serialization,
			REConstructor.newBuilder()
				.put(CreateSystem.class, new CreateSystemConstructorV2())
				.build(),
			cm,
			store
		);
		this.genesis = this.sut.construct(
			TxnConstructionRequest.create()
				.action(new CreateSystem(0))
		).buildWithoutSignature();
		this.sut.execute(List.of(genesis), null, PermissionLevel.SYSTEM);
	}

	@Test
	public void using_own_mutex_should_work() throws Exception {
		var txn = this.sut.construct(b -> b.mutex(keyPair.getPublicKey(), "np"))
			.signAndBuild(keyPair::sign);
		this.sut.execute(List.of(txn));
	}

	@Test
	public void using_someone_elses_mutex_should_fail() throws Exception {
		var addr = REAddr.ofHashedKey(ECKeyPair.generateNew().getPublicKey(), "smthng");
		var builder = TxBuilder.newBuilder(parser.getSubstateDeserialization(), serialization)
			.toLowLevelBuilder()
			.syscall(Syscall.READDR_CLAIM, "smthng".getBytes(StandardCharsets.UTF_8))
			.virtualDown(SubstateId.ofSubstate(genesis.getId(), 0), addr.getBytes())
			.end();
		var sig = keyPair.sign(builder.hashToSign());
		var txn = builder.sig(sig).build();
		assertThatThrownBy(() -> this.sut.execute(List.of(txn)))
			.isInstanceOf(RadixEngineException.class);
	}
}
