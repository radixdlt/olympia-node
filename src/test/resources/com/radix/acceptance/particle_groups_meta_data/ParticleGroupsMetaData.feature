# See https://radixdlt.atlassian.net/browse/RLAU-572
@rlau-572
Feature: Particle Groups MetaData
  As a developer,
  I want to attach arbitrary metadata to Atoms and ParticleGroups
  so that I can identify their purpose and relations to each other.


  Scenario: 1: Normal MetaData
    Given that I have access to a suitable Radix network
    When I submit an atom with particle groups which have some arbitrary metadata
    Then I should observe the atom being accepted

  Scenario: 2: No MetaData
    Given that I have access to a suitable Radix network
    When I submit an atom with particle groups which have no metadata
    Then I should observe the atom being accepted

  Scenario: 3: MetaData exceeds maximum atom size
    Given that I have access to a suitable Radix network
    When I submit an atom with particle groups which have metadata exceeding the max allowed atom size 65536 bytes
    Then I should observe the atom being rejected
