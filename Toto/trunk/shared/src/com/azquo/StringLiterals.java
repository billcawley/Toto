package com.azquo;

import java.util.Collections;
import java.util.List;

/**
 * Created by edward on 14/10/16.
 *
 * Just a place to collate the String literals. Makes sense given that some are used across classes.
 */
public class StringLiterals {

    // shared ones that used to be in "Constants"
    public static final String DEFAULT_DISPLAY_NAME = "DEFAULT_DISPLAY_NAME";
    public static final List<String> DEFAULT_DISPLAY_NAME_AS_LIST = Collections.singletonList(DEFAULT_DISPLAY_NAME);
    public static String IN_SPREADSHEET = "in spreadsheet"; // We'll do this by string literal for the moment - might reconsider later. This is shared as currently it's part of the drilldown syntax and provenance, I'm not sure about this!
    public static int UKDATE = 1;
    public static int USDATE = 2;
    public static final String CALCULATION = "CALCULATION";
    public static final String DEFINITION = "DEFINITION";
    public static final String DISPLAYROWS = "DISPLAYROWS";
    public static final String LOCAL = "LOCAL";
    public static final String ATTRIBUTEDIVIDER = "↑"; // it will go attribute name, attribute vale, attribute name, attribute vale
    public static final String NEXTATTRIBUTE = "\\|\\|";//when we need to be able to look up different versions of an attribute (e.g importing where more than one version exists)
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
    public static final String SELECT = "select";
    public static final String CONTAINS = "contains";
    public static final String TEMPORARYNAMES  = "temporary names";
    //public static final String LOWEST = "lowest";
    //public static final String ALL = "all";
    public static final char NAMEMARKER = '~';
    public static final char ATTRIBUTEMARKER = '#';
    public static final String CREATE = "create";
    public static final String EDIT = "edit";
    public static final String NEW = "new";
    public static final String DELETE = "delete";
    public static final String COMPAREWITH = "comparewith";
    public static final String AS = "as";
    public static final String ASGLOBAL = "asglobal";
    public static final String ASGLOBAL2 = "->";
    public static final char ASSYMBOL = '@';
    public static final char CONTAINSSYMBOL = 127;
    public static final char ASGLOBALSYMBOL = '¬';//searching for symbols!
    public static final String WHERE = "where";
    public static final String languageIndicator = "<-";
    public static final String HIERARCHY = "hierarchy";
    public static final String EDITABLE = " editable"; // why the space before?

    public static final String EXP = "exp"; // new math function for calculation . . , considered a keyword or operator
    public static final char MATHFUNCTION = '⠁';// braille pattern
    public static final char GREATEROREQUAL = '$';//NOT TOO HAPPY WITH ALL THESE SYMBOLS - USEFUL WHEN DEBUGGING, BUT MAYBE REPLACE WITH NON-PRINTABLES (ascii nos 1-25) later
    public static final char LESSOREQUAL = '!';
    public static final String copyPrefix = "TEMPORARY COPY"; // spaces shouldn't be there for normal persistence names so shouldn't clash
}
