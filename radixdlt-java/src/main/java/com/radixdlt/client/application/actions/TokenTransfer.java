package com.radixdlt.client.application.actions;

import com.radixdlt.client.application.objects.Data;
import com.radixdlt.client.assets.Asset;
import com.radixdlt.client.assets.AssetAmount;
import com.radixdlt.client.core.address.RadixAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TokenTransfer {
	private final RadixAddress from;
	private final RadixAddress to;
	private final Asset tokenClass;
	private final Map<String, Object> metaData;
	private final long subUnitAmount;
	private final Data attachment;

	private TokenTransfer(
		RadixAddress from,
		RadixAddress to,
		Asset tokenClass,
		long subUnitAmount,
		Data attachment,
		Map<String, Object> metaData
	) {
		this.from = from;
		this.to = to;
		this.tokenClass = tokenClass;
		this.subUnitAmount = subUnitAmount;
		this.attachment = attachment;
		this.metaData = metaData;
	}

	public static TokenTransfer create(RadixAddress from, RadixAddress to, Asset tokenClass, long subUnitAmount) {
		return new TokenTransfer(from, to, tokenClass, subUnitAmount, null, Collections.emptyMap());
	}

	public static TokenTransfer create(RadixAddress from, RadixAddress to, Asset tokenClass, long subUnitAmount, Data attachment) {
		return new TokenTransfer(from, to, tokenClass, subUnitAmount, attachment, Collections.emptyMap());
	}

	public static TokenTransfer create(RadixAddress from, RadixAddress to, Asset tokenClass, long subUnitAmount, Long timestamp) {
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", timestamp);

		return new TokenTransfer(from, to, tokenClass, subUnitAmount, null, metaData);
	}

	public static TokenTransfer create(
		RadixAddress from,
		RadixAddress to,
		Asset tokenClass,
		long subUnitAmount,
		Data attachment,
		Long timestamp
	) {
		Map<String, Object> metaData = new HashMap<>();
		metaData.put("timestamp", timestamp);

		return new TokenTransfer(from, to, tokenClass, subUnitAmount, attachment, metaData);
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

	public Asset getTokenClass() {
		return tokenClass;
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
		return timestamp + " " + from + " -> " + to + " " + new AssetAmount(tokenClass, subUnitAmount).toString()
			+ (attachment == null ? "" : " " + attachment);
	}
}
