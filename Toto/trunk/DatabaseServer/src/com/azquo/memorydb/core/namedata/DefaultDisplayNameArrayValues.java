package com.azquo.memorydb.core.namedata;

import com.azquo.memorydb.core.Value;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultDisplayNameArrayValues extends DefaultDisplayNameOnly {

    private volatile Value[] values;

    public DefaultDisplayNameArrayValues() {
        super();
        values = new Value[0];
    }

    @Override
    public NameData getImplementationThatCanAddValue() {
        return null;
    }

    @Override
    public NameData getImplementationThatCanAddChild() {
        return null;
    }

    public boolean hasValues() {
        return values.length != 0;
    }

    public Collection<Value> getValues() {
        return Arrays.asList(values);
    }

    // to do with when a db might have some corruption - zap any nulls that might have got in there
    public void valueArrayCheck() {
        ArrayList<Value> newList = new ArrayList<>();
        for (Value v : values) {
            if (v != null) {
                newList.add(v);
            }
        }
        // intellij wants an empty array here, not quite sure why
        values = newList.toArray(new Value[0]);
    }

    public boolean addToValues(Value value, boolean backupRestore, boolean databaseIsLoading) throws Exception {
        // it's this contains expense that means we should stop using ArrayList over a certain size.
        // If loading skip the duplication check, we assume data integrity, asList an attempt to reduce garbage, I think it's object is lighter than an ArrayList. It won't be hit during loading but will during importing,
        if (databaseIsLoading || !Arrays.asList(values).contains(value)) {
            if (values.length >= ARRAYTHRESHOLD) { // code should check first
                throw new UnsupportedOperationException();
            } else { // ok we have to switch a new array in
                // by this point should be synchronized, I should be able to be simple about this and be safe still
                // new code to deal with arrays assigned to the correct size on loading
                // don't trust no_values, compensate and log
                if (values.length != 0 && values[values.length - 1] == null) {
                    if (!databaseIsLoading) {
                        /* todo - how to deal with this? Do we have to pass a reference to azquomemorydb?
                        System.out.println("empty space in values after the database has finished loading - no_values wrong on name id " + getId() + " " + getDefaultDisplayName());
                        getAzquoMemoryDB().forceNameNeedsPersisting(this);*/
                    }
                    // If there's a mismatch and noValues is too small it just won't be added to the list. But if noValues isn't correct things have already gone wrong
                    for (int i = 0; i < values.length; i++) { // being synchronised this should be all ok
                        if (values[i] == null) {
                            values[i] = value;
                            break;
                        }
                    }
                } else { // normal modification
                    if (databaseIsLoading) {
                        /* todo - how to deal with this? Do we have to pass a reference to azquomemorydb?
                        System.out.println("while loading ran out of values space - no_values wrong on name id " + getId() + " " + getDefaultDisplayName());
                        getAzquoMemoryDB().forceNameNeedsPersisting(this);
                         */
                    }
                    Value[] newValuesArray = new Value[values.length + 1];
                    System.arraycopy(values, 0, newValuesArray, 0, values.length); // intellij simplified it to this, should be fine
                    newValuesArray[values.length] = value;
                    values = newValuesArray;
                }
                return true;
            }
        }
        return false;
    }

    public boolean removeFromValues(Value value) {
        List<Value> valuesList = Arrays.asList(values);
        if (valuesList.contains(value)) {
            // ok and a manual copy, again since synchronized I can't see a massive problem here.
            Value[] newValuesArray = new Value[values.length - 1];
            int newArrayPosition = 0;// gotta have a separate index on the new array, they will go out of sync
            for (Value value1 : values) { // do one copy skipping the element we want removed
                if (!value1.equals(value)) { // if it's not the one we want to return then copy
                    newValuesArray[newArrayPosition] = value1;
                    newArrayPosition++;
                }
            }
            values = newValuesArray;
            return true;
        }
        return false;
    }

    // should provide direct access to the field - replacing the direct access calls used before. Often implementations might return null.
    public Value[] directArrayValues() {
        return values;
    }

    public boolean canAddValue() {
        return values.length < ARRAYTHRESHOLD;
    }

}
