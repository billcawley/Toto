package com.azquo.dataimport;

import com.azquo.memorydb.core.Name;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by edward on 09/09/16.
 *
 * Extracted from DSImportService as the class was long. It represents the heading of a column in an import file.
 *
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
    // derived from the "child of" clause, a comma separated list of names
    Set<Name> parentNames = new HashSet<>();
    // result of the attribute clause. Notable that "." is replaced with ;attribute
    String attribute = null;
    //should we try to treat the cell as a date?
    boolean isDate = false;
    /* the results of the peers clause are jammed in peers but then we need to know which headings those peers refer to - if context assign the name otherwise it's going to be the cell index that's used */
    Set<Integer> peerCellIndexes = new HashSet<>();
    // if context provides any of the peers they're in here
    Set<Name> peersFromContext = new HashSet<>();
    // todo clarify in the comments why these are needed given that context headings will have them internally
    // the same as the above two but for a peer set defined in the context. The normal peers can get names from context, the context can get names from itself
    Set<Integer> contextPeerCellIndexes = new HashSet<>();
    Set<Name> contextPeersFromContext = new HashSet<>();

    /*if there are multiple attributes then effectively there will be multiple columns with the same "heading", define which one we're using when the heading is referenced by other headings.
    Language will trigger something as being the attribute subject, after if on searching there is only one it might be set for convenience when sorting attributes */
    boolean isAttributeSubject = false;
    // when using the heading divider (a pipe at the moment) this indicates context headings which are now stacked against this heading
    // the key with context is that it sets a bunch of stuff that extends across subsequent headings until context is set again. E.g. setting a context to do with monthly turnover on January which would then be there for February, March, April etc.
    List<MutableImportHeading> contextHeadings = new ArrayList<>();
    // Affects child of and parent of clauses - the other heading is local in the case of parent of and this one in the case of child of. Local as in Azquo name logic.
    boolean isLocal = false;
    // If `only` is specified on the first heading, the import will ignore any line that does not have this line value. Typically to deal with a file of mixed data where we want only some to go in the database.
    String only = null;
    /* to make the line value a composite of other values. Syntax is pretty simple replacing anything in quotes with the referenced line value
    `a column name`-`another column name` might make 1233214-1234. Such columns would probably be at the end,
    they are virtual in the sense that these values are made on uploading they are not there in the source file though the components are.
    A newer use of this is to create name->name2->name3, a name structure in a virtual column at the end
    also supports left, right, mid Excel string functions*/
    String compositionPattern = null;
    // a default value if the line value is blank
    String defaultValue = null;
    // don't import zero values
    boolean blankZeroes = false;
    // is this a column representing names (as opposed to values or attributes). Derived from parent of child of and being referenced by other headings, it's saying : does name, the field above, need to be populated?
    boolean lineNameRequired = false;
    /* used in context of "parent of". Can be blank in which case it means that the child can't have two siblings as parents, this heading will override existing parents
    , if it has a value it references a set higher up e.g. if a product is being moved in a structure (this heading is parent of the product) with many levels then the
    set referenced in the exclusive clause might be "Categories", the top set, so that the product would be removed from any names in Categories before being added to this heading*/
    String exclusive = null;
    // in context of childof - only load the line if this name is in the set already
    boolean existing = false;
}