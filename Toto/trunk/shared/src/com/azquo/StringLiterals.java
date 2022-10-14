package com.azquo;

import java.util.Collections;
import java.util.List;

/**
 * Created by edward on 14/10/16.
 *
 * Just a place to collate the String literals. Makes sense given that some are used across classes.
 *
 * I think moving them all in here but grouped properly is a plan
 */
public class StringLiterals {

    // provenance
    public static final String IN_SPREADSHEET = "in spreadsheet"; // We'll do this by string literal for the moment - might reconsider later. This is shared as currently it's part of the drilldown syntax and provenance, I'm not sure about this!
    // used in the headings. Not Strings
    public static final int UKDATE = 1;
    public static final int USDATE = 2;
    public static final int STRING = 3;
    public static final int NUMBER = 4;
    // attributes of names that also have "system" meanings
    public static final String DEFAULT_DISPLAY_NAME = "DEFAULT_DISPLAY_NAME";
    // pure convenience
    public static final List<String> DEFAULT_DISPLAY_NAME_AS_LIST = Collections.singletonList(DEFAULT_DISPLAY_NAME);
    public static final String CALCULATION = "CALCULATION";
    public static final String DEFINITION = "DEFINITION";
    public static final String DISPLAYROWS = "DISPLAYROWS";
    public static final String LOCAL = "LOCAL";
    // these two are for storing attributes in the key pair persistence
    public static final String ATTRIBUTEDIVIDER = "↑"; // it will go attribute name, attribute vale, attribute name, attribute vale
    public static final String NEXTATTRIBUTE = "\\|\\|";//when we need to be able to look up different versions of an attribute (e.g importing where more than one version exists)
    // the following block relates to the parser
    public static final char QUOTE = '`';
    public static final String MEMBEROF = "->"; // used to qualify names, no longer using ","
    public static final String LEVEL = "level";
    public static final String PARENTS = "parents";
    public static final String FROM = "from";
    public static final String OFFSET = "offset";
    public static final String BACKSTEP = "backstep";
    public static final String TO = "to";
    public static final String AND = "and";
    public static final String COUNT = "count";
    public static final String SORTED = "sorted";
    public static final String CHILDREN = "children";
    public static final String ATTRIBUTESET = "attributeset";
    public static final String CLASSIFYBY = "classifyby";
    public static final String FILTERBY = "filterby"; // see how this is used in NameQueryParser
    public static final String CONTEXT= "context"; // see how this is used in NameQueryParser
    public static final String SELECT = "select";
    public static final String CONTAINS = "contains";
    public static final String TEMPORARYNAMES  = "temporary names";
    public static final char NAMEMARKER = '~';
    public static final char ATTRIBUTEMARKER = '#';
    public static final String CREATE = "create";
    public static final String EDIT = "edit";
    public static final String NEW = "new";
    public static final String DELETE = "delete";
    public static final String AS = "as";
    public static final String ASGLOBAL = "asglobal";
    public static final String ASGLOBAL2 = "->";
    public static final char ASSYMBOL = '@';// if a name had an e-mail might this be a problem?
    public static final char CONTAINSSYMBOL = 127;
    public static final char FILTERBYSYMBOL = 128;// I think this will work, These are a bit hacky overall
    public static final char TOPNAMESSYMBOL = 129;// who knows?? EFC note 23/07/22, maybe make better later
    public static final char ASGLOBALSYMBOL = '¬';//searching for symbols!
    public static final String WHERE = "where";
    public static final String languageIndicator = "<-";
    public static final String HIERARCHY = "hierarchy";
    public static final String EDITABLE = " editable"; // why the space before?
    public static final String EXP = "exp"; // new math function for calculation . . , considered a keyword or operator
    // we have to replace some symbols and words as part of the statement processing
    public static final char MATHFUNCTION = '⠁';// braille pattern
    public static final char GREATEROREQUAL = '$';//NOT TOO HAPPY WITH ALL THESE SYMBOLS - USEFUL WHEN DEBUGGING, BUT MAYBE REPLACE WITH NON-PRINTABLES (ascii nos 1-25) later
    public static final char LESSOREQUAL = '!';
    // used by the importer
    public static final String copyPrefix = "TEMPORARY COPY"; // spaces shouldn't be there for normal persistence names so shouldn't clash
    public static final String DELIBERATELYSKIPPINGLINE = "Deliberately skipping line ";
    public static final String REJECTEDBYUSER = "Rejected by user"; // a bit of a misnomer in that applies to e.g. files that have no data. todo?
    public static final String PARAMETERS = "Parameters";
    public static final String IMPORTDATA = "importdata";
    public static final String MANUALLYREJECTEDLINES = "Manually Rejected Lines";
    public static final String ROWHEADING = "ROWHEADING";
    public static final String COLUMNHEADING = "COLUMNHEADING";
    // report rendering - names of named regions
    // all case insensitive now so make these lower case and make the names from the reports .toLowerCase().startsWith().
    public static final String AZDATAREGION = "az_dataregion";
    public static final String AZLISTSTART = "az_ListStart";
    public static final String AZDISPLAY = "az_display";
    public static final String AZDRILLDOWN = "az_drilldown";
    public static final String AZOPTIONS = "az_options";
    public static final String AZTRACK = "az_track";
    public static final String AZREPEATREGION = "az_repeatregion";
    public static final String AZREPEATSCOPE = "az_repeatscope";
    public static final String AZREPEATITEM = "az_repeatitem";
    public static final String AZREPEATLIST = "az_repeatlist";
    public static final String AZDISPLAYROWHEADINGS = "az_displayrowheadings";
    public static final String AZDISPLAYCOLUMNHEADINGS = "az_displaycolumnheadings";
    public static final String AZCOLUMNHEADINGS = "az_columnheadings";
    public static final String AZROWHEADINGS = "az_rowheadings";
    public static final String AZRDATA = "az_RData";
    public static final String AZRQUERY = "az_RQuery";
    public static final String AZXML = "az_xml";
    public static final String AZXMLEXTRAINFO = "az_xmlextrainfo";
    public static final String AZXMLFILENAME = "az_xmlfilename";
    public static final String AZXMLFLAG = "az_xmlflag";
    public static final String AZSUPPORTREPORTNAME = "az_supportreportname";
    public static final String AZSUPPORTREPORTFILEXMLTAG = "az_supportreportfilexmltag";
    public static final String AZSUPPORTREPORTSELECTIONS = "az_supportreportselections";
    public static final String AZSUPPORTREPORTFILENAME = "az_supportreportfilename";
    public static final String AZCONTEXT = "az_context";
    public static final String AZPIVOTFILTERS = "az_pivotfilters";//old version - not to be continued
    public static final String AZCONTEXTFILTERS = "az_contextfilters";
    public static final String AZCONTEXTHEADINGS = "az_contextheadings";
    public static final String AZPIVOTHEADINGS = "az_pivotheadings";//old version
    public static final String AZMULTISELECTHEADINGS = "az_multiselectheadings";//EFC - only switch on the multi select headings if this name is there. Having them on auto causes problems
    public static final String AZREPORTNAME = "az_reportname";
    public static final String EXECUTE = "az_execute";
    public static final String PREEXECUTE = "az_preexecute";
    public static final String FOLLOWON = "az_followon";
    public static final String AZSAVE = "az_save";
    public static final String AZREPEATSHEET = "az_repeatsheet";
    public static final String AZPDF = "az_pdf";
    public static final String AZTOTALFORMAT = "az_totalformat";
    public static final String AZFASTLOAD = "az_fastload";
    public static final String AZSKIPCHARTSNAP = "az_skipchartsnap";
    public static final String AZEMAILADDRESS = "az_emailaddress";
    public static final String AZEMAILSUBJECT = "az_emailsubject";
    public static final String AZEMAILTEXT = "az_emailtext";
    public static final String AZCURRENTUSER = "az_currentuser";
    // on an upload file, should this file be flagged as one that moves with backups and is available for non admin users to download
    public static final String AZFILETYPE = "az_filetype";

