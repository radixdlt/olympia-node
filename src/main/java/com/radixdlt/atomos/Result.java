package com.radixdlt.atomos;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Results of a constraint check
 */
public final class Result {
	private static final Result SUCCESS = new Result(false, null);
	private final boolean isError;
	private final String errorMsg;

	private Result(boolean isError, String errorMsg) {
		this.isError = isError;
		this.errorMsg = errorMsg;
	}

	/**
	 * Returns a result indicating success
	 */
	public static Result success() {
		return SUCCESS;
	}

	/**
	 * Returns a result indicating error with an error message
	 */
	public static Result error(String errorMsg) {
		return new Result(true, errorMsg);
	}

	/**
	 * Returns a result indicating success or error given the success boolean
	 * @param success The boolean indicating success or error
	 * @param messageIfError The message to supply if it's an error
	 * @return The result
	 */
	public static Result of(boolean success, Supplier<String> messageIfError) {
		if (success) {
			return SUCCESS;
		} else {
			return error(messageIfError.get());
		}
	}

	/**
	 * Returns a result indicating success or error given the success boolean
	 * @param success The boolean indicating success or error
	 * @param messageIfError The message if an error
	 * @return The result
	 */
	public static Result of(boolean success, String messageIfError) {
		if (success) {
			return SUCCESS;
		} else {
			return error(messageIfError);
		}
	}

	/**
	 * Combine multiple results into one.
	 * If all are success, returns success.
	 * If one or more are error, return combined error messages.
	 * @param results The results to combine
	 * @return The combined result
	 */
	public static Result combine(Result... results) {
		return combine(Stream.of(results));
	}

	/**
	 * Combine multiple results into one.
	 * If all are success, returns success.
	 * If one or more are error, return combined error messages.
	 * @param results The results to combine
	 * @return The combined result
	 */
	public static Result combine(Stream<Result> results) {
		Objects.requireNonNull(results, "results is required");

		String error = results
			.filter(Result::isError)
			.map(Result::getErrorMessage)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.collect(Collectors.joining(", "));

		return Result.of(error.isEmpty(), error);
	}

	/**
	 * Returns whether constraint check this object represents was successful or not
	 *
	 * @return if success returns false, otherwise returns true
	 */
	public boolean isError() {
		return isError;
	}

	/**
	 * Returns whether constraint check this object represents was sucessful or not
	 *
	 * @return if success returns false, otherwise returns true
	 */
	public boolean isSuccess() {
		return !isError;
	}

	/**
	 * Maps an error message given a function.
	 *
	 * @param mapper mapper from error message to a T object
	 * @param <T> class of the object to map to
	 * @return Optional containing mapped error message
	 */
	public <T> Optional<T> mapOnError(Function<String, T> mapper) {
		return isError ? Optional.of(mapper.apply(errorMsg)) : Optional.empty();
	}

	/**
	 * Returns singleton stream of the error message if an error exists.
	 * Useful for functional stream logic
	 *
	 * @return if error, singleton stream of error message, otherwise, an empty stream
	 */
	public Stream<String> errorStream() {
		return isError ? Stream.of(errorMsg) : Stream.empty();
	}

	/**
	 * Get the error message of this Result, exists if there is an error
	 * @return if error, contains the error message, empty otherwise
	 */
	public Optional<String> getErrorMessage() {
		return Optional.ofNullable(this.errorMsg);
	}

	/**
	 * Throws a given exception if an instance of an error
	 *
	 * @param exceptionMapper mapper to exception
	 * @param <T> class of exception thrown
	 * @throws T exception thrown
	 */
	public <T extends Exception> void throwOnError(Function<String, T> exceptionMapper) throws T {
		if (isError) {
			throw exceptionMapper.apply(errorMsg);
		}
	}

	/**
	 * Calls a given handler with the error message if an instance of an error
	 * @param onErrorHandler The error handle to call with the error message if error
	 */
	public void onError(Consumer<String> onErrorHandler) {
		if (isError) {
			onErrorHandler.accept(this.errorMsg);
		}
	}
}
