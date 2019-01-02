package com.azquo.dataimport;

import java.util.Iterator;
import java.util.List;


/*

Will process a ValuesImportConfig until it's ready to be used by Values import.

Complex stuff such as finding headings which can be in a name or names in the db or attached to the uploaded file
and then resolving these headings is done or called from here.

Headings are often found in the DB, put there by setup files, to enable importing of client files "as is".

 */


class ValuesImportConfigProcessor {

    // the idea is that a heading could be followed by successive clauses on cells below and this might be easier to read
    static boolean buildHeadingsFromVerticallyListedClauses(List<String> headers, Iterator<String[]> lineIterator) {
        String[] nextLine = lineIterator.next();
        int headingCount = 1;
        boolean lastfilled;
        while (nextLine != null && headingCount++ < 10) {
            int colNo = 0;
            lastfilled = false;
            // while you find known names, insert them in reverse order with separator |.  Then use ; in the usual order
            for (String heading : nextLine) {
                if (heading.length() > 0 && !heading.equals("--")) { //ignore "--", can be used to give space below the headers
                    if (colNo >= headers.size()) {
                        headers.add(heading);
                    } else {
                        if (heading.startsWith(".")) {
                            headers.set(colNo, headers.get(colNo) + heading);
                        } else {
                            if (headers.get(colNo).length() == 0) {
                                headers.set(colNo, heading);
                            } else {
                                if (findReservedWord(heading)) {
                                    headers.set(colNo, headers.get(colNo) + ";" + heading.trim());
                                } else {
                                    headers.set(colNo, heading.trim() + "|" + headers.get(colNo));
                                }
                            }
                        }
                    }
                    lastfilled = true;
                }
                colNo++;
            }
            if (lineIterator.hasNext() && lastfilled) {
                nextLine = lineIterator.next();
            } else {
                nextLine = null;
            }
        }
        return headingCount != 11;
    }

    private static boolean findReservedWord(String heading) {
        heading = heading.toLowerCase();
        return heading.startsWith(HeadingReader.CHILDOF)
                || heading.startsWith(HeadingReader.PARENTOF)
                || heading.startsWith(HeadingReader.ATTRIBUTE)
                || heading.startsWith(HeadingReader.LANGUAGE)
                || heading.startsWith(HeadingReader.PEERS)
                || heading.startsWith(HeadingReader.LOCAL)
                || heading.startsWith(HeadingReader.COMPOSITION)
                || heading.startsWith(HeadingReader.IGNORE)
                || heading.startsWith(HeadingReader.DEFAULT)
                || heading.startsWith(HeadingReader.NONZERO)
                || heading.startsWith(HeadingReader.REMOVESPACES)
                || heading.startsWith(HeadingReader.DATELANG)
                || heading.startsWith(HeadingReader.ONLY)
                || heading.startsWith(HeadingReader.EXCLUSIVE)
                || heading.startsWith(HeadingReader.CLEAR)
                || heading.startsWith(HeadingReader.COMMENT)
                || heading.startsWith(HeadingReader.EXISTING)
                || heading.startsWith(HeadingReader.LINEHEADING)
                || heading.startsWith(HeadingReader.LINEDATA)
                || heading.startsWith(HeadingReader.CLASSIFICATION)
                || heading.startsWith(HeadingReader.SPLIT);
    }}