    public static final String AZCSVDOWNLOADNAME = "az_csvdownloadname";
    public static final String AZSHOWIN = "az_showin";
    public static final String AZMENU = "az_menu";
    public static final String AZMENUSPEC = "az_menuspec";
    public static final String AZIMPORTDATA = "az_importdata";

    public static final String TOPNAMES = "TOPNAMES";
 /*
    These are heading clauses. Heading definitions can be in the data file but Azquo is setup to support data "as it comes".
    Hence when dealing with a new set of data the key is to set up sets and headings so that the system can load the data.
    Setting up the sets and headings could be seen as similar to setting up the tables in an SQL database.

    Note : the clauses here tend to reverse the subject/object used in the code. If an object in the code has children we'll say object.children, not object.parentOf.
    This isn't a big problem and the way the clauses are set up probably makes sense in their context, I just want to note that as they are parsed naming may reverse - parentOf to children etc.

    How these are used is described in more detail in MutableImportHeading and the clause interpreter.
     */


    public static final String CHILDOF = "child of "; // trailing space I suppose one could otherwise get a false "child ofweryhwrs" match which can't happen with the others
    // parent of another heading (as opposed to name), would like the clause to be more explicit, as in differentiate between a name in the database and a column. This distinction still bugs me . . .
    public static final String PARENTOF = "parent of ";
    public static final String ATTRIBUTE = "attribute";
    public static final String LANGUAGE = "language";
    public static final String DATATYPE = "datatype";
    public static final String PEERS = "peers";
    //public static final String LOCAL = "local";
    /*
    COMPOSITION  <phrase with column heading names enclosed in ``>
    e.g   COMPOSITION  `Name`, `address` Tel No: `Telephone No`

Attributes of the names in other cells can be referenced also
    */

