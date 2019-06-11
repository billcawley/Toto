package com.azquo.dataimport;

import java.util.List;

public class LineDataWithLineNumber {

    private final List<ImportCellWithHeading> lineData;
    private final int lineNumber;

    public LineDataWithLineNumber(List<ImportCellWithHeading> lineData, int lineNumber) {
        this.lineData = lineData;
        this.lineNumber = lineNumber;
    }

    public List<ImportCellWithHeading> getLineData() {
        return lineData;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}
