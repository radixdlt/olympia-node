package org.radix.common;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Syncronicity {
	SYNCRONOUS,
	ASYNCRONOUS;

	@JsonValue
	@Override
	public String toString() {
		return this.name();
	}
}
