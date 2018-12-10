package com.radixdlt.client.application.translate.unique;

import com.google.gson.JsonObject;
import com.radixdlt.client.application.translate.ActionExecutionExceptionReason;
import com.radixdlt.client.application.translate.AtomErrorToExceptionReasonMapper;
import com.radixdlt.client.atommodel.quarks.NonFungibleQuark;
import com.radixdlt.client.atommodel.unique.UniqueId;
import com.radixdlt.client.atommodel.unique.UniqueParticle;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import java.util.stream.Stream;

public class AlreadyUsedUniqueIdReasonMapper implements AtomErrorToExceptionReasonMapper {
	@Override
	public Stream<ActionExecutionExceptionReason> mapAtomErrorToExceptionReasons(Atom atom, JsonObject errorData) {
		if (errorData.has("pointerToIssue")) {
			String particleIndexStr = errorData.get("pointerToIssue").getAsString().substring("#/particles/".length());
			int particleIndex = Integer.valueOf(particleIndexStr);
			SpunParticle<?> spunParticle = atom.getSpunParticles().get(particleIndex);
			if (spunParticle.getParticle() instanceof UniqueParticle) {
				UniqueParticle uniqueParticle = (UniqueParticle) spunParticle.getParticle();
				NonFungibleQuark nonFungibleQuark = uniqueParticle.getQuarkOrError(NonFungibleQuark.class);
				UniqueId uniqueId = (UniqueId) nonFungibleQuark.getIndex();
				return Stream.of(new AlreadyUsedUniqueIdReason(uniqueId));
			}
		}

		return Stream.empty();
	}
}
