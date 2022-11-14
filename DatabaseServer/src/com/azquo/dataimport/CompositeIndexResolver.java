package com.azquo.dataimport;

import java.util.Map;

class CompositeIndexResolver {

    // see DSImportService where these are assigned
    private final Map<String, Integer> fileHeadingCompositeLookup;
    private final Map<String, Integer> interimCompositeLookup;
    private final Map<String, Integer> azquoHeadingCompositeLookup;

    CompositeIndexResolver(Map<String, Integer> fileHeadingCompositeLookup, Map<String, Integer> interimCompositeLookup, Map<String, Integer> azquoHeadingCompositeLookup) {
        this.fileHeadingCompositeLookup = fileHeadingCompositeLookup;
        this.interimCompositeLookup = interimCompositeLookup;
        this.azquoHeadingCompositeLookup = azquoHeadingCompositeLookup;
    }

    // is there a performance concern if this is really hammered?
    int getColumnIndexForHeading(String headingName){
        if (fileHeadingCompositeLookup.get(headingName.toUpperCase()) != null) {
            return fileHeadingCompositeLookup.get(headingName.toUpperCase());
        }
        if (interimCompositeLookup.get(headingName.toUpperCase()) != null) {
            return interimCompositeLookup.get(headingName.toUpperCase());
        }
        if (azquoHeadingCompositeLookup.get(headingName.toUpperCase()) != null) {
            return azquoHeadingCompositeLookup.get(headingName.toUpperCase());
        }
        return -1;
    }
}