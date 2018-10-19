package com.radixdlt.client.core.atoms.particles;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.particles.quarks.Quark;
import com.radixdlt.client.core.crypto.ECPublicKey;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerDummy;
import org.radix.serialization2.SerializerId2;

import java.util.*;
import java.util.stream.Stream;

/**
 * A logical action on the ledger, composed of distinct {@link Quark} properties
 */
@SerializerId2("PARTICLE")
public abstract class Particle {
	private Spin spin;

	@JsonProperty("quarks")
	@DsonOutput(DsonOutput.Output.ALL)
	private final List<Quark> quarks; // immutable for now, later on will be able to modify after construction

	// Placeholder for the serializer ID
	@JsonProperty("serializer")
	@DsonOutput({DsonOutput.Output.API, DsonOutput.Output.WIRE, DsonOutput.Output.PERSIST})
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	protected Particle() {
		this.quarks = Collections.emptyList();
	}

	protected Particle(Spin spin, Quark... quarks) {
		this.spin = spin;
		this.quarks = Arrays.asList(quarks);
	}

	protected Particle(Spin spin, List<Quark> quarks) {
		this.spin = spin;
		this.quarks = Collections.unmodifiableList(quarks);
	}

	public final Spin getSpin() {
		return this.spin;
	}

	public abstract Set<ECPublicKey> getAddresses();

	/// Methods taken from Particle.java in Core

	public final <T extends Quark> Optional<T> getQuark(Class<T> type) {
		Objects.requireNonNull(type, "type is required");

		if (this.quarks != null) {
			for (Quark quark : this.quarks) {
				// all quark classes must be final
				if (type == quark.getClass()) {
					return Optional.of((T) quark);
				}
			}
		}

		return Optional.empty();
	}

	public final <T extends Quark> T getQuarkOrError(Class<T> type) {
		Objects.requireNonNull(type, "type is required");

		if (this.quarks != null) {
			for (Quark quark : this.quarks) {
				// all quark classes must be final
				if (type == quark.getClass()) {
					return (T) quark;
				}
			}
		}

		throw new RuntimeException("No quark with type " + type + " found in this particle");
	}

	public final List<Quark> getQuarks() {
		return Collections.unmodifiableList(this.quarks);
	}

	public final <T extends Quark> List<T> getQuarks(Class<T> type) {
		Objects.requireNonNull(type, "type is required");

		List<Quark> quarks = new ArrayList<>();

		if (this.quarks != null) {
			for (Quark quark : this.quarks) {
				if (type == quark.getClass()) {
					quarks.add(quark);
				}
			}
		}

		return (List<T>) Collections.unmodifiableList(quarks);
	}

	public final <T extends Quark> Stream<T> quarks(Class<T> type) {
		if (this.quarks != null) {
			return this.quarks.stream().filter(q -> type == null || type == q.getClass()).map(q -> (T) q);
		} else {
			return Stream.empty();
		}
	}

	public final <T extends Quark> boolean containsQuark(Class<T> type) {
		if (this.quarks != null) {
			return this.quarks.stream().anyMatch(q -> type == null || type == q.getClass());
		} else {
			return false;
		}
	}

	@JsonProperty("spin")
	@DsonOutput(value = {DsonOutput.Output.WIRE, DsonOutput.Output.API, DsonOutput.Output.PERSIST})
	private int getJsonSpin() {
		return this.spin.ordinalValue();
	}

	@JsonProperty("spin")
	private void setJsonSpin(int spin) {
		this.spin = Spin.valueOf(spin);
	}

}
