package com.azquo.dataimport;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.StringLiterals;
import com.azquo.memorydb.core.Name;
import com.azquo.spreadsheet.transport.UploadedFile;
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
    private UploadedFile uploadedFile; // groovy can override the path
    private final AtomicInteger valuesModifiedCounter;
    private List<String> languages;
    private String importAttribute;
    private Name importInterpreter;
    private Map<String, String> assumptions;
    private List<String> headers;
    private int skipLines;
    // held for Ed broking stuff, might be factored in a mo
    private final Map<String, String> topHeadings;
    private int lineCells = 0;


    // exactly the same as is passed to valuesImport, no coincidence. Set these in this object then pass the object through various processes until it's ready to go
    ValuesImportConfig(AzquoMemoryDBConnection azquoMemoryDBConnection, UploadedFile uploadedFile, AtomicInteger valuesModifiedCounter) {
        this.azquoMemoryDBConnection = azquoMemoryDBConnection;
        this.uploadedFile = uploadedFile;
        this.valuesModifiedCounter = valuesModifiedCounter;
        languages = StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST;
        importAttribute = null;
        importInterpreter = null;
        assumptions = null;
        headers = new ArrayList<>();
        skipLines = 0;
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

    public UploadedFile getUploadedFile() {
        return uploadedFile;
    }

    public void setUploadedFile(UploadedFile uploadedFile) {
        this.uploadedFile = uploadedFile;
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

    Map<String, String> getTopHeadings() {
        return topHeadings;
    }

    int getLineCells() {return lineCells; }

    void setLineCells(int lineCells) {this.lineCells = lineCells; }
}
