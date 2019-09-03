package com.radixdlt.tempo.consensus;

import com.radixdlt.common.AID;
import com.radixdlt.tempo.TempoAtom;

public interface ConsensusReceptor {
	void change(TempoAtom oldPreference, AID newPreferenceAid);

	void commit(TempoAtom preference);
}
