package com.radixdlt.client.application.actions;

import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.core.atoms.Token;
import com.radixdlt.client.application.objects.Amount;
import com.radixdlt.client.core.address.RadixAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TokenTransfer {
	private final RadixAddress from;
	private final RadixAddress to;
	private final Token token;
	private final Map<String, Object> metaData;
	private final long subUnitAmount;
	private final Data attachment;

	private TokenTransfer(
		RadixAddress from,
		RadixAddress to,
		Token token,
		long subUnitAmount,
		Data attachment,
		Map<String, Object> metaData
	) {
		this.from = from;
		this.to = to;
		this.token = token;
		this.subUnitAmount = subUnitAmount;
		this.attachment = attachment;
		this.metaData = metaData;
	}

	public static TokenTransfer create(RadixAddress from, RadixAddress to, Token token, long subUnitAmount) {
		return new TokenTransfer(from, to, token, subUnitAmount, null, Collections.emptyMap());
	}

	public static TokenTransfer create(RadixAddress from, RadixAddress to, Token token, long subUnitAmount, Data attachment) {
		return new TokenTransfer(from, to, token, subUnitAmount, attachment, Collections.emptyMap());
	}

	public static TokenTransfer create(RadixAddress from, RadixAddress to, Token token, long subUnitAmount, Long timestamp) {
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", timestamp);

		return new TokenTransfer(from, to, token, subUnitAmount, null, metaData);
	}

	public static TokenTransfer create(
		RadixAddress from,
		RadixAddress to,
		Token token,
		long subUnitAmount,
		Data attachment,
		Long timestamp
	) {
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", timestamp);

		return new TokenTransfer(from, to, token, subUnitAmount, attachment, metaData);
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

	public Token getToken() {
		return token;
	}

	public long getSubUnitAmount() {
		return subUnitAmount;
	}

	public Map<String, Object> getMetaData() {
		return Collections.unmodifiableMap(metaData);
	}

	@Override
	public String toString() {
		Long timestamp = (Long) metaData.get("timestamp");
		return timestamp + " " + from + " -> " + to + " " + Amount.subUnitsOf(subUnitAmount, token).toString()
			+ (attachment == null ? "" : " " + attachment);
	}
}
