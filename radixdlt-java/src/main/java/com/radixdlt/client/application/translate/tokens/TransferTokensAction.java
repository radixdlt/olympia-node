package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.identity.Data;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TransferTokensAction implements Action {
	private final RadixAddress from;
	private final RadixAddress to;
	private final TokenClassReference tokenClassReference;
	private final Map<String, Object> metaData;
	private final BigDecimal amount;
	private final Data attachment;

	private TransferTokensAction(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenClassReference tokenClassReference,
		Data attachment,
		Map<String, Object> metaData
	) {
		if (amount.stripTrailingZeros().scale() > TokenClassReference.getTokenScale()) {
			throw new IllegalArgumentException("Amount must scale by " + TokenClassReference.getTokenScale());
		}

		this.from = from;
		this.to = to;
		this.tokenClassReference = tokenClassReference;
		this.amount = amount;
		this.attachment = attachment;
		this.metaData = metaData;
	}

	public static TransferTokensAction create(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenClassReference tokenClassReference
	) {
		return new TransferTokensAction(from, to, amount, tokenClassReference, null, Collections.emptyMap());
	}

	public static TransferTokensAction create(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenClassReference tokenClassReference,
		Data attachment
	) {
		return new TransferTokensAction(from, to, amount, tokenClassReference, attachment, Collections.emptyMap());
	}

	public static TransferTokensAction create(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenClassReference tokenClassReference,
		Long timestamp
	) {
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", timestamp);

		return new TransferTokensAction(from, to, amount, tokenClassReference, null, metaData);
	}

	public static TransferTokensAction create(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		TokenClassReference tokenClassReference,
		Data attachment,
		Long timestamp
	) {
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", timestamp);

		return new TransferTokensAction(from, to, amount, tokenClassReference, attachment, metaData);
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

	public TokenClassReference getTokenClassReference() {
		return tokenClassReference;
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
		return timestamp + " " + from + " -> " + to + " " + amount + " "  + tokenClassReference.getSymbol()
			+ (attachment == null ? "" : " " + attachment);
	}
}
