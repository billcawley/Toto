//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//
// edd hacking ZK poi versions to work with the latest ooxml full jar, am not happy about doing this but needs must

package org.zkoss.poi.xssf.usermodel;

import org.openxmlformats.schemas.spreadsheetml.x2006.main.*;
// EFC change
import org.openxmlformats.schemas.officeDocument.x2006.sharedTypes.STVerticalAlignRun;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTFont.Factory;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STUnderlineValues.Enum;
import org.zkoss.poi.POIXMLException;
import org.zkoss.poi.ss.usermodel.Font;
import org.zkoss.poi.ss.usermodel.FontCharset;
import org.zkoss.poi.ss.usermodel.FontFamily;
import org.zkoss.poi.ss.usermodel.FontScheme;
import org.zkoss.poi.ss.usermodel.FontUnderline;
import org.zkoss.poi.ss.usermodel.IndexedColors;
import org.zkoss.poi.util.Internal;
import org.zkoss.poi.xssf.model.StylesTable;
import org.zkoss.poi.xssf.model.ThemesTable;

public class XSSFFont implements Font {
    public static final String DEFAULT_FONT_NAME = "Calibri";
    public static final short DEFAULT_FONT_SIZE = 11;
    public static final short DEFAULT_FONT_COLOR;
    private ThemesTable _themes;
    private CTFont _ctFont;
    private short _index;

    public XSSFFont(CTFont font) {
        this._ctFont = font;
        this._index = 0;
    }

    public XSSFFont(CTFont font, int index) {
        this._ctFont = font;
        this._index = (short)index;
    }

    protected XSSFFont() {
        this._ctFont = Factory.newInstance();
        this.setFontName("Calibri");
        this.setFontHeight(11.0D);
    }

    @Internal
    public CTFont getCTFont() {
        return this._ctFont;
    }

    public boolean getBold() {
        CTBooleanProperty bold = this._ctFont.sizeOfBArray() == 0 ? null : this._ctFont.getBArray(0);
        return bold != null && bold.getVal();
    }

    public int getCharSet() {
        CTIntProperty charset = this._ctFont.sizeOfCharsetArray() == 0 ? null : this._ctFont.getCharsetArray(0);
        int val = charset == null ? FontCharset.ANSI.getValue() : FontCharset.valueOf(charset.getVal()).getValue();
        return val;
    }

    public short getColor() {
        CTColor color = this._ctFont.sizeOfColorArray() == 0 ? null : this._ctFont.getColorArray(0);
        if (color == null) {
            return IndexedColors.BLACK.getIndex();
        } else {
            long index = color.getIndexed();
            if (index == (long)DEFAULT_FONT_COLOR) {
                return IndexedColors.BLACK.getIndex();
            } else {
                return index == (long)IndexedColors.RED.getIndex() ? IndexedColors.RED.getIndex() : (short)((int)index);
            }
        }
    }

    public XSSFColor getXSSFColor() {
        CTColor ctColor = this._ctFont.sizeOfColorArray() == 0 ? null : this._ctFont.getColorArray(0);
        if (ctColor != null) {
            XSSFColor color = new XSSFColor(ctColor);
            if (this._themes != null) {
                this._themes.inheritFromThemeAsRequired(color);
            }

            return color;
        } else {
            return null;
        }
    }

    public short getThemeColor() {
        CTColor color = this._ctFont.sizeOfColorArray() == 0 ? null : this._ctFont.getColorArray(0);
        long index = color == null ? 0L : color.getTheme();
        return (short)((int)index);
    }

    public short getFontHeight() {
        CTFontSize size = this._ctFont.sizeOfSzArray() == 0 ? null : this._ctFont.getSzArray(0);
        if (size != null) {
            double fontHeight = size.getVal();
            return (short)((int)(fontHeight * 20.0D));
        } else {
            return 220;
        }
    }

    public short getFontHeightInPoints() {
        return (short)(this.getFontHeight() / 20);
    }

