/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radix.regression;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

public class Util {
	private Util() {
		throw new IllegalArgumentException("Can't construct");
	}

	public static <T> Observer<T> loggingObserver(String name) {
		return new Observer<>() {
			@Override
			public void onSubscribe(Disposable d) {
				System.out.println(System.currentTimeMillis() + " " + name + ": <SUBSCRIBED>");
			}

			@Override
			public void onNext(T t) {
				System.out.println(System.currentTimeMillis() + " " + name + ": <NEXT> " + t);
			}

			@Override
			public void onError(Throwable e) {
				System.out.println(System.currentTimeMillis() + " " + name + ": <ERROR> " + e);
			}

			@Override
			public void onComplete() {
				System.out.println(System.currentTimeMillis() + " " + name + ": <COMPLETE>");
			}
		};
	}
}
