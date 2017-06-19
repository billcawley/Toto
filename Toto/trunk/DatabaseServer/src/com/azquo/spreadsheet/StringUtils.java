package com.azquo.spreadsheet;

import com.azquo.StringLiterals;
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
 * Edd trying to factor off some functions. We want functions that are fairly simple, stateless and do not require database access.
 * <p>
 */
public class StringUtils {

    // returns parsed names from a name qualified with parents, the parents are returned first. Note, support for "," removed

    public static List<String> parseNameQualifiedWithParents(String source) {
        List<String> toReturn = new ArrayList<>();
        if (source == null || source.isEmpty()) return toReturn;
        while (source.contains(StringLiterals.MEMBEROF) && !source.endsWith(StringLiterals.MEMBEROF)) {
            toReturn.add(source.substring(0, source.indexOf(StringLiterals.MEMBEROF)).replace(StringLiterals.QUOTE, ' ').trim());
            source = source.substring(source.indexOf(StringLiterals.MEMBEROF) + StringLiterals.MEMBEROF.length()); // chop that one off source
        }
        toReturn.add(source.replace(StringLiterals.QUOTE, ' ').trim()); // what's left
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
        Pattern p = Pattern.compile("" + StringLiterals.QUOTE + ".*?" + StringLiterals.QUOTE + ""); // don't need escaping here I don't think. Possible to add though.
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
            modifiedStatement.append(StringLiterals.NAMEMARKER).append(twoDigit.format(quotedNameCache.size()));
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
                while (term.indexOf(StringLiterals.NAMEMARKER) != -1) { // we need to put the quoted ones back in, it will be the same order they were taken out in, hence remove(0) will work.
                    term = term.substring(0, term.indexOf(StringLiterals.NAMEMARKER)) + quotedNameCache.remove(0) + term.substring(term.indexOf(StringLiterals.NAMEMARKER) + 3);
                }
                /* ok the use of name marker might be a bit ambiguous. First used internally here for names or fragments in quotes.
                 Now used in the returned string and the names strings are chucked into an array to be resolved in name spreadsheet
                we also need attribute names in array so attribute names don't trip the shunting yard algorithm etc.
                I'm not completely clear this is the best way but it the resultant statement is "safe" for the shunting yard algorithm
                */
                if (term.startsWith(".")) {
                    // I was using name marker, no good as it would be caught by a later conditional parser
                    modifiedStatement.append(StringLiterals.ATTRIBUTEMARKER).append(twoDigit.format(attributeStrings.size()));
                    attributeStrings.add(term.substring(1).replace("`", "")); // knock off the . and remove ``
                } else {
                    modifiedStatement.append(StringLiterals.NAMEMARKER).append(twoDigit.format(nameNames.size()));
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
                || term.equalsIgnoreCase(StringLiterals.AND)
                || term.equalsIgnoreCase(StringLiterals.LEVEL) || term.equalsIgnoreCase(StringLiterals.FROM)
                || term.equalsIgnoreCase(StringLiterals.TO) || term.equalsIgnoreCase(StringLiterals.COUNT)
                || term.equalsIgnoreCase(StringLiterals.SORTED) || term.equalsIgnoreCase(StringLiterals.CHILDREN)
                || term.equalsIgnoreCase(StringLiterals.PARENTS) || term.equalsIgnoreCase(StringLiterals.ATTRIBUTESET)
                || term.equalsIgnoreCase(StringLiterals.OFFSET) || term.equalsIgnoreCase(StringLiterals.COMPAREWITH)
                || term.equalsIgnoreCase(StringLiterals.AS) || term.equalsIgnoreCase(StringLiterals.ASGLOBAL)
                || term.equalsIgnoreCase(StringLiterals.BACKSTEP) || term.equalsIgnoreCase(StringLiterals.CREATE)
                || term.equalsIgnoreCase(StringLiterals.EDIT) || term.equalsIgnoreCase(StringLiterals.NEW)
                || term.equalsIgnoreCase(StringLiterals.SELECT)
                || term.equalsIgnoreCase(StringLiterals.DELETE) || term.equalsIgnoreCase(StringLiterals.WHERE);
    }

    /*
     reverse polish is a list of values with a list of operations so 5*(2+3) would be 5,2,3,+,*
    this function assumes a string ready to parse, quoted areas dealt with
    */

    public static String shuntingYardAlgorithm(String calc) {
        Pattern p = Pattern.compile("[" + StringLiterals.ASSYMBOL + StringLiterals.ASGLOBALSYMBOL + "\\-\\+/\\*\\(\\)&]"); // only simple maths allowed at present
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
                return "Mismatched brackets in " + calc;
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

    public static String standardizeDate(String oldDate) {
        //dates may be stored in many forms - this attempts to standardize them to yyyy-mm-dd hh:mm:ss
        //IT CANNOT DETECT US DATES!
        if (oldDate.length() < 8 || oldDate.charAt(4) == '-') {
            return oldDate;
        }
        String newDate = oldDate;
        if (oldDate.charAt(2) == '-' || oldDate.charAt(2) == '/' || oldDate.charAt(2) == '.') {
            String monthDay = oldDate.substring(3, 5) + "-" + oldDate.substring(0, 2);
            if (oldDate.length() == 8 || oldDate.charAt(8) == ' ') {
                newDate = "20" + oldDate.substring(6, 8) + "-" + monthDay;
                if (oldDate.length() > 9) {
                    newDate += oldDate.substring(8);
                }
            } else {
                if (oldDate.length() == 10 || oldDate.charAt(10) == ' ') {
                    newDate = oldDate.substring(6, 10) + "-" + monthDay;
                }
                if (oldDate.length() > 11) {
                    newDate += oldDate.substring(10);
                }
            }
        }
        return newDate;
    }

    public static boolean compareStringValues(final String val1, final String val2) {
        //tries to work out if numbers expressed with different numbers of decimal places, maybe including percentage signs and currency symbols are the same.
        if (val1.equals(val2)) return true;
        String val3 = val1;
        String val4 = val2;
        if (val1.endsWith("%") && val2.endsWith("%")) {
            val3 = val1.substring(0, val1.length() - 1);
            val4 = val2.substring(0, val2.length() - 1);
        }
        val3 = stripCurrency(val3);
        val4 = stripCurrency(val4);
        if (NumberUtils.isNumber(val3) && NumberUtils.isNumber(val4)) {
            Double n1 = Double.parseDouble(val3);
            Double n2 = Double.parseDouble(val4);
            if (n1 - n2 == 0) return true;
        }
        return false;
    }

    // used when comparing values. So ignore the currency symbol if the numbers are the same
    private static String stripCurrency(String val) {
        //TODO we need to be able to detect other currencies
        if (val.length() > 1 && "$£".contains(val.substring(0, 1))) {
            return val.substring(1);
        }
        return val;
    }
}