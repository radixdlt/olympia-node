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

import com.radixdlt.atom.Txn;
import com.radixdlt.constraintmachine.CallData;
import com.radixdlt.constraintmachine.CallDataAccessException;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.REOp;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.constraintmachine.TxnParseException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.UInt256;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class REParser {
	private static final int MAX_TXN_SIZE = 1024 * 1024;
	private final SubstateDeserialization substateDeserialization;

	public REParser(SubstateDeserialization substateDeserialization) {
		this.substateDeserialization = substateDeserialization;
	}

	public SubstateDeserialization getSubstateDeserialization() {
		return substateDeserialization;
	}

	private static class ParserState {
		private final List<REInstruction> instructions = new ArrayList<>();
		private byte[] msg = null;
		private int substateUpdateCount = 0;
		private int endCount = 0;
		private boolean disableResourceAllocAndDestroy = false;

		void header(boolean disableResourceAllocAndDestroy) throws TxnParseException {
			if (instructions.size() != 1) {
				throw new TxnParseException("Header must be first");
			}
			this.disableResourceAllocAndDestroy = disableResourceAllocAndDestroy;
		}

		void read() {
		}

		void nextInstruction(REInstruction inst) {
			instructions.add(inst);
		}

		int curIndex() {
			return instructions.size();
		}

		void msg(byte[] msg) throws TxnParseException {
			if (this.msg != null) {
				throw new TxnParseException("Too many messages");
			}
			this.msg = msg;
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

	@SuppressWarnings("rawtypes")
	public ParsedTxn parse(Txn txn) throws TxnParseException {
		if (txn.getPayload().length > MAX_TXN_SIZE) {
			throw new TxnParseException("Transaction is too big: " + txn.getPayload().length + " > " + MAX_TXN_SIZE);
		}

		UInt256 feePaid = null;
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
				inst = REInstruction.readFrom(txn, parserState.curIndex(), buf, substateDeserialization);
			} catch (DeserializeException e) {
				throw new TxnParseException("Could not read instruction", e);
			}
			parserState.nextInstruction(inst);

			if (inst.isStateUpdate()) {
				parserState.substateUpdate();
			} else if (inst.getMicroOp().getOp() == REOp.READ) {
				parserState.read();
			} else if (inst.getMicroOp() == REInstruction.REMicroOp.HEADER) {
				parserState.header(inst.getData());
			} else if (inst.getMicroOp() == REInstruction.REMicroOp.SYSCALL) {
				try {
					CallData callData = inst.getData();
					// TODO: Need to rethink how stateless verification occurs here
					// TODO: Along with FeeConstraintScrypt.java
					if (callData.get(0) != 0) {
						throw new TxnParseException("Invalid call data type: " + callData.get(0));
					}
					if (feePaid != null) {
						throw new TxnParseException("Should only pay fees once.");
					}
					feePaid = callData.getUInt256(1);
				} catch (CallDataAccessException e) {
					throw new TxnParseException(e);
				}
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

		final ECPublicKey pubKey;
		if (sig != null) {
			var hash = HashUtils.sha256(txn.getPayload(), 0, sigPosition); // This is a double hash
			pubKey = ECPublicKey.recoverFrom(hash, sig)
				.orElseThrow(() -> new TxnParseException("Invalid signature"));
			if (!pubKey.verify(hash, sig)) {
				throw new TxnParseException("Invalid signature");
			}
		} else {
			pubKey = null;
		}

		return new ParsedTxn(
			txn,
			feePaid,
			parserState.instructions,
			parserState.msg,
			pubKey,
			parserState.disableResourceAllocAndDestroy
		);
	}
}
