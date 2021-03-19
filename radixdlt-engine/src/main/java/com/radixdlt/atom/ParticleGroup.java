/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.atom;

import com.google.common.collect.ImmutableList;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A group of particles representing one action, e.g. a transfer.
 */
public final class ParticleGroup {
	/**
	 * The particles and their spin contained within this {@link ParticleGroup}.
	 */
	private ImmutableList<SpunParticle> particles;

	private ParticleGroup(Iterable<SpunParticle> particles) {
		Objects.requireNonNull(particles, "particles is required");

		this.particles = ImmutableList.copyOf(particles);
	}

	public int getParticleCount() {
		return this.particles.size();
	}

	public SpunParticle getSpunParticle(int index) {
		return this.particles.get(index);
	}

	/**
	 * Get a stream of particles of a certain spin in this group
	 * @return The particles in this group with that spin
	 */
	public Stream<Particle> upParticles() {
		return this.particles.stream()
				.filter(p -> p.getSpin() == Spin.UP)
				.map(SpunParticle::getParticle);
	}

	/**
	 * Get a boolean if this particle group contains no particles
	 * @return if this particle group has no particles
	 */
	public boolean isEmpty() {
		return this.particles.isEmpty();
	}

	public static ParticleGroupBuilder builder() {
		return new ParticleGroupBuilder();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ParticleGroup that = (ParticleGroup) o;
		return Objects.equals(particles, that.particles);
	}

	@Override
	public int hashCode() {
		return Objects.hash(particles);
	}

	/**
	 * A builder for immutable {@link ParticleGroup}s
	 */
	public static class ParticleGroupBuilder {
		private List<SpunParticle> particles = new ArrayList<>();

		private ParticleGroupBuilder() {
		}

		public final ParticleGroupBuilder spinUp(Particle particle) {
			Objects.requireNonNull(particle, "particle is required");
			SpunParticle spunParticle = SpunParticle.of(particle, Spin.UP);
			this.particles.add(spunParticle);
			return this;
		}

		public final ParticleGroupBuilder virtualSpinDown(Particle particle) {
			Objects.requireNonNull(particle, "particle is required");
			SpunParticle spunParticle = SpunParticle.of(particle, Spin.DOWN);
			this.particles.add(spunParticle);
			return this;
		}

		public final ParticleGroupBuilder spinDown(Particle particle) {
			Objects.requireNonNull(particle, "particle is required");
			SpunParticle spunParticle = SpunParticle.of(particle, Spin.DOWN);
			this.particles.add(spunParticle);
			return this;
		}

		public ParticleGroup build() {
			return new ParticleGroup(ImmutableList.copyOf(this.particles));
		}
	}

	@Override
	public String toString() {
		var particlesStr = (this.particles == null)
			? "null"
		   	: particles.stream().map(SpunParticle::toString).collect(Collectors.joining(","));

		return String.format("%s[%s]", getClass().getSimpleName(), particlesStr);
	}
}
