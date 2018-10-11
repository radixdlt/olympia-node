package com.radixdlt.client.application.actions;

import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.core.atoms.TokenReference;
import com.radixdlt.client.core.address.RadixAddress;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TokenTransfer {
	private final RadixAddress from;
	private final RadixAddress to;
	private final TokenReference tokenReference;
	private final Map<String, Object> metaData;
	private final BigDecimal amount;
	private final Data attachment;

	private TokenTransfer(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenReference tokenReference,
		Data attachment,
		Map<String, Object> metaData
	) {
		if (amount.stripTrailingZeros().scale() > TokenReference.getTokenScale()) {
			throw new IllegalArgumentException("Amount must scale by " + TokenReference.getTokenScale());
		}

		this.from = from;
		this.to = to;
		this.tokenReference = tokenReference;
		this.amount = amount;
		this.attachment = attachment;
		this.metaData = metaData;
	}

	public static TokenTransfer create(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenReference tokenReference
	) {
		return new TokenTransfer(from, to, amount, tokenReference, null, Collections.emptyMap());
	}

	public static TokenTransfer create(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenReference tokenReference,
		Data attachment
	) {
		return new TokenTransfer(from, to, amount, tokenReference, attachment, Collections.emptyMap());
	}

	public static TokenTransfer create(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenReference tokenReference,
		Long timestamp
	) {
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", timestamp);

		return new TokenTransfer(from, to, amount, tokenReference, null, metaData);
	}

	public static TokenTransfer create(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenReference tokenReference,
		Data attachment,
		Long timestamp
	) {
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", timestamp);

		return new TokenTransfer(from, to, amount, tokenReference, attachment, metaData);
	}

	public Data getAttachment() {
		return attachment;
	}

	public RadixAddress getFrom() {
		return from;
	}

	public RadixAddress getTo() {
		return to;
	}

	public TokenReference getTokenRef() {
		return tokenReference;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public Map<String, Object> getMetaData() {
		return Collections.unmodifiableMap(metaData);
	}

	@Override
	public String toString() {
		Long timestamp = (Long) metaData.get("timestamp");
		return timestamp + " " + from + " -> " + to + " " + amount + " "  + tokenReference.getIso()
			+ (attachment == null ? "" : " " + attachment);
	}
}
