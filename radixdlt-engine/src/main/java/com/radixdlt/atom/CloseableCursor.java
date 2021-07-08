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

import com.google.common.collect.Iterators;

import java.io.Closeable;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Cursor into a substate store. Will often be a real cursor in a database so
 * cursor must be closed after use.
 */
public interface CloseableCursor<T> extends Iterator<T>, Closeable {
	void close();

	default <U> CloseableCursor<U> map(Function<T, U> mapper) {
		return new CloseableCursor<>() {
			@Override
			public void close() {
				CloseableCursor.this.close();
			}

			@Override
			public boolean hasNext() {
				return CloseableCursor.this.hasNext();
			}

			@Override
			public U next() {
				var next = CloseableCursor.this.next();
				return mapper.apply(next);
			}
		};
	}

	default CloseableCursor<T> filter(Predicate<T> substatePredicate) {
		var iterator = Iterators.filter(this, substatePredicate::test);
		return new CloseableCursor<>() {
			@Override
			public void close() {
				CloseableCursor.this.close();
			}

			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override
			public T next() {
				return iterator.next();
			}
		};
	}

	default CloseableCursor<T> concat(Supplier<CloseableCursor<T>> supplier1) {
		return new CloseableCursor<>() {
			private CloseableCursor<T> cursor1;

			@Override
			public void close() {
				CloseableCursor.this.close();
				if (cursor1 != null) {
					cursor1.close();
				}
			}

			@Override
			public boolean hasNext() {
				if (CloseableCursor.this.hasNext()) {
					return true;
				}

				if (cursor1 == null) {
					cursor1 = supplier1.get();
				}

				return cursor1.hasNext();
			}

			@Override
			public T next() {
				if (cursor1 != null) {
					return cursor1.next();
				} else {
					var s = CloseableCursor.this.next();
					if (!CloseableCursor.this.hasNext()) {
						cursor1 = supplier1.get();
					}
					return s;
				}
			}
		};
	}


	static <T> CloseableCursor<T> wrapIterator(Iterator<T> i) {
		return new CloseableCursor<>() {
			@Override
			public void close() {
			}

			@Override
			public boolean hasNext() {
				return i.hasNext();
			}

			@Override
			public T next() {
				return i.next();
			}
		};
	}

	static <T> CloseableCursor<T> empty() {
		return new CloseableCursor<>() {
			@Override
			public void close() {
			}

			@Override
			public boolean hasNext() {
				return false;
			}

			@Override
			public T next() {
				throw new NoSuchElementException();
			}
		};
	}
}
