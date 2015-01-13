package com.azquo.service;

import com.azquo.memorydb.Name;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by cawley on 27/10/14.
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

    public String getInstruction(final String instructions, final String instructionName) {
        String toReturn = null;
        //needs to detect that e.g. 'from' is an instruction, and not contained in a word
        int iPos = instructions.toLowerCase().indexOf(instructionName.toLowerCase());
        if (iPos >= 0) {
            while (iPos > 0 && instructions.charAt(iPos - 1) != ';') {
                iPos = instructions.toLowerCase().indexOf(instructionName.toLowerCase(), iPos + 1);
            }
        }
        if (iPos >= 0) {
            //find to the next semicolon, or line end
            int commandStart = iPos + instructionName.length() + 1;

            if (commandStart < instructions.length()) {
                int commandEnd =instructions.indexOf(";", commandStart + 1);
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

    // when you see things like WHERE Review date >= "xxxxxxxxxx" this is what did that.

    public String extractQuotedTerms(String calc, List<String> strings){

        int   quotePos = calc.indexOf("\"");

        while (quotePos >= 0){
            int quoteEnd = calc.indexOf("\"", quotePos + 1);
            if (quoteEnd > 0){
                strings.add(calc.substring(quotePos + 1, quoteEnd));
                calc = calc.substring(0,quotePos +1) + setOfx(quoteEnd - quotePos -1) + calc.substring(quoteEnd);
                quotePos = calc.indexOf("\"", quoteEnd + 1);
            }else{
                quotePos = -1;
            }
        }
        return calc;
    }

    // simply returns a string of x of that length

    public String setOfx(int len) {
        StringBuilder set = new StringBuilder();
        for (int i = 0; i < len; i++) {
            set.append('x');
        }
        return set.toString();
    }

    protected String replaceQuotedNamesWithMarkers(String instructions, List<String> nameStrings) throws Exception {
        //System.out.println("strip quotes : " + instructions + " attribute names  " + attributeNames);
        int lastQuoteEnd = instructions.lastIndexOf(Name.QUOTE);
        while (lastQuoteEnd >= 0) {
            int lastQuoteStart = instructions.lastIndexOf(Name.QUOTE, lastQuoteEnd - 1);
            //find the parents - if they exist
            String nameToFind = instructions.substring(lastQuoteStart, lastQuoteEnd + 1);
            if (lastQuoteEnd < instructions.length() - 1 && instructions.charAt(lastQuoteEnd + 1) == ',') {
                Pattern p = Pattern.compile("[;\\+\\*]");
                Matcher m = p.matcher(instructions.substring(lastQuoteEnd + 1));
                if (m.find()) {
                    lastQuoteEnd += m.start();//one too little....
                    nameToFind = instructions.substring(lastQuoteStart, lastQuoteEnd + 1);//adding one in here to be consistent with line adjustment below
                }
            }
            nameStrings.add(nameToFind);
                instructions = instructions.substring(0, lastQuoteStart) + NameService.NAMEMARKER + (nameStrings.size() - 1) + " " + instructions.substring(lastQuoteEnd + 1);
                lastQuoteEnd = instructions.lastIndexOf(Name.QUOTE);
        }
        return instructions;
    }

    // reverse polish is a list of values with a list of operations so 5*(2+3) would be 5,2,3,+,*
    // it's a list of values and operations
    // ok, edd here, I don't 100% understand  the exact logic but I do know what it's doing. Maybe some more checking into it later.
    // I'm beginning to understand. Practically speaking this is where parsing starts.


    protected String shuntingYardAlgorithm(String calc, List<String> nameReferences, NameService nameService) throws Exception {
        // note from Edd, I'm taking the functions to sort out things in name and normal quotes OUT of here, this funciton assumes a string ready to parse
/*   TODO SORT OUT ACTION ON ERROR
        Routine to convert a formula (if it exists) to reverse polish.

        Read a token.
                If the token is a number, then add it to the output queue.
        If the token is a function token, then push it onto the stack.
                If the token is a function argument separator (e.g., a comma):
        Until the token at the top of the stack is a left parenthesis, pop operators off the stack onto the output queue. If no left parentheses are encountered, either the separator was misplaced or parentheses were mismatched.
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



        Pattern p = Pattern.compile("[\\+\\-/\\*\\(\\)&]"); // only simple maths allowed at present
        StringBuilder sb = new StringBuilder();
        String stack = "";
        Matcher m = p.matcher(calc);
        String origCalc = calc;
        int startPos = 0;


        while (m.find()) {
            String opfound = m.group();
            char thisOp = opfound.charAt(0);
            int pos = m.start();
            String namefound = calc.substring(startPos, pos).trim();
            if (namefound.length() > 0) {
                String result = nameService.interpretTerm(namefound, nameReferences);
                if (result.startsWith("error:")) {
                    return result;
                }
                sb.append(result);
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
                return "error: mismatched brackets in " + origCalc;
            }
            if (thisOp != ')') {
                stack = thisOp + stack;
            }
            startPos = m.end();

        }
        // the last term...

        if (calc.substring(startPos).trim().length() > 0) {
            String result = nameService.interpretTerm(calc.substring(startPos).trim(), nameReferences);
            if (result.startsWith("error:")) {
                return result;
            }
            sb.append(result);
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
