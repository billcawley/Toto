//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.zkoss.poi.xssf.usermodel;

import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.namespace.QName;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTColor;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTFont;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTRElt;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTRPrElt;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTRst;
//EFC change
import org.openxmlformats.schemas.officeDocument.x2006.sharedTypes.STXstring;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTRst.Factory;
import org.zkoss.poi.ss.usermodel.Font;
import org.zkoss.poi.ss.usermodel.RichTextString;
import org.zkoss.poi.util.Internal;
import org.zkoss.poi.xssf.model.StylesTable;
import org.zkoss.poi.xssf.model.ThemesTable;

public class XSSFRichTextString implements RichTextString {
    private static final Pattern utfPtrn = Pattern.compile("_x([0-9A-F]{4})_");
    private CTRst st;
    private StylesTable styles;

    public XSSFRichTextString(String str) {
        this.st = Factory.newInstance();
        this.st.setT(utfEncode(str));
        preserveSpaces(this.st.xgetT());
    }

    public XSSFRichTextString() {
        this.st = Factory.newInstance();
    }

    public XSSFRichTextString(CTRst st) {
        this.st = st;
    }

    public void applyFont(int startIndex, int endIndex, short fontIndex) {
        XSSFFont font;
        if (this.styles == null) {
            font = new XSSFFont();
            font.setFontName("#" + fontIndex);
        } else {
            font = this.styles.getFontAt(fontIndex);
        }

        this.applyFont(startIndex, endIndex, font);
    }

    public void applyFont(int startIndex, int endIndex, Font font) {
        if (startIndex > endIndex) {
            throw new IllegalArgumentException("Start index must be less than end index.");
        } else if (startIndex >= 0 && endIndex <= this.length()) {
            if (startIndex != endIndex) {
                if (this.st.sizeOfRArray() == 0 && this.st.isSetT()) {
                    this.st.addNewR().setT(this.st.getT());
                    this.st.unsetT();
                }

                String text = this.getRawString();
                XSSFFont xssfFont = (XSSFFont)font;
                TreeMap<Integer, CTRPrElt> formats = this.getFormatMap(this.st);
                CTRPrElt fmt = xssfFont == null ? null : org.openxmlformats.schemas.spreadsheetml.x2006.main.CTRPrElt.Factory.newInstance();
                if (fmt != null) {
                    this.setRunAttributes(xssfFont.getCTFont(), fmt);
                }

                this.applyFont(formats, startIndex, endIndex, fmt);
                CTRst newSt = this.buildCTRst(text, formats);
                this.st.set(newSt);
            }
        } else {
            throw new IllegalArgumentException("Start and end index not in range.");
        }
    }

    public void applyFont(Font font) {
        String text = this.getString();
        this.applyFont(0, text.length(), font);
    }

    public void applyFont(short fontIndex) {
        XSSFFont font;
        if (this.styles == null) {
            font = new XSSFFont();
            font.setFontName("#" + fontIndex);
        } else {
            font = this.styles.getFontAt(fontIndex);
        }

        String text = this.getString();
        this.applyFont(0, text.length(), font);
    }

    public void append(String text, XSSFFont font) {
        CTRElt lt;
        if (this.st.sizeOfRArray() == 0 && this.st.isSetT()) {
            lt = this.st.addNewR();
            lt.setT(this.st.getT());
            preserveSpaces(lt.xgetT());
            this.st.unsetT();
        }

        lt = this.st.addNewR();
        lt.setT(text);
        preserveSpaces(lt.xgetT());
        CTRPrElt pr = lt.addNewRPr();
        if (font != null) {
            this.setRunAttributes(font.getCTFont(), pr);
        }

    }

    public void append(String text) {
        this.append(text, (XSSFFont)null);
    }

