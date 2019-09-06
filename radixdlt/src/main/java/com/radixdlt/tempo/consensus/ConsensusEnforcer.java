package com.radixdlt.tempo.consensus;

import com.radixdlt.common.AID;
import com.radixdlt.tempo.TempoAtom;
import org.radix.network2.addressbook.Peer;

import java.util.Set;

// FIXME this is a really ugly interface
public interface ConsensusEnforcer {
	void requestChangePreference(TempoAtom oldPreference, AID newPreferenceAid, Set<Peer> peersToContact);

	void requestCommit(TempoAtom preference);
}
