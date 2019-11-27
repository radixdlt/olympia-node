package com.radixdlt.tempo.delivery;

import com.radixdlt.ledger.LedgerEntry;
import org.radix.network2.addressbook.Peer;

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
