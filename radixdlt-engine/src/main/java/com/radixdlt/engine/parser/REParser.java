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

package com.radixdlt.engine.parser;

import com.radixdlt.atom.Substate;
import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.StatelessSubstateVerifier;
import com.radixdlt.constraintmachine.TxnParseException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Pair;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class REParser {
	private static final int MAX_TXN_SIZE = 1024 * 1024;
	private final StatelessSubstateVerifier<Particle> statelessVerifier;

	public REParser(StatelessSubstateVerifier<Particle> statelessVerifier) {
		this.statelessVerifier = statelessVerifier;
	}

	private static class ParserState {
		final List<REInstruction> instructions = new ArrayList<>();
		private byte[] msg = null;
		int substateUpdateCount = 0;
		int endCount = 0;

		void nextInstruction(REInstruction inst) {
			instructions.add(inst);
		}

		int curIndex() {
			return instructions.size();
		}

		void msg(byte[] msg) throws TxnParseException {
			if (msg != null) {
				throw new TxnParseException("Too many messages");
			}
		}

		void substateUpdate() {
			substateUpdateCount++;
		}

		void end() throws TxnParseException {
			if (substateUpdateCount == 0) {
				throw new TxnParseException("Empty group");
			}
			endCount++;
			substateUpdateCount = 0;
		}

		void finish() throws TxnParseException {
			if (substateUpdateCount != 0) {
				throw new TxnParseException("Missing end");
			}

			if (endCount == 0) {
				throw new TxnParseException("No state updates");
			}
		}

	}

	public ParsedTxn parse(Txn txn) throws TxnParseException {
		if (txn.getPayload().length > MAX_TXN_SIZE) {
			throw new TxnParseException("Transaction is too big: " + txn.getPayload().length + " > " + MAX_TXN_SIZE);
		}

		ECDSASignature sig = null;
		int sigPosition = 0;
		var parserState = new ParserState();

		var buf = ByteBuffer.wrap(txn.getPayload());
		while (buf.hasRemaining()) {
			if (sig != null) {
				throw new TxnParseException("Signature must be last");
			}

			int curPos = buf.position();
			final REInstruction inst;
			try {
				inst = REInstruction.readFrom(txn, parserState.curIndex(), buf);
			} catch (DeserializeException e) {
				throw new TxnParseException("Could not read instruction", e);
			}
			parserState.nextInstruction(inst);

			if (inst.isStateUpdate()) {
				var data = inst.getData();
				if (data instanceof Substate) {
					Substate substate = (Substate) data;
					statelessVerifier.verify(substate.getParticle());
				} else if (data instanceof Pair) {
					Substate substate = (Substate) ((Pair) data).getFirst();
					statelessVerifier.verify(substate.getParticle());
				}

				parserState.substateUpdate();

			} else if (inst.getMicroOp() == REInstruction.REMicroOp.MSG) {
				parserState.msg(inst.getData());
			} else if (inst.getMicroOp() == REInstruction.REMicroOp.END) {
				parserState.end();
			} else if (inst.getMicroOp() == REInstruction.REMicroOp.SIG) {
				sigPosition = curPos;
				sig = inst.getData();
			} else {
				throw new TxnParseException("Unknown CM Op " + inst.getMicroOp());
			}
		}

		parserState.finish();


		if (sig != null) {
			var hash = HashUtils.sha256(txn.getPayload(), 0, sigPosition); // This is a double hash
			var pubKey = ECPublicKey.recoverFrom(hash, sig)
				.orElseThrow(() -> new TxnParseException("Invalid signature"));
			if (!pubKey.verify(hash, sig)) {
				throw new TxnParseException("Invalid signature");
			}
			return new ParsedTxn(txn, parserState.instructions, parserState.msg, pubKey);
		}

		return new ParsedTxn(txn, parserState.instructions, parserState.msg, null);
	}
}
