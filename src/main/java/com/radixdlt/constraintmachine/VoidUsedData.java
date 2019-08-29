package com.radixdlt.constraintmachine;

import com.google.common.reflect.TypeToken;

public final class VoidUsedData implements UsedData {
	private VoidUsedData() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	@Override
	public TypeToken<? extends UsedData> getTypeToken() {
		return TypeToken.of(VoidUsedData.class);
	}
}
