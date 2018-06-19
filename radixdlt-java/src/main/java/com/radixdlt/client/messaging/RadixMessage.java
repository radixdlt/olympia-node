package com.radixdlt.client.messaging;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.address.RadixAddress;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomValidationException;
import com.radixdlt.client.core.crypto.ECSignature;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

public class RadixMessage {
	private final RadixMessageContent content;
	private final Atom atom;
	private final long timestamp;

	public RadixMessage(RadixMessageContent content, Atom atom) {
		this.content = content;
		this.timestamp = atom.getTimestamp();
		this.atom = atom;
	}

	public boolean validateSignature() {
		Optional<ECSignature> signature = this.getSignature(this.getFrom().getUID());
		if (!signature.isPresent()) {
			return false;
		}

		if (!atom.getHash().verifySelf(this.getFrom().getPublicKey(), signature.get())) {
			return false;
		}

		return true;
	}

	public Optional<ECSignature> getSignature(EUID euid) {
		return atom.getSignature(euid);
	}

	public RadixAddress getFrom() {
		return content.getFrom();
	}

	public RadixAddress getTo() {
		return content.getTo();
	}

	public String getContent() {
		return content.getContent();
	}

	public long getTimestamp() {
		return timestamp;
	}

	@Override
	public String toString() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());
		sdf.setTimeZone(TimeZone.getDefault());

		return sdf.format(new Date(timestamp)) + " " + content.toString();
	}
}
