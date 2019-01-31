package com.radix.acceptance.token_character_set;
import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.SnippetType;
import cucumber.api.junit.Cucumber;

@RunWith(Cucumber.class)
@CucumberOptions(snippets = SnippetType.UNDERSCORE, strict = true, monochrome = true, plugin = { "pretty" })
public class RunTokenSymbolCharacterSet {
	// Stub for running cucumber tests
}
