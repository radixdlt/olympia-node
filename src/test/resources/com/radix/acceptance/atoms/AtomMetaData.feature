# See https://radixdlt.atlassian.net/browse/RLAU-572
@rlau-572
Feature: Atom MetaData
  As a developer,
  I want to attach arbitrary metadata to Atoms and ParticleGroups
  so that I can identify their purpose and relations to each other.

  Scenario: 1: MetaData exceeds maximum atom size
    Given that I have access to a suitable Radix network
    When I submit a valid atom with metadata exceeding max atom size 65536 bytes
    Then I should observe the atom being rejected

  Scenario: 2: MetaData is invalid JSON
    Given that I have access to a suitable Radix network
    When I submit an atom with invalid json in the metadata field
    Then I should get a deserialization error

  Scenario: 3: MetaData is wrong type
    Given that I have access to a suitable Radix network
    When I submit an atom with the metadata field as something other than a map
    Then I should get a deserialization error

