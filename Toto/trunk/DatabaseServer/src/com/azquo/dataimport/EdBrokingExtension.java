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

    public static final String IMPORT_TEMPLATE = "import template";

    static void checkImportFormatterLanguage(ValuesImportConfig valuesImportConfig) {
        // EFC revising logic based off WFC recommendations, set language from the second half of the import format if it's there
        if (valuesImportConfig.getFileNameParameters() != null && valuesImportConfig.getFileNameParameters().get(IMPORT_TEMPLATE) != null) {
            String importFormat = valuesImportConfig.getFileNameParameters().get(IMPORT_TEMPLATE);
            if (importFormat.contains(" ")){
                List<String> languages = new ArrayList<>();
                //wfc added ability to put in a list of languages - NOT CURRENTLY USED
                String[] newLangs = importFormat.substring(importFormat.indexOf(" ")).split("-");//may need to be part of a Groovy file name, hence '-' rather than ','
                for (String newLang:newLangs) {
                    languages.add(newLang.trim());
                }
                languages.add(Constants.DEFAULT_DISPLAY_NAME);
                valuesImportConfig.setLanguages(languages);
            }
        }

    }

    // EFC 18/09/2018, moving from using zip name to deduce things to IMPORT_TEMPLATE from fileNameParameters
    static void checkImportFormat(ValuesImportConfig valuesImportConfig) throws Exception {
        // prepares for the more complex "headings as children with attributes" method of importing
        valuesImportConfig.setAssumptions(valuesImportConfig.getFileNameParameters());
        if (valuesImportConfig.getFileNameParameters() != null && valuesImportConfig.getFileNameParameters().get(IMPORT_TEMPLATE) != null) {
            String importFormat = valuesImportConfig.getFileNameParameters().get(IMPORT_TEMPLATE);
            // this is passed through to preProcessHeadersAndCreatePivotSetsIfRequired
            // it issued as a straight replacement e.g. that Apr-18 in something like
            // so the attribute might be "HEADINGS RISK" assuming the import format was "Risk Apr-18"
            if (importFormat.contains(" ")){
                String firstPart = importFormat.substring(0, importFormat.indexOf(" "));
                valuesImportConfig.setImportInterpreter(NameService.findByName(valuesImportConfig.getAzquoMemoryDBConnection(), "dataimport " + firstPart));
                valuesImportConfig.setImportAttribute("HEADINGS " + firstPart);
            }
        }
    }

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
For example "Contract Reference". "Contract Reference" has attributes. "HEADINGS RISK" is the default but there may also be "RLD" or "HISCOX"
or another derived from the zip file name.

The value of this attribute is equivalent to the old heading - it is a name followed by clauses. Maybe just the name
e.g. "Carrier" (meaning in the actual import file the heading is "Carrier") or a name of the heading in the import file
followed by semi-colons and clauses or composition where there is no data,in the import file, the column in generated

