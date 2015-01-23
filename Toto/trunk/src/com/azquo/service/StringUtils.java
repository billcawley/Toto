package com.azquo.service;

import com.azquo.memorydb.Name;
import org.apache.commons.lang.math.NumberUtils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Edd trying to factor off some functions. We want functions that are fairly simple and do not require database access.
 */
public class StringUtils {

    // when passed a name tries to find the last in the list e.g. london, ontario, canada gets canada
    public String findParentFromList(final String name) {
        // ok preprocess to remove commas in quotes, easiest way.
        String nameWithoutCommasInQuotes = replaceCommasInQuotes(name);
        if (!nameWithoutCommasInQuotes.contains(",")) return null;
        // get the position from the string with commas in quotes removed
        int commaPos = nameWithoutCommasInQuotes.lastIndexOf(",");
        // but return from the unmodified string
        return name.substring(commaPos + 1).trim();
    }

    // replaces commas in quotes (e.g. "shop", "location", "region with a , in it's name" should become "shop", "location", "region with a - in it's name")  with -, useful for parsing name lists

    public String replaceCommasInQuotes(String s) {
        boolean inQuotes = false;
        StringBuilder withoutCommasInQuotes = new StringBuilder();
        char[] charactersString = s.toCharArray();
        for (char c : charactersString) {
            if (c == Name.QUOTE) {
                inQuotes = !inQuotes;
            }
            if (c == ',') {
                withoutCommasInQuotes.append(inQuotes ? '-' : ',');
            } else {
                withoutCommasInQuotes.append(c);
            }
        }
        return withoutCommasInQuotes.toString();
    }

    // edd changed references to ; to a space, hope it's all going to keep working . . .

