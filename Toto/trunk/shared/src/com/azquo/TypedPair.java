package com.azquo;

import java.io.Serializable;

/**
 * Created by edward on 15/09/16.
 *
 * Edd after a generic pair, could be useful in a few places
 *
 * Is being serializeable a problem?
 */
public class TypedPair<F,S> implements Serializable {
    private final F first;
    private final S second;

    public TypedPair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    public F getFirst() {
        return first;
    }

    public S getSecond() {
        return second;
    }

    // these two are hacky
    @Override
    public int hashCode() {
        return (first.toString() + second.toString()).hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof TypedPair){
            TypedPair tp = (TypedPair)o;
            return tp.first.equals(first) && tp.second.equals(second);
        }
        return super.equals(o);
    }
}
