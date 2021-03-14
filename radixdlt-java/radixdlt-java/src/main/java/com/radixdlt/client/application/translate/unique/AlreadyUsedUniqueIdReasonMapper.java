/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.application.translate.unique;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.radixdlt.client.application.translate.ActionExecutionExceptionReason;
import com.radixdlt.client.application.translate.AtomErrorToExceptionReasonMapper;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.client.atommodel.unique.UniqueParticle;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.atom.SpunParticle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.utils.Pair;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AlreadyUsedUniqueIdReasonMapper implements AtomErrorToExceptionReasonMapper {

	public static final Logger LOGGER = LogManager.getLogger(AlreadyUsedUniqueIdReasonMapper.class);

	@Override
	public Stream<ActionExecutionExceptionReason> mapAtomErrorToExceptionReasons(Atom atom, JsonObject errorData) {
		if (errorData.has("pointerToIssue")) {
			JsonElement pointerToIssueJson = errorData.get("pointerToIssue");
			Optional<Pair<ParticleGroup, SpunParticle>> particleIssue = this.extractParticleFromPointerToIssue(atom, pointerToIssueJson);

			if (particleIssue.isPresent() && particleIssue.get().getSecond().getParticle() instanceof RRIParticle) {
				RRIParticle rriParticle = (RRIParticle) particleIssue.get().getSecond().getParticle();

				return particleIssue.get().getFirst()
					.particles(Spin.UP)
					.filter(UniqueParticle.class::isInstance)
					.map(UniqueParticle.class::cast)
					.filter(u -> u.getRRI().equals(rriParticle.getRri()))
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
	private Optional<Pair<ParticleGroup, SpunParticle>> extractParticleFromPointerToIssue(Atom atom, JsonElement pointerToIssueJson) {
		try {
			String pointerToIssue = pointerToIssueJson.getAsString();
			String groupIndexStr = pointerToIssue.split("/")[2];
			String particleIndexStr = pointerToIssue.substring(pointerToIssue.lastIndexOf('/') + 1);

			int groupIndex = Integer.parseInt(groupIndexStr);
			int particleIndex = Integer.parseInt(particleIndexStr);

			ParticleGroup particleGroup = atom.particleGroups().collect(Collectors.toList()).get(groupIndex);

			return Optional.of(Pair.of(particleGroup,
				particleGroup.spunParticles().collect(Collectors.toList()).get(particleIndex)));
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			LOGGER.error("Malformed pointerToIssue");

			return Optional.empty();
		}
	}
}
