package com.radixdlt.engine;

import com.radixdlt.atomos.Result;

public interface CMSuccessHook<T extends RadixEngineAtom> {
	Result hook(T atom);
}
