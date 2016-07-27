package com.azquo.spreadsheet;

import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameService;
import org.apache.commons.lang.math.NumberUtils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Edd trying to factor off some functions. We want functions that are fairly simple and do not require database access.
 * <p>
 * Generally stateless functions that could be static.
 */
public class StringUtils {

    public static final String MEMBEROF = "->"; // used to qualify names, no longer using ","

    // returns parsed names from a name qualified with parents, the parents are returned first. Note, support for "," removed

    public static List<String> parseNameQualifiedWithParents(String source) {
        List<String> toReturn = new ArrayList<>();
        if (source == null || source.isEmpty()) return toReturn;
        while (source.contains(MEMBEROF) && !source.endsWith(MEMBEROF)) {
            toReturn.add(source.substring(0, source.indexOf(MEMBEROF)).replace(Name.QUOTE, ' ').trim());
            source = source.substring(source.indexOf(MEMBEROF) + MEMBEROF.length()); // chop that one off source
        }
        toReturn.add(source.replace(Name.QUOTE, ' ').trim()); // what's left
        return toReturn;
    }

    // Used to use a ; between instructions. A basic part of the parser e.g. from X level Y etc.

    public static String getInstruction(final String instructions, final String instructionName) {
        String toReturn = null;
        //needs to detect that e.g. 'from' is an instruction, and not contained in a word
        int iPos = instructions.toLowerCase().indexOf(instructionName.toLowerCase());
        if (iPos >= 0) {
            while (iPos > 0 && instructions.charAt(iPos - 1) != ' ') {
                iPos = instructions.toLowerCase().indexOf(instructionName.toLowerCase(), iPos + 1);
            }
        }
        if (iPos >= 0) {
            //find to the next semicolon, or line end
            int commandStart = iPos + instructionName.length() + 1;
            if (commandStart < instructions.length()) {
                int commandEnd = instructions.indexOf(" ", commandStart + 1);
                if (commandEnd < 0) {
                    commandEnd = instructions.length();
                }
                toReturn = instructions.substring(commandStart, commandEnd).trim();
            } else {
                toReturn = "";
            }
            if (toReturn.length() > 0 && toReturn.charAt(0) == '=') {
                toReturn = toReturn.substring(1).trim();
            }
        }
        return toReturn;
    }

    public static boolean precededBy(String searchText, String testItem, int pos) {
        int len = testItem.length();
        return pos >= len + 2 && searchText.substring(pos - len - 1, pos).toLowerCase().equals(testItem + " ");
    }

/* after some syntax changes we might need some updated examples here

nameStrings are strings we assume are references to names
string literals are things in normal quotes e.g. dates
Names need to be quoted like Mysql table names with ` if they contain spaces, are keywords etc.
Also - operators + - / * and , added also to facilitate a list of statements (decode string)

I'll add better tracking of where an error is later

Essentially prepares a statement for functions like interpretSetTerm and shuntingYardAlgorithm

     */

    private static DecimalFormat twoDigit = new DecimalFormat("00");

