package com.azquo.spreadsheet.transport.json;

import java.io.Serializable;
import java.util.List;

/**
 * Created by edward on 15/11/16.
 * <p>
 * We're now trying Excel integration again, this time by using a plugin written in C#
 * <p>
 * //{"rowHeadings":[["Opening balance"],["Inputs"],["Withdrawals"],["Interest"],["Closing balance"]],"columnHeadings":[["`All Months` children"]],"context":[[""]]}
 */
public class ExcelJsonRequest implements Serializable {
    public int reportId;
    public String sheetName; // hopefully ok if not passed
    public String region;
    public String optionsSource;
    public List<List<String>> rowHeadings;
    public List<List<String>> columnHeadings;
    public List<List<String>> context;
}
