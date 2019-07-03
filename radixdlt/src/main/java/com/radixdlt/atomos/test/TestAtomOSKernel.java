package com.radixdlt.atomos.test;

import com.radixdlt.atomos.AtomOSKernel;
import com.radixdlt.atomos.Result;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.universe.Universe;

/**
 * A stubbed Kernel layer used for testing Atom Model Kernel Layer Code.
 * Note that this class is not thread-safe.
 */
public final class TestAtomOSKernel implements AtomOSKernel {
	private final List<AtomKernelConstraintCheck> atomChecks = new ArrayList<>();
	private Universe universe;
	private long currentTimestamp;

	public TestAtomOSKernel(Universe universe) {
		this.universe = universe;
	}

	@Override
	public AtomKernel onAtom() {
		return new AtomKernel() {
			@Override
			public void require(AtomKernelConstraintCheck constraint) {
				atomChecks.add(constraint);
			}

			@Override
			public void compute(String key, AtomKernelCompute compute) {
			}
		};
	}

	/**
	 * Mimics an atom kernel constraint check call
	 * @param cmAtom The atom
	 * @return Result of the atom kernel constraint checks
	 */
	public TestResult testAtom(CMAtom cmAtom) {
		List<Result> results = atomChecks.stream()
			.map(constraint -> constraint.check(cmAtom))
			.collect(Collectors.toList());

		return new TestResult(results);
	}

	public void setUniverse(Universe universe) {
		this.universe = universe;
	}

	@Override
	public Universe getUniverse() {
		return universe;
	}

	public void setCurrentTimestamp(long currentTimestamp) {
		this.currentTimestamp = currentTimestamp;
	}

	@Override
	public long getCurrentTimestamp() {
		return currentTimestamp;
	}
}