additional note - having discussed with WFC whether this more complex system is required is a bit contentious.
Support for this *could* be done on the old way - a single long string attribute for each variant.
There would be some duplication but it would be less complex to make
     */

    static void checkForImportNameChildren(ValuesImportConfig valuesImportConfig) throws Exception {
        Name importInterpreter = valuesImportConfig.getImportInterpreter();
        String importAttribute = valuesImportConfig.getImportAttribute();
        List<String> languages = valuesImportConfig.getLanguages();
        List<String> headersOut = null;
        // ok there are some files that have multiple headings e.g. Wind the first line and Limit the Second, they are
        // referenced like Wind|Limit. Based off the number of pipes code below will squash the headers into one. See comments where topHeadingNames is queried below
        int headingLineCount = 1;
        Set<Name> topHeadingNames = new HashSet<>();
        if (importInterpreter != null && importInterpreter.hasChildren()) {//check for top headers
            /*CHECK FOR CONVERSION :   PERMISSIBLE IN '.<language name> is <header name> additional header info
            this converts to header name in this attribute (without brackets), together with additional header info
            in the attribute (importInterpreter.getDefaultDisplayName() + " " + <language>

            Check the function for more details
            */
            checkImportChildrenForConversion(importInterpreter, importAttribute, languages.get(0));
            // so now go through the names, this is like a pre scan, find the line count (as in when multiple headings are sqaushed into one e.g. heading1|heading2|heading3)
            // and look for HeadingReader.TOPHEADING as referenced above topHeadingNames
            String topline = null;  //topline is set when the topline can be detected by searching for this name
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
                if (interpretation!=null && interpretation.toLowerCase().contains(HeadingReader.TOPLINE)){
                    for (String language:languages){
                        topline = name.getAttribute(language);
                        if (topline!=null) break;
                    }
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
                headersOut = new ArrayList<>(Arrays.asList(valuesImportConfig.getLineIterator().next()));
                for (String header : headersOut) {
                    // so only if there was a previous heading which is in the db and top headings then add this heading
                    // as a value with the previous heading as a key in the map and zap the last heading from the topHeadingNames
                    // as in we found the heading required
                    if (lastHeading != null) {
                        Name headingName = NameService.findByName(valuesImportConfig.getAzquoMemoryDBConnection(), lastHeading, languages);
                        if (headingName != null && topHeadingNames.remove(headingName)) { // remove returns true if it was in there. More concise
                            valuesImportConfig.getTopHeadings().put(lastHeading, header);
                        }
                    }
                    lastHeading = header;
                }
                lineNo++;
            }
            if (topHeadingNames.size() > 0){
                throw new Exception("Cannot find topheading " + topHeadingNames.iterator().next());
            }
            // get the next line, that may just be the headers if there are no top headings
            if (lineNo++ < 20 && valuesImportConfig.getLineIterator().hasNext()) {
                headersOut = new ArrayList<>(Arrays.asList(valuesImportConfig.getLineIterator().next()));
            }
            //looking for something in column A, there may be a gap after things like Coverholder: Joe Bloggs
            // so keep looking until we have headers
            while (lineNo < 20 && (headersOut == null || headersOut.size() == 0 || headingCount(headersOut) < 2  || headersOut.get(0).length() == 0 || (topline!=null && !containsIgnoreCase(headersOut,topline))) && valuesImportConfig.getLineIterator().hasNext()) {
                headersOut = new ArrayList<>(Arrays.asList(valuesImportConfig.getLineIterator().next()));
            }
            // finally we assume we have headers. If there more than a line of headers we'll have to squash them together. Heading1|Heading2|Heading3
            if (headingLineCount > 1) {
                buildHeadersFromVerticallyListedNames(headersOut, valuesImportConfig.getLineIterator(), headingLineCount - 1);
            }

            if (lineNo == 20 || !valuesImportConfig.getLineIterator().hasNext()) {
                throw new Exception("Unable to find headers!");
            }
        }
        if (headersOut != null) {
            valuesImportConfig.setHeaders(headersOut);
        }


    }

    private static int headingCount(List<String>headers){
        int headingCount = 0;
        for (String header:headers){
            if (header!=null && header.length()>0){
                //if (header.contains(";")) headingCount++; // usually we shall ignore lines with one name only, but if that name contains a semicolon, then it cannot be ignored
                headingCount++;
            }
        }
        return headingCount;
    }

    public static boolean containsIgnoreCase(List<String> list, String toTest){
        toTest = toTest.toLowerCase();
        for (String element:list){
            if (element.toLowerCase().equals(toTest)){
                return true;
            }
        }
        return false;
    }


    /*
     EFC comments

    For the more complex import header resolution for Ed Broking, children of importInterpreter rather than a single attribute
    if it's a simple <Policy #> for example it simply gets set back in without the <> it seems BUT
    if there's something after > e,g, "<Policy Type> language NEWRENEWAL" being the value of "RLD" attribute  in the name "Transaction Type" then
    "RLD" is chopped down to to "Policy Type" and a new attribute "HEADINGS RISK RLD" is set with the value "language NEWRENEWAL".

    A lookup . . . policy type in transaction type. It is a parent of policy reference : classification Policy Reference;required
    so . . . there are two different policy types, new or renewal
    Hiscox just have <Policy Type> , they might have "new" or "renewal" in that column, RLD, defined as <Policy Type>
    language NEWRENEWAL might have "policy" or "renewal" in that column. Y/N, true false
    it's a lookup, in this case from column F to column E in risk setup notes worksheet

            Conversion means conversion in terms of possible values. An example might be that the value in the column
            "Transaction Type" can be "New" or "Renewal". The custom syntax for RLD is "<Policy Type> language NEWRENEWAL"
            This means that "Transaction Type" is found in the column "Policy Type" and that it will have values that
            are equivalent to "New" and "Renewal" but they might need to be looked up, that being the language.

            THis function is just about preparing the conversion attributes, splitting e.g. "<Policy Type> language NEWRENEWAL"
            into two attributes.
    */

    private static void checkImportChildrenForConversion(Name importInterpreter, String importAttribute, String language) throws Exception {
            /* EFC -so, and the attribute name seems a bit arbitrary, this attribute is the name of the lookup language if we do
            need to convert column values. Should this be a groovy thing really?
             */
        importAttribute = importAttribute + " " + language;
        for (Name importField : importInterpreter.getChildren()) {
            String existingName = importField.getAttribute(importAttribute);
            if (existingName != null && existingName.startsWith("<") && existingName.contains(">")) {
                String newName = "";
                String newHeadingAttributes = existingName;
                int nameEndPos = existingName.indexOf(">");
                 newName = existingName.substring(1, nameEndPos).trim();
                 newHeadingAttributes = existingName.substring(nameEndPos + 1).trim();
                 importField.setAttributeWillBePersisted(language, newName);
                // often it seems newHeadingAttributes will be empty and hence no attribute will be created
                importField.setAttributeWillBePersisted(importAttribute, newHeadingAttributes);
            }
        }
    }

    /* new WFC function relevant to Ed Broking and import headings by child names
     a syntax check that if something says required it must have a fallback of composition or topheading or default?


check that the headings that are required are there . . .
     */

    static void checkRequiredHeadings(ValuesImportConfig valuesImportConfig) throws Exception {
        Name importInterpreter = valuesImportConfig.getImportInterpreter();
        List<String> headers = valuesImportConfig.getHeaders();
        List<String> languages = valuesImportConfig.getLanguages();
        // add in the top headings
        for (String topHeadingKey : valuesImportConfig.getTopHeadings().keySet()) {
            headers.add(topHeadingKey);
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
            if (attribute != null) {
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
                    if (required && !defaultNames.contains(name.getDefaultDisplayName()) && name.getAttribute(languages.get(0))==null) { // so we don't already have this header and we need it
                        if (composition) {// so a composition in one of the name children, add it to the headers
                            headers.add(name.getDefaultDisplayName());
                            defaultNames.add(name.getDefaultDisplayName());
                        } else {
                            /*check both the general and specific import attributes
                            so it is required and not composition or top heading and it doesn't have a default value
                            *then* we exception. Note that this isn't talking about the value on a line it's asking if the header itself exists
                            so a problem in here is a header config problem I think rather than a data problem
                            */
                            if (!attribute.toLowerCase().contains(HeadingReader.DEFAULT)
                                    && !attribute.toLowerCase().contains(HeadingReader.COMPOSITION)
                                    && !attribute.toLowerCase().contains(HeadingReader.TOPHEADING)
                                    && valuesImportConfig.getAssumptions().get(clauses[0].toLowerCase()) == null
                            ) {
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

    /*

    hiscox has more than one line of existing headers

    uses separator to stack them up e.g. to into Flood|Inc Y/N which can be referenced just like that e.g.  risk setup -> dataset, cell H69 which contains <Flood|Inc Y/N>

    in fact that definition is what tells this code the lineCount

     */

    private static void buildHeadersFromVerticallyListedNames(List<String> headers, Iterator<String[]> lineIterator, int lineCount) {
        String[] nextLine = lineIterator.next();
        boolean lastfilled;
        while (nextLine != null && lineCount-- > 0) {
            int colNo = 0;
            lastfilled = false;
            // while you find known names, insert them in reverse order with separator |.  Then use ; in the usual order
            String lastHeading = null;
            for (String heading : nextLine) {
                heading = heading.trim();
                if (heading.length() > 0 && !heading.equals("--") && colNo < headers.size()) { //ignore "--", can be used to give space below the headers
                    if (heading.startsWith(".")) {
                        headers.set(colNo, headers.get(colNo) + heading);
                    } else {
                        if (headers.get(colNo).length() == 0) {
                            // if there's nothing in this header yet replave the end of the one to the left. Need to check why this is
                            if (lastHeading != null) {
                                int lastSplit = lastHeading.lastIndexOf("|");
                                if (lastSplit > 0) {
                                    headers.set(colNo, lastHeading.substring(0, lastSplit + 1) + heading);
                                } else {
                                    headers.set(colNo, lastHeading.trim() + "|" + heading.trim());
                                }
                            } else { // if there's no previous or above I guess just set it to the line value? Added by EFC due to legitimate NPE objection from intellij
                                headers.set(colNo, heading.trim());
                            }
                        } else {
                            headers.set(colNo, headers.get(colNo).trim() + "|" + heading.trim());
                        }
                    }
                    lastfilled = true;
                }
                lastHeading = headers.get(colNo).trim();
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

    // assumptions being a bunch of defaults it seems.
    static void dealWithAssumptions(ValuesImportConfig valuesImportConfig) {
        // internally can further adjust the headings based off a name attributes. See HeadingReader for details.
        if (valuesImportConfig.getAssumptions() != null && !valuesImportConfig.getAssumptions().isEmpty()) {
            List<String> headers = valuesImportConfig.getHeaders();
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                String[] clauses = header.split(";");
                String assumption = valuesImportConfig.getAssumptions().get(clauses[0].toLowerCase().trim());
                if (assumption != null) {
                    headers.set(i, header + ";default " + assumption);
                }
            }
        }
    }
}