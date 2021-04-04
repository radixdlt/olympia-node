/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.atom;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import com.radixdlt.constraintmachine.Particle;

import java.io.Closeable;
import java.util.Iterator;
import java.util.function.Supplier;

public interface SubstateStore {
	interface SubstateCursor extends Iterator<Substate>, Closeable {
		void close();

		static SubstateCursor filter(SubstateCursor cursor, Predicate<Substate> substatePredicate) {
			var iterator = Iterators.filter(cursor, substatePredicate);
			return new SubstateCursor() {
				@Override
				public void close() {
					cursor.close();
				}

				@Override
				public boolean hasNext() {
					return iterator.hasNext();
				}

				@Override
				public Substate next() {
					return iterator.next();
				}
			};
		}

		static SubstateCursor concat(SubstateCursor cursor0, Supplier<SubstateCursor> supplier1) {
			return new SubstateCursor() {
				private SubstateStore.SubstateCursor cursor1;

				@Override
				public void close() {
					cursor0.close();
					if (cursor1 != null) {
						cursor1.close();
					}
				}

				@Override
				public boolean hasNext() {
					return cursor0.hasNext() || (cursor1 != null && cursor1.hasNext());
				}

				@Override
				public Substate next() {
					if (cursor1 != null) {
						return cursor1.next();
					} else {
						var s = cursor0.next();
						if (!cursor0.hasNext()) {
							cursor1 = supplier1.get();
						}
						return s;
					}
				}
			};
		}
	}

	SubstateCursor openIndexedCursor(Class<? extends Particle> particleClass);

	static SubstateStore empty() {
		return c -> new SubstateCursor() {
			@Override
			public void close() {
			}

			@Override
			public boolean hasNext() {
				return false;
			}

			@Override
			public Substate next() {
				throw new IllegalStateException();
			}
		};
	}

	static SubstateCursor wrapCursor(Iterator<Substate> i) {
		return new SubstateCursor() {
			@Override
			public void close() {
			}

			@Override
			public boolean hasNext() {
				return i.hasNext();
			}

			@Override
			public Substate next() {
				return i.next();
			}
		};
	}
}
