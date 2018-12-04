package com.radixdlt.client.core.atoms.particles;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.quarks.AccountableQuark;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.SerializableObject;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.crypto.ECPublicKey;

/**
 * A logical action on the ledger, composed of distinct {@link Quark} properties
 */
@SerializerId2("PARTICLE")
public abstract class Particle extends SerializableObject {
	@JsonProperty("quarks")
	@DsonOutput(DsonOutput.Output.ALL)
	private final List<Quark> quarks; // immutable for now, later on will be able to modify after construction

	protected Particle() {
		this.quarks = Collections.emptyList();
	}

	protected Particle(Quark... quarks) {
		this.quarks = Arrays.asList(quarks);
	}

	protected Particle(List<Quark> quarks) {
		this.quarks = Collections.unmodifiableList(quarks);
	}

	public final Set<ECPublicKey> getAddresses() {
		return this.getQuark(AccountableQuark.class)
			.map(AccountableQuark::getAddresses)
			.orElse(Collections.emptyList()).stream()
			.map(RadixAddress::getPublicKey)
			.collect(Collectors.toSet());
	}

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
}
