package org.radix.network;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Protocol
{
	TCP,
	UDP;

    @JsonValue
	@Override
	public String toString() {
		return name();
	}
}
