package com.azquo.dataimport;

import java.util.List;

class LineDataWithLineNumber {

    private final List<ImportCellWithHeading> lineData;
    private final int lineNumber;

    LineDataWithLineNumber(List<ImportCellWithHeading> lineData, int lineNumber) {
        this.lineData = lineData;
        this.lineNumber = lineNumber;
    }

    List<ImportCellWithHeading> getLineData() {
        return lineData;
    }

    int getLineNumber() {
        return lineNumber;
    }
}
