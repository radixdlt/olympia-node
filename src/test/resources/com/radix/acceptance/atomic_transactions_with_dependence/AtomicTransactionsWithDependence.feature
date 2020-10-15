# See: https://radixdlt.atlassian.net/browse/RLAU-645
@rlau-645
Feature: Atomic Transactions with Stateful Dependencies
  As a Library developer
  I would like to execute dependent actions in an atomic fashion
  So that I can create functionality dependent on the atomic execution of multiple dependent actions

  Scenario: 1: Dependent Particle in Particle Group "After" Dependency
    Given I have access to a suitable Radix network
    When I submit a particle group spending a consumable that was created in a group with a lower index
    Then I can observe atom 1 being accepted

  Scenario: 2: Dependent Particle in Particle Group "Before" Dependency
    Given I have access to a suitable Radix network
    When I submit a particle group spending a consumable that was created in a group with a higher index
    Then I can observe atom 1 being rejected with a validation error
