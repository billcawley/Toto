package com.azquo.dataimport;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import org.springframework.util.StringUtils;

import java.util.*;

class EdBrokingExtension {

    /*
    Info from WFC e-mail about changes to this function where it can have versions of headers, the ability to override defaults as required :

     The main change in the import process is to allow the import headings to be stored against a 'name' with the same name as the heading,
      these headings being stored as children of the 'Dataimport' name.

i.e.   instead of

'dataimport <importname>' having an attribute 'HEADINGS': <heading1>;<heading1 clauses>¬<heading2>;<heading2 clauses> ....

we have
     'dataimport <importname>
            <heading1>. <importname> HEADING <heading1 clauses>
            <heading2>. <importname> HEADING <heading2 clauses>

This is complicated, from necessity.  The purpose is to allow the same data to be imported from many different sources
(different attributes stored against the individual heading names) so that a new source will be relatively easy to script

You can check this by looking at the 'dataimport' variables on the 'risk' database in edbroking.

<heading>  topheading

this heading will appears as a field pair above the usual heading line

e.g.   Month Jul-18

Heading1   Heading2   Heading3 ....


EFC note . . . it seems code is being added to support Ed Broking. If this code is too specific it should be done by groovy perhaps . . .
The issue here it seems is that Ed Broking import the same data from different sources, the data might have different column names in some cases
So to clarify, we might have a name in "All Import Sheets", "DATAIMPORT Risk". But instead of this having an attribute with the headers it has children.
For example "Contract Reference". "Contract Reference" has attributes. "HEADINGS RISK" is the default but there may also be "RLD" or "HISCOX" or another derived from the zip file name.
The value of this attribute is equivalent to the old heading - it is a name followed by clauses. Maybe just the name e.g. "Carrier" (meaning in the actual import file the heading is "Carrier")
or a name of the heading in the import file followed by semi-colons and clauses or composition where there is no data,in the import file, the column in generated

additional note - having discussed with WFC whether this more complex system is required is a bit contentious. If it can be factored off it's less of a concern
     */


