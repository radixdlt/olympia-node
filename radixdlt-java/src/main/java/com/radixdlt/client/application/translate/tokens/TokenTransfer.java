package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.identity.UnencryptedData;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.math.BigDecimal;
import java.util.Optional;

public class TokenTransfer {
	private final RadixAddress from;
	private final RadixAddress to;
	private final TokenDefinitionReference tokenClass;
	private final BigDecimal amount;
	private final UnencryptedData attachment;
	private final long timestamp;

	public TokenTransfer(
		RadixAddress from,
		RadixAddress to,
		TokenDefinitionReference tokenClass,
		BigDecimal amount,
		UnencryptedData attachment,
		long timestamp
	) {
		this.from = from;
		this.to = to;
		this.tokenClass = tokenClass;
		this.amount = amount;
		this.attachment = attachment;
		this.timestamp = timestamp;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public RadixAddress getFrom() {
		return from;
	}

	public RadixAddress getTo() {
		return to;
	}

	public TokenDefinitionReference getTokenClass() {
		return tokenClass;
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
		return timestamp + " " + from + " -> " + to + " " + amount
			+ (attachment == null ? "" : " " + attachment);
	}
}
