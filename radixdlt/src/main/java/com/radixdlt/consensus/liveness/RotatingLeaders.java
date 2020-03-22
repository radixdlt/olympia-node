package com.radixdlt.consensus.liveness;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.EUID;
import com.radixdlt.consensus.View;
import java.util.Objects;

public final class RotatingLeaders implements ProposerElection {
	private final ImmutableList<EUID> leaders;
	public RotatingLeaders(ImmutableList<EUID> leaders) {
		this.leaders = Objects.requireNonNull(leaders);
	}

	@Override
	public boolean isValidProposer(EUID nid, View view) {
		return nid.equals(getProposer(view));
	}

	@Override
	public EUID getProposer(View view) {
		int index = (int) (view.number() % leaders.size());
		return leaders.get(index);
	}
}
