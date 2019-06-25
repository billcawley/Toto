package com.azquo;

import java.io.Serializable;

/**
 * Created 25th June 2019
 *
 * I want to move away from generic typed pairs
 */
public class RowColumn implements Serializable {
    private final int row;
    private final int column;

    public RowColumn(int row, int column) {
        this.row = row;
        this.column = column;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    @Override
    public int hashCode() {
        // hacky I know, todo better solution?
        return (row + " " + column).hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof RowColumn){
            RowColumn rc = (RowColumn) o;
            return rc.row == row && rc.column == column;
        }
        return false;
    }
}
