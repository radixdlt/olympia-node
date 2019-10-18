# Contributing to radixdlt-js

### Table of contents
- [Code of conduct](#code-of-conduct)
- [Get started](#get-started)
  - [Package manager](#package-manager)
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
  - [Code structure](#code-structure)
  - [Testing](#testing)
  - [Code structure](#code-structure)
  - [Commit messages](#commit-messages)
  - [Opening a pull request](#opening-a-pull-request)
  

## Code of conduct

This project adheres to the Contributor Covenant [code of conduct](CODE_OF_CONDUCT.md).
By participating, you are expected to uphold this code.
Please report unacceptable behavior to [hello@radixdlt.com](mailto:hello@radixdlt.com).

## Get started

### Package manager 

We use [yarn only](https://yarnpkg.com/lang/en/).

### Reporting a bug

* **Ensure the bug was not already reported** by searching on GitHub under [Issues](https://github.com/radixdlt/radixdlt-js/issues).
* If you're unable to find an open issue addressing the problem, [open a new one](https://github.com/radixdlt/radixdlt-js/issues/new). Be sure to include:
  * a **title**,
  * a **clear description**, 
  * as much **relevant information** as possible,
  * a **code sample** or an **executable test case** demonstrating the expected behavior that is not occurring.

## Branching strategy

### Rebasing

Rebasing should be avoided. Rebases cause potential conflicts with other people's work on the same branches, overwrite history of the project and ruin any GPG signed commits from other developers. The only time it is OK to rebase is when there is a local branch that hasn't been pushed to any remotes yet.

### Main flow

* Create a feature branch off the previous release, release candidate or dependency feature branch
* Feature branches get merged into the next release candidate branch
* When QA approves, release candidate branch is renamed to release, all merged feature branches are deleted, release becomes the new base
* Hotfixes come off the lowest affected version, then get merged downstream into every next release branch
* Feature fixes go on feature branches, then again into release candidate

### Branch types and naming

* Release candidate  - `rc/1.0.0`
* Release - `release/1.0.0`
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


### Code structure

* Single quote strings, 4 spaces indentation, no semicolons
* Use [EditorConfig plugin](https://editorconfig.org/) for your IDE to enforce consistency
* Follow tsconfig and tslint recommendations (make sure to install IDE plugins for Typescript)
* All new public methods must have a [TSDoc](https://github.com/microsoft/tsdoc) (if possible add one to old methods as well when working on them) 

### Testing

* Unit tests go next to the module being tested
* Integration tests go into `test/integration` 

### Code structure

* [Don't use default exports](https://basarat.gitbooks.io/typescript/docs/tips/defaultIsBad.html)
* Export everything that needs to be public in `src/index.ts`
* Everything in `src/modules/atommodel`  must be exported through `src/modules/atommodel/index.ts`. 
  * The `atommodel` classes should have no references to any other parts of the project. 
  * This is to avoid a circular dependency issues, and potentially split out the `atommodel` into a separate package in the future
* Here's a [good way to do singletons in JS/TS](https://k94n.com/es6-modules-single-instance-pattern). 
  * See an example in `RadixUniverse.ts`.
  

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


