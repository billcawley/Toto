//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

// EFC note - I've not modified this from the decompiler and yet is solves a runtime linking problem . . .

package org.zkoss.poi.xssf.usermodel;

import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTMarker;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTMarker.Factory;
import org.zkoss.poi.ss.usermodel.ClientAnchor;
import org.zkoss.poi.util.Internal;

public final class XSSFClientAnchor extends XSSFAnchor implements ClientAnchor {
    private int anchorType;
    private CTMarker cell1;
    private CTMarker cell2;

    public XSSFClientAnchor() {
        this.cell1 = Factory.newInstance();
        this.cell1.setCol(0);
        this.cell1.setColOff(0L);
        this.cell1.setRow(0);
        this.cell1.setRowOff(0L);
        this.cell2 = Factory.newInstance();
        this.cell2.setCol(0);
        this.cell2.setColOff(0L);
        this.cell2.setRow(0);
        this.cell2.setRowOff(0L);
    }

    public XSSFClientAnchor(int dx1, int dy1, int dx2, int dy2, int col1, int row1, int col2, int row2) {
        this();
        this.cell1.setCol(col1);
        this.cell1.setColOff((long)dx1);
        this.cell1.setRow(row1);
        this.cell1.setRowOff((long)dy1);
        this.cell2.setCol(col2);
        this.cell2.setColOff((long)dx2);
        this.cell2.setRow(row2);
        this.cell2.setRowOff((long)dy2);
    }

    protected XSSFClientAnchor(CTMarker cell1, CTMarker cell2) {
        this.cell1 = cell1;
        this.cell2 = cell2;
    }

    public short getCol1() {
        return (short)this.cell1.getCol();
    }

    public void setCol1(int col1) {
        this.cell1.setCol(col1);
    }

    public short getCol2() {
        return (short)this.cell2.getCol();
    }

    public void setCol2(int col2) {
        this.cell2.setCol(col2);
    }

    public int getRow1() {
        return this.cell1.getRow();
    }

    public void setRow1(int row1) {
        this.cell1.setRow(row1);
    }

    public int getRow2() {
        return this.cell2.getRow();
    }

    public void setRow2(int row2) {
        this.cell2.setRow(row2);
    }

    public int getDx1() {
        return (int)this.cell1.getColOff();
    }

    public void setDx1(int dx1) {
        this.cell1.setColOff((long)dx1);
    }

    public int getDy1() {
        return (int)this.cell1.getRowOff();
    }

    public void setDy1(int dy1) {
        this.cell1.setRowOff((long)dy1);
    }

    public int getDy2() {
        return (int)this.cell2.getRowOff();
    }

    public void setDy2(int dy2) {
        this.cell2.setRowOff((long)dy2);
    }

    public int getDx2() {
        return (int)this.cell2.getColOff();
    }

    public void setDx2(int dx2) {
        this.cell2.setColOff((long)dx2);
    }

    public boolean equals(Object o) {
        if (o != null && o instanceof XSSFClientAnchor) {
            XSSFClientAnchor anchor = (XSSFClientAnchor)o;
            return this.getDx1() == anchor.getDx1() && this.getDx2() == anchor.getDx2() && this.getDy1() == anchor.getDy1() && this.getDy2() == anchor.getDy2() && this.getCol1() == anchor.getCol1() && this.getCol2() == anchor.getCol2() && this.getRow1() == anchor.getRow1() && this.getRow2() == anchor.getRow2();
        } else {
            return false;
        }
    }

    public String toString() {
        return "from : " + this.cell1.toString() + "; to: " + this.cell2.toString();
    }

    @Internal
    public CTMarker getFrom() {
        return this.cell1;
    }

    protected void setFrom(CTMarker from) {
        this.cell1 = from;
    }

    @Internal
    public CTMarker getTo() {
        return this.cell2;
    }

    protected void setTo(CTMarker to) {
        this.cell2 = to;
    }

    public void setAnchorType(int anchorType) {
        this.anchorType = anchorType;
    }

    public int getAnchorType() {
        return this.anchorType;
    }

    public boolean isSet() {
        return this.cell1.getCol() != 0 || this.cell2.getCol() != 0 || this.cell1.getRow() != 0 || this.cell2.getRow() != 0;
    }
}
