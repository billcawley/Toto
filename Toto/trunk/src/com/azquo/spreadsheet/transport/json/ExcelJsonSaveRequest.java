package com.azquo.spreadsheet.transport.json;

import java.util.List;

/**
 * Created by edward on 24/11/16.
 * <p>
 * Each region to be saved in a report is sent in one of these
 */
public class ExcelJsonSaveRequest {
    public int reportId;
    public String region;
    public List<List<String>> data;
    public List<List<String>> comments; // will hardly be used but it should have the same dimensions as the data
    public String context;
}
