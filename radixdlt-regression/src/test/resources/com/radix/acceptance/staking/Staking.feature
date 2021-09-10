Feature: Staking
  As an account owner,
  I would like to delegate stake to validators,
  And receive token emissions based on the amount staked

  Scenario: 1: Query the total delegated stake of validators
    Given I have an account with funds at a suitable Radix network
    When I request validator information
    Then I observe that validators have stakes delegated to them

  Scenario: 2: Stake tokens
    Given I have an account with funds at a suitable Radix network
    When I stake 110XRD to a validator
    #Then I observe that the validator has 110XRD more stake

  #Scenario: 3: Unstake tokens
  #  Given I have an account with funds at a suitable Radix network
  #  When I stake 110XRD to a validator
  #  And I unstake 110XRD from the same validator
  #  Then I observe that my stake is unstaked and I got my tokens back