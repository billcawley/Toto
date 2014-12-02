package com.azquo.service;

import com.azquo.memorydb.Name;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by cawley on 20/11/14.
 */
public class NameUtils {

    private int parseInt(final String string, int existing) {
        try {
            return Integer.parseInt(string);
        } catch (Exception e) {
            return existing;
        }
    }

    public List<Name> findChildrenFromToCount(final List<Name> names, final String fromString, final String toString,final int count,  final int offset, final String compareWithString) throws Exception {
        int from = 0;
        int to = 0;
        int derivedFromIndex = getIndexOfNameInNameList(names, fromString);
        if (derivedFromIndex != -1){
            from = derivedFromIndex;
        } else {
            from = parseInt(fromString, from);
        }
        int derivedToIndex = getIndexOfNameInNameList(names, toString);
        if (derivedToIndex != -1){
            to = derivedToIndex;
        } else {
            to = parseInt(toString, to);
        }
        // offset only relevant when using from and to strings
        // try and follow existing logic.
        final ArrayList<Name> toReturn = new ArrayList<Name>();
        int compareWith = parseInt(compareWithString, 0);
        int space = 1; //spacing between 'compare with' fields
        //first look for integers and encoded names...

        int position = 1;
        boolean inSet = false;
        if (to != -1000 && to < 0) {
            to = names.size() + to;
        }


        int added = 0;

        for (int i = offset; i < names.size() + offset; i++) {

            if (position == from || (i < names.size() && names.get(i).getDefaultDisplayName().equals(fromString)))
                inSet = true;
            if (inSet) {
                toReturn.add(names.get(i - offset));
                added++;
            }
            if (position == to || (i < names.size() && names.get(i).getDefaultDisplayName().equals(toString)) || added == count)
                inSet = false;
            position++;
        }
        while (added++ < count) {
            toReturn.add(null);

        }
        return toReturn;
    }

    // ok here's the thing : the old code wasn't case insensitive and didn't care about language so I don't think I will.

    public int getIndexOfNameInNameList(final List<Name> names, String attributeValue){
        if (attributeValue != null){
            for (int index = 0; index < names.size(); index++){
                Name name = names.get(index);
                if (attributeValue.startsWith(NameService.NAMEMARKER  + "")){
                    // I'm duplicating some logic in the name service
                    try{
                        if (name.getId() == Integer.parseInt(attributeValue.substring(1))){
                            return index;
                        }
                    } catch (NumberFormatException nfe){
                        // hmmm, shouldn't happen
                    }
                } else {
                    if (name.getAttribute(Name.DEFAULT_DISPLAY_NAME).equals(attributeValue)){
                        return index;
                    }
                }
            }
        }
        return -1;
    }


}
