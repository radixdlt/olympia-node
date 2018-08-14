package com.radixdlt.client.messaging;

import com.radixdlt.client.core.address.RadixAddress;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class RadixMessage {
	private final long timestamp;
	private final RadixAddress to;
	private final RadixAddress from;
	private final String content;

	public RadixMessage(
		RadixAddress from,
		RadixAddress to,
		String content,
		long timestamp
	) {
		this.from = from;
		this.to = to;
		this.content = content;
		this.timestamp = timestamp;
	}

	public RadixAddress getFrom() {
		return from;
	}

	public RadixAddress getTo() {
		return to;
	}

	public String getContent() {
		return content;
	}

	public long getTimestamp() {
		return timestamp;
	}

	@Override
	public String toString() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault());
		sdf.setTimeZone(TimeZone.getDefault());

		return "Time: " + sdf.format(new Date(timestamp)) + "\nFrom: " + from + "\nTo: " + to + "\nContent: " + content;
	}
}
