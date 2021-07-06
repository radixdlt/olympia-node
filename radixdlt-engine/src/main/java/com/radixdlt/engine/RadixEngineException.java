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

import com.radixdlt.atom.Txn;
import com.radixdlt.utils.Bytes;

/**
 * Exception thrown by Radix Engine
 */
@SuppressWarnings("serial")
public final class RadixEngineException extends Exception {
	private final Txn txn;
	private final int txnIndex;
	private final int batchSize;

	public RadixEngineException(int txnIndex, int batchSize, Txn txn, Exception cause) {
		super("index=" + txnIndex + " batchSize=" + batchSize + " txnId=" + txn.getId()
			+ " txn_size=" + txn.getPayload().length + " txn=" + Bytes.toHexString(txn.getPayload()), cause);
		this.txn = txn;
		this.txnIndex = txnIndex;
		this.batchSize = batchSize;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public Txn getTxn() {
		return txn;
	}

	public int getTxnIndex() {
		return txnIndex;
	}
}
