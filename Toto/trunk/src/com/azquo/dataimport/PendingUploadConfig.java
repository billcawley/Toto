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
    private final Map<Integer, java.util.Set<Integer>> fileRejectLines;
    private final AtomicInteger counter;

    public PendingUploadConfig(Map<String, Map<String, String>> parametersPerFile, Set<Integer> filesToReject, Map<Integer, Set<Integer>> fileRejectLines) {
        this.parametersPerFile = parametersPerFile;
        this.filesToReject = filesToReject;
        this.fileRejectLines = fileRejectLines;
        counter = new AtomicInteger(0);
    }

    public boolean isFileToReject(){
        return filesToReject.contains(counter.get());
    }

    public Set<Integer> getFileRejectLines() {
        return fileRejectLines.get(counter.get());
    }

    public void incrementFileCounter(){
        counter.incrementAndGet();
    }

    public Map<String, String> getParametersForFile(String name) {
        return parametersPerFile.get(name);
    }
}
