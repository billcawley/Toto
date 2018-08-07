package com.azquo.dataimport;

import com.azquo.memorydb.Constants;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import org.springframework.util.StringUtils;

import java.util.*;

/*

Code written by WFC for Ed Broking, extracted/factored to here by EFC, modified to use the ValuesImportConfig object

 */


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


    static void checkForImportNameChildren(ValuesImportConfig valuesImportConfig) throws Exception {
        Name importInterpreter = valuesImportConfig.getImportInterpreter();
        String importAttribute = valuesImportConfig.getImportAttribute();
        List<String> languages = valuesImportConfig.getLanguages();
        List<String> headersOut = null;
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
            while (topHeadingNames.size() > 0 && lineNo < 20 && valuesImportConfig.getLineIterator().hasNext()) {
                String lastHeading = null;
                headersOut = ValuesImportConfigProcessor.getNextLine(valuesImportConfig);
                for (String header : headersOut) {
                    if (lastHeading != null) { // so only if there was a previous heading which is in the db and top headings then add this heading as a value with the previous heading as a key in the map and zap the last heading from the topHeadingNames . .
                        Name headingName = NameService.findByName(valuesImportConfig.getAzquoMemoryDBConnection(), lastHeading, languages);
                        if (headingName != null && topHeadingNames.contains(headingName)) {
                            valuesImportConfig.getTopHeadings().put(lastHeading, header);
                            topHeadingNames.remove(headingName);
                        }
                    }
                    lastHeading = header;
                }
                lineNo++;
            }
            if (lineNo++ < 20 && valuesImportConfig.getLineIterator().hasNext()) {
                headersOut = ValuesImportConfigProcessor.getNextLine(valuesImportConfig);
            }
            //looking for something in column A, there may be a gap after things like Coverholder: Joe Bloggs
            while (lineNo < 20 && (headersOut.size() == 0 || headersOut.get(0).length() == 0) && valuesImportConfig.getLineIterator().hasNext()) {
                headersOut = ValuesImportConfigProcessor.getNextLine(valuesImportConfig);
            }
            if (headingLineCount > 1) {
                buildHeadersFromVerticallyListedNames(headersOut, valuesImportConfig.getLineIterator(), headingLineCount - 1);
            }

            if (lineNo == 20 || !valuesImportConfig.getLineIterator().hasNext()) {
                return;//TODO   notify that headings are not found.
            }
        }
        if (headersOut != null){
            valuesImportConfig.setHeaders(headersOut);
        }
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

    // new WFC function relevant to Ed Broking and import headings by child names
    // a syntax check that if something says required it must have a fallback of composition or topheading or default?

    static void checkRequiredHeadings(ValuesImportConfig valuesImportConfig) throws Exception {
        Name importInterpreter = valuesImportConfig.getImportInterpreter();
        List<String> headers = valuesImportConfig.getHeaders();
        List<String> languages = valuesImportConfig.getLanguages();
        // so we record the original size then jam all the top headings on to the end . . .
        // is there a reason this and the adding of default isn't done above? Does it need the checkRequiredHeadings and preProcessHeadersAndCreatePivotSetsIfRequired
        for (String topHeadingKey : valuesImportConfig.getTopHeadings().keySet()){
            headers.add(topHeadingKey + ";default " + valuesImportConfig.getTopHeadings().get(topHeadingKey));
        }
        if (importInterpreter == null || !importInterpreter.hasChildren()) return;
        List<String> defaultNames = new ArrayList<>();
        for (String header : headers) {
            Name name = NameService.findByName(valuesImportConfig.getAzquoMemoryDBConnection(), header, languages);
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
            if ((valuesImportConfig.getAssumptions() == null || valuesImportConfig.getAssumptions().getAttribute(name.getDefaultDisplayName()) == null) && attribute != null) { //if there's an assumption then no need to check required.
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

    static void addZipNameToLanguages(ValuesImportConfig valuesImportConfig) {
                    /*
            New Ed Broking logic wants to do a combination of lookup initially based on the first half of the zip name then using the first half of the file name
            in language as a way of "versioning" the headers. The second half of the zip name is held to the side to perhaps be replaced in headers later too.
            */
        if (valuesImportConfig.getZipName() != null) {
            List<String> languages = new ArrayList<>();
            languages.add(valuesImportConfig.getFileName().substring(0, valuesImportConfig.getFileName().indexOf(" ")));
            languages.add(Constants.DEFAULT_DISPLAY_NAME);
            valuesImportConfig.setLanguages(languages);
        }

    }

    // so we got the import attribute based off the beginning of the zip name, same for the import interpreter, the former starts HEADINGS, the latter dataimport
    // assumptions is a name found if created by an import file in the normal way. zip version is the end of the zip file name, at the moment a date e.g. Feb-18
    static void checkForZipNameImportInterpreterAndAssumptions(ValuesImportConfig valuesImportConfig) throws Exception {
        String zipName = valuesImportConfig.getZipName();
        // prepares for the more complex "headings as children with attributes" method of importing
        // a bunch of files in a zip file,
        if (zipName != null && zipName.length() > 0 && zipName.indexOf(" ") > 0) {// EFC - so if say it was "Risk Apr-18.zip" we have Apr-18 as the zipVersion.
            // this is passed through to preProcessHeadersAndCreatePivotSetsIfRequired
            // it isused as a straight replacement e.g. that Apr-18 in something like
            // composition `Policy No` `Policy Issuance Date`;parent of Policy Line;child of ZIPVERSION;required
            // EFC note - this seems a bit hacky, specific to Ed Broking
            valuesImportConfig.setZipVersion(zipName.substring(zipName.indexOf(" ")).trim());

            String zipPrefix = zipName.substring(0, zipName.indexOf(" ")); // e.g. Risk
            valuesImportConfig.setImportInterpreter(NameService.findByName(valuesImportConfig.getAzquoMemoryDBConnection(), "dataimport " + zipPrefix));
            // so the attribute might be "HEADINGS RISK" assuming the file was "Risk Apr-18.zip"
            valuesImportConfig.setImportAttribute("HEADINGS " + zipPrefix);
            String importFile;
            String filePath = valuesImportConfig.getFilePath();
            if (filePath.contains("/")) {
                importFile = filePath.substring(filePath.lastIndexOf("/") + 1);
            } else {
                importFile = filePath.substring(filePath.lastIndexOf("\\") + 1);
            }
            int blankPos = importFile.indexOf(" ");
            if (blankPos > 0) {
                /* EFC - this looks hacky. more string literals, So it's something like "zip file name assumptions firstbitofimportfilename"
                it turns out assumptions is a sheet in a workbook - it gets put into All Import Sheets as usual
                BUT there's also this name e.g. "Risk test2 Assumptions RLD" which is in Risk test2 Assumptions which is in Import Assumptions
                notably this isn't set as part of any code, it's created when the assumptions file is uploaded, that it should match is based on the headings in tha file
                matching. Rather fragile I'd say.
                Assumptions in the example simply has the attribute "COVERHOLDER NAME" which has the value "Unknown Coverholder"

                Note : assumptions unsued at the moment - todo - clarify the situation and maybe remove?
                */
                valuesImportConfig.setAssumptions(NameService.findByName(valuesImportConfig.getAzquoMemoryDBConnection(), zipName + " assumptions " + importFile.substring(0, blankPos)));
            }
        }
    }

    static void dealWithAssumptions(ValuesImportConfig valuesImportConfig) {
        // internally can further adjust the headings based off a name attributes. See HeadingReader for details.
        if (valuesImportConfig.getAssumptions() != null) {
            List<String> headers = valuesImportConfig.getHeaders();
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                String[] clauses = header.split(";");
                String assumption = valuesImportConfig.getAssumptions().getAttribute(clauses[0]);
                if (assumption != null) {
                    headers.set(i, header + ";default " + assumption);
                }
            }
        }
    }
}