    private void setRunAttributes(CTFont ctFont, CTRPrElt pr) {
        if (ctFont.sizeOfBArray() > 0) {
            pr.addNewB().setVal(ctFont.getBArray(0).getVal());
        }

        if (ctFont.sizeOfUArray() > 0) {
            pr.addNewU().setVal(ctFont.getUArray(0).getVal());
        }

        if (ctFont.sizeOfIArray() > 0) {
            pr.addNewI().setVal(ctFont.getIArray(0).getVal());
        }

        if (ctFont.sizeOfColorArray() > 0) {
            CTColor c1 = ctFont.getColorArray(0);
            CTColor c2 = pr.addNewColor();
            if (c1.isSetAuto()) {
                c2.setAuto(c1.getAuto());
            }

            if (c1.isSetIndexed()) {
                c2.setIndexed(c1.getIndexed());
            }

            if (c1.isSetRgb()) {
                c2.setRgb(c1.getRgb());
            }

            if (c1.isSetTheme()) {
                c2.setTheme(c1.getTheme());
            }

            if (c1.isSetTint()) {
                c2.setTint(c1.getTint());
            }
        }

        if (ctFont.sizeOfSzArray() > 0) {
            pr.addNewSz().setVal(ctFont.getSzArray(0).getVal());
        }

        if (ctFont.sizeOfNameArray() > 0) {
            pr.addNewRFont().setVal(ctFont.getNameArray(0).getVal());
        }

        if (ctFont.sizeOfFamilyArray() > 0) {
            pr.addNewFamily().setVal(ctFont.getFamilyArray(0).getVal());
        }

        if (ctFont.sizeOfSchemeArray() > 0) {
            pr.addNewScheme().setVal(ctFont.getSchemeArray(0).getVal());
        }

        if (ctFont.sizeOfCharsetArray() > 0) {
            pr.addNewCharset().setVal(ctFont.getCharsetArray(0).getVal());
        }

        if (ctFont.sizeOfCondenseArray() > 0) {
            pr.addNewCondense().setVal(ctFont.getCondenseArray(0).getVal());
        }

        if (ctFont.sizeOfExtendArray() > 0) {
            pr.addNewExtend().setVal(ctFont.getExtendArray(0).getVal());
        }

        if (ctFont.sizeOfVertAlignArray() > 0) {
            pr.addNewVertAlign().setVal(ctFont.getVertAlignArray(0).getVal());
        }

        if (ctFont.sizeOfOutlineArray() > 0) {
            pr.addNewOutline().setVal(ctFont.getOutlineArray(0).getVal());
        }

        if (ctFont.sizeOfShadowArray() > 0) {
            pr.addNewShadow().setVal(ctFont.getShadowArray(0).getVal());
        }

        if (ctFont.sizeOfStrikeArray() > 0) {
            pr.addNewStrike().setVal(ctFont.getStrikeArray(0).getVal());
        }

    }

    public void clearFormatting() {
        String text = this.getString();
        this.st.setRArray((CTRElt[])null);
        this.st.setT(text);
    }

    public int getIndexOfFormattingRun(int index) {
        if (this.st.sizeOfRArray() == 0) {
            return 0;
        } else {
            int pos = 0;

            for(int i = 0; i < this.st.sizeOfRArray(); ++i) {
                CTRElt r = this.st.getRArray(i);
                if (i == index) {
                    return pos;
                }

                pos += r.getT().length();
            }

            return -1;
        }
    }

    public int getLengthOfFormattingRun(int index) {
        if (this.st.sizeOfRArray() == 0) {
            return this.length();
        } else {
            for(int i = 0; i < this.st.sizeOfRArray(); ++i) {
                CTRElt r = this.st.getRArray(i);
                if (i == index) {
                    return r.getT().length();
                }
            }

            return -1;
        }
    }

