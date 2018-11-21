package com.azquo.spreadsheet.transport;

/*

Created by EFC November 2018

Represents a file which has been uploaded and is being processed by Azquo.

Notably this represents files within files, files extracted from zip files and files generated from spreadsheets - a file for each worksheet.

We need much better handling of feedback from uploading. Errors, number of lines rejected, data modified or not etc.

I'm going to put this information in this class - it already contains relevant information.

Note that this means this class will be sent to the DB server via RMI and then sent back with additional information.

 */

import java.io.Serializable;
import java.util.*;

public class UploadedFile implements Serializable {

    private final String path; // the physical location
    /*
    The initial file name needs to be stored as the on disk physical name might be mangled by temp file name suffixes etc.
    Multiple fileNames are needed as we want to store the "parent" file fileNames. So it might be Database.zip->Setup.xlsx->Sets.csv
     */
    private final List<String> fileNames;

    // parameters to be passed to the import code - can be set by users or derived from the file name

    private final Map<String, String> parameters;

    // as it says - server side this can change how headers are looked up. Legacy logic that could be clarified.
    private final boolean convertedFromWorksheet;

    // how ling it took to process - I said duration as "time" is ambiguous in this context
    private long processingDuration;

    private int noLinesImported;
    private int noValuesAdjusted;
    private final ArrayList<String> linesRejected;
    private String error;
    // as in "was data modified?"
    private boolean dataModified;
    // results of execute if the file had an execute
    private String execute;
    // whether the execute was run or not - to stop executes being run twice by recursive code
    private boolean executed;
    // if the upload was a report this was the name found in the excel file
    private String reportName;

    public UploadedFile(String path, List<String> names) {
        this(path,names,null,false);
    }

    public UploadedFile(String path, List<String> names, Map<String, String> parameters, boolean convertedFromWorksheet) {
        this.path = path;
        // keeping immutable should avoid some nasty bugs
        this.fileNames = Collections.unmodifiableList(names);
        this.parameters = parameters != null ? Collections.unmodifiableMap(parameters) : Collections.emptyMap();
        this.convertedFromWorksheet = convertedFromWorksheet;
        processingDuration = 0;
        noLinesImported = 0;
        noValuesAdjusted = 0;
        linesRejected = new ArrayList<>();
        error = null;
        dataModified = false;
        execute = null;
        executed = false;
        reportName = null;
    }


    public String getPath() {
        return path;
    }

    public List<String> getFileNames() {
        return fileNames;
    }

    public String getFileNamessAsString() {
        StringBuilder sb = new StringBuilder();
        for (String name : fileNames){
            sb.append(name).append(", ");
        }
        if (sb.length() > 0){
            return sb.substring(0, sb.length() - 2);
        } else { // or null? dunno . . .
            return "";
        }
    }

    public String getFileName(){
        return getFileNames().get(fileNames.size() - 1);
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public boolean isConvertedFromWorksheet() {
        return convertedFromWorksheet;
    }

    public long getProcessingDuration() {
        return processingDuration;
    }

    public void setProcessingDuration(long processingDuration) {
        this.processingDuration = processingDuration;
    }

    public int getNoLinesImported() {
        return noLinesImported;
    }

    public void setNoLinesImported(int noLinesImported) {
        this.noLinesImported = noLinesImported;
    }

    public int getNoValuesAdjusted() {
        return noValuesAdjusted;
    }

    public void setNoValuesAdjusted(int noValuesAdjusted) {
        this.noValuesAdjusted = noValuesAdjusted;
    }

    public List<String> getLinesRejected() {
        return linesRejected;
    }

    public void addToLinesRejected(Collection<String> lines) {
        linesRejected.addAll(lines);
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean isDataModified() {
        return dataModified;
    }

    public void setDataModified(boolean dataModified) {
        this.dataModified = dataModified;
    }

    public String getExecute() {
        return execute;
    }

    public void setExecute(String execute) {
        this.execute = execute;
    }

    public boolean isExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    public void addToProcessingDuration(long convertTime) {
        processingDuration += convertTime;
    }
}