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

package com.radixdlt.integration.distributed.simulation.application;

import com.google.inject.Inject;
import com.radixdlt.application.system.scrypt.Syscall;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.InitialForkConfig;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.statecomputer.checkpoint.Genesis;

import java.nio.charset.StandardCharsets;

/**
 * Generates a new unique rri consumer command. Because new addresses are used
 * on every call, the command should never fail when executed on a radix engine.
 */
public class RadixEngineUniqueGenerator implements TxnGenerator {
	@Inject
	private REParser parser;

	@Inject
	@InitialForkConfig
	private ForkConfig forkConfig;

	@Inject
	@Genesis
	private Txn genesis;

	@Override
	public Txn nextTxn() {
		var keyPair = ECKeyPair.generateNew();
		try {
			var addr = REAddr.ofHashedKey(keyPair.getPublicKey(), "smthng");
			var builder = TxBuilder.newBuilder(parser.getSubstateDeserialization(), forkConfig.engineRules().getSerialization())
				.toLowLevelBuilder()
				.syscall(Syscall.READDR_CLAIM, "smthng".getBytes(StandardCharsets.UTF_8))
				.virtualDown(SubstateId.ofSubstate(genesis.getId(), 0), addr.getBytes())
				.end();
			var sig = keyPair.sign(builder.hashToSign());
			return builder.sig(sig).build();
		} catch (TxBuilderException e) {
			throw new RuntimeException(e);
		}
	}
}
