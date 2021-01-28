# See: https://radixdlt.atlassian.net/browse/RPNV1-186
@rpnv1-186
Feature: Mempool to reject invalid atoms
  As a token buyer,
  I would like the Radix mempool to reject atoms that it can prove are invalid
  So that the network is not overburdened with invalid transactions

  Scenario: 1: Submitting illegal atom
    Given I have access to a suitable Radix network
    When I submit an illegal atom
    Then I will receive an event letting me know the atom was not added to the mempool because it is illegal

  Scenario: 2: Submitting conflicting atom
    Given I have access to a suitable Radix network
    When I submit an atom conflicting with an atom already committed
    Then I will receive an event letting me know the atom was not added to the mempool because it is conflicting
