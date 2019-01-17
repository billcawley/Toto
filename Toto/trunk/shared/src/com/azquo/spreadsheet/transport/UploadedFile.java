package com.azquo.spreadsheet.transport;

/*

Created by EFC November 2018

Represents a file which has been uploaded and is being processed by Azquo.

Notably this represents files within files, files extracted from zip files and files generated from spreadsheets - a file for each worksheet.

We need much better handling of feedback from uploading. Errors, number of lines rejected, data modified or not etc.

I'm going to put this information in this class - it already contains relevant information.

Note that this means this class will be sent to the DB server via RMI and then sent back with additional information.

New import template functionality means information extracted from the import template is going in here too before it is sent to the database server
 */

import com.azquo.StringLiterals;
import com.azquo.TypedPair;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class UploadedFile implements Serializable {

    private String path; // the physical location
    /*
    The initial file name needs to be stored as the on disk physical name might be mangled by temp file name suffixes etc.
    Multiple fileNames are needed as we want to store the "parent" file fileNames. So it might be Database.zip->Setup.xlsx->Sets.csv
     */
    private final List<String> fileNames;

    // parameters to be passed to the import code - can be set by users or derived from the file name

    private Map<String, String> parameters;

    // as it says - server side this can change how headings are looked up. Legacy logic that could be clarified.
    private boolean convertedFromWorksheet;

    // how ling it took to process - I said duration as "time" is ambiguous in this context
    private long processingDuration;

    private int noLinesImported;
    private final AtomicInteger noValuesAdjusted;
    private final ArrayList<String> linesRejected;
    private String error;
    // as in "was data modified?"
    private boolean dataModified;
    // if the upload was a report this was the name found in the excel file
    private String reportName;
    // was it an import template?
    private boolean importTemplate;

    private int skipLines;

    private String preProcessor;
    private String additionalDataProcessor;
    private String postProcessor;
    // result of the execute from postProcessor
    private String postProcessingResult;
    private String fileEncoding;
    // heading definitions. At its most simple it would be a list of strings but it can be a lookup based on file headings and there could be multiple headingss so
    private List<String> simpleHeadings;

    // not required for importing to work but a copy of the headings we found on the file can dramatically improve feedback to the user
    private List<List<String>> fileHeadings;

    /* more complex - list of strings for lookup as sometimes the headings are double decker or more so to speak
     OK, so there are two strings in the values. The first is the straight value, the heading as it will be used by Azquo, the second is optional, the interim lookup
     where the import sheet had modes. E.g. Allrisks mode defined Address1 as the file heading linked to Risk Address 1 on the main lookup sheet which in turn had the
      Azquo clauses. The interim  Risk Address 1 wasn't being sent to the database server as I thought it irrelevant BUT it can be referenced in compositions
      Also it might not be the worst thing for users to see as feedback on the import
     */
    private Map<List<String>, TypedPair<String, String>> headingsByFileHeadingsWithInterimLookup;

    // ok so the above is where the headings have headings on the file to reference but there might be quite a few headings e.g. with defaults or composite with no
    // file headings. They go in here.
    private List<TypedPair<String, String>> headingsNoFileHeadingsWithInterimLookup;
    // ok, so, we need to resolve composite on the report server and send it over which means we need a way to reference headings

    // top headings will be the location on the sheet and the name of a value to check for or a value to put aside for the import
    // as in "do we have 'Cover Note' in a given cell" vs "take the value in a given cell and store it under 'Cover Note' to use later"
    // the latter signified by quotes
    // row + col starting index 0. Need to think a little about what to do if they're not found
    private Map<TypedPair<Integer, Integer>, String> topHeadings;
    // languages now set in the template or perhaps overriden by parameters - not sure how necessary this will be
    private List<String> languages;
    // the provenanceId attached to the data in the file
    private int provenanceId;

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
        noValuesAdjusted = new AtomicInteger(0);
        linesRejected = new ArrayList<>();
        error = null;
        dataModified = false;
        reportName = null;
        importTemplate = false;

        skipLines = 0;

        preProcessor = null;
        additionalDataProcessor = null;
        postProcessor = null;
        postProcessingResult = null;
        fileEncoding = null;
        simpleHeadings = null;
        fileHeadings = null;
        headingsByFileHeadingsWithInterimLookup = null;
        topHeadings = null;
        languages = StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST;
        headingsNoFileHeadingsWithInterimLookup = null;
        provenanceId = -1;
    }


    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public boolean isConvertedFromWorksheet() {
        return convertedFromWorksheet;
    }

    public void setConvertedFromWorksheet(boolean convertedFromWorksheet) {
        this.convertedFromWorksheet = convertedFromWorksheet;
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

    public AtomicInteger getNoValuesAdjusted() {
        return noValuesAdjusted;
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

    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    public int getSkipLines() {
        return skipLines;
    }

    public void setSkipLines(int skipLines) {
        this.skipLines = skipLines;
    }

    public boolean isImportTemplate() {
        return importTemplate;
    }

    public void setImportTemplate(boolean importTemplate) {
        this.importTemplate = importTemplate;
    }

    public void addToProcessingDuration(long convertTime) {
        processingDuration += convertTime;
    }

    public String getParameter(String key) {
        if (parameters != null && key != null) { // not sure if parameters can be null? not a biggy . . .
            return parameters.get(key.toLowerCase());
        }
        return null;
    }

    public String getPreProcessor() {
        return preProcessor;
    }

    public void setPreProcessor(String preProcessor) {
        this.preProcessor = preProcessor;
    }

    public String getAdditionalDataProcessor() {
        return additionalDataProcessor;
    }

    public void setAdditionalDataProcessor(String additionalDataProcessor) {
        this.additionalDataProcessor = additionalDataProcessor;
    }

    public String getPostProcessor() {
        return postProcessor;
    }

    public void setPostProcessor(String postProcessor) {
        this.postProcessor = postProcessor;
    }

    public String getPostProcessingResult() {
        return postProcessingResult;
    }

    public void setPostProcessingResult(String postProcessingResult) {
        this.postProcessingResult = postProcessingResult;
    }

    public String getFileEncoding() {
        return fileEncoding;
    }

    public void setFileEncoding(String fileEncoding) {
        this.fileEncoding = fileEncoding;
    }

    public List<String> getSimpleHeadings() {
        return simpleHeadings;
    }

    public void setSimpleHeadings(List<String> simpleHeadings) {
        this.simpleHeadings = simpleHeadings;
    }

    public List<List<String>> getFileHeadings() {
        return fileHeadings;
    }

    public void setFileHeadings(List<List<String>> fileHeadings) {
        this.fileHeadings = fileHeadings;
    }

    public Map<List<String>, TypedPair<String, String>> getHeadingsByFileHeadingsWithInterimLookup() {
        return headingsByFileHeadingsWithInterimLookup;
    }

    public void setHeadingsByFileHeadingsWithInterimLookup(Map<List<String>, TypedPair<String, String>> headingsByFileHeadingsWithInterimLookup) {
        this.headingsByFileHeadingsWithInterimLookup = headingsByFileHeadingsWithInterimLookup;
    }

    public List<TypedPair<String, String>> getHeadingsNoFileHeadingsWithInterimLookup() {
        return headingsNoFileHeadingsWithInterimLookup;
    }

    public void setHeadingsNoFileHeadingsWithInterimLookup(List<TypedPair<String, String>> headingsNoFileHeadingsWithInterimLookup) {
        this.headingsNoFileHeadingsWithInterimLookup = headingsNoFileHeadingsWithInterimLookup;
    }

    public Map<TypedPair<Integer, Integer>, String> getTopHeadings() {
        return topHeadings;
    }

    public void setTopHeadings(Map<TypedPair<Integer, Integer>, String> topHeadings) {
        this.topHeadings = topHeadings;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    public int getProvenanceId() {
        return provenanceId;
    }

    public void setProvenanceId(int provenanceId) {
        this.provenanceId = provenanceId;
    }
}