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
