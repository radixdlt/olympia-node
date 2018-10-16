package com.radixdlt.client.application.objects;

import com.radixdlt.client.assets.Amount;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.core.address.RadixAddress;
import java.util.Optional;

public class TokenTransfer {
	private final RadixAddress from;
	private final RadixAddress to;
	private final Asset tokenClass;
	private final long subUnitAmount;
	private final UnencryptedData attachment;
	private final long timestamp;

	public TokenTransfer(
		RadixAddress from,
		RadixAddress to,
		Asset tokenClass,
		long subUnitAmount,
		UnencryptedData attachment,
		long timestamp
	) {
		this.from = from;
		this.to = to;
		this.tokenClass = tokenClass;
		this.subUnitAmount = subUnitAmount;
		this.attachment = attachment;
		this.timestamp = timestamp;
	}

	public RadixAddress getFrom() {
		return from;
	}

	public RadixAddress getTo() {
		return to;
	}

	public Asset getTokenClass() {
		return tokenClass;
	}

	public long getSubUnitAmount() {
		return subUnitAmount;
	}

	public Optional<UnencryptedData> getAttachment() {
		return Optional.ofNullable(attachment);
	}

	public Optional<String> getAttachmentAsString() {
		return getAttachment().map(UnencryptedData::getData).map(String::new);
	}

	public long getTimestamp() {
		return timestamp;
	}

	@Override
	public String toString() {
		return timestamp + " " + from + " -> " + to + " " + Amount.subUnitsOf(subUnitAmount, tokenClass).toString()
			+ (attachment == null ? "" : " " + attachment);
	}
}
