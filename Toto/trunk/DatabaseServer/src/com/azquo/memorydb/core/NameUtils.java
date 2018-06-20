package com.azquo.memorydb.core;

import com.azquo.StringLiterals;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extracted from Name by edward on 07/10/16.
 * <p>
 * Just some low level stuff extracted from Name, trying to reduce the size of that class.
 */
public class NameUtils {

    // note these two should be called in synchronized blocks if acting on things like parents, children etc
    // doesn't check contains, there is logic after the contains when adding which can't go in here (as in are we going to switch to set?)

    static Name[] nameArrayAppend(Name[] source, Name toAppend) {
        Name[] newArray = new Name[source.length + 1];
        System.arraycopy(source, 0, newArray, 0, source.length); // intellij simplified it to this, should be fine. TODO - saw a warning about this on twitter, maybe double check performance implications?
        newArray[source.length] = toAppend;
        return newArray;
    }

    // I realise some of this stuff is probably very like the internal workings of ArrayList! Important here to save space with vanilla arrays I'm rolling my own.

    static Name[] nameArrayRemoveIfExists(Name[] source, Name toRemove) {
        List<Name> sourceList = Arrays.asList(source);
        if (sourceList.contains(toRemove)) {
            return nameArrayRemove(source, toRemove);
        } else {
            return source;
        }
    }

    // note, assumes it is in there! Otherwise will be an exception

    static Name[] nameArrayRemove(Name[] source, Name toRemove) {
        Name[] newArray = new Name[source.length - 1];
        int newArrayPosition = 0;// gotta have a separate index on the new array, they will go out of sync
        for (Name name : source) { // do one copy skipping the element we want removed
            if (name != toRemove) { // if it's not the one we want to return then copy
                newArray[newArrayPosition] = name;
                newArrayPosition++;
            }
        }
        return newArray;
    }

    public static String getFullyQualifiedDefaultDisplayName(Name name) {
        if (!name.hasParents()) {
            return StringLiterals.QUOTE + name.getDefaultDisplayName() + StringLiterals.QUOTE;
        }
        Collection<Name> parents = name.getParents();
        // ignore where there's no default display name
        StringBuilder qualified = new StringBuilder(name.getDefaultDisplayName() != null ? name.getDefaultDisplayName() : "");
        // IntelliJ suggested StringBuilder with .insert, I didn't know about that before :)
        while (!parents.isEmpty()) {
            Name parent = parents.iterator().next();
            if (parent.getDefaultDisplayName() != null){
                if (qualified.length() == 0){
                    qualified.append(parent.getDefaultDisplayName());
                } else {
                    qualified.insert(0, parent.getDefaultDisplayName() + StringLiterals.MEMBEROF);
                }
            }
            parents = parent.getParents();
        }
        return StringLiterals.QUOTE + qualified.toString() + StringLiterals.QUOTE;
    }

/*    public static String getFullyQualifiedDefaultDisplayName(Name name) {
        if (!name.hasParents()) {
            return StringLiterals.QUOTE + name.getDefaultDisplayName() + StringLiterals.QUOTE;
        }
        Collection<Name> parents = name.getParents();
        String qualified = name.getDefaultDisplayName();
        while (!parents.isEmpty()) {
            Name parent = parents.iterator().next();
            qualified = parent.getDefaultDisplayName() + StringLiterals.MEMBEROF + qualified;
            parents = parent.getParents();
        }
        qualified = qualified.replace(StringLiterals.MEMBEROF + "null","");//deal with user-specific names
        return StringLiterals.QUOTE + qualified + StringLiterals.QUOTE;
    }
*/
    static void clearAtomicIntegerCounters(AtomicInteger... args){
        for (AtomicInteger counter : args){
            counter.set(0);
        }
    }
}