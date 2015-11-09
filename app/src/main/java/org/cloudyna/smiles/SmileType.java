package org.cloudyna.smiles;

/**
 * Created by Michal on 02.11.15.
 */
public enum SmileType {

    LIKE("SINGLE"), LOVE("DOUBLE");

    private final String value;

    private SmileType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}

