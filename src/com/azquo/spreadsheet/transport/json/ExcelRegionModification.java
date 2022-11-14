package com.azquo.spreadsheet.transport.json;

/*
EFC 09/08/2019

Send details of manually modified cells from the Excel javascript plugin
 */

import java.util.List;

public class ExcelRegionModification {
    public String sheet;
    public String region;
    public List<CellModification> cellModifications;

    public static class CellModification{
        public int row;
        public int col;
        public String newValue;
    }
}
