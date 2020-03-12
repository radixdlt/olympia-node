package com.radixdlt.client.application.translate.unique;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.radixdlt.client.application.translate.ActionExecutionExceptionReason;
import com.radixdlt.client.application.translate.AtomErrorToExceptionReasonMapper;
import com.radixdlt.client.atommodel.rri.RRIParticle;
import com.radixdlt.client.atommodel.unique.UniqueParticle;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.utils.Pair;
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
			Optional<Pair<ParticleGroup, SpunParticle<?>>> particleIssue = this.extractParticleFromPointerToIssue(atom, pointerToIssueJson);

			if (particleIssue.isPresent() && particleIssue.get().getSecond().getParticle() instanceof RRIParticle) {
				RRIParticle rriParticle = (RRIParticle) particleIssue.get().getSecond().getParticle();

				return particleIssue.get().getFirst()
					.particles(Spin.UP)
					.filter(UniqueParticle.class::isInstance)
					.map(UniqueParticle.class::cast)
					.filter(u -> u.getRRI().equals(rriParticle.getRRI()))
					.map(p -> new AlreadyUsedUniqueIdReason(new UniqueId(p.getRRI().getAddress(), p.getName())));
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
	private Optional<Pair<ParticleGroup, SpunParticle<?>>> extractParticleFromPointerToIssue(Atom atom, JsonElement pointerToIssueJson) {
		try {
			String pointerToIssue = pointerToIssueJson.getAsString();
			String groupIndexStr = pointerToIssue.split("/")[2];
			String particleIndexStr = pointerToIssue.substring(pointerToIssue.lastIndexOf('/') + 1);

			int groupIndex = Integer.parseInt(groupIndexStr);
			int particleIndex = Integer.parseInt(particleIndexStr);

			ParticleGroup particleGroup = atom.particleGroups().collect(Collectors.toList()).get(groupIndex);

			return Optional.of(Pair.of(particleGroup,
				(SpunParticle<?>) particleGroup.spunParticles().collect(Collectors.toList()).get(particleIndex)));
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			LOGGER.error("Malformed pointerToIssue");

			return Optional.empty();
		}
	}
}
