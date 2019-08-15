package com.radixdlt.atomos.test;

import com.radixdlt.atomos.Result;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Results from test calls
 */
public final class TestResult {
	private final List<Result> results;

	TestResult(List<Result> results) {
		this.results = results;
	}

	public void assertSuccess() {
		if (results.isEmpty()) {
			throw new AssertionError("No results");
		}

		Optional<String> errResult = results.stream().flatMap(Result::errorStream).findFirst();
		if (errResult.isPresent()) {
			throw new AssertionError("Constraint fail: " + errResult.get());
		}
	}

	public void assertError() {
		if (results.stream().noneMatch(Result::isError)) {
			throw new AssertionError("No error found.");
		}
	}

	public void assertErrorWithMessageContaining(String errMessage) {
		if (results.stream().noneMatch(r -> r.getErrorMessage().map(msg -> msg.contains(errMessage)).orElse(false))) {
			throw new AssertionError(String.format("No error message found with: '%s': '%s'", errMessage, results.stream()
				.flatMap(Result::errorStream)
				.collect(Collectors.joining(", "))));
		}
	}

	public void assertNoErrorWithMessageContaining(String errMessage) {
		Optional<String> resultWithErrMessage = results.stream()
			.flatMap(Result::errorStream)
			.filter(err -> err.contains(errMessage))
			.findAny();

		if (resultWithErrMessage.isPresent()) {
			throw new AssertionError(String.format("Error message found with '%s': '%s'", errMessage, resultWithErrMessage.get()));
		}
	}
}
