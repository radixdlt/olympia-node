package com.radixdlt.consensus;

import com.radixdlt.common.Atom;
import java.util.function.Consumer;

public interface Network {
	void addCallback(Consumer<Atom> callback);
}
