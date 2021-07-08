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
import com.radixdlt.application.system.scrypt.Syscall;
import com.radixdlt.constraintmachine.CallData;
import com.radixdlt.constraintmachine.exceptions.CallDataAccessException;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.REOp;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.engine.parser.exceptions.TxnParseException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
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

	public static class ParserState {
		private final Txn txn;
		private final List<REInstruction> instructions = new ArrayList<>();
		private byte[] msg = null;
		private int upSubstateCount = 0;
		private int substateUpdateCount = 0;
		private int endCount = 0;
		private int position = 0;
		private boolean disableResourceAllocAndDestroy = false;

		ParserState(Txn txn) {
			this.txn = txn;
		}

		public List<REInstruction> instructions() {
			return instructions;
		}

		void header(boolean disableResourceAllocAndDestroy) throws TxnParseException {
			if (instructions.size() != 1) {
				throw new TxnParseException(this, "Header must be first");
			}
			this.disableResourceAllocAndDestroy = disableResourceAllocAndDestroy;
		}

		void read() {
		}

		void pos(int curPos) {
			this.position = curPos;
		}

		public int curPosition() {
			return this.position;
		}

		void nextInstruction(REInstruction inst) {
			instructions.add(inst);
		}

		public int curIndex() {
			return instructions.size();
		}

		public AID txnId() {
			return txn.getId();
		}

		public int upSubstateCount() {
			return upSubstateCount;
		}

		void msg(byte[] msg) throws TxnParseException {
			if (this.msg != null) {
				throw new TxnParseException(this, "Too many messages");
			}
			this.msg = msg;
		}

		void substateUpdate(REOp op) {
			substateUpdateCount++;
			if (op == REOp.UP) {
				upSubstateCount++;
			}
		}

		void end() throws TxnParseException {
			if (substateUpdateCount == 0) {
				throw new TxnParseException(this, "Empty group");
			}
			endCount++;
			substateUpdateCount = 0;
		}

		void finish() throws TxnParseException {
			if (substateUpdateCount != 0) {
				throw new TxnParseException(this, "Missing end");
			}

			if (endCount == 0) {
				throw new TxnParseException(this, "No state updates");
			}
		}
	}

	@SuppressWarnings("rawtypes")
	public ParsedTxn parse(Txn txn) throws TxnParseException {
		UInt256 feePaid = null;
		ECDSASignature sig = null;
		int sigPosition = 0;
		var parserState = new ParserState(txn);

		if (txn.getPayload().length > MAX_TXN_SIZE) {
			throw new TxnParseException(parserState, "Transaction is too big: " + txn.getPayload().length + " > " + MAX_TXN_SIZE);
		}

		var buf = ByteBuffer.wrap(txn.getPayload());
		while (buf.hasRemaining()) {
			if (sig != null) {
				throw new TxnParseException(parserState, "Signature must be last");
			}

			int curPos = buf.position();
			parserState.pos(curPos);
			final REInstruction inst;
			try {
				inst = REInstruction.readFrom(parserState, buf);
			} catch (Exception e) {
				throw new TxnParseException(parserState, "Could not read instruction", e);
			}
			parserState.nextInstruction(inst);

			if (inst.isStateUpdate()) {
				parserState.substateUpdate(inst.getMicroOp().getOp());
			} else if (inst.getMicroOp().getOp() == REOp.READ || inst.getMicroOp().getOp() == REOp.READINDEX) {
				parserState.read();
			} else if (inst.getMicroOp() == REInstruction.REMicroOp.HEADER) {
				parserState.header(inst.getData());
			} else if (inst.getMicroOp() == REInstruction.REMicroOp.SYSCALL) {
				try {
					CallData callData = inst.getData();
					byte id = callData.get(0);
					var syscall = Syscall.of(id).orElseThrow(() -> new TxnParseException(parserState, "Invalid call data type: " + id));

					switch (syscall) {
						case FEE_RESERVE_PUT:
							if (feePaid != null) {
								throw new TxnParseException(parserState, "Should only pay fees once.");
							}
							feePaid = callData.getUInt256(1);
							break;
						case FEE_RESERVE_TAKE:
							if (feePaid == null) {
								throw new TxnParseException(parserState, "No fees paid");
							}
							var takeAmount = callData.getUInt256(1);
							if (takeAmount.compareTo(feePaid) > 0) {
								throw new TxnParseException(parserState, "Trying to take more fees than paid");
							}
							feePaid = feePaid.subtract(takeAmount);
							break;
						case READDR_CLAIM:
							break;
						// TODO: Need to rethink how stateless verification occurs here
						// TODO: Along with FeeConstraintScrypt.java
						default:
							throw new TxnParseException(parserState, "Invalid call data type: " + id);
					}
				} catch (CallDataAccessException e) {
					throw new TxnParseException(parserState, e);
				}
			} else if (inst.getMicroOp() == REInstruction.REMicroOp.MSG) {
				parserState.msg(inst.getData());
			} else if (inst.getMicroOp() == REInstruction.REMicroOp.END) {
				parserState.end();
			} else if (inst.getMicroOp() == REInstruction.REMicroOp.SIG) {
				sigPosition = curPos;
				sig = inst.getData();
			} else {
				throw new TxnParseException(parserState, "Unknown CM Op " + inst.getMicroOp());
			}
		}

		parserState.finish();

		final ECPublicKey pubKey;
		if (sig != null) {
			var hash = HashUtils.sha256(txn.getPayload(), 0, sigPosition); // This is a double hash
			pubKey = ECPublicKey.recoverFrom(hash, sig)
				.orElseThrow(() -> new TxnParseException(parserState, "Invalid signature"));
			if (!pubKey.verify(hash, sig)) {
				throw new TxnParseException(parserState, "Invalid signature");
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