    public static final String COMPOSITION = "composition";
    public static final String COMPOSITIONXL = "compositionxl";
    public static final String AZEQUALS = "az=";
    // shorthand for parent of/child of/exclusive, see comments below where it's used
    public static final String CLASSIFICATION = "classification";
    public static final String DEFAULT = "default";
    // if there's no file heading then make composite and default ignore any data found on that line - we assume it's irrelevant or junk
    public static final String NOFILEHEADING = "nofileheading";
    public static final String OVERRIDE = "override";
    public static final String NONZERO = "nonzero";
    public static final String REMOVESPACES = "removespaces";
    public static final String REQUIRED = "required";
    public static final String DATELANG = "date";
    public static final String USDATELANG = "us date";
    public static final String ONLY = "only";
    public static final String IGNORE = "ignore";
    public static final String EXCLUSIVE = "exclusive";
    public static final String CLEAR = "clear";
    public static final String CLEARDATA = "cleardata"; // like the file parameter but for a column
    public static final String COMMENT = "comment";
    public static final String EXISTING = "existing"; // only works in in context of child of - reject the line if not existing
    public static final String OPTIONAL = "optional"; // only works in in context of child of - carry on with blank if not existing
    // essentially using either of these keywords switches to pivot mode (like an Excel pivot) where a name is created
    // from the line number and in a set called the name of the file, uploading successive files with the same name would of course cause problems for this system, data should be cleared before re uploading
    public static final String LINEHEADING = "lineheading";//lineheading and linedata are shortcuts for data destined for a pivot table, they are replaced before parsing starts properly
    public static final String LINEDATA = "linedata";
    public static final String SPLIT = "split";
    public static final String REPLACE = "replace";
    public static final String PROVISIONAL = "provisional";//used with 'parent of' to indicate that the parent child relationship should only be created if none exists already (originally for Ed Broking Premium imports)

    public static final String STRINGSTR = "string";

    public static final String NUMBERSTR = "number";

    public static final int EXCLUSIVETOCHILDOF = -1;
    public static final int NOTEXCLUSIVE = -2;

    /*DICTIONARY finds a name based on the string value of the cell.  The system will search all names for the attribute given by the 'dictionary' term.  For instance if the phrase is 'dictionary complaint terms'
    the system will look through all the attributes 'complaint terms' to see if any match the value of this cell.
    the 'terms' consist of words or phrases separated by '+','-' or ','.   ',' means  'or'  '+' means 'and' and '-' means 'and not'
    e.g      'car, bus, van + accident - sunday,saturday' would find any phrase containing 'car' or 'bus' or 'van' AND 'accident' but NOT containing 'saturday' or 'sunday'
    DICTIONARY can be used in conjunction with the set 'SYNONYMS`.  The elements of 'Synonyms` are names with an attribute 'synonyms'.  The attribute gives a comma-separated list of synonyms.
    e.g  if an element of 'Synonyms' is 'car'    then 'car' may have an attribute 'synonyms' consisting of 'motor, auto, vehicle'  which DICTIONARY  would consider to mean the same as 'car'
    EFC - initially I wanted to move this to reports but it's actually fairly manageable. Less of a concern than lookups. Check tryToResolveNames in BatchImporter
     */
    public static final String DICTIONARY = "dictionary";
    /*
    see checkLookup in BatchImporter, this can be quite involved
     */
    public static final String LOOKUP = "lookup";
    // required to load Ed B GXB tracking. Essentially call an attribute something01 and if it's sequential and the attribute is imported multiple times it goes in as something01, something02, something03 etc rather than overwriting
    public static final String SEQUENTIALATTRIBUTE = "sequentialattribute";

    public static final String ALL = "--ALL--";




}
