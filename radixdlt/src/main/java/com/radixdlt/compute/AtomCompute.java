package com.radixdlt.compute;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.AtomOSKernel.AtomKernelCompute;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.constraintmachine.CMAtom;

/**
 * Computation (rather than validation) done per atom.
 */
public final class AtomCompute {
	public static class Builder {
		private ImmutableMap.Builder<String, AtomKernelCompute> computations = new ImmutableMap.Builder<>();

		public AtomCompute.Builder addCompute(String key, AtomKernelCompute atomKernelCompute) {
			computations.put(key, atomKernelCompute);
			return this;
		}

		public AtomCompute build() {
			return new AtomCompute(computations.build());
		}
	}

	private final ImmutableMap<String, AtomKernelCompute> computations;

	private AtomCompute(ImmutableMap<String, AtomKernelCompute> computations) {
		this.computations = computations;
	}

	public ImmutableMap<String, Object> compute(CMAtom cmAtom) {
		final ImmutableAtom atom = cmAtom.getAtom();
		final ImmutableMap.Builder<String, Object> atomCompute = new ImmutableMap.Builder<>();
		computations.forEach((key, c) -> atomCompute.put(key, c.compute(atom)));
		return atomCompute.build();
	}
}
