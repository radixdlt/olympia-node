package com.radixdlt;

/**
 * A POJO that holds info needed to create tokens
 */
public class TokenCreationProperties {

    private final String symbol;
    private final String name;
    private final String description;
    private final String iconUrl;
    private final String tokenInfoUrl;
    private final int amount;

    public TokenCreationProperties(String symbol, String name, String description, String iconUrl, String tokenInfoUrl) {
        this(symbol, name, description, iconUrl, tokenInfoUrl, 0);
    }

    public TokenCreationProperties(String symbol, String name, String description, String iconUrl, String tokenInfoUrl, int amount) {
        this.symbol = symbol;
        this.name = name;
        this.description = description;
        this.iconUrl = iconUrl;
        this.tokenInfoUrl = tokenInfoUrl;
        this.amount = amount;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public String getTokenInfoUrl() {
        return tokenInfoUrl;
    }

    public int getAmount() {
        return amount;
    }

}
