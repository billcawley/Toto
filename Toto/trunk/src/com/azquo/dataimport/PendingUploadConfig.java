package com.azquo.dataimport;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/*

Consolidating a number of objects that were being passed through ImportService relating to Pending Uploads

Counter is just the index of the current end file (a workbook for example will result in a file per sheet)

 */

public class PendingUploadConfig {

    private final Map<String, Map<String, String>> parametersPerFile;
    private final Set<Integer> filesToReject;
    private final Map<Integer, Map<Integer, String>> fileRejectLines;
    private final AtomicInteger counter;
    private final String pendingDataClearCommand;

    public PendingUploadConfig(Map<String, Map<String, String>> parametersPerFile, Set<Integer> filesToReject, Map<Integer, Map<Integer, String>> fileRejectLines, String pendingDataClearCommand) {
        this.parametersPerFile = parametersPerFile;
        this.filesToReject = filesToReject;
        this.fileRejectLines = fileRejectLines;
        this.pendingDataClearCommand = pendingDataClearCommand;
        counter = new AtomicInteger(0);
    }

    public boolean isFileToReject(){
        return filesToReject.contains(counter.get());
    }

    public Map<Integer, String> getFileRejectLines() {
        return fileRejectLines.get(counter.get());
    }

    public void incrementFileCounter(){
        counter.incrementAndGet();
    }

    public Map<String, String> getParametersForFile(String name) {
        if (parametersPerFile == null){
            return null;
        }
        return parametersPerFile.get(name);
    }

    public String getPendingDataClearCommand() {
        return pendingDataClearCommand;
    }
}
