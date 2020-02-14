package com.radixdlt.consensus;

import com.radixdlt.common.Atom;
import java.util.function.Consumer;

public interface NetworkRx {
	void addProposalCallback(Consumer<Atom> callback);
}
