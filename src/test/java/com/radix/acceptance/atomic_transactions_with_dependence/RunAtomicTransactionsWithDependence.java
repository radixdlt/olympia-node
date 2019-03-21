package com.radix.acceptance.atomic_transactions_with_dependence;

import cucumber.api.CucumberOptions;
import cucumber.api.SnippetType;
import cucumber.api.junit.Cucumber;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(snippets = SnippetType.UNDERSCORE, strict = true, monochrome = true, plugin = { "pretty" })
public class RunAtomicTransactionsWithDependence {
	// Stub for running cucumber tests
}