    public String getString() {
        if (this.st.sizeOfRArray() == 0) {
            return utfDecode(this.st.getT());
        } else {
            StringBuffer buf = new StringBuffer();
            CTRElt[] var2 = this.st.getRArray();
            int var3 = var2.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                CTRElt r = var2[var4];
                buf.append(r.getT());
            }

            return utfDecode(buf.toString());
        }
    }

    public void setString(String s) {
        this.clearFormatting();
        this.st.setT(s);
        preserveSpaces(this.st.xgetT());
    }

    public String toString() {
        return this.getString();
    }

    public int length() {
        return this.getString().length();
    }

    public int numFormattingRuns() {
        return this.st.sizeOfRArray();
    }

    public XSSFFont getFontOfFormattingRun(XSSFWorkbook book, int index) {
        if (index >= 0 && index < this.st.sizeOfRArray()) {
            CTRElt r = this.st.getRArray(index);
            CTRPrElt rpr = r.getRPr();
            if (rpr != null) {
                XSSFFont fnt = new XSSFFont(toCTFont(rpr));
                fnt.setThemesTable(book.getStylesSource().getTheme());
                Font fnt0 = book.getStylesSource().findFont(fnt.getBoldweight(), fnt.getXSSFColor(), fnt.getFontHeight(), fnt.getFontName(), fnt.getItalic(), fnt.getStrikeout(), fnt.getTypeOffset(), fnt.getUnderline());
                if (fnt0 == null) {
                    fnt.setThemesTable(this.getThemesTable());
                    fnt.registerTo(book.getStylesSource());
                    return fnt;
                } else {
                    return (XSSFFont)fnt0;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public XSSFFont getFontAtIndex(int index) {
        if (this.st.sizeOfRArray() == 0) {
            return null;
        } else {
            int pos = 0;

            for(int i = 0; i < this.st.sizeOfRArray(); ++i) {
                CTRElt r = this.st.getRArray(i);
                if (index >= pos && index < pos + r.getT().length()) {
                    XSSFFont fnt = new XSSFFont(toCTFont(r.getRPr()));
                    fnt.setThemesTable(this.getThemesTable());
                    return fnt;
                }

                pos += r.getT().length();
            }

            return null;
        }
    }

    @Internal
    public CTRst getCTRst() {
        return this.st;
    }

    protected void setStylesTableReference(StylesTable tbl) {
        this.styles = tbl;
        if (this.st.sizeOfRArray() > 0) {
            CTRElt[] var2 = this.st.getRArray();
            int var3 = var2.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                CTRElt r = var2[var4];
                CTRPrElt pr = r.getRPr();
                if (pr != null && pr.sizeOfRFontArray() > 0) {
                    String fontName = pr.getRFontArray(0).getVal();
                    if (fontName.startsWith("#")) {
                        int idx = Integer.parseInt(fontName.substring(1));
                        XSSFFont font = this.styles.getFontAt(idx);
                        pr.removeRFont(0);
                        this.setRunAttributes(font.getCTFont(), pr);
                    }
                }
            }
        }

    }

    protected static CTFont toCTFont(CTRPrElt pr) {
        CTFont ctFont = org.openxmlformats.schemas.spreadsheetml.x2006.main.CTFont.Factory.newInstance();
        if (pr.sizeOfBArray() > 0) {
            ctFont.addNewB().setVal(pr.getBArray(0).getVal());
        }

        if (pr.sizeOfUArray() > 0) {
            ctFont.addNewU().setVal(pr.getUArray(0).getVal());
        }

        if (pr.sizeOfIArray() > 0) {
            ctFont.addNewI().setVal(pr.getIArray(0).getVal());
        }

        if (pr.sizeOfColorArray() > 0) {
            CTColor c1 = pr.getColorArray(0);
            CTColor c2 = ctFont.addNewColor();
            if (c1.isSetAuto()) {
                c2.setAuto(c1.getAuto());
            }

            if (c1.isSetIndexed()) {
                c2.setIndexed(c1.getIndexed());
            }

            if (c1.isSetRgb()) {
                c2.setRgb(c1.getRgb());
            }

            if (c1.isSetTheme()) {
                c2.setTheme(c1.getTheme());
            }

            if (c1.isSetTint()) {
                c2.setTint(c1.getTint());
            }
        }

        if (pr.sizeOfSzArray() > 0) {
            ctFont.addNewSz().setVal(pr.getSzArray(0).getVal());
        }

        if (pr.sizeOfRFontArray() > 0) {
            ctFont.addNewName().setVal(pr.getRFontArray(0).getVal());
        }

        if (pr.sizeOfFamilyArray() > 0) {
            ctFont.addNewFamily().setVal(pr.getFamilyArray(0).getVal());
        }

        if (pr.sizeOfSchemeArray() > 0) {
            ctFont.addNewScheme().setVal(pr.getSchemeArray(0).getVal());
        }

        if (pr.sizeOfCharsetArray() > 0) {
            ctFont.addNewCharset().setVal(pr.getCharsetArray(0).getVal());
        }

        if (pr.sizeOfCondenseArray() > 0) {
            ctFont.addNewCondense().setVal(pr.getCondenseArray(0).getVal());
        }

        if (pr.sizeOfExtendArray() > 0) {
            ctFont.addNewExtend().setVal(pr.getExtendArray(0).getVal());
        }

        if (pr.sizeOfVertAlignArray() > 0) {
            ctFont.addNewVertAlign().setVal(pr.getVertAlignArray(0).getVal());
        }

        if (pr.sizeOfOutlineArray() > 0) {
            ctFont.addNewOutline().setVal(pr.getOutlineArray(0).getVal());
        }

        if (pr.sizeOfShadowArray() > 0) {
            ctFont.addNewShadow().setVal(pr.getShadowArray(0).getVal());
        }

        if (pr.sizeOfStrikeArray() > 0) {
            ctFont.addNewStrike().setVal(pr.getStrikeArray(0).getVal());
        }

        return ctFont;
    }

    protected static void preserveSpaces(STXstring xs) {
        String text = xs.getStringValue();
        if (text != null && text.length() > 0) {
            char firstChar = text.charAt(0);
            char lastChar = text.charAt(text.length() - 1);
            if (Character.isWhitespace(firstChar) || Character.isWhitespace(lastChar)) {
                XmlCursor c = xs.newCursor();
                c.toNextToken();
                c.insertAttributeWithValue(new QName("http://www.w3.org/XML/1998/namespace", "space"), "preserve");
                c.dispose();
            }
        }

    }

    static String utfDecode(String value) {
        if (value == null) {
            return null;
        } else {
            StringBuffer buf = new StringBuffer();
            Matcher m = utfPtrn.matcher(value);

            int idx;
            for(idx = 0; m.find(); idx = m.end()) {
                int pos = m.start();
                if (pos > idx) {
                    buf.append(value.substring(idx, pos));
                }

                String code = m.group(1);
                int icode = Integer.decode("0x" + code);
                buf.append((char)icode);
            }

            buf.append(value.substring(idx));
            return buf.toString();
        }
    }

    void applyFont(TreeMap<Integer, CTRPrElt> formats, int startIndex, int endIndex, CTRPrElt fmt) {
        int runStartIdx = 0;

        Iterator it;
        int runEndIdx;
        for(it = formats.keySet().iterator(); it.hasNext(); runStartIdx = runEndIdx) {
            runEndIdx = (Integer)it.next();
            if (runStartIdx >= startIndex && runEndIdx < endIndex) {
                it.remove();
            }
        }

        if (startIndex > 0 && !formats.containsKey(startIndex)) {
            it = formats.entrySet().iterator();

            while(it.hasNext()) {
                Entry<Integer, CTRPrElt> entry = (Entry)it.next();
                if ((Integer)entry.getKey() > startIndex) {
                    formats.put(startIndex, entry.getValue());
                    break;
                }
            }
        }

        if (fmt != null) {
            formats.put(endIndex, fmt);
        }

        SortedMap sub = formats.subMap(startIndex, endIndex);

        while(sub.size() > 1) {
            sub.remove(sub.lastKey());
        }

    }

    TreeMap<Integer, CTRPrElt> getFormatMap(CTRst entry) {
        int length = 0;
        TreeMap<Integer, CTRPrElt> formats = new TreeMap();
        CTRElt[] var4 = entry.getRArray();
        int var5 = var4.length;

        for(int var6 = 0; var6 < var5; ++var6) {
            CTRElt r = var4[var6];
            String txt = r.getT();
            CTRPrElt fmt = r.getRPr();
            length += txt.length();
            formats.put(length, fmt);
        }

        return formats;
    }

    CTRst buildCTRst(String text, TreeMap<Integer, CTRPrElt> formats) {
        if (text.length() != (Integer)formats.lastKey()) {
            throw new IllegalArgumentException("Text length was " + text.length() + " but the last format index was " + formats.lastKey());
        } else {
            CTRst st = Factory.newInstance();
            int runStartIdx = 0;

            int runEndIdx;
            for(Iterator it = formats.keySet().iterator(); it.hasNext(); runStartIdx = runEndIdx) {
                runEndIdx = (Integer)it.next();
                CTRElt run = st.addNewR();
                String fragment = text.substring(runStartIdx, runEndIdx);
                run.setT(fragment);
                preserveSpaces(run.xgetT());
                CTRPrElt fmt = (CTRPrElt)formats.get(runEndIdx);
                if (fmt != null) {
                    run.setRPr(fmt);
                }
            }

            return st;
        }
    }

    private ThemesTable getThemesTable() {
        return this.styles == null ? null : this.styles.getTheme();
    }

    public String getStringAt(int runIndex) {
        CTRElt r = this.st.getRArray()[runIndex];
        return utfDecode(r.getT().toString());
    }

    public static String utfEncode(String value) {
        if (value == null) {
            return null;
        } else {
            StringBuilder buf = new StringBuilder();
            int j = 0;

            for(int len = value.length(); j < len; ++j) {
                char ch = value.charAt(j);
                if (Character.isISOControl(ch) && ch != '\n') {
                    String s = Integer.toString(ch, 16).toUpperCase();
                    buf.append("_x").append(s.length() <= 4 ? "0000".substring(s.length()) + s : s).append("_");
                } else {
                    buf.append(ch);
                }
            }

            return buf.toString();
        }
    }

    public String getRawString() {
        if (this.st.sizeOfRArray() == 0) {
            return this.st.getT();
        } else {
            StringBuffer buf = new StringBuffer();
            CTRElt[] var2 = this.st.getRArray();
            int var3 = var2.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                CTRElt r = var2[var4];
                buf.append(r.getT());
            }

            return buf.toString();
        }
    }

    public void addRun(String text, XSSFFont font) {
        if (this.st.sizeOfRArray() == 0 && this.st.isSetT()) {
            this.st.unsetT();
        }

        CTRElt lt = this.st.addNewR();
        lt.setT(utfEncode(text));
        preserveSpaces(lt.xgetT());
        CTRPrElt pr = lt.addNewRPr();
        if (font != null) {
            this.setRunAttributes(font.getCTFont(), pr);
        }

    }
}
