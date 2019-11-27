package com.radixdlt.engine;

import com.radixdlt.atomos.Result;
import com.radixdlt.common.Atom;

public interface CMSuccessHook {
	Result hook(Atom atom);
}
