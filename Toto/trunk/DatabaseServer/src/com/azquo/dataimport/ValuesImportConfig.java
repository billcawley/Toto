package com.azquo.dataimport;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.StringLiterals;
import com.azquo.memorydb.core.Name;
import com.fasterxml.jackson.databind.MappingIterator;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/*
Gathers objects required to configure and start the import.
Originally just the iterator and headings and batch size but it will now move config information too
which should help to factor code in the ValuesImport. Set input params on this object and pass it around

Note : while some fields might be thread safe, this object is not designed to be, a single thread should pass it through numerous
adjusting functions before it's passed to ValuesImport
 */

public class ValuesImportConfig {
    private MappingIterator<String[]> originalIterator;
    private Iterator<String[]> lineIterator;
    private int batchSize;
    private List<ImmutableImportHeading> headings;
    private final AzquoMemoryDBConnection azquoMemoryDBConnection;
    private String filePath; // grovvy can override
    private final String fileName;
    private final String fileSource;
    private final Map<String, String> fileNameParameters;
    private boolean isSpreadsheet;
    private final AtomicInteger valuesModifiedCounter;
    private List<String> languages;
    private String importAttribute;
    private Name importInterpreter;
    private Map<String, String> assumptions;
    private List<String> headers;
    private int skipLines;
    private boolean clearData;
    // held for Ed broking stuff, might be factored in a mo
    private final Map<String, String> topHeadings;
    private int lineCells = 0;


    // exactly the same as is passed to valuesImport, no coincidence. Set these in this object then pass the object through various processes until it's ready to go
    ValuesImportConfig(AzquoMemoryDBConnection azquoMemoryDBConnection, String filePath, String fileName, String fileSource, Map<String, String> fileNameParameters, boolean isSpreadsheet, AtomicInteger valuesModifiedCounter, boolean clearData) {
        this.azquoMemoryDBConnection = azquoMemoryDBConnection;
        this.filePath = filePath;
        this.fileName = fileName;
        this.fileSource = fileSource;
        this.fileNameParameters = fileNameParameters;
        this.valuesModifiedCounter = valuesModifiedCounter;
        this.isSpreadsheet = isSpreadsheet;
        languages = StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST;
        importAttribute = null;
        importInterpreter = null;
        assumptions = null;
        headers = new ArrayList<>();
        skipLines = 0;
        this.clearData = clearData;
        topHeadings = new HashMap<>();
    }

    MappingIterator<String[]> getOriginalIterator() {
        return originalIterator;
    }

    void setOriginalIterator(MappingIterator<String[]> originalIterator) {
        this.originalIterator = originalIterator;
    }

    Iterator<String[]> getLineIterator() {
        return lineIterator;
    }

    void setLineIterator(Iterator<String[]> lineIterator) {
        this.lineIterator = lineIterator;
    }

    int getBatchSize() {
        return batchSize;
    }

    void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public List<ImmutableImportHeading> getHeadings() {
        return headings;
    }

    public void setHeadings(List<ImmutableImportHeading> headings) {
        this.headings = headings;
    }

    public AzquoMemoryDBConnection getAzquoMemoryDBConnection() {
        return azquoMemoryDBConnection;
    }

    String getFilePath() {
        return filePath;
    }

    void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    String getFileName() {
        return fileName;
    }

    String getFileSource() {return fileSource; }

    public Map<String, String> getFileNameParameters() {
        return fileNameParameters;
    }

    public boolean isSpreadsheet() {
        return isSpreadsheet;
    }

    public void setSpreadsheet(boolean spreadsheet) {
        isSpreadsheet = spreadsheet;
    }

    AtomicInteger getValuesModifiedCounter() {
        return valuesModifiedCounter;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    String getImportAttribute() {
        return importAttribute;
    }

    void setImportAttribute(String importAttribute) {
        this.importAttribute = importAttribute;
    }

    Name getImportInterpreter() {
        return importInterpreter;
    }

    void setImportInterpreter(Name importInterpreter) {
        this.importInterpreter = importInterpreter;
    }

    Map<String, String> getAssumptions() {
        return assumptions;
    }

    void setAssumptions(Map<String, String> assumptions) {
        this.assumptions = assumptions;
    }

    List<String> getHeaders() {
        return headers;
    }

    void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    int getSkipLines() {
        return skipLines;
    }

    void setSkipLines(int skipLines) {
        this.skipLines = skipLines;
    }

    boolean getClearData() { return clearData; }


    Map<String, String> getTopHeadings() {
        return topHeadings;
    }

    int getLineCells() {return lineCells; }

    void setLineCells(int lineCells) {this.lineCells = lineCells; }
}