    public String getFontName() {
        CTFontName name = this._ctFont.sizeOfNameArray() == 0 ? null : this._ctFont.getNameArray(0);
        return name == null ? "Calibri" : name.getVal();
    }

    public boolean getItalic() {
        CTBooleanProperty italic = this._ctFont.sizeOfIArray() == 0 ? null : this._ctFont.getIArray(0);
        return italic != null && italic.getVal();
    }

    public boolean getStrikeout() {
        CTBooleanProperty strike = this._ctFont.sizeOfStrikeArray() == 0 ? null : this._ctFont.getStrikeArray(0);
        return strike != null && strike.getVal();
    }

    public short getTypeOffset() {
        CTVerticalAlignFontProperty vAlign = this._ctFont.sizeOfVertAlignArray() == 0 ? null : this._ctFont.getVertAlignArray(0);
        if (vAlign == null) {
            return 0;
        } else {
            int val = vAlign.getVal().intValue();
            switch(val) {
                case 1:
                    return 0;
                case 2:
                    return 1;
                case 3:
                    return 2;
                default:
                    throw new POIXMLException("Wrong offset value " + val);
            }
        }
    }

    public byte getUnderline() {
        CTUnderlineProperty underline = this._ctFont.sizeOfUArray() == 0 ? null : this._ctFont.getUArray(0);
        if (underline != null) {
            FontUnderline val = FontUnderline.valueOf(underline.getVal().intValue());
            return val.getByteValue();
        } else {
            return 0;
        }
    }

    public void setBold(boolean bold) {
        if (bold) {
            CTBooleanProperty ctBold = this._ctFont.sizeOfBArray() == 0 ? this._ctFont.addNewB() : this._ctFont.getBArray(0);
            ctBold.setVal(bold);
        } else {
            this._ctFont.setBArray((CTBooleanProperty[])null);
        }

    }

    public void setBoldweight(short boldweight) {
        this.setBold(boldweight == 700);
    }

    public short getBoldweight() {
        return (short)(this.getBold() ? 700 : 400);
    }

    public void setCharSet(byte charset) {
        int cs = charset;
        if (charset < 0) {
            cs = charset + 256;
        }

        this.setCharSet(cs);
    }

    public void setCharSet(int charset) {
        FontCharset fontCharset = FontCharset.valueOf(charset);
        if (fontCharset != null) {
            this.setCharSet(fontCharset);
        } else {
            throw new POIXMLException("Attention: an attempt to set a type of unknow charset and charset");
        }
    }

    public void setCharSet(FontCharset charSet) {
        CTIntProperty charsetProperty;
        if (this._ctFont.sizeOfCharsetArray() == 0) {
            charsetProperty = this._ctFont.addNewCharset();
        } else {
            charsetProperty = this._ctFont.getCharsetArray(0);
        }

        charsetProperty.setVal(charSet.getValue());
    }

    public void setColor(short color) {
        CTColor ctColor = this._ctFont.sizeOfColorArray() == 0 ? this._ctFont.addNewColor() : this._ctFont.getColorArray(0);
        switch(color) {
            case 10:
                ctColor.setIndexed((long)IndexedColors.RED.getIndex());
                break;
            case 32767:
                ctColor.setIndexed((long)DEFAULT_FONT_COLOR);
                break;
            default:
                ctColor.setIndexed((long)color);
        }

    }

    public void setColor(XSSFColor color) {
        if (color == null) {
            this._ctFont.setColorArray((CTColor[])null);
        } else {
            CTColor srcctColor = color.getCTColor();
            CTColor ctColor = this._ctFont.sizeOfColorArray() == 0 ? this._ctFont.addNewColor() : this._ctFont.getColorArray(0);
            if (srcctColor.isSetIndexed()) {
                ctColor.setIndexed(color.getCTColor().getIndexed());
            } else if (srcctColor.isSetTheme()) {
                ctColor.setTheme(srcctColor.getTheme());
            } else {
                ctColor.setRgb(color.isArgb() ? color.getARgb() : color.getRgb());
            }

            if (srcctColor.isSetTint()) {
                ctColor.setTint(srcctColor.getTint());
            }
        }

    }

