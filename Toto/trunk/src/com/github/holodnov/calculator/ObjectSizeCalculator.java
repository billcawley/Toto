package com.github.holodnov.calculator;

import com.azquo.memorydb.AzquoMemoryDB;
import com.azquo.memorydb.Name;
import com.azquo.memorydb.Provenance;
import com.azquo.memorydb.Value;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * @author Kyrylo Holodnov
 */
public class ObjectSizeCalculator {
    private static final int REFERENCE_SIZE;
    private static final int HEADER_SIZE;
    private static final int LONG_SIZE = 8;
    private static final int INT_SIZE = 4;
    private static final int BYTE_SIZE = 1;
    private static final int BOOLEAN_SIZE = 1;
    private static final int CHAR_SIZE = 2;
    private static final int SHORT_SIZE = 2;
    private static final int FLOAT_SIZE = 4;
    private static final int DOUBLE_SIZE = 8;
    private static final int ALIGNMENT = 8;

    static {
        try {
            if (System.getProperties().get("java.vm.name").toString()
                    .contains("64")) {
// java.vm.name is something like
// "Java HotSpot(TM) 64-Bit Server VM"
                REFERENCE_SIZE = 8;
                HEADER_SIZE = 16;
            } else {
                REFERENCE_SIZE = 4;
                HEADER_SIZE = 8;
            }
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    public static long sizeOf(Object o) throws IllegalAccessException {
        return sizeOf(o, new HashSet<ObjectWrapper>());
    }

    public static long sizeOfForAzquo(Object o, List<StringBuilder> report) throws IllegalAccessException {
        return sizeOfForAzquo(o, new HashSet<ObjectWrapper>(), 0, report);
    }

    private static long sizeOf(Object o, Set<ObjectWrapper> visited)
            throws IllegalAccessException {
        if (o == null) {
            return 0;
        }
        ObjectWrapper objectWrapper = new ObjectWrapper(o);
        if (visited.contains(objectWrapper)) {
// We have reference graph with cycles.
            return 0;
        }
        visited.add(objectWrapper);
        long size = HEADER_SIZE;
        Class clazz = o.getClass();
        if (clazz.isArray()) {
            if (clazz == long[].class) {
                long[] objs = (long[]) o;
                size += objs.length * LONG_SIZE;
            } else if (clazz == int[].class) {
                int[] objs = (int[]) o;
                size += objs.length * INT_SIZE;
            } else if (clazz == byte[].class) {
                byte[] objs = (byte[]) o;
                size += objs.length * BYTE_SIZE;
            } else if (clazz == boolean[].class) {
                boolean[] objs = (boolean[]) o;
                size += objs.length * BOOLEAN_SIZE;
            } else if (clazz == char[].class) {
                char[] objs = (char[]) o;
                size += objs.length * CHAR_SIZE;
            } else if (clazz == short[].class) {
                short[] objs = (short[]) o;
                size += objs.length * SHORT_SIZE;
            } else if (clazz == float[].class) {
                float[] objs = (float[]) o;
                size += objs.length * FLOAT_SIZE;
            } else if (clazz == double[].class) {
                double[] objs = (double[]) o;
                size += objs.length * DOUBLE_SIZE;
            } else {
                Object[] objs = (Object[]) o;
                for (int i = 0; i < objs.length; i++) {
                    size += sizeOf(objs[i], visited) + REFERENCE_SIZE;
                }
            }
            size += INT_SIZE;
        } else {
            List<Field> fields = new ArrayList<Field>();
            do {
                Field[] classFields = clazz.getDeclaredFields();
                for (Field field : classFields) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        fields.add(field);
                    }
                }
                clazz = clazz.getSuperclass();
            } while (clazz != null);
            for (Field field : fields) {
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }
                String fieldType = field.getGenericType().toString();
                if (fieldType.equals("long")) {
                    size += LONG_SIZE;
                } else if (fieldType.equals("int")) {
                    size += INT_SIZE;
                } else if (fieldType.equals("byte")) {
                    size += BYTE_SIZE;
                } else if (fieldType.equals("boolean")) {
                    size += BOOLEAN_SIZE;
                } else if (fieldType.equals("char")) {
                    size += CHAR_SIZE;
                } else if (fieldType.equals("short")) {
                    size += SHORT_SIZE;
                } else if (fieldType.equals("float")) {
                    size += FLOAT_SIZE;
                } else if (fieldType.equals("double")) {
                    size += DOUBLE_SIZE;
                } else {
                    size += sizeOf(field.get(o), visited) + REFERENCE_SIZE;
                }
            }
        }
        if ((size % ALIGNMENT) != 0) {
            size = ALIGNMENT * (size / ALIGNMENT + 1);
        }
        return size;
    }

    private static final class ObjectWrapper {
        private Object object;

        public ObjectWrapper(Object object) {
            this.object = object;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != ObjectWrapper.class) {
                return false;
            }
            return object == ((ObjectWrapper) obj).object;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 47 * hash + System.identityHashCode(object);
            return hash;
        }
    }

    private static long sizeOfForAzquo(Object o, Set<ObjectWrapper> visited, int depth, List<StringBuilder> report)
            throws IllegalAccessException {

        StringBuilder info = new StringBuilder();
        if (report != null) report.add(info);
        for (int i = 0; i < depth; i++) {
            if (report != null) info.append(" ");
        }
        if (o == null) {
            if (report != null) info.append("NULL");
            return 0;
        }
        // essentially allow calcs against a name value or provenance but stop navigating when you hit another db entity . . .
        if (depth > 0) {
            if (o instanceof AzquoMemoryDB || o instanceof Name || o instanceof Value || o instanceof Provenance) {
                if (report != null) info.append(o.getClass());
                return 0;
            }
        }
        ObjectWrapper objectWrapper = new ObjectWrapper(o);
        if (visited.contains(objectWrapper)) {
// We have reference graph with cycles.
            info.append("VISITED");
            return 0;
        }
        visited.add(objectWrapper);
        long size = HEADER_SIZE;
        Class clazz = o.getClass();
        if (clazz.isArray()) {
            StringBuilder arrayEntryLine = new StringBuilder(info);
            if (report != null) report.add(arrayEntryLine);
            if (clazz == long[].class) {
                long[] objs = (long[]) o;
                if (report != null) arrayEntryLine.append(" " + objs.length + " long(s) " + objs.length * LONG_SIZE);
                size += objs.length * LONG_SIZE;
            } else if (clazz == int[].class) {
                int[] objs = (int[]) o;
                if (report != null) arrayEntryLine.append(" " + objs.length + " int(s) " + objs.length * INT_SIZE);
                size += objs.length * INT_SIZE;
            } else if (clazz == byte[].class) {
                byte[] objs = (byte[]) o;
                if (report != null) arrayEntryLine.append(" " + objs.length + " byte(s) " + objs.length * BYTE_SIZE);
                size += objs.length * BYTE_SIZE;
            } else if (clazz == boolean[].class) {
                boolean[] objs = (boolean[]) o;
                if (report != null)
                    arrayEntryLine.append(" " + objs.length + " boolean(s) " + objs.length * BOOLEAN_SIZE);
                size += objs.length * BOOLEAN_SIZE;
            } else if (clazz == char[].class) {
                char[] objs = (char[]) o;
                if (report != null) arrayEntryLine.append(" " + objs.length + " char(s) " + objs.length * CHAR_SIZE);
                size += objs.length * CHAR_SIZE;
            } else if (clazz == short[].class) {
                short[] objs = (short[]) o;
                if (report != null) arrayEntryLine.append(" " + objs.length + " short(s) " + objs.length * SHORT_SIZE);
                size += objs.length * SHORT_SIZE;
            } else if (clazz == float[].class) {
                float[] objs = (float[]) o;
                if (report != null) arrayEntryLine.append(" " + objs.length + " float(s) " + objs.length * FLOAT_SIZE);
                size += objs.length * FLOAT_SIZE;
            } else if (clazz == double[].class) {
                double[] objs = (double[]) o;
                if (report != null)
                    arrayEntryLine.append(" " + objs.length + " double(s) " + objs.length * DOUBLE_SIZE);
                size += objs.length * DOUBLE_SIZE;
            } else {
                Object[] objs = (Object[]) o;
                if (report != null) arrayEntryLine.append(" object array (size " + objs.length + ")");
                for (int i = 0; i < objs.length; i++) {
                    StringBuilder arrayEntryObjectLine = new StringBuilder(info);
                    if (report != null) {
                        report.add(arrayEntryObjectLine);
                        arrayEntryObjectLine.append("  object ref " + REFERENCE_SIZE);
                    }
                    size += sizeOfForAzquo(objs[i], visited, depth + 3, report) + REFERENCE_SIZE;
                }
            }
            if (report != null) {
                StringBuilder arrayLength = new StringBuilder(info);
                arrayLength.append("array length int " + INT_SIZE);
                report.add(arrayLength);
            }
            size += INT_SIZE;
        } else {
            List<Field> fields = new ArrayList<Field>();
            do {
                Field[] classFields = clazz.getDeclaredFields();
                for (Field field : classFields) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        fields.add(field);
                    }
                }
                clazz = clazz.getSuperclass();
            } while (clazz != null);
            for (Field field : fields) {
                StringBuilder fieldLine = new StringBuilder(info);
                if (report != null) {
                    report.add(fieldLine);
                    fieldLine.append(" " + field.getName());
                }
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }
                String fieldType = field.getGenericType().toString();
                if (fieldType.equals("long")) {
                    if (report != null) fieldLine.append("long " + LONG_SIZE);
                    size += LONG_SIZE;
                } else if (fieldType.equals("int")) {
                    if (report != null) fieldLine.append("int " + INT_SIZE);
                    size += INT_SIZE;
                } else if (fieldType.equals("byte")) {
                    if (report != null) fieldLine.append("byte " + BYTE_SIZE);
                    size += BYTE_SIZE;
                } else if (fieldType.equals("boolean")) {
                    if (report != null) fieldLine.append("boolean " + BOOLEAN_SIZE);
                    size += BOOLEAN_SIZE;
                } else if (fieldType.equals("char")) {
                    if (report != null) fieldLine.append("char " + CHAR_SIZE);
                    size += CHAR_SIZE;
                } else if (fieldType.equals("short")) {
                    if (report != null) fieldLine.append("short " + SHORT_SIZE);
                    size += SHORT_SIZE;
                } else if (fieldType.equals("float")) {
                    if (report != null) fieldLine.append("float " + FLOAT_SIZE);
                    size += FLOAT_SIZE;
                } else if (fieldType.equals("double")) {
                    if (report != null) fieldLine.append("double " + DOUBLE_SIZE);
                    size += DOUBLE_SIZE;
                } else {
                    if (report != null) fieldLine.append("object ref " + REFERENCE_SIZE);
                    size += sizeOfForAzquo(field.get(o), visited, depth + 2, report) + REFERENCE_SIZE;
                }
            }
        }
        long sizeBefore = size;
        if ((size % ALIGNMENT) != 0) {
            size = ALIGNMENT * (size / ALIGNMENT + 1);
        }

        if (report != null) {
            info.append(o.getClass() + " " + size);
            info.append(" - " + HEADER_SIZE + " header");
            if (size > sizeBefore) {
                info.append(" - " + (size - sizeBefore) + " alignment added");
            }
        }
        return size;
    }


}