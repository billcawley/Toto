package com.azquo.dataimport;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.StringLiterals;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import com.azquo.spreadsheet.transport.UploadedFile;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/*
Created August 2018, it's a simple little bit of code but there's no reason it can't live in it's own class.
 */

class SetsImport {

    // typically used to create the basic name structure, an Excel set up workbook with many sheets would have a sets sheet

    static void setsImport(final AzquoMemoryDBConnection azquoMemoryDBConnection, UploadedFile uploadedFile) throws Exception {
        int lines;
        try (BufferedReader br = Files.newBufferedReader(Paths.get(uploadedFile.getPath()))) {
            // the filename can override the attribute for name creation/search. Seems a bit hacky but can make sense if the set up is a series of workbooks.
            List<String> languages;
            if (uploadedFile.getParameter(StringLiterals.LANGUAGE) != null) {
                languages = Collections.singletonList(uploadedFile.getParameter(StringLiterals.LANGUAGE));
            } else {
                // todo - zap this back to normal. When I do then zap the filename parameter and return lines
                if (uploadedFile.getFileName().length() > 4 && uploadedFile.getFileName().charAt(4) == '-') { // see if you can derive a language from the file name
                    String sheetLanguage = uploadedFile.getFileName().substring(5);
                    if (sheetLanguage.contains(".")) { // knock off the suffix if it's there. Used to be removed client side, makes more sense here
                        sheetLanguage = sheetLanguage.substring(0, sheetLanguage.lastIndexOf("."));
                    }
                    languages = Collections.singletonList(sheetLanguage);
                } else { // normal
                    languages = StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST;
                }
            }

            String line;
            lines = 0;
            // should we be using a CSV reader?
            while ((line = br.readLine()) != null) {
                String[] cells = line.split("\t"); // split does NOT return empty cells so to speak but it might return "" or a blank space
                Name set = null;
                for (String cell : cells) {
                    cell = cell.replace("\"", "").trim();
                    if (!cell.isEmpty()) { // why we're not just grabbing the first cell for the set, might be blank
                        if (set == null) { // assign it - I'm not just grabbing the first cell since there may be cells with spaces or "" at the beginning
                            set = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, cell, null, false, languages);
                            // empty it in case it existed and had children
                            set.setChildrenWillBePersisted(Collections.emptyList(), azquoMemoryDBConnection);
                        } else { // set is created or found, so start gathering children
                            set.addChildWillBePersisted(NameService.findOrCreateNameInParent(azquoMemoryDBConnection, cell, set, false, languages), azquoMemoryDBConnection);
                        }
                    }
                }
                lines++;
            }
        }
        uploadedFile.setNoLinesImported(lines);
    }
}