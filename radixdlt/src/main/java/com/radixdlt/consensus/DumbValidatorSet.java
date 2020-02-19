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


package com.radixdlt.consensus;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;

/**
 * Stub {@link ValidatorSet} implementation.
 * Threadsafe.
 */
public final class DumbValidatorSet implements ValidatorSet {

	private final EUID self;

	@GuardedBy("lock")
	private ImmutableSet<Validator> validators = ImmutableSet.of();
	private final Object lock = new Object();

	@Inject
	DumbValidatorSet(@Named("self") EUID self) {
		this.self = Objects.requireNonNull(self);
	}

	@Override
	public void replace(Collection<Validator> validators) {
		synchronized (lock) {
			this.validators = validators.stream()
				.filter(this::notSelf)
				.collect(ImmutableSet.toImmutableSet());
		}
	}

	@Override
	public Set<Validator> validators() {
		synchronized (lock) {
			return ImmutableSet.copyOf(this.validators);
		}
	}

	private boolean notSelf(Validator v) {
		return !self.equals(v.nodeId());
	}

	@Override
	public String toString() {
		List<EUID> nids = validators().stream().map(Validator::nodeId).collect(Collectors.toList());
		return String.format("%s[%s]", getClass().getSimpleName(), nids);
	}
}
