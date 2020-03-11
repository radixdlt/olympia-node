package com.radixdlt.consensus;

/**
 * Represents a BFT view used by the Pacemaker of a BFT instance
 */
public final class View implements Comparable<View> {
	private final long view;

	private View(long view) {
		if (view < 0) {
			throw new IllegalArgumentException("view must be >= 0 but was " + view);
		}

		this.view = view;
	}

	public static View of(long view) {
		return new View(view);
	}

	public View next() {
		if (this.view == Long.MAX_VALUE) {
			throw new IllegalStateException("View Overflow");
		}

		return new View(view + 1);
	}

	public long number() {
		return this.view;
	}

	@Override
	public int compareTo(View otherView) {
		return Long.compare(this.view, otherView.view);
	}

	@Override
	public int hashCode() {
		return Long.hashCode(view);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof View)) {
			return false;
		}

		View view = (View) o;
		return view.view == this.view;
	}


	@Override
	public String toString() {
		return "view " + this.view;
	}
}
