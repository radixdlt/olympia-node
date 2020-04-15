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

package com.radixdlt.delivery;

import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.store.LedgerEntry;

import javax.annotation.Nullable;
import java.util.Objects;

public final class DeliveryResult {
	public enum Type {
		SUCCESS,
		ALREADY_STORED,
		FAILED
	}

	private final Type type;
	private final Peer peer;
	private final LedgerEntry ledgerEntry;

	private DeliveryResult(Type type, Peer peer, LedgerEntry ledgerEntry) {
		this.type = type;
		this.peer = peer;
		this.ledgerEntry = ledgerEntry;
	}

	public Type getType() {
		return type;
	}

	public @Nullable
	Peer getPeer() {
		return peer;
	}

	public @Nullable
	LedgerEntry getLedgerEntry() {
		return ledgerEntry;
	}

	public boolean isSuccess() {
		return type == Type.SUCCESS;
	}

	public static DeliveryResult success(LedgerEntry ledgerEntry, Peer peer) {
		Objects.requireNonNull(ledgerEntry);
		return new DeliveryResult(Type.SUCCESS, peer, ledgerEntry);
	}

	public static DeliveryResult alreadyStored() {
		return new DeliveryResult(Type.ALREADY_STORED, null, null);
	}

	public static DeliveryResult failed() {
		return new DeliveryResult(Type.FAILED, null, null);
	}
}
