package org.radix.collections;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Serializable {@link ArrayList}.
 * <p>
 * FIXME: This class is no longer required.
 *
 * @param <T> Element type.
 */
@SuppressWarnings("serial")
public class WireableList<T> extends ArrayList<T> {
	public WireableList() {
		// Nothing to do here
	}

	public WireableList(Collection<T> defaults) {
		super(defaults);
	}
}
