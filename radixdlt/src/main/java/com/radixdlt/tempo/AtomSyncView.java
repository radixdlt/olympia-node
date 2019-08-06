package com.radixdlt.tempo;

import com.radixdlt.atoms.AtomStatus;
import com.radixdlt.common.AID;
import org.radix.atoms.Atom;

import java.util.Map;

/**
 * Temporary interface to seamlessly interchange new and old AtomSync
 */
public interface AtomSyncView {
	void receive(Atom atom);

	AtomStatus getAtomStatus(AID aid);

	long getQueueSize();

	Map<String, Object> getMetaData();
}
