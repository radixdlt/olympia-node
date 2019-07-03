package org.radix.collections;

import java.util.Collection;
import java.util.LinkedHashSet;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Serializable {@link LinkedHashSet}.
 * <p>
 * FIXME: This class can be removed now.
 *
 * @param <T> The element type.
 */
@SuppressWarnings("serial")
@JsonSerialize(as = LinkedHashSet.class)
public class WireableSet<T> extends LinkedHashSet<T> {
	public WireableSet() {
		super();
	}

	public WireableSet(Collection<T> defaults) {
		super(defaults);
	}

	public boolean containsAny(Collection<T> collection) {
		if (this.isEmpty() || collection.isEmpty())
			return false;

		for (T item : collection)
			if (this.contains(item))
				return true;

		return false;
	}
}