    static List<String> checkForImportNameChildren(AzquoMemoryDBConnection azquoMemoryDBConnection, List<String> headersIn, Name importInterpreter, String importAttribute, List<String> languages, HeadingsWithIteratorAndBatchSize lineIteratorAndBatchSize, Map<String, String> topHeadings) throws Exception {
        List<String> headersOut = headersIn; // at least initially!
        // ok so we may have headings as simply saved in the database. Now check more complicated definition added for Ed Broking.
        // rather than a simple attribute against the import interpreter it may have children
        int headingLineCount = 1;
        Set<Name> topHeadingNames = new HashSet<>();
        if (importInterpreter != null && importInterpreter.hasChildren()) {//check for top headers
            //CHECK FOR CONVERSION :   PERMISSIBLE IN '.<language name> is <header name> additional header info
            // this converts to header name in this attribute (without brackets), together with additional header info in the attribute (importInterpreter.getDefaultDisplayName() + " " + <language>
            // EFC note - it does but I don't yet know why!
            checkImportChildrenForConversion(importInterpreter, importAttribute, languages.get(0));
            // so now go through the names, this is like a pre scan, find the line count and look for HeadingReader.TOPHEADING though I need to know what that means
            for (Name name : importInterpreter.getChildren()) {
                // if we take policy no as an example, there's "HEADINGS RISK" (the importAttribute) which is "required" and "RLD" (an example of a language) as "Policy #"
                // of course the language might not have any entry, languageName being null
                String interpretation = name.getAttribute(importAttribute);
                String languageName = name.getAttribute(languages.get(0));
                if (languages.size() == 2) { // so zip prefix e.g. Risk and the default name. Can we be in here without zip prefix??
                    if (languageName != null) {
                        // so the pipe . . . it might be as in the case of "WS Limit" in Hiscox "Wind|Limit"
                        int thisHeadingLineCount = StringUtils.countOccurrencesOf(languageName, "|") + 1; // there's one more than the number of pipes
                        if (thisHeadingLineCount > headingLineCount) {
                            headingLineCount = thisHeadingLineCount;
                        }
                    }
                    String localInterpretation = name.getAttribute(importAttribute + " " + languages.get(0)); // so this was the extra bit that might have been added
                    if (localInterpretation != null) {
                        if (interpretation == null) { // meaning it will be local interpretation twice?? Check with WFC todo
                            interpretation = localInterpretation;
                        }
                        interpretation += ";" + localInterpretation;
                    }
                }
                // it seems to be only about gathering the topheadings though whatever they are
                if (interpretation != null && interpretation.toLowerCase().contains(HeadingReader.TOPHEADING)) {
                    topHeadingNames.add(name);
                }
            }
            int lineNo = 0;
            /* ok so go through the first 20 lines of the file assuming some top headings were found (only relevant for the Ed Broking style names)
             the key to this is that the tio names are apparently in pairs, if this is always so then the code here shuld be changed to be clearer
             so you have
             key1 value3
             key2 value3
             key3 value3
             And these keys are added as headings with the values being the default values an example is  <Coverholder:> topheading

             OK, I now understand the purpose

             Some import files will have things like
             Coverholder: Joe Bloggs
             Contract ref: ABC123

            This jams them as columns at the end with a default value
             */
            while (topHeadingNames.size() > 0 && lineNo < 20 && lineIteratorAndBatchSize.lineIterator.hasNext()) {
                String lastHeading = null;
                headersOut = getNextLine(lineIteratorAndBatchSize);
                for (String header : headersOut) {
                    if (lastHeading != null) { // so only if there was a previous heading which is in the db and top headings then add this heading as a value with the previous heading as a key in the map and zap the last heading from the topHeadingNames . .
                        Name headingName = NameService.findByName(azquoMemoryDBConnection, lastHeading, languages);
                        if (headingName != null && topHeadingNames.contains(headingName)) {
                            topHeadings.put(lastHeading, header);
                            topHeadingNames.remove(headingName);
                        }
                    }
                    lastHeading = header;
                }
                lineNo++;
            }
            if (lineNo++ < 20 && lineIteratorAndBatchSize.lineIterator.hasNext()) {
                headersOut = getNextLine(lineIteratorAndBatchSize);

            }
            //looking for something in column A, there may be a gap after things like Coverholder: Joe Bloggs
            while (lineNo < 20 && (headersOut.size() == 0 || headersOut.get(0).length() == 0) && lineIteratorAndBatchSize.lineIterator.hasNext()) {
                headersOut = getNextLine(lineIteratorAndBatchSize);
            }
            if (headingLineCount > 1) {
                buildHeadersFromVerticallyListedNames(headersOut, lineIteratorAndBatchSize.lineIterator, headingLineCount - 1);
            }

            if (lineNo == 20 || !lineIteratorAndBatchSize.lineIterator.hasNext()) {
                return null;//TODO   notify that headings are not found.
            }
        }
        return headersOut;
    }

    // for the more complex import header resolution for Ed Broking, children of importInterpreter rather than a single attribute
    // if it's a simple <Policy #> for example it simply gets set back in without the <> it seems BUT
    // if there's something after > e,g, "<Policy Type> language NEWRENEWAL" being the value of "RLD" attribute  in the name "Transaction Type" then
    // "RLD" is set to "Policy Type" and a new attribute "HEADINGS RISK RLD" is set with the value "language NEWRENEWAL". Yikes.

    private static void checkImportChildrenForConversion(Name importInterpreter, String importAttribute, String language) throws Exception {
        boolean toBeConverted = false;
        for (Name importField : importInterpreter.getChildren()) {
            String existingName = importField.getAttribute(language);
            // so in the language of this file name (probably set due to the zip file's name . . .) we have an attribute of teh format <something> . . . we want to convert this
            if (existingName != null && existingName.startsWith("<") && existingName.contains(">")) {
                toBeConverted = true;
                break;
            }
        }
        if (toBeConverted) {
            // EFC -the local version as referenced in other bits of the code. Still not completely sure what that means!
            importAttribute = importAttribute + " " + language;
            for (Name importField : importInterpreter.getChildren()) {
                String existingName = importField.getAttribute(language);
                if (existingName != null) {
                    String newName = "";
                    String newHeadingAttributes = existingName;
                    if (existingName.startsWith("<")) {
                        int nameEndPos = existingName.indexOf(">");
                        if (nameEndPos > 0) {
                            newName = existingName.substring(1, nameEndPos).trim();
                            newHeadingAttributes = existingName.substring(nameEndPos + 1).trim();
                        }
                    }
                    importField.setAttributeWillBePersisted(language, newName);
                    // often it seems newHeadingAttributes will be empty and hence no attribute will be created
                    importField.setAttributeWillBePersisted(importAttribute, newHeadingAttributes);
                }
            }
        }
    }

