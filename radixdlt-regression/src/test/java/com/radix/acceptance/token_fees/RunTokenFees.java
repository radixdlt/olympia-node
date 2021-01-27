package com.radix.acceptance.token_fees;
import org.junit.runner.RunWith;

import io.cucumber.junit.CucumberOptions;
import io.cucumber.junit.CucumberOptions.SnippetType;
import io.cucumber.junit.Cucumber;

@RunWith(Cucumber.class)
@CucumberOptions(snippets = SnippetType.UNDERSCORE, monochrome = true, plugin = { "pretty" })
public class RunTokenFees {
	// Stub for running cucumber tests
}