    public void setFontHeight(short height) {
        this.setFontHeight((double)height / 20.0D);
    }

    public void setFontHeight(double height) {
        CTFontSize fontSize = this._ctFont.sizeOfSzArray() == 0 ? this._ctFont.addNewSz() : this._ctFont.getSzArray(0);
        fontSize.setVal(height);
    }

    public void setFontHeightInPoints(short height) {
        this.setFontHeight((double)height);
    }

    public void setThemeColor(short theme) {
        CTColor ctColor = this._ctFont.sizeOfColorArray() == 0 ? this._ctFont.addNewColor() : this._ctFont.getColorArray(0);
        ctColor.setTheme((long)theme);
    }

    public void setFontName(String name) {
        CTFontName fontName = this._ctFont.sizeOfNameArray() == 0 ? this._ctFont.addNewName() : this._ctFont.getNameArray(0);
        fontName.setVal(name == null ? "Calibri" : name);
    }

    public void setItalic(boolean italic) {
        if (italic) {
            CTBooleanProperty bool = this._ctFont.sizeOfIArray() == 0 ? this._ctFont.addNewI() : this._ctFont.getIArray(0);
            bool.setVal(italic);
        } else {
            this._ctFont.setIArray((CTBooleanProperty[])null);
        }

    }

    public void setStrikeout(boolean strikeout) {
        if (!strikeout) {
            this._ctFont.setStrikeArray((CTBooleanProperty[])null);
        } else {
            CTBooleanProperty strike = this._ctFont.sizeOfStrikeArray() == 0 ? this._ctFont.addNewStrike() : this._ctFont.getStrikeArray(0);
            strike.setVal(strikeout);
        }

    }

    public void setTypeOffset(short offset) {
        if (offset == 0) {
            this._ctFont.setVertAlignArray((CTVerticalAlignFontProperty[])null);
        } else {
            CTVerticalAlignFontProperty offsetProperty = this._ctFont.sizeOfVertAlignArray() == 0 ? this._ctFont.addNewVertAlign() : this._ctFont.getVertAlignArray(0);
            switch(offset) {
                case 0:
                    offsetProperty.setVal(STVerticalAlignRun.BASELINE);
                    break;
                case 1:
                    offsetProperty.setVal(STVerticalAlignRun.SUPERSCRIPT);
                    break;
                case 2:
                    offsetProperty.setVal(STVerticalAlignRun.SUBSCRIPT);
            }
        }

    }

    public void setUnderline(byte underline) {
        this.setUnderline(FontUnderline.valueOf(underline));
    }

    public void setUnderline(FontUnderline underline) {
        if (underline == FontUnderline.NONE && this._ctFont.sizeOfUArray() > 0) {
            this._ctFont.setUArray((CTUnderlineProperty[])null);
        } else {
            CTUnderlineProperty ctUnderline = this._ctFont.sizeOfUArray() == 0 ? this._ctFont.addNewU() : this._ctFont.getUArray(0);
            Enum val = Enum.forInt(underline.getValue());
            ctUnderline.setVal(val);
        }

    }

    public String toString() {
        return this._ctFont.toString();
    }

    public long registerTo(StylesTable styles) {
        this._themes = styles.getTheme();
        short idx = (short)styles.putFont(this, true);
        this._index = idx;
        return (long)idx;
    }

    public void setThemesTable(ThemesTable themes) {
        this._themes = themes;
    }

    public FontScheme getScheme() {
        CTFontScheme scheme = this._ctFont.sizeOfSchemeArray() == 0 ? null : this._ctFont.getSchemeArray(0);
        return scheme == null ? FontScheme.NONE : FontScheme.valueOf(scheme.getVal().intValue());
    }