    public static String prepareStatement(String statement, List<String> nameNames, List<String> attributeStrings, List<String> stringLiterals) throws Exception {
        // sort the name quotes - will be replaces with !01 !02 etc.
        StringBuilder modifiedStatement = new StringBuilder();
        Pattern p = Pattern.compile("" + Name.QUOTE + ".*?" + Name.QUOTE + ""); // don't need escaping here I don't think. Possible to add though.
        Matcher matcher = p.matcher(statement);
        int lastEnd = 0;
        List<String> quotedNameCache = new ArrayList<>();
        while (matcher.find()) {
            if (modifiedStatement.length() == 0) {
                modifiedStatement.append(statement.substring(0, matcher.start()));
            } else {
                modifiedStatement.append(statement.substring(lastEnd, matcher.start()));
            }
            lastEnd = matcher.end();
            while (lastEnd < statement.length() - 2 && statement.substring(lastEnd - 1, lastEnd + 2).equals("`.`")) {
                int nextQuote = statement.indexOf("`", lastEnd + 2);
                if (nextQuote > 0) {
                    matcher.find();//skip the next field
                    lastEnd = nextQuote + 1;
                }
            }
            quotedNameCache.add(statement.substring(matcher.start(), lastEnd));
            // it should never be more and it breaks our easy fixed length marker thing here
            if (quotedNameCache.size() > 100) {
                throw new Exception("More than 100 quoted names.");
            }
            // I don't even need the number here but I'll leave it here for the moment
            modifiedStatement.append(NameService.NAMEMARKER).append(twoDigit.format(quotedNameCache.size()));
        }
        if (lastEnd != 0) {
            modifiedStatement.append(statement.substring(lastEnd));
        }
        if (modifiedStatement.length() > 0) {
            statement = modifiedStatement.toString();
        }

        /* now we'll do the string literals - was the other way around but what about quotes in names
         this matcher deals with escaped quotes
         the goal of this little chunk is pretty simple, replace all the "here is a string literal with all sorts of characters*&)*(&" strings with "01" */
        modifiedStatement = new StringBuilder();
        p = Pattern.compile("(\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\")");
        matcher = p.matcher(statement);
        lastEnd = 0;
        while (matcher.find()) {
            if (modifiedStatement.length() == 0) {
                modifiedStatement.append(statement.substring(0, matcher.start()));
            } else {
                modifiedStatement.append(statement.substring(lastEnd, matcher.start()));
            }
            lastEnd = matcher.end();
            // We do need the literals index here, filter which uses it
            modifiedStatement.append("\"").append(twoDigit.format(stringLiterals.size())).append("\"");
            stringLiterals.add(matcher.group().substring(1, matcher.group().length() - 1)); // don't add the quotes. Should we unescape here???
        }
        if (lastEnd != 0) {
            modifiedStatement.append(statement.substring(lastEnd));
        }
        if (modifiedStatement.length() > 0) {
            statement = modifiedStatement.toString();
        }


        // ok we've escaped what we need to (we have quoted names and strings squirrelled away)

        statement = statement.replace(";", " "); // legacy from when this was required

        // now, we want to run validation on what's left really. One problem is that operators might not have spaces
        // ok this is hacky, I don't really care for the moment
        statement = statement.replace("*", " * ").replace("  ", " ");
        statement = statement.replace("+", " + ").replace("  ", " ");
        statement = statement.replace("-", " - ").replace("  ", " ");
        statement = statement.replace("/", " / ").replace("  ", " ");
        statement = statement.replace("<", " < ").replace("  ", " ");
        statement = statement.replace(">", " > ").replace("  ", " ");
        statement = statement.replace("<=", " <= ").replace("  ", " ");
        statement = statement.replace(">=", " >= ").replace("  ", " ");
        statement = statement.replace("=", " = ").replace("  ", " ");
        statement = statement.replace("(", " ( ").replace("  ", " ");
        statement = statement.replace(")", " ) ").replace("  ", " ");
        // this assumes that the , will be taken care of after the parsing
        statement = statement.replace(",", " , ").replace("  ", " ");
        statement = statement.replaceAll("(?i)level lowest", "level 100");
        statement = statement.replaceAll("(?i)level highest", "level -100");
        statement = statement.replaceAll("(?i)level all", "level 101");
        // can be used by the new "exclude" syntax
        statement = statement.replace("[", " [ ").replace("  ", " ");
        statement = statement.replace("]", " ] ").replace("  ", " ");


 /* so now we have things like this, should be ready for a basic test
        !1 level 2 from !2 to !3 as !4
!1,Ontario->London level 2 from !2 to !3 as !4
!1,!2 level lowest
Entities children
!1 children * !2
!1 children from !2 to !3 as !4
!1 children sorted * !2 children level lowest parents
!1 children level 1 sorted
!1 level lowest WHERE !2 >= 54 * order level lowest * !3 level lowest 114 thing thing

I should be ok for StringTokenizer at this point
        */

        StringTokenizer st = new StringTokenizer(statement, " ");
        modifiedStatement = new StringBuilder();
        while (st.hasMoreTokens()) {
            modifiedStatement.append(" ");
            String term = st.nextToken();
            if (!isKeywordOrOperator(term) && !NumberUtils.isNumber(term) && !term.startsWith("\"")) { // then we assume a name or attribute
                while (term.indexOf(NameService.NAMEMARKER) != -1) { // we need to put the quoted ones back in, it will be the same order they were taken out in, hence remove(0) will work.
                    term = term.substring(0, term.indexOf(NameService.NAMEMARKER)) + quotedNameCache.remove(0) + term.substring(term.indexOf(NameService.NAMEMARKER) + 3);
                }
                /* ok the use of name marker might be a bit ambiguous. First used internally here for names or fragments in quotes.
                 Now used in the returned string and the names strings are chucked into an array to be resolved in name spreadsheet
                we also need attribute names in array so attribute names don't trip the shunting yard algorithm etc.
                I'm not completely clear this is the best way but it the resultant statement is "safe" for the shunting yard algorithm
                */
                if (term.startsWith(".")) {
                    // I was using name marker, no good as it would be caught by a later conditional parser
                    modifiedStatement.append(NameService.ATTRIBUTEMARKER).append(twoDigit.format(attributeStrings.size()));
                    attributeStrings.add(term.substring(1).replace("`", "")); // knock off the . and remove ``
                } else {
                    modifiedStatement.append(NameService.NAMEMARKER).append(twoDigit.format(nameNames.size()));
                    nameNames.add(term);
                }
            } else {
                modifiedStatement.append(term);
            }
        }
        statement = modifiedStatement.toString().trim();
        statement = statement.replace("> =", ">=").replace("< =", "<=");//remove unnecessary blanks
        return statement;
    }

