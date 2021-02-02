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

package com.radixdlt.client.core.atoms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

/**
 * A group of particles representing one intent, e.g. a transfer.
 */
@SerializerId2("radix.particle_group")
public class ParticleGroup {

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("particles")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableList<SpunParticle> particles;


	@JsonProperty("metaData")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableMap<String, String> metaData;

	ParticleGroup() {
		// Serializer only
		this.particles = ImmutableList.of();
		this.metaData = ImmutableMap.of();
	}

	public ParticleGroup(ImmutableList<SpunParticle> particles, ImmutableMap<String, String> metaData) {
		this.particles = Objects.requireNonNull(particles);
		this.metaData = metaData == null ? ImmutableMap.of() : metaData;
	}

	public ParticleGroup(Iterable<SpunParticle> particles) {
		this(ImmutableList.copyOf(particles), ImmutableMap.of());
	}

	public ParticleGroup(Iterable<SpunParticle> particles, Map<String, String> metaData) {
		this(ImmutableList.copyOf(particles), ImmutableMap.copyOf(metaData));
	}

	public ImmutableList<SpunParticle> getSpunParticles() {
		return particles;
	}

	/**
	 * Get a stream of the spun particles in this group
	 * @return The spun particles in this group
	 */
	public final Stream<SpunParticle> spunParticles() {
		return this.particles.stream();
	}

	/**
	 * Get a stream of particles of a certain spin in this group
	 *
	 * @param spin The spin to filter by
	 * @return The particles in this group with that spin
	 */
	public final Stream<Particle> particles(Spin spin) {
		return this.spunParticles().filter(p -> p.getSpin() == spin).map(SpunParticle::getParticle);
	}

	/** Get the metadata associated with the particle group
	 * @return an immutable map of the metadata
	 */
	public Map<String, String> getMetaData() {
		return this.metaData;
	}

	/**
	 * Get a {@link ParticleGroup} consisting of the given particles
	 *
	 * @param particles particles to include in this group
	 * @return instance of {@link ParticleGroup}
	 */
	public static ParticleGroup of(Iterable<SpunParticle> particles) {
		Objects.requireNonNull(particles, "particles is required");

		return new ParticleGroup(particles);
	}

	/**
	 * Get a {@link ParticleGroup} consisting of the given particles
	 *
	 * @param particles particles to include in this group
	 * @param metaData extra metadata for group
	 * @return instance of {@link ParticleGroup}
	 */
	public static ParticleGroup of(Iterable<SpunParticle> particles, Map<String, String> metaData) {
		Objects.requireNonNull(particles, "particles is required");
		Objects.requireNonNull(metaData, "metaData is required");

		return new ParticleGroup(particles, metaData);
	}

	/**
	 * Get a {@link ParticleGroup} consisting of the given particles
	 *
	 * @param particles particles to include in this group
	 * @return instance of {@link ParticleGroup}
	 */
	public static ParticleGroup of(SpunParticle... particles) {
		Objects.requireNonNull(particles, "particles is required");

		return new ParticleGroup(Arrays.asList(particles));
	}

	/**
	 * Whether this {@link ParticleGroup} contains any particles
	 * @return {@code true} if group contains particles
	 */
	public boolean hasParticles() {
		return !this.particles.isEmpty();
	}

	@Override
	public String toString() {
		String particlesStr = (this.particles == null)
			? "null"
			: particles.stream().map(SpunParticle::toString).collect(Collectors.joining(","));
		return String.format("%s[%s]", getClass().getSimpleName(), particlesStr);
	}

	/**
	 * Get a build for a single {@link ParticleGroup}
	 * @return The {@link ParticleGroupBuilder}
	 */
	public static ParticleGroupBuilder builder() {
		return new ParticleGroupBuilder();
	}

	/**
	 * A builder for immutable {@link ParticleGroup}s
	 */
	public static class ParticleGroupBuilder {
		private List<SpunParticle> particles = new ArrayList<>();
		private Map<String, String> metaData = new HashMap<>();

		private ParticleGroupBuilder() {
		}

		public final ParticleGroupBuilder addParticle(SpunParticle spunParticle) {
			Objects.requireNonNull(spunParticle, "spunParticle is required");

			this.particles.add(spunParticle);

			return this;
		}

		public final ParticleGroupBuilder addParticle(Particle particle, Spin spin) {
			Objects.requireNonNull(particle, "particle is required");
			Objects.requireNonNull(spin, "spin is required");

			SpunParticle spunParticle = SpunParticle.of(particle, spin);
			this.particles.add(spunParticle);

			return this;
		}

		public final ParticleGroupBuilder addMetaData(String key, String value) {
			Objects.requireNonNull(key, "key is required");
			Objects.requireNonNull(value, "value is required");

			this.metaData.put(key, value);

			return this;
		}


		public ParticleGroup build() {
			return new ParticleGroup(this.particles, metaData);
		}
	}
}
