package com.azquo.dataimport;

public class LineRejectionException extends Exception {
    private final String reason;

    public LineRejectionException(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
