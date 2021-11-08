Feature: Token constraints
  As a token trader,
  I would like token parameters to be constrained
  So that identifying and referring to tokens won't be confusing

  Scenario: 1: Token symbol constraints
    Given I have an account with funds at a suitable Radix network
    Then I cannot create a token with symbol 'symbol!'
    And  I cannot create a token with symbol 'symbol_one'
    And I cannot create a token with symbol '@wesome'
    Then I cannot create a token with symbol 'my token'

  Scenario: 2: Token url constraints
    Given I have an account with funds at a suitable Radix network
    Then I cannot create a token with token info url 'not-a-url.com'
    And I cannot create a token with an icon rul 'www.not-a-url'