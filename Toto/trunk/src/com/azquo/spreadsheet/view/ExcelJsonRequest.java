package com.azquo.spreadsheet.view;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Created by edward on 15/11/16.
 *
 * We're now trying Excel integration again, this time by using a plugin written in C#
 *
 *             //{"rowHeadings":[["Opening balance"],["Inputs"],["Withdrawals"],["Interest"],["Closing balance"]],"columnHeadings":[["`All Months` children"]],"context":[[""]]}
 */
public class ExcelJsonRequest implements Serializable{
    public int reportId;
    public String region;
    public String optionsSource;
    public List<List<String>> rowHeadings;
    public List<List<String>> columnHeadings;
    public List<List<String>> context;
}