package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.RRI;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TransferTokensAction implements Action {
	private final RadixAddress from;
	private final RadixAddress to;
	private final RRI tokenDefinitionReference;
	private final Map<String, Object> metaData;
	private final BigDecimal amount;
	private final byte[] attachment;

	private TransferTokensAction(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		RRI tokenDefinitionReference,
		byte[] attachment,
		Map<String, Object> metaData
	) {
		if (amount.stripTrailingZeros().scale() > TokenUnitConversions.getTokenScale()) {
			throw new IllegalArgumentException("Amount must scale by " + TokenUnitConversions.getTokenScale());
		}

		this.from = from;
		this.to = to;
		this.tokenDefinitionReference = tokenDefinitionReference;
		this.amount = amount;
		this.attachment = attachment;
		this.metaData = metaData;
	}

	public static TransferTokensAction create(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		RRI tokenDefinitionReference
	) {
		return new TransferTokensAction(from, to, amount, tokenDefinitionReference, null, Collections.emptyMap());
	}

	public static TransferTokensAction create(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		RRI tokenDefinitionReference,
		byte[] attachment
	) {
		return new TransferTokensAction(from, to, amount, tokenDefinitionReference, attachment, Collections.emptyMap());
	}

	public static TransferTokensAction create(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		RRI tokenDefinitionReference,
		Long timestamp
	) {
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", timestamp);

		return new TransferTokensAction(from, to, amount, tokenDefinitionReference, null, metaData);
	}

	public static TransferTokensAction create(
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		RRI tokenDefinitionReference,
		byte[] attachment,
		Long timestamp
	) {
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", timestamp);

		return new TransferTokensAction(from, to, amount, tokenDefinitionReference, attachment, metaData);
	}

	public byte[] getAttachment() {
		return attachment;
	}

	public RadixAddress getFrom() {
		return from;
	}

	public RadixAddress getTo() {
		return to;
	}

	public RRI getTokenDefRef() {
		return tokenDefinitionReference;
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
		return timestamp + " " + from + " -> " + to + " " + amount + " "  + tokenDefinitionReference.getName()
			+ (attachment == null ? "" : " " + attachment);
	}
}