    public void setScheme(FontScheme scheme) {
        CTFontScheme ctFontScheme = this._ctFont.sizeOfSchemeArray() == 0 ? this._ctFont.addNewScheme() : this._ctFont.getSchemeArray(0);
        org.openxmlformats.schemas.spreadsheetml.x2006.main.STFontScheme.Enum val = org.openxmlformats.schemas.spreadsheetml.x2006.main.STFontScheme.Enum.forInt(scheme.getValue());
        ctFontScheme.setVal(val);
    }

    // EFC change these two functions
    public int getFamily() {
        CTFontFamily family = this._ctFont.sizeOfFamilyArray() == 0 ? this._ctFont.addNewFamily() : this._ctFont.getFamilyArray(0);
        return family == null ? FontFamily.NOT_APPLICABLE.getValue() : FontFamily.valueOf(family.getVal()).getValue();
    }

    public void setFamily(int value) {
        CTFontFamily family = this._ctFont.sizeOfFamilyArray() == 0 ? this._ctFont.addNewFamily() : this._ctFont.getFamilyArray(0);
        family.setVal(value);
    }

    /*
    public int getFamily() {
        CTIntProperty family = this._ctFont.sizeOfFamilyArray() == 0 ? this._ctFont.addNewFamily() : this._ctFont.getFamilyArray(0);
        return family == null ? FontFamily.NOT_APPLICABLE.getValue() : FontFamily.valueOf(family.getVal()).getValue();
    }

    public void setFamily(int value) {
        CTIntProperty family = this._ctFont.sizeOfFamilyArray() == 0 ? this._ctFont.addNewFamily() : this._ctFont.getFamilyArray(0);
        family.setVal(value);
    }
     */

    public void setFamily(FontFamily family) {
        this.setFamily(family.getValue());
    }

    public short getIndex() {
        return this._index;
    }

    public int hashCode() {
        return this._ctFont.toString().hashCode();
    }

    public boolean equals(Object o) {
        if (!(o instanceof XSSFFont)) {
            return false;
        } else {
            XSSFFont cf = (XSSFFont)o;
            return this._ctFont.toString().equals(cf.getCTFont().toString());
        }
    }

    public boolean isOverrideName() {
        CTFontName name = this._ctFont.sizeOfNameArray() == 0 ? null : this._ctFont.getNameArray(0);
        return name != null;
    }

    public boolean isOverrideColor() {
        CTColor color = this._ctFont.sizeOfColorArray() == 0 ? null : this._ctFont.getColorArray(0);
        return color != null;
    }

    public boolean isOverrideBold() {
        CTBooleanProperty bold = this._ctFont.sizeOfBArray() == 0 ? null : this._ctFont.getBArray(0);
        return bold != null;
    }

    public boolean isOverrideItalic() {
        CTBooleanProperty italic = this._ctFont.sizeOfIArray() == 0 ? null : this._ctFont.getIArray(0);
        return italic != null;
    }

    public boolean isOverrideStrikeout() {
        CTBooleanProperty strike = this._ctFont.sizeOfStrikeArray() == 0 ? null : this._ctFont.getStrikeArray(0);
        return strike != null;
    }

    public boolean isOverrideUnderline() {
        CTUnderlineProperty underline = this._ctFont.sizeOfUArray() == 0 ? null : this._ctFont.getUArray(0);
        return underline != null;
    }

    public boolean isOverrideHeightPoints() {
        CTFontSize size = this._ctFont.sizeOfSzArray() == 0 ? null : this._ctFont.getSzArray(0);
        return size != null;
    }

    public boolean isOverrideTypeOffset() {
        CTVerticalAlignFontProperty vAlign = this._ctFont.sizeOfVertAlignArray() == 0 ? null : this._ctFont.getVertAlignArray(0);
        return vAlign != null;
    }

    public static XSSFFont createDxfFont() {
        return new XSSFFont(Factory.newInstance());
    }

    static {
        DEFAULT_FONT_COLOR = IndexedColors.BLACK.getIndex();
    }
}