    private static boolean isKeywordOrOperator(String term) {
        return term.equals("*") || term.equals("/") || term.equals("+") || term.equals("-") || term.equals(">")
                || term.equals("<") || term.equals("=") || term.equals(",")
                || term.equals("(") || term.equals(")")
                || term.equals("[") || term.equals("]")
                || term.equalsIgnoreCase("and")
                || term.equalsIgnoreCase(NameService.LEVEL) || term.equalsIgnoreCase(NameService.FROM)
                || term.equalsIgnoreCase(NameService.TO) || term.equalsIgnoreCase(NameService.COUNT)
                || term.equalsIgnoreCase(NameService.SORTED) || term.equalsIgnoreCase(NameService.CHILDREN)
                || term.equalsIgnoreCase(NameService.PARENTS)|| term.equalsIgnoreCase(NameService.ATTRIBUTESET)
                || term.equalsIgnoreCase(NameService.COUNTBACK) || term.equalsIgnoreCase(NameService.COMPAREWITH)
                || term.equalsIgnoreCase(NameService.AS)
                || term.equalsIgnoreCase(NameService.CREATE)
                || term.equalsIgnoreCase(NameService.EDIT) || term.equalsIgnoreCase(NameService.NEW)
                || term.equalsIgnoreCase(NameService.SELECT)
                || term.equalsIgnoreCase(NameService.DELETE) || term.equalsIgnoreCase(NameService.WHERE);
    }

    /*
     reverse polish is a list of values with a list of operations so 5*(2+3) would be 5,2,3,+,*
    this function assumes a string ready to parse, quoted areas dealt with
    */

    public static String shuntingYardAlgorithm(String calc) {
        Pattern p = Pattern.compile("[" + NameService.ASSYMBOL + "\\-\\+/\\*\\(\\)&]"); // only simple maths allowed at present
        StringBuilder sb = new StringBuilder();
        String stack = "";
        Matcher m = p.matcher(calc);
        int startPos = 0;
        while (m.find()) {
            String opfound = m.group();
            char thisOp = opfound.charAt(0);
            int pos = m.start();
            String namefound = calc.substring(startPos, pos).trim();
            if (namefound.length() > 0) {
                sb.append(namefound).append(" ");
            }
            char lastOffStack = ' ';
            while (!(thisOp == ')' && lastOffStack == '(') && (stack.length() > 0 && ")+-/*(".indexOf(thisOp) <= "(+-/*".indexOf(stack.charAt(0)))) {
                if (stack.charAt(0) != '(') {
                    sb.append(stack.charAt(0)).append(" ");
                }
                lastOffStack = stack.charAt(0);
                stack = stack.substring(1);
            }
            if ((thisOp == ')' && lastOffStack != '(') || (thisOp != ')' && lastOffStack == '(')) {
                return "error: mismatched brackets in " + calc;
            }
            if (thisOp != ')') {
                stack = thisOp + stack;
            }
            startPos = m.end();
        }
        // the last term...
        if (calc.substring(startPos).trim().length() > 0) {
            sb.append(calc.substring(startPos)).append(" ");
        }
        //.. and clear the stack
        while (stack.length() > 0) {
            sb.append(stack.charAt(0)).append(" ");
            stack = stack.substring(1);
        }
        return sb.toString();
    }
}