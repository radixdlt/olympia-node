/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.utils;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.Objects;
import java.util.function.Function;

/**
 * Converts an object consumer to an rx stream
 *
 * @param <T> first object type
 * @param <U> second object type
 */
public final class SenderToRx<T, U> {
	private final Subject<U> subject = BehaviorSubject.<U>create().toSerialized();
	private final Function<T, U> mapper;

	public SenderToRx(Function<T, U> mapper) {
		this.mapper = Objects.requireNonNull(mapper);
	}

	public void send(T sendObject) {
		subject.onNext(mapper.apply(sendObject));
	}

	public Observable<U> rx() {
		return subject;
	}
}
