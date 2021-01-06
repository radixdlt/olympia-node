# Contributing to radixdlt-java

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

Our branching strategy is based on [this article](https://medium.com/@matt.dekrey/a-better-git-branching-model-b3bc8b73e472).

### Rebasing

Rebasing should be avoided. Rebases cause potential conflicts with other people's work on the same branches, overwrite history of the project and ruin any GPG signed commits from other developers. The only time it is OK to rebase is when there is a local branch that hasn't been pushed to any remotes yet.

### Main flow

* Create a feature branch off the previous release, release candidate or dependency feature branch
* Feature branches get merged into the next release candidate branch
* When QA approves, release candidate branch is renamed to release, all merged feature branches are deleted, release becomes the new base
* Hotfixes come off the lowest affected version, then get merged downstream into every next release branch
* Feature fixes go on feature branches, then again into release candidate

### Branch types and naming

* Release candidate  - `rc/1.0`
* Release - `release/1.0`
* Feature - `feature/cool-bananas`
* Hotfix - `hotfix/bananas-too-hot`


### Features

Feature branches are where the main work happens. The goal is to keep them as independent from each other as possible. 

They can be based off a previous release, a previous release candidate if they depend on an upcoming release or another feature branch if it depends directly on another feature. If it has multiple dependencies, first create an integration branch with all the dependencies merged together.

They get merged into release candidates, or if there is a complex merge with another feature, it is recommended to create an integration branch for merging these features before merging that into the release candidate.

Feature branches get deleted only when they become a part of a full release.

If a bug is discovered, it is fixed in the feature branch and merged into the release candidate again - this way if a feature needs to be removed from a release, nothing is lost.

### Release candidates

These branches act as staging for new releases, and are where most of QA should happen.

When QA gives the green light, they are merged into a new release branch and then deleted.

If QA discovers a bug with any of the features, it is fixed in the feature branch and then merged into the release candidate again.

If there are any downstream release candidates based on this release candidate, when a feature is merged into the release candidate, it should immediately be propagated to the dependent release candidates.

The biggest advantage of the release candidate branches is that if it's decided that a feature needs to be removed from a release (because it is either incomplete, or cancelled) it is possible to delete and recreate the release candidate from scratch without the offending feature. This is a bit of work but could be a lifesaver.

### Releases

These branches will stay alive forever, or at least while we support the release, thereby allowing us to release security hotfixes for older versions.

Only hotfixes can be merged directly into these, everything else goes through release candidates.

We should also add tags here to each commit that is a published release.

### Hotfixes

Hotfix branches are for providing emergency security fixes to older versions. 

The hotifx should be created for the oldest affected release, merged into that release, and then the release merged downstream into the next release or release candidate, repeated until up to date. The reason for doing this, instead of cherry-picking the commit, is that git remembers merge history, and any future hotfixes will be trivial to propagate downstream.

Once the hotfix branch is ready, it should be treated basically as a release candidate and handed off to QA, deleted when merged into the release branch.


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
