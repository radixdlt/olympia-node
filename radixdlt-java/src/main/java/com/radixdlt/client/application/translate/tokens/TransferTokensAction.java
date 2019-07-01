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
	private final RRI rri;
	private final Map<String, Object> metaData;
	private final BigDecimal amount;
	private final byte[] attachment;

	private TransferTokensAction(
		RRI rri,
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		byte[] attachment,
		Map<String, Object> metaData
	) {
		if (amount.stripTrailingZeros().scale() > TokenUnitConversions.getTokenScale()) {
			throw new IllegalArgumentException("Amount must scale by " + TokenUnitConversions.getTokenScale());
		}

		this.from = from;
		this.to = to;
		this.rri = rri;
		this.amount = amount;
		this.attachment = attachment;
		this.metaData = metaData;
	}

	public static TransferTokensAction create(
		RRI rri,
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount
	) {
		return new TransferTokensAction(rri, from, to, amount, null, Collections.emptyMap());
	}

	public static TransferTokensAction create(
		RRI rri,
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		byte[] attachment
	) {
		return new TransferTokensAction(rri, from, to, amount, attachment, Collections.emptyMap());
	}

	public static TransferTokensAction create(
		RRI rri,
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		Long timestamp
	) {
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", timestamp);

		return new TransferTokensAction(rri, from, to, amount, null, metaData);
	}

	public static TransferTokensAction create(
		RRI rri,
		RadixAddress from,
		RadixAddress to,
		BigDecimal amount,
		byte[] attachment,
		Long timestamp
	) {
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", timestamp);

		return new TransferTokensAction(rri, from, to, amount, attachment, metaData);
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

	public RRI getRRI() {
		return rri;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public Map<String, Object> getMetaData() {
		return Collections.unmodifiableMap(metaData);
	}

	@Override
	public String toString() {
		return "TRANSFER TOKEN " + amount + " " + rri.getName() + " FROM " + from + " TO " + to;
	}
}
