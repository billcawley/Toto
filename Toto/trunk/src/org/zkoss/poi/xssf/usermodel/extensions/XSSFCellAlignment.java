//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.zkoss.poi.xssf.usermodel.extensions;

import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTCellAlignment;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STHorizontalAlignment;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STVerticalAlignment;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STVerticalAlignment.Enum;
import org.zkoss.poi.ss.usermodel.HorizontalAlignment;
import org.zkoss.poi.ss.usermodel.VerticalAlignment;
import org.zkoss.poi.util.Internal;

import java.math.BigInteger;

public class XSSFCellAlignment {
    private CTCellAlignment cellAlignement;

    public XSSFCellAlignment(CTCellAlignment cellAlignment) {
        this.cellAlignement = cellAlignment;
    }

    public VerticalAlignment getVertical() {
        Enum align = this.cellAlignement.getVertical();
        if (align == null) {
            align = STVerticalAlignment.BOTTOM;
        }

        return VerticalAlignment.values()[align.intValue() - 1];
    }

    public void setVertical(VerticalAlignment align) {
        this.cellAlignement.setVertical(Enum.forInt(align.ordinal() + 1));
    }

    public HorizontalAlignment getHorizontal() {
        org.openxmlformats.schemas.spreadsheetml.x2006.main.STHorizontalAlignment.Enum align = this.cellAlignement.getHorizontal();
        if (align == null) {
            align = STHorizontalAlignment.GENERAL;
        }

        return HorizontalAlignment.values()[align.intValue() - 1];
    }

    public void setHorizontal(HorizontalAlignment align) {
        this.cellAlignement.setHorizontal(org.openxmlformats.schemas.spreadsheetml.x2006.main.STHorizontalAlignment.Enum.forInt(align.ordinal() + 1));
    }

    public long getIndent() {
        return this.cellAlignement.getIndent();
    }

    public void setIndent(long indent) {
        this.cellAlignement.setIndent(indent);
    }

    public long getTextRotation() {
        long rotation = this.cellAlignement.getTextRotation().longValue();
        if (rotation == 255L) {
            return rotation;
        } else {
            if (rotation > 90L) {
                rotation = 90L - rotation;
            }

            return rotation;
        }
    }

    public void setTextRotation(long rotation) {
        if (rotation != 255L) {
            if (rotation < 0L && rotation >= -90L) {
                rotation = (long)((short)((int)(90L - rotation)));
            } else if (rotation < -90L || rotation > 90L) {
                rotation = 0L;
            }
        }

        this.cellAlignement.setTextRotation(BigInteger.valueOf(rotation));
    }

    public boolean getWrapText() {
        return this.cellAlignement.getWrapText();
    }

    public void setWrapText(boolean wrapped) {
        this.cellAlignement.setWrapText(wrapped);
    }

    @Internal
    public CTCellAlignment getCTCellAlignment() {
        return this.cellAlignement;
    }
}
