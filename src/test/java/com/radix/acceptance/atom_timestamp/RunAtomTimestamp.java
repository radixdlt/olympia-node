package com.radix.acceptance.atom_timestamp;

import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.SnippetType;
import cucumber.api.junit.Cucumber;

@RunWith(Cucumber.class)
@CucumberOptions(snippets = SnippetType.UNDERSCORE, strict = true, monochrome = true, plugin = { "pretty" })
public class RunAtomTimestamp {
	// Stub for running cucumber tests
}
