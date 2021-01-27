package com.radix.acceptance.token_character_set;
import org.junit.runner.RunWith;

import io.cucumber.junit.CucumberOptions;
import io.cucumber.junit.CucumberOptions.SnippetType;
import io.cucumber.junit.Cucumber;

@RunWith(Cucumber.class)
@CucumberOptions(snippets = SnippetType.UNDERSCORE, monochrome = true, plugin = { "pretty" })
public class RunTokenSymbolCharacterSet {
	// Stub for running cucumber tests
}
