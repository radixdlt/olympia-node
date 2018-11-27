package com.radixdlt.client.application.translate.tokenclasses;

import com.radixdlt.client.atommodel.quarks.FungibleQuark;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import java.util.Collections;
import java.util.List;

public class MintTokensActionMapper {
	public List<SpunParticle> map(MintTokensAction mintTokensAction) {
		if (mintTokensAction == null) {
			return Collections.emptyList();
		}

		OwnedTokensParticle minted = new OwnedTokensParticle(
			mintTokensAction.getAmount() * TokenClassReference.SUB_UNITS,
			FungibleQuark.FungibleType.MINTED,
			mintTokensAction.getTokenClassReference().getAddress(),
			System.currentTimeMillis(),
			mintTokensAction.getTokenClassReference(),
			System.currentTimeMillis() / 60000L + 60000
		);

		return Collections.singletonList(SpunParticle.up(minted));
	}
}
