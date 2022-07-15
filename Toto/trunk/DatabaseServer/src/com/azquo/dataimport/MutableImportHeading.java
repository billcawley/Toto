package com.azquo.dataimport;

import com.azquo.memorydb.core.Name;

import java.util.*;

/**
 * Created by edward on 09/09/16.
 * <p>
 * This represents the heading of a column in a data import file. Quite a bit of logic or modelling can be described there.
 * <p>
 * To multi thread I wanted this to be immutable but there are things that are only set after in context of other headings so I can't do this initially.
 * No problem, make this very simple and mutable then have an immutable version for the multi threaded stuff which is held against line.
 */
class MutableImportHeading {
    // the bit before the first ";", the name of the heading - often referenced by other headings in clauses e.g. parent of
    String heading = null;
    // the Azquo Name that might be set on the heading
    Name name = null;
    // this class used to use the now removed peers against the name object, in its absence just put a set here, and this set simply refers to headings which may be names or not
    // notable that it is NOT in ImmutableImportHeading as by that point it will have been resolved into peerCellIndexes and peersFromContext
    Set<String> peers = new HashSet<>();
    /* the index of the heading that an attribute refers to so if the heading is Customer.Address1 then this is the index of customer.
    Has been kept as an index as it will be used to access the data itself (the array of Strings from each line) */
    int indexForAttribute = -1;
    // The parent of clause is an internal reference - to other headings, as in what is this a parent of - need to have it here to resolve later when we have a complete headings list
    // it will be resolved into indexForChild
    String parentOfClause = null;
    // index in the headings array of a child derived from parent of
    int indexForChild = -1;
    // derived from the "child of" clause, a comma separated list of names - Azquo names not a reference to headings
    Set<Name> parentNames = new HashSet<>();
    // result of the attribute clause. Notable that "." is replaced with ;attribute
    String attribute = null;
    //used where the attribute name is taken from another column
    int attributeColumn = -1;
    //Now it is data type not just date type, compositionXL finds this useful
    int datatype = 0;
    /* the results of the peers clause are jammed in peers but then we need to know which headings those peers refer to. The heading with the clause can immediately be resolved as a name
    * as can peers referenced in the context, the others come from other columns referred to by their indexes. Peers can be defined in the main heading or context,
    * there's no difference to how they're used but I'm going to throw an error if they're defined in both as you can't have more than one set of peers defined.
    * peerNames could be seen as names that are being added to every value on this line
    * */
    Set<Name> peerNames = new HashSet<>();
    // Indexes of columns to be resolved on each line in the BatchImporter
    Set<Integer> peerIndexes = new HashSet<>();
    /*if there are multiple attributes then effectively there will be multiple columns with the same "heading", define which one we're using when the heading is referenced by other headings.
    Language will trigger something as being the attribute subject, after if on searching there is only one it might be set for convenience when sorting attributes */
    boolean isAttributeSubject = false;
    // when using the heading divider (a pipe at the moment) this indicates context headings which are now stacked against this heading
    // the key with context is that it sets a bunch of stuff that extends across subsequent headings until context is set again. E.g. setting a context to do with monthly turnover on January which would then be there for February, March, April etc.
    List<MutableImportHeading> contextHeadings = new ArrayList<>();
    // Affects child of and parent of clauses - the other heading is local in the case of parent of and this one in the case of child of. Local as in Azquo name logic.
    boolean isLocal = false;
    // If `only` is specified on a heading, the import will ignore any line that does not have this line value. Typically to deal with a file of mixed data where we want only some to go in the database.
    // e.g. only import when there's "AB" on a given line for example
    String only = null;
    /* to make the line value a composite of other values. Syntax is pretty simple replacing anything in quotes with the referenced line value
    `a column name`-`another column name` might make 1233214-1234. Such columns would probably be at the end,
    they are virtual in the sense that these values are made on uploading they are not there in the source file though the components are.
    A newer use of this is to create name->name2->name3, a name structure in a virtual column at the end
    also supports left, right, mid Excel string functions
    composite can be called by the syntax default also
    */
    String compositionPattern = null;
    /*
    composition using Excel formulae that will be resolved by POI. If it works could replace normal composition, potentially very powerful
     */
    boolean compositionXL = false;
    // if there's no file heading then make composite and default ignore any data found on that line - we assume it's irrelevant or junk
    boolean noFileHeading = false;
    //override is the same as default, but it ignores any existing value - used for file parameter assumptions
    String override = null;
    //ignore is the opposite of 'only', omit any lines where this field consists of any element of the string list
    List<String> ignoreList = null;
    // don't import zero values
    boolean blankZeroes = false;
    // remove spaces from the cell value
    boolean removeSpaces = false;
    // is the heading required - must it be found in the file that's being loaded? Very relevant since we're now using import templates
    boolean required = false;
    // is this a column representing names (as opposed to values or attributes). Derived from parent of child of and being referenced by other headings, it's saying : does name, the field above, need to be populated?
    boolean lineNameRequired = false;
    /* used in context of "parent of". Can be blank in which case it means that the child can't have two siblings as parents, this heading will override existing parents
    , if it has a value it references a set higher up e.g. if a product is being moved in a structure (this heading is parent of the product) with many levels then the
    set referenced in the exclusive clause might be "Categories", the top set, so that the product would be removed from any names in Categories before being added to this heading
    Where there is a term following 'exclusive' that term is a column heading - hence storing the index for that term

    Properly documented in BatchImporter
    */
    String exclusiveClause = null;
    int exclusiveIndex = HeadingReader.NOTEXCLUSIVE;
    // in context of child of - only load the line if this name is in the set already
    boolean existing = false;
    // in context of child of - only set the value if the element and set exists
    boolean optional = false;
    // if line values had a comma separated list for example , would be the split char. Only used for PwC russia so far
    String splitChar = null;
    // local names are a potential problem if not resolved in the right order, main purpose of this is to flag cell names as only to be resolved by parent cells
    // EFC 26/02/20. More than one local parent on a heading would cause problems so changing this from a list to an int, if it's set more than once then exception
    int localParentIndex = -1;
    /*dictionaryTerms used to try to bring order to unrestricted string input.   A table can be uploaded (dictionaryTerms) against items in a list.  Each term element contains a comma-separated list of strings
    the terms are connected by + or - signs to indicate that elements from both sets must be present, or to exclude any item that contains elements from the list respectively.
    */
    Map<Name,List<ImmutableImportHeading.DictionaryTerm>> dictionaryMap = null;
    // synonyms are used with dictionary if you want to add equivalent words, e.g. if when categorising and asking if a string has "car" in it we might say that "auto" + "vehicle" could also count as "car"
    Map<String, List<String>> synonyms = null;
    boolean replace = false; //usually numbers will add if on the same file.  'replace' will accept the last one.
    // only put an attribute in if it's not there already
    boolean provisional = false;
    //See BatchImporter checkLookup, this can get complex
    int lookupParentIndex = -1;
    String lookupString = null;
    // required to load Ed B GXB tracking. Essentially call an attribute something01 and if it's sequential and the attribute is imported multiple times it goes in as something01, something02, something03 etc rather than overwriting
    boolean sequentialAttribute = false;
    // same as the option that can be put in for a whole file but now for a column
    boolean cleardata = false;

}