    public String getInstruction(final String instructions, final String instructionName) {
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
                int commandEnd =instructions.indexOf(" ", commandStart + 1);
                if (commandEnd < 0){
                    commandEnd = instructions.length();
                }
                toReturn = instructions.substring(commandStart, commandEnd).trim();
               /*
                  Pattern p = Pattern.compile("[^a-z A-Z\\-0-9!]");
                 Matcher m = p.matcher(instructions.substring(commandStart));
                commandEnd = instructions.length();
                if (m.find()) {
                    commandEnd = commandStart + m.start();
                }
                */
            } else {
                toReturn = "";
            }
            //  if (toReturn.startsWith(Name.QUOTE)) {
            //      toReturn = toReturn.substring(1, toReturn.length() - 1); // trim quotes
            // }
            if (toReturn.length() > 0 && toReturn.charAt(0) == '=') {
                toReturn = toReturn.substring(1).trim();
            }
        }
        return toReturn;
    }

    public boolean precededBy(String searchText, String testItem, int pos){
        int len = testItem.length();
        return pos >= len + 2 && searchText.substring(pos - len - 1, pos).toLowerCase().equals(testItem + " ");
    }

    /* rewriting the parsing, it needs to deal with this sort of thing :

`All months` level 2 from `2014-01-01` to `2015-01-01` as `Period Chosen`
`High Street`,London,Ontario level 2 from `2014-01-01` to `2015-01-01` as `Period Chosen`
`2013-12-05`,`All dates` level lowest
Entities children
`All Customers` children - `Customer Unknown`
`All Months` children from `2014-01-01` to `2015-01-01` as `Period Chosen`
`All products` children sorted * `Kids UK foot size` children level lowest parents
`Kids UK foot size` children level 1 sorted
'Boutique Hotels';level lowest WHERE Review date >= "2015-05-05" * order level lowest * All ratings level lowest

nameStrings are strings we assume are references to names
string literals are things in normal quotes e.g. dates
Names need to be quoted like Mysql table names with ` if they contain spaces, are keywords etc.
Also - operators + - / *

I'll add better tracking of where an error is later

     */

    DecimalFormat twoDigit = new DecimalFormat("00");

    public String parseStatement(String statement, List<String> nameNames, List<String> attributeStrings, List<String> stringLiterals) throws Exception {

        /* sort the name quotes - what is replaced is just needed here, the way names can be referenced
         with hierarchy and commas makes things more interesting e.g. `High Street`,London,Ontario. In this case we'll have !01,London,Ontario instead
         that will be resolved more properly below. Also note that this takes care of attribute quotes. Distinguished by starting with a . e.g. .`some attribute name` */
        StringBuilder modifiedStatement = new StringBuilder();
        Pattern p = Pattern.compile(""+ Name.QUOTE + ".*?"+ Name.QUOTE + ""); // don't need escaping here I don't think. Possible to add though.
        Matcher matcher = p.matcher(statement);
        int lastEnd = 0;
        List<String> quotedNameCache = new ArrayList<String>();
        while(matcher.find()){
            if (modifiedStatement.length() == 0){
                modifiedStatement.append(statement.substring(0,matcher.start()));
            } else {
                modifiedStatement.append(statement.substring(lastEnd, matcher.start()));
            }
            lastEnd = matcher.end();
            quotedNameCache.add(matcher.group());
            // it should never be more and it breaks our easy fixed length marker thing here
            if (quotedNameCache.size() > 100){
                throw new Exception("More than 100 quoted names.");
            }
            // I don't even need the number here but I'll leave it here for the moment
            modifiedStatement.append(NameService.NAMEMARKER).append(twoDigit.format(quotedNameCache.size()));
        }
        if (lastEnd != 0){
            modifiedStatement.append(statement.substring(lastEnd));
        }
        if (modifiedStatement.length() > 0){
            statement = modifiedStatement.toString();
        }

        /* now we'll do the string literals - was the other way around but what about quotes in names
         this matcher deals with escaped quotes
         the goal of this little chunk is pretty simple, replace all the "here is a string literal with all sorts of characters*&)*(&" strings with "01" */
        modifiedStatement = new StringBuilder();
        p = Pattern.compile("(\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\")");
        matcher = p.matcher(statement);
        lastEnd = 0;
        while(matcher.find()){
            if (modifiedStatement.length() == 0){
                modifiedStatement.append(statement.substring(0,matcher.start()));
            } else {
                modifiedStatement.append(statement.substring(lastEnd, matcher.start()));
            }
            lastEnd = matcher.end();
            // We do need the literals index here, filter which uses it
            modifiedStatement.append("\"").append(twoDigit.format(stringLiterals.size())).append("\"");
            stringLiterals.add(matcher.group().substring(1, matcher.group().length() - 1)); // don't add the quotes. Should we unescape here???
        }
        if (lastEnd != 0){
            modifiedStatement.append(statement.substring(lastEnd));
        }
        if (modifiedStatement.length() > 0){
            statement = modifiedStatement.toString();
        }


        // ok we've escaped what we need to

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


 /* so now we have things like this, should be ready for a basic test
        !1 level 2 from !2 to !3 as !4
!1,London,Ontario level 2 from !2 to !3 as !4
!1,!2 level lowest
Entities children
!1 children * !2
!1 children from !2 to !3 as !4
!1 children sorted * !2 children level lowest parents
!1 children level 1 sorted
!1 level lowest WHERE !2 >= 54 * order level lowest * !3 level lowest 114 thing thing


I should be ok for stringtokenizer at this point
        */

        StringTokenizer st = new StringTokenizer(statement, " ");
        modifiedStatement = new StringBuilder();
        while (st.hasMoreTokens()){
            modifiedStatement.append(" ");
            String term = st.nextToken();
            if (!isKeywordOrOperator(term) && !NumberUtils.isNumber(term) && !term.startsWith("\"")){ // then we assume a name or attribute
                while (term.indexOf(NameService.NAMEMARKER) != -1){ // we need to put the quoted ones back in, it will be the same order they were taken out in, hence remove(0) will work.
                    term = term.substring(0, term.indexOf(NameService.NAMEMARKER)) + quotedNameCache.remove(0) + term.substring(term.indexOf(NameService.NAMEMARKER) + 3);
                }
                /* ok the use of name marker might be a bit ambiguous. First used internally here for names or fragments in quotes. Now used in the returned string and the names strings are chucked into an array to be resolved in name service
                we also need attribute names in array so attribute names don't trip the shunting hard algorithm etc. Of course attributes aren't fit for batch resolution after this  parse
                I'm not completely clear this is the best way but it the resultant statement is "safe" for the shunting yard algorithm
                */
                if (term.startsWith(".")){
                    // I was using name marker, no good as it would be caught by a later conditional parser
                    modifiedStatement.append(NameService.ATTRIBUTEMARKER).append(twoDigit.format(attributeStrings.size()));
                    attributeStrings.add(term.substring(1)); // knock off the .
                } else {
                    modifiedStatement.append(NameService.NAMEMARKER).append(twoDigit.format(nameNames.size()));
                    nameNames.add(term);
                }
            } else {
                modifiedStatement.append(term);
            }
        }
        statement = modifiedStatement.toString().trim();
        return statement;
    }

    public boolean isKeywordOrOperator(String term){
        return term.equals("*") || term.equals("/") || term.equals("+") || term.equals("-") || term.equals(">")
                || term.equals("<") || term.equals(">=") || term.equals("<=") || term.equals("=")
                || term.equalsIgnoreCase(NameService.LEVEL) || term.equalsIgnoreCase(NameService.FROM)
                || term.equalsIgnoreCase(NameService.TO) || term.equalsIgnoreCase(NameService.COUNT)
                || term.equalsIgnoreCase(NameService.SORTED) || term.equalsIgnoreCase(NameService.CHILDREN)
                || term.equalsIgnoreCase(NameService.PARENTS) || term.equalsIgnoreCase(NameService.LOWEST)
                || term.equalsIgnoreCase(NameService.ALL) || term.equalsIgnoreCase(NameService.PEERS)
                || term.equalsIgnoreCase(NameService.COUNTBACK) || term.equalsIgnoreCase(NameService.COMPAREWITH)
                || term.equalsIgnoreCase(NameService.AS) || term.equalsIgnoreCase(NameService.STRUCTURE)
                || term.equalsIgnoreCase(NameService.NAMELIST) || term.equalsIgnoreCase(NameService.CREATE)
                || term.equalsIgnoreCase(NameService.EDIT) || term.equalsIgnoreCase(NameService.NEW)
                || term.equalsIgnoreCase(NameService.DELETE) || term.equalsIgnoreCase(NameService.WHERE);

    }


    // reverse polish is a list of values with a list of operations so 5*(2+3) would be 5,2,3,+,*
    // it's a list of values and operations
    // ok, edd here, I don't 100% understand  the exact logic but I do know what it's doing. Maybe some more checking into it later.
    // I'm beginning to understand. Practically speaking this is where parsing starts.


    protected String shuntingYardAlgorithm(String calc) throws Exception {
        // note from Edd, I'm taking the functions to sort out things in name and normal quotes OUT of here, this funciton assumes a string ready to parse
/*   TODO SORT OUT ACTION ON ERROR
        Routine to convert a formula (if it exists) to reverse polish.

        Read a token.
                If the token is a number, then add it to the output queue.
        If the token is a function token, then push it onto the stack.
                If the token is a function argument separator (e.g., a comma):
        Until the token at the top of the stack is a left parenthesis, pop operators off the stack onto the output queue.
        If no left parentheses are encountered, either the separator was misplaced or parentheses were mismatched.
        If the token is an operator, o1, then:
        while there is an operator token, o2, at the top of the stack, and
        either o1 is left-associative and its precedence is equal to that of o2,
                or o1 has precedence less than that of o2,
        pop o2 off the stack, onto the output queue;
        push o1 onto the stack.
                If the token is a left parenthesis, then push it onto the stack.
                If the token is a right parenthesis:
        Until the token at the top of the stack is a left parenthesis, pop operators off the stack onto the output queue.
        Pop the left parenthesis from the stack, but not onto the output queue.
                If the token at the top of the stack is a function token, pop it onto the output queue.
                If the stack runs out without finding a left parenthesis, then there are mismatched parentheses.
        When there are no more tokens to read:
        While there are still operator tokens in the stack:
        If the operator token on the top of the stack is a parenthesis, then there are mismatched parentheses.
        Pop the operator onto the output queue.
                Exit.
*/

        // todo - edd commented some bits of code , interpret term, check how the logic may have changed. Also try to understand what the function is doing, could perhaps be integrated

        Pattern p = Pattern.compile("[\\+\\-/\\*\\(\\)&]"); // only simple maths allowed at present
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
/*                String result = nameService.interpretTerm(namefound, nameReferences);
                if (result.startsWith("error:")) {
                    return result;
                }*/
                sb.append(namefound).append(" ");
            }
            char lastOffStack = ' ';
            while (!(thisOp == ')' && lastOffStack == '(') && (stack.length() > 0 && ")+-*/(".indexOf(thisOp) <= "(+-*/".indexOf(stack.charAt(0)))) {

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
/*            String result = nameService.interpretTerm(calc.substring(startPos).trim(), nameReferences);
            if (result.startsWith("error:")) {
                return result;
            }*/
            sb.append(calc.substring(startPos)).append(" ");
        }

        //.. and clear the stack
        while (stack.length() > 0) {
            sb.append(stack.charAt(0)).append(" ");
            stack = stack.substring(1);
        }
        return sb.toString();
    }


    // edd jamming this commented function in here for the moment
    // untested!
/*
    public ArrayList<Name> sortNames(final ArrayList<Name> namesList, final String language) {
        Comparator<Name> compareName = new Comparator<Name>() {
            public int compare(Name n1, Name n2) {
            return n1.getAttribute(language).compareTo(n1.getAttribute(language));
            }
        };
        Collections.sort(namesList, compareName);
        return namesList;
    }*/


}
