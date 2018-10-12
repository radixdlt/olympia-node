package com.radixdlt.client.application.actions;

import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.address.RadixAddress;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TransferTokens {
	private final RadixAddress from;
	private final RadixAddress to;
	private final TokenRef tokenRef;
	private final Map<String, Object> metaData;
	private final BigDecimal amount;
	private final Data attachment;

	private TransferTokens(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenRef tokenRef,
		Data attachment,
		Map<String, Object> metaData
	) {
		if (amount.stripTrailingZeros().scale() > TokenRef.getTokenScale()) {
			throw new IllegalArgumentException("Amount must scale by " + TokenRef.getTokenScale());
		}

		this.from = from;
		this.to = to;
		this.tokenRef = tokenRef;
		this.amount = amount;
		this.attachment = attachment;
		this.metaData = metaData;
	}

	public static TransferTokens create(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenRef tokenRef
	) {
		return new TransferTokens(from, to, amount, tokenRef, null, Collections.emptyMap());
	}

	public static TransferTokens create(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenRef tokenRef,
		Data attachment
	) {
		return new TransferTokens(from, to, amount, tokenRef, attachment, Collections.emptyMap());
	}

	public static TransferTokens create(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenRef tokenRef,
		Long timestamp
	) {
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", timestamp);

		return new TransferTokens(from, to, amount, tokenRef, null, metaData);
	}

	public static TransferTokens create(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenRef tokenRef,
		Data attachment,
		Long timestamp
	) {
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", timestamp);

		return new TransferTokens(from, to, amount, tokenRef, attachment, metaData);
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

	public TokenRef getTokenRef() {
		return tokenRef;
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
		return timestamp + " " + from + " -> " + to + " " + amount + " "  + tokenRef.getIso()
			+ (attachment == null ? "" : " " + attachment);
	}
}
