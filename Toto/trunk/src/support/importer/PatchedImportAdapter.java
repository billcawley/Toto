package support.importer;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

import org.zkoss.lang.Library;
import org.zkoss.poi.POIXMLDocument;
import org.zkoss.poi.poifs.filesystem.POIFSFileSystem;
import org.zkoss.zss.api.Importer;
import org.zkoss.zss.model.SBook;
import org.zkoss.zss.range.impl.imexp.AbstractExcelImporter;
import org.zkoss.zss.range.impl.imexp.AbstractImporter;
import org.zkoss.zss.range.impl.imexp.ExcelXlsImporter;

public class PatchedImportAdapter extends AbstractImporter {

	@Override
	public SBook imports(InputStream is, String bookName) throws IOException {
		if(!is.markSupported()) {
			is = new PushbackInputStream(is, 8);
		}
		AbstractExcelImporter importer = null;
		if (POIFSFileSystem.hasPOIFSHeader(is)) {
			importer = new ExcelXlsImporter();
		}else if (POIXMLDocument.hasOOXMLHeader(is)) {
			importer = new PatchedExcelXlsxImporter(); //PATCH in this line
		}
		if (importer != null) {
			importer.setImportCache(this.isImportCache()); //ZSS-873
			return importer.imports(is, bookName);
		}
		throw new IllegalArgumentException("The input stream to be imported is neither an OLE2 stream, nor an OOXML stream");
	}
	
	//ZSS-873
	private boolean isImportCache() {
		String importCache = Library.getProperty("org.zkoss.zss.import.cache", "false");
		return "true".equalsIgnoreCase(importCache.trim());
	}
}
