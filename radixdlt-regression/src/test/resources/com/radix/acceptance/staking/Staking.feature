Feature: Staking
  As a radix user,
  I would like to delegate stake to validators,
  And receive token emissions based on the amount staked

  Scenario: 1: Query the total delegated stake of validators
    Given I have an account with funds at a suitable Radix network
    When I request validator information
    Then I observe that validators have stakes delegated to them

  Scenario: 2: Stake tokens
    Given I have an account with funds at a suitable Radix network
    When I stake 5XRD to a validator
    Then I observe that validator having 5XRD more stake