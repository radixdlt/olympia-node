package com.radixdlt.tempo.delivery;

import com.radixdlt.tempo.TempoAtom;
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
	private final TempoAtom atom;

	private DeliveryResult(Type type, Peer peer, TempoAtom atom) {
		this.type = type;
		this.peer = peer;
		this.atom = atom;
	}

	public Type getType() {
		return type;
	}

	public @Nullable Peer getPeer() {
		return peer;
	}

	public @Nullable TempoAtom getAtom() {
		return atom;
	}

	public boolean isSuccess() {
		return type == Type.SUCCESS;
	}

	public static DeliveryResult success(TempoAtom atom, Peer peer) {
		Objects.requireNonNull(atom);
		return new DeliveryResult(Type.SUCCESS, peer, atom);
	}

	public static DeliveryResult alreadyStored() {
		return new DeliveryResult(Type.ALREADY_STORED, null, null);
	}

	public static DeliveryResult failed() {
		return new DeliveryResult(Type.FAILED, null, null);
	}
}
