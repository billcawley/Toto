package com.azquo.memorydb.core;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Created by edward on 31/08/16.
 * <p>
 * To facilitate a history of values for a given name set. I'm not entirely happy about this as a new class in core, we'll see.
 * <p>
 * Immutable which is nice though a bit of a mute point due to how it's accessed.
 */
public class ValueHistory {

    //private static final Logger logger = Logger.getLogger(Value.class);
    private final int id;
    private final Provenance provenance;
    private final String text;

    private final Name[] names;


    public ValueHistory(final AzquoMemoryDB azquoMemoryDB, final int id, final int provenanceId, String text, byte[] namesCache) throws Exception {
        this.provenance = azquoMemoryDB.getProvenanceById(provenanceId);
        this.id = id;
        this.text = text.intern();
        int noNames = namesCache.length / 4;
        ByteBuffer byteBuffer = ByteBuffer.wrap(namesCache);
        Name[] newNames = new Name[noNames];
        for (int i = 0; i < noNames; i++) {
            newNames[i] = azquoMemoryDB.getNameById(byteBuffer.getInt(i * 4));
        }
        this.names = newNames;
    }

    public int getId() {
        return id;
    }

    public Provenance getProvenance() {
        return provenance;
    }

    public String getText() {
        return text;
    }

    public Collection<Name> getNames() {
        return Collections.unmodifiableList(Arrays.asList(names));
    }

    @Override
    public String toString() {
        return "ValueHistory{" +
                "id=" + id +
                ", provenance=" + provenance +
                ", text='" + text + '\'' +
                ", names=" + Arrays.toString(names) +
                '}';
    }

    // copied from Value, now required for backing up databases. Factor?
    public byte[] getNameIdsAsBytes() {
        return getNameIdsAsBytes(0);
    }

    private byte[] getNameIdsAsBytes(int tries) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(names.length * 4);
            for (Name name : names) {
                buffer.putInt(name.getId());
            }
            return buffer.array();
        } catch (BufferOverflowException e) {
            if (tries < 5) { // a bit arbitrary
                tries++;
                System.out.println("retrying after buffer overflow in getNameIdsAsBytes on value id " + getId() + " try number " + tries);
                return getNameIdsAsBytes(tries);
            } else {
                throw e;
            }
        }
    }


}
