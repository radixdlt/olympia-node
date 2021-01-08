/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.middleware;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import com.google.common.collect.Streams.FunctionWithIndex;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A group of particles representing one action, e.g. a transfer.
 */
@SerializerId2("radix.particle_group")
public final class ParticleGroup {
	/**
	 * The particles and their spin contained within this {@link ParticleGroup}.
	 */
	private ImmutableList<SpunParticle> particles;
	private ImmutableMap<SpunParticle, Integer> indexByParticle;

	/**
	 * Metadata about the particle group, such as what the purpose of each group is in the app
	 */
	@JsonProperty("metaData")
	@DsonOutput(DsonOutput.Output.ALL)
	private final ImmutableMap<String, String> metaData;

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("version")
	@DsonOutput(Output.ALL)
	private short version = 100;

	private ParticleGroup() {
		this.particles = ImmutableList.of();
		this.indexByParticle = ImmutableMap.of();
		this.metaData = ImmutableMap.of();
	}

	private ParticleGroup(Iterable<SpunParticle> particles) {
		Objects.requireNonNull(particles, "particles is required");

		this.particles = ImmutableList.copyOf(particles);
		this.indexByParticle = index(this.particles);
		this.metaData = ImmutableMap.of();
	}

	private ParticleGroup(Iterable<SpunParticle> particles, Map<String, String> metaData) {
		Objects.requireNonNull(particles, "particles is required");
		Objects.requireNonNull(metaData, "metaData is required");

		this.particles = ImmutableList.copyOf(particles);
		this.indexByParticle = index(this.particles);
		this.metaData = ImmutableMap.copyOf(metaData);
	}

	private ParticleGroup(ImmutableList<SpunParticle> particles, Map<String, String> metaData) {
		Objects.requireNonNull(particles, "particles is required");
		Objects.requireNonNull(metaData, "metaData is required");

		this.particles = particles;
		this.indexByParticle = index(this.particles);
		this.metaData = ImmutableMap.copyOf(metaData);
	}

	private ImmutableMap<SpunParticle, Integer> index(List<SpunParticle> particles) {
		final Map<SpunParticle, Integer> indexByParticle = Maps.newHashMap();
		for (int i = 0; i < particles.size(); i += 1) {
			indexByParticle.putIfAbsent(particles.get(i), i);
		}
		return ImmutableMap.copyOf(indexByParticle);
	}

	public ImmutableList<SpunParticle> getParticles() {
		return particles;
	}

	/**
	 * Get a stream of the spun particles in this group
	 */
	public Stream<SpunParticle> spunParticles() {
		return this.particles.stream();
	}

	/**
	 * Get a stream of the spun particles of a certain particle type in this group
	 */
	public <T extends Particle> Stream<SpunParticle> spunParticles(Class<T> particleType) {
		return this.particles.stream()
				.filter(p -> particleType.isAssignableFrom(p.getParticle().getClass()));
	}

	public int getParticleCount() {
		return this.particles.size();
	}

	public SpunParticle getSpunParticle(int index) {
		return this.particles.get(index);
	}

	/**
	 * Get a stream of particles of a certain spin in this group
	 * @param spin The spin to filter by
	 * @return The particles in this group with that spin
	 */
	public <T extends Particle> Stream<T> particles(Class<T> particleClass, Spin spin) {
		return this.spunParticles(particleClass)
			.filter(p -> p.getSpin() == spin)
			.map(SpunParticle::getParticle)
			.map(particleClass::cast);
	}

	public <U> Stream<U> spunParticlesWithIndex(FunctionWithIndex<SpunParticle, U> f) {
		return Streams.mapWithIndex(this.spunParticles(), (sp, i) -> Stream.of(sp)
			.map(p -> f.apply(p, i))
		).flatMap(l -> l);
	}

	/**
	 * Get a stream of particles of a certain spin in this group
	 * @param spin The spin to filter by
	 * @return The particles in this group with that spin
	 */
	public <T extends Particle, U> Stream<U> particlesWithIndex(Class<T> particleClass, Spin spin, FunctionWithIndex<T, U> f) {
		return Streams.mapWithIndex(this.spunParticles(), (sp, i) -> Stream.of(sp)
			.filter(p -> p.getSpin() == spin)
			.filter(p -> particleClass.isInstance(p.getParticle()))
			.map(p -> particleClass.cast(p.getParticle()))
			.map(p -> f.apply(p, i))
		).flatMap(l -> l);
	}

	/**
	 * Get a stream of particles of a certain spin in this group
	 * @param spin The spin to filter by
	 * @return The particles in this group with that spin
	 */
	public Stream<Particle> particles(Spin spin) {
		return this.spunParticles()
				.filter(p -> p.getSpin() == spin)
				.map(SpunParticle::getParticle);
	}

	/**
	 * Returns the index of a given spun particle
	 * Returns -1 if not found
	 *
	 * @param spunParticle the particle to look for
	 * @return index of the particle
	 */
	public int indexOfSpunParticle(SpunParticle spunParticle) {
		return this.indexByParticle.getOrDefault(spunParticle, -1);
	}

	/**
	 * Returns all indices of a given spun particle (there may be duplicates)
	 * Returns empty stream if not found
	 * @param spunParticle the particle to look for
	 * @return all indices of the particle
	 */
	public IntStream indicesOfSpunParticle(SpunParticle spunParticle) {
		return IntStream.range(0, particles.size())
			.filter(i -> this.particles.get(i).equals(spunParticle));
	}

	/**
	 * Get a boolean if this particle group contains any particles
	 * @return if this particle group has particles
	 */
	public boolean hasParticles() {
		return !this.particles.isEmpty();
	}

	/**
	 * Get a boolean if this particle group contains no particles
	 * @return if this particle group has no particles
	 */
	public boolean isEmpty() {
		return this.particles.isEmpty();
	}

	/**
	 * Check whether this particle group contains the given spun particle
	 * @return if this particle group contains the given spun particle
	 */
	public boolean contains(SpunParticle spunParticle) {
		return this.indexByParticle.containsKey(spunParticle);
	}

	/**
	 * Get the metadata associated with the particle group
	 * @return an immutable map of the metadata
	 */
	public Map<String, String> getMetaData() {
		return this.metaData;
	}

	@JsonProperty("particles")
	@DsonOutput(DsonOutput.Output.ALL)
	List<SpunParticle> getJsonParticles() {
		return this.particles;
	}

	@JsonProperty("particles")
	void setJsonParticles(List<SpunParticle> particles) {
		this.particles = ImmutableList.copyOf(particles);
		this.indexByParticle = index(particles);
	}

	/**
	 * Get a {@link ParticleGroup} consisting of the given particles
	 */
	public static ParticleGroup of(Iterable<SpunParticle> particles) {
		return new ParticleGroup(particles);
	}


	/**
	 * Get a {@link ParticleGroup} consisting of the given particles
	 */
	public static ParticleGroup of(Iterable<SpunParticle> particles, Map<String, String> metaData) {
		return new ParticleGroup(particles, metaData);
	}

	/**
	 * Get a {@link ParticleGroup} consisting of the given particles
	 * @param particles
	 * @return
	 */
	public static ParticleGroup of(SpunParticle... particles) {
		Objects.requireNonNull(particles, "particles is required");

		return new ParticleGroup(Arrays.asList(particles));
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
		return version == that.version
				&& Objects.equals(particles, that.particles)
				&& Objects.equals(metaData, that.metaData);
	}

	@Override
	public int hashCode() {
		return Objects.hash(particles, metaData, version);
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
			return new ParticleGroup(ImmutableList.copyOf(this.particles), this.metaData);
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
