Feature: Staking

  As a radix user,
  I would like to delegate stake to validators,
  And observe these validators perform work based on their stake

  Scenario: 1: Query the total delegated stake of validators
    Given I have an account with funds at a suitable Radix network
    And I request the total delegated stake from all validators
    Then I can observe that validators have 100000000XRD delegated stake each

  #Scenario: 2: Staking to a validator
    #Given I have an account with funds at a suitable Radix network
    #And I stake 100000000 some tokens to validator1
    #Then I can observe that validator1 prepares more QCs than anyone else

  #Scenario: 3: Unstaking from a delegator
    #Given I have an account with funds at a suitable Radix network
