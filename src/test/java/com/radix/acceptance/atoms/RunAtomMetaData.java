package com.radix.acceptance.atoms;

import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.SnippetType;
import cucumber.api.junit.Cucumber;

@RunWith(Cucumber.class)
@CucumberOptions(snippets = SnippetType.UNDERSCORE, strict = true, monochrome = true, plugin = { "pretty" })
public class RunAtomMetaData {
	// Stub for running cucumber tests
}
