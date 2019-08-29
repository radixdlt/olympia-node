package com.radixdlt.constraintmachine;

import com.google.common.reflect.TypeToken;

public interface UsedData {
	TypeToken<? extends UsedData> getTypeToken();
}
