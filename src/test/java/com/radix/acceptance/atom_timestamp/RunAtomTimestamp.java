package com.radix.acceptance.atom_timestamp;

import org.junit.runner.RunWith;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import io.cucumber.junit.CucumberOptions.SnippetType;

@RunWith(Cucumber.class)
@CucumberOptions(snippets = SnippetType.UNDERSCORE, monochrome = true, plugin = { "pretty" })
public class RunAtomTimestamp {
	// Stub for running cucumber tests
}
