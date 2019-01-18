package com.radixdlt.client.application.translate.unique;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.radixdlt.client.application.translate.ActionExecutionExceptionReason;
import com.radixdlt.client.application.translate.AtomErrorToExceptionReasonMapper;
import com.radixdlt.client.atommodel.quarks.IdentifiableQuark;
import com.radixdlt.client.atommodel.unique.UniqueParticle;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.RadixResourceIdentifer;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AlreadyUsedUniqueIdReasonMapper implements AtomErrorToExceptionReasonMapper {

	public static final Logger LOGGER = LoggerFactory.getLogger(AlreadyUsedUniqueIdReasonMapper.class);

	@Override
	public Stream<ActionExecutionExceptionReason> mapAtomErrorToExceptionReasons(Atom atom, JsonObject errorData) {
		if (errorData.has("pointerToIssue")) {
			JsonElement pointerToIssueJson = errorData.get("pointerToIssue");
			Optional<SpunParticle<?>> spunParticle = this.extractParticleFromPointerToIssue(atom, pointerToIssueJson);

			if (spunParticle.isPresent() && spunParticle.get().getParticle() instanceof UniqueParticle) {
				UniqueParticle uniqueParticle = (UniqueParticle) spunParticle.get().getParticle();
				IdentifiableQuark identifiableQuark = uniqueParticle.getQuarkOrError(IdentifiableQuark.class);
				RadixResourceIdentifer id = identifiableQuark.getId();
				UniqueId uniqueId = new UniqueId(id.getAddress(), id.getUnique());
				return Stream.of(new AlreadyUsedUniqueIdReason(uniqueId));
			}
		}

		return Stream.empty();
	}

	/**
	 * Extract the affected particle from pointerToIssue of an Atom
	 *
	 * @param atom               The atom the particle is in
	 * @param pointerToIssueJson The pointer to issue in Json form returned by the node
	 * @return The SpunParticle if it could be extracted
	 */
	private Optional<SpunParticle<?>> extractParticleFromPointerToIssue(Atom atom, JsonElement pointerToIssueJson) {
		try {
			String pointerToIssue = pointerToIssueJson.getAsString();
			String groupIndexStr = pointerToIssue.split("/")[2];
			String particleIndexStr = pointerToIssue.substring(pointerToIssue.lastIndexOf('/') + 1);

			int groupIndex = Integer.parseInt(groupIndexStr);
			int particleIndex = Integer.parseInt(particleIndexStr);

			ParticleGroup particleGroup = atom.particleGroups().collect(Collectors.toList()).get(groupIndex);

			return Optional.of((SpunParticle<?>) particleGroup.spunParticles().collect(Collectors.toList()).get(particleIndex));
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			LOGGER.error("Malformed pointerToIssue");

			return Optional.empty();
		}
	}
}
