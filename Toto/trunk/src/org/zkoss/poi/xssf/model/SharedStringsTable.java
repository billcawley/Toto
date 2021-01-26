//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.zkoss.poi.xssf.model;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTRst;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTSst;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.SstDocument;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.SstDocument.Factory;
import org.zkoss.poi.POIXMLDocumentPart;
import org.zkoss.poi.openxml4j.opc.PackagePart;
import org.zkoss.poi.openxml4j.opc.PackageRelationship;

public class SharedStringsTable extends POIXMLDocumentPart {
    private final List<CTRst> strings = new ArrayList();
    private final Map<String, Integer> stmap = new HashMap();
    private int count;
    private int uniqueCount;
    private SstDocument _sstDoc;
    private static final XmlOptions options = new XmlOptions();

    public SharedStringsTable() {
        this._sstDoc = Factory.newInstance();
        this._sstDoc.addNewSst();
    }

    public SharedStringsTable(PackagePart part, PackageRelationship rel) throws IOException {
        super(part, rel);
        this.readFrom(part.getInputStream());
    }

    public void readFrom(InputStream is) throws IOException {
        try {
            int cnt = 0;
            this._sstDoc = Factory.parse(is);
            CTSst sst = this._sstDoc.getSst();
            this.count = (int)sst.getCount();
            this.uniqueCount = (int)sst.getUniqueCount();
            CTRst[] var4 = sst.getSiArray();
            int var5 = var4.length;

            for(int var6 = 0; var6 < var5; ++var6) {
                CTRst st = var4[var6];
                this.stmap.put(this.getKey(st), cnt);
                this.strings.add(st);
                ++cnt;
            }

        } catch (XmlException var8) {
            throw new IOException(var8.getLocalizedMessage());
        }
    }

    private String getKey(CTRst st) {
        return st.xmlText(options);
    }

    public CTRst getEntryAt(int idx) {
        return (CTRst)this.strings.get(idx);
    }

    public int getCount() {
        return this.count;
    }

    public int getUniqueCount() {
        return this.uniqueCount;
    }

    public int addEntry(CTRst st) {
        String s = this.getKey(st);
        ++this.count;
        if (this.stmap.containsKey(s)) {
            return (Integer)this.stmap.get(s);
        } else {
            ++this.uniqueCount;
            CTRst newSt = this._sstDoc.getSst().addNewSi();
            newSt.set(st);
            int idx = this.strings.size();
            this.stmap.put(s, idx);
            this.strings.add(newSt);
            return idx;
        }
    }

    public List<CTRst> getItems() {
        return this.strings;
    }

    public void writeTo(OutputStream out) throws IOException {
        XmlOptions options = new XmlOptions(DEFAULT_XML_OPTIONS);
        options.setSaveCDataLengthThreshold(1000000);
        options.setSaveCDataEntityCountThreshold(-1);
        CTSst sst = this._sstDoc.getSst();
        sst.setCount((long)this.count);
        sst.setUniqueCount((long)this.uniqueCount);
        this._sstDoc.save(out, options);
    }

    protected void commit() throws IOException {
        PackagePart part = this.getPackagePart();
        this.clearMemoryPackagePart(part);
        OutputStream out = part.getOutputStream();
        this.writeTo(out);
        out.close();
    }

    static {
        //EFC replace 3 lines. For exporting files . . .
        options.setSaveInner();
        options.setSaveAggressiveNamespaces();
        options.setUseDefaultNamespace();
        /*
        options.put("SAVE_INNER");
        options.put("SAVE_AGGRESSIVE_NAMESPACES");
        options.put("SAVE_USE_DEFAULT_NAMESPACE");*/
        options.setSaveImplicitNamespaces(Collections.singletonMap("", "http://schemas.openxmlformats.org/spreadsheetml/2006/main"));
    }
}
