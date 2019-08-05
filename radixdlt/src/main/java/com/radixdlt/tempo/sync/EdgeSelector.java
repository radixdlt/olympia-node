package com.radixdlt.tempo.sync;

import com.radixdlt.common.EUID;
import org.radix.atoms.Atom;

import java.util.Collection;
import java.util.List;

public interface EdgeSelector {
	List<EUID> selectEdges(Collection<EUID> nodes, Atom atom);
}
