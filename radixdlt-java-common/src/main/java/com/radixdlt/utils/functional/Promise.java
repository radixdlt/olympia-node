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

package com.radixdlt.utils.functional;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public class Promise<T> extends CompletableFuture<Result<T>> {
	private Promise() {
	}

	private Promise(Result<T> value) {
		complete(value);
	}

	public static <R> Promise<R> promise() {
		return new Promise<>();
	}

	public static <R> Promise<R> promise(Function<? super Throwable, ? extends Failure> errorMapper, CompletableFuture<R> future) {
		var promise = new Promise<R>();
		future.whenComplete(
			(value, exception) ->
				promise.resolve(exception != null ? Result.fail(errorMapper.apply(exception)) : Result.ok(value))
		);
		return promise;
	}

	public static <R> Promise<R> promise(CompletableFuture<Result<R>> future) {
		var promise = new Promise<R>();
		future.thenAccept(promise::resolve);
		return promise;
	}

	public static <R> Promise<R> promise(Consumer<Promise<R>> setupLambda) {
		var promise = new Promise<R>();
		setupLambda.accept(promise);
		return promise;
	}

	public static <R> Promise<R> promise(Result<R> value) {
		return new Promise<>(value);
	}

	public static <R> Promise<R> ok(R value) {
		return Promise.promise(Result.ok(value));
	}

	public static <R> Promise<R> failure(Failure failure) {
		return Promise.promise(Result.fail(failure));
	}

	public Promise<T> resolve(Result<T> value) {
		complete(value);
		return this;
	}

	public Promise<T> onResult(Consumer<Result<T>> action) {
		thenAccept(action);
		return this;
	}

	public Promise<T> onSuccess(Consumer<T> action) {
		return onResult(result -> result.onSuccess(action));
	}

	public Promise<T> onFailure(Consumer<? super Failure> action) {
		return onResult(result -> result.onFailure(action));
	}

	public <R> Promise<R> map(Function<? super T, R> mapper) {
		var result = Promise.<R>promise();

		onResult(r -> result.resolve(r.map(mapper)));

		return result;
	}

	public <R> Promise<R> flatMap(Function<? super T, Result<R>> mapper) {
		var result = Promise.<R>promise();

		onResult(r -> result.resolve(r.flatMap(mapper)));

		return result;
	}
}
