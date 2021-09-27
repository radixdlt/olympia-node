# Contributing to radixdlt

### Table of contents
- [Code of conduct](#code-of-conduct)
- [Get started](#get-started)
  - [Reporting a bug](#reporting-a-bug)
- [Branching strategy](#branching-strategy)
  - [Rebasing](#rebasing)
  - [Main flow](#main-flow)
  - [Branch types and naming](#branch-types-and-naming)
  - [Features](#features)
  - [Release candidates](#release-candidates)
  - [Releases](#releases)
  - [Hotfixes](#hotfixes)  
- [Contribute](#contribute)
  - [Code style](#code-style)
  - [Code structure](#code-structure)
  - [Commit messages](#commit-messages)
  - [Opening a pull request](#opening-a-pull-request)
  

## Code of conduct

This project adheres to the Contributor Covenant [code of conduct](CODE_OF_CONDUCT.md).
By participating, you are expected to uphold this code.
Please report unacceptable behavior to [hello@radixdlt.com](mailto:hello@radixdlt.com).

## Get started

### Reporting a bug

* **Ensure the bug was not already reported** by searching on GitHub under [Issues](https://github.com/radixdlt/radixdlt-parent/issues).
* If you're unable to find an open issue addressing the problem, [open a new one](https://github.com/radixdlt/radixdlt-parent/issues/new). Be sure to include:
  * a **title**,
  * a **clear description**, 
  * as much **relevant information** as possible,
  * a **code sample** or an **executable test case** demonstrating the expected behavior that is not occurring.

## Branching strategy

This branching scheme is a combination of GitHub flow and git-flow where there’s the develop branch, feature branches and then release branches.


### Rebasing

Rebasing in develop should be avoided. Rebases cause potential conflicts with other people's work on the same branches, overwrite the history of the project and ruin any GPG signed commits from other developers. On feature branches, especially if only one developer is working on it, it’s ok to do rebasing.


### Main flow

* Create feature branches using develop as a starting point to start new work

When the release process starts:

* Create a release branch
* Tag the commit with a release candidate tag, e.g. rc/1.0.3
* Once a release branch is created, only add serious bug fixes to the branch.
* Once a release branch (release or hotfix) is released, merge back to develop



### Branch types and naming

* Development  - `develop`
* Release - `release/1.0.0`
* Feature - `feature/cool-bananas`
* Hotfix - `release/1.0.1`


### Features

Feature branches are where the main work happens. The goal is to keep them as independent from each other as possible. They can be based on a previous release or from develop.

> develop branch is not a place to dump WIP features

It’s important to remark that feature branches should only be merged to develop once they are complete and ideally tested in a test network.

### Develop

This branch acts as staging for new releases, and are where most of QA should happen.

When QA gives the green light, a new release branch is created

### Releases

These branches will stay alive forever, or at least while we support the release, thereby allowing us to release security hotfixes for older versions.

If QA discovers a bug with any of the features before a release happens, it is fixed in the feature branch taken from the release branch and then merged into the release again. 

These changes should immediately be propagated to the current release candidate branch.

### Hotfixes

Hotfix branches are for providing emergency security fixes to older versions and should be treated like release branches.

The hotifx should be created for the oldest affected release, and then merged downstream into the next release or release candidate, repeated until up to date.

## Contribute


### Code style

#### Opening Braces on the Same Line

Braces follow the Kernighan and Ritchie (K&R) style for nonempty blocks and block-like constructs:

* No line break before the opening brace.
* Line break after the opening brace.
* Line break before the closing brace.
* Line break after the closing brace, only if that brace terminates a statement or terminates the body of a method, constructor, or named class. For example, there is no line break after the brace if it is followed by else or a comma.

#### Braces Always Required

* Braces are always required with `if`, `else`, `for`, `do` and `while` statements, even when the body of the statement is empty or contains only a single statement.

#### Use of "this." for Field Access

* Use of the `this` keyword is preferred in situations where there may be ambiguity in field and variable names, such as in setters and constructors.

### Code structure

#### Javadoc locations

* Properly formatted and complete Javadoc must be included for all fields and methods with either `public` or `protected` visibility.
* Note that overridden instance methods or implemented `interface` methods need not have Javadoc if the inherited Javadoc is correct and suitable.  In particular methods that override superclass methods and change the behaviour of the method should document the new behaviour.

### Commit messages

  *  Separate subject from body with a blank line
  *  Limit the subject line to 50 characters
  *  Capitalise the subject line
  *  Do not end the subject line with a period
  *  Use the imperative mood in the subject line
  *  Wrap the body at 72 characters
  *  Use the body to explain what and why vs. how, separating paragraphs with an empty line.


### Opening a pull request

* Fork the codebase and make changes, following these guidelines.
* Submit a new GitHub pull request with the proposed patch for review.
* Ensure the **pull request** description clearly describes the problem and solution. Include the relevant issue number if applicable.
