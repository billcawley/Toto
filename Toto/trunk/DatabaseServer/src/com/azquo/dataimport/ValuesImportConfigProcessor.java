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

    static boolean buildHeadingsFromVerticallyListedClauses(List<String> headings, Iterator<String[]> lineIterator) {
        String[] nextLine = lineIterator.next();
        int headingCount = 1;
        boolean lastfilled;
        while (nextLine != null && headingCount++ < 10) {
            int colNo = 0;
            lastfilled = false;
            // while you find known names, insert them in reverse order with separator |.  Then use ; in the usual order
            for (String heading : nextLine) {
                if (heading.length() > 0 && !heading.equals("--")) { //ignore "--", can be used to give space below the headings
                    if (colNo >= headings.size()) {
                        headings.add(heading);
                    } else {
                        if (heading.startsWith(".")) {
                            headings.set(colNo, headings.get(colNo) + heading);
                        } else {
                            if (headings.get(colNo).length() == 0) {
                                headings.set(colNo, heading);
                            } else {
                                headings.set(colNo, headings.get(colNo) + ";" + heading.trim());
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
}