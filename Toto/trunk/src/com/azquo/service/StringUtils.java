package com.azquo.service;

import com.azquo.memorydb.Name;

import java.util.List;

/**
 * Created by cawley on 27/10/14.
 * Edd trying to factor off some
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

    public String extractStrings(String calc, List<String> strings){

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
