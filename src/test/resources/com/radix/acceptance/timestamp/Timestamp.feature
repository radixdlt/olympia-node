# See https://radixdlt.atlassian.net/browse/RLAU-576
@rlau-576
Feature: Timestamp
  As a developer,
  I want to attach timestamps to Atoms
  so that I can know when an Atom was submitted.

  Scenario: 1: Metadata with valid timestamp
    Given that I have access to a suitable Radix network
    When I submit a valid atom with arbitrary metadata containing a valid timestamp
    Then I should observe the atom being accepted

  Scenario: 2: Metadata with invalid timestamp
    Given that I have access to a suitable Radix network
    When I submit a valid atom with arbitrary metadata containing an invalid timestamp
    Then I should observe the atom being rejected

  Scenario: 3: Arbitrary metadata
    Given that I have access to a suitable Radix network
    When I submit a valid atom with arbitrary metadata without a valid timestamp
    Then I should observe the atom being rejected

  Scenario: 4: Empty metadata
    Given that I have access to a suitable Radix network
    When I submit a valid atom with no metadata
    Then I should observe the atom being rejected
