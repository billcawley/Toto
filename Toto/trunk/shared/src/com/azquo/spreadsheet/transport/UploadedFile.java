package com.azquo.spreadsheet.transport;

/*

Represents a file which has been uploaded and is being processed by Azquo.

Notably this represents files within files, files extracted from zip files and files generated from spreadsheets - a file for each worksheet.

 */

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UploadedFile implements Serializable {

    private final String path; // the physical location
    /*
    The initial file name needs to be stored as the on disk physical name might be mangled by temp file name suffixes etc.
    Multiple fileNames are needed as we want to store the "parent" file fileNames. So it might be Database.zip->Setup.xlsx->Sets.csv
     */
    private final List<String> fileNames;

    // parameters to be passed to the import code - can be set by users or derived from the file name

    private final Map<String, String> parameters;

    private final boolean convertedFromWorksheet;

    public UploadedFile(String path, List<String> names, Map<String, String> parameters, boolean convertedFromWorksheet) {
        this.path = path;
        // keeping immutable should avoid some nasty bugs
        this.fileNames = Collections.unmodifiableList(names);
        this.parameters = parameters != null ? Collections.unmodifiableMap(parameters) : Collections.emptyMap();
        this.convertedFromWorksheet = convertedFromWorksheet;
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
}