    // two new functions added by WFC, need to check them
    static List<String> getNextLine(HeadingsWithIteratorAndBatchSize lineIterator) {
        List<String> toReturn = new ArrayList<>();
        toReturn.addAll(Arrays.asList(lineIterator.lineIterator.next()));
        return toReturn;
    }

    // new WFC function relevant to Ed Broking and import headings by child names
    // a syntax check that if something says required it must have a fallback of composition or topheading or default?

    static void checkRequiredHeadings(AzquoMemoryDBConnection azquoMemoryDBConnection, List<String> headers, Name importInterpreter, Name assumptions, List<String> languages) throws Exception {
        if (importInterpreter == null || !importInterpreter.hasChildren()) return;
        List<String> defaultNames = new ArrayList<>();
        for (String header : headers) {
            Name name = NameService.findByName(azquoMemoryDBConnection, header, languages);
            if (name != null) {
                defaultNames.add(name.getDefaultDisplayName());
            } else {
                defaultNames.add(header);
            }
        }
        String importAttribute = importInterpreter.getDefaultDisplayName().replace("DATAIMPORT", "HEADINGS"); // this keeps being done! Factor properly, todo
        for (Name name : importInterpreter.getChildren()) {
            // not attribute but composite attributes. So it will look for the "base" and the second as created by checkImportChildrenForConversion e.g. "HEADINGS RISK RLD".
            // I can't see any of the second which might be relevant with "required" but I imagine there will be some from the first
            // for example Policy Reference.Contract Reference;required
            String attribute = HeadingReader.getCompositeAttributes(name, importAttribute, importAttribute + " " + languages.get(0));
            if ((assumptions == null || assumptions.getAttribute(name.getDefaultDisplayName()) == null) && attribute != null) { //if there's an assumption then no need to check required.
                boolean required = false;
                boolean composition = false;
                String[] clauses = attribute.split(";");
                for (String clause : clauses) {
                    if (clause.toLowerCase().startsWith("required")) {
                        required = true;
                    }
                    if (clause.toLowerCase().startsWith("composition")) {
                        composition = true;
                    }
                    if (!defaultNames.contains(name.getDefaultDisplayName()) && required) {
                        if (composition) {
                            headers.add(name.getDefaultDisplayName());
                            defaultNames.add(name.getDefaultDisplayName());
                        } else {
                            //check both the general and specific import attributes
                            // edd commented attribute2, it's the same as attribute
                            //String attribute2 = NameService.getCompositeAttributes(name, importAttribute, importAttribute + " " + languages.get(0));
                            if (/* cannot be null! attribute2 == null || (*/
                                    !attribute.toLowerCase().contains(HeadingReader.DEFAULT)
                                            && !attribute.toLowerCase().contains(HeadingReader.COMPOSITION)
                                            && !attribute.toLowerCase().contains(HeadingReader.TOPHEADING)) {
                                throw new Exception("headers missing required header: " + name.getDefaultDisplayName());
                            } else {
                                headers.add(name.getDefaultDisplayName());//maybe a problem if there is another name in the given language
                                defaultNames.add(name.getDefaultDisplayName());
                            }
                        }
                    }
                }
            }
        }
    }

    private static void buildHeadersFromVerticallyListedNames(List<String> headers, Iterator<String[]> lineIterator, int lineCount) {
        String[] nextLine = lineIterator.next();
        boolean lastfilled;
        while (nextLine != null && lineCount-- > 0) {
            int colNo = 0;
            lastfilled = false;
            // while you find known names, insert them in reverse order with separator |.  Then use ; in the usual order
            String lastHeading = null;
            for (String heading : nextLine) {
                if (heading.length() > 0 && !heading.equals("--") && colNo < headers.size()) { //ignore "--", can be used to give space below the headers
                    if (heading.startsWith(".")) {
                        headers.set(colNo, headers.get(colNo) + heading);
                    } else {
                        if (headers.get(colNo).length() == 0) {
                            int lastSplit = lastHeading.lastIndexOf("|");
                            if (lastSplit > 0) {
                                headers.set(colNo, lastHeading.substring(0, lastSplit + 1) + heading);
                            } else {
                                headers.set(colNo, lastHeading + "|" + heading);
                            }
                        } else {
                            headers.set(colNo, headers.get(colNo) + "|" + heading);
                        }
                    }
                    lastfilled = true;
                }
                lastHeading = headers.get(colNo);
                colNo++;
            }
            if (lineIterator.hasNext() && lastfilled) {
                nextLine = lineIterator.next();
            } else {
                nextLine = null;
            }
        }
//        return lineCount ==  0;
    }

}
