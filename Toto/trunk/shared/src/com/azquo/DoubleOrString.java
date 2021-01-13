package com.azquo;

// currently not implementing equals or hash . . .
public class DoubleOrString {
    private final Double d;
    private final String s;

    public DoubleOrString(Double d, String s) {
        this.s = s;
        this.d = d;
    }

    public String getString() {
        return s;
    }

    public Double getDouble() {
        return d;
    }
}
