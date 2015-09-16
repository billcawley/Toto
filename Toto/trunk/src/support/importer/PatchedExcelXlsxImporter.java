package support.importer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.poi.ss.usermodel.AutoFilter;
import org.zkoss.poi.ss.usermodel.CellStyle;
import org.zkoss.poi.ss.usermodel.NamedStyle;
import org.zkoss.poi.ss.usermodel.Row;
import org.zkoss.poi.ss.usermodel.Sheet;
import org.zkoss.poi.ss.usermodel.Workbook;
import org.zkoss.poi.ss.util.CellRangeAddress;
import org.zkoss.util.Locales;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SAutoFilter;
import org.zkoss.zss.model.SBook;
import org.zkoss.zss.model.SBookSeries;
import org.zkoss.zss.model.SBooks;
import org.zkoss.zss.model.SNamedStyle;
import org.zkoss.zss.model.SSheet;
import org.zkoss.zss.model.impl.AbstractBookAdv;
import org.zkoss.zss.model.impl.NamedStyleImpl;
import org.zkoss.zss.range.impl.imexp.ExcelXlsxImporter;


public class PatchedExcelXlsxImporter extends ExcelXlsxImporter {

	Logger logger = LoggerFactory.getLogger(PatchedExcelXlsxImporter.class);

	@Override
	public SBook imports(InputStream is, String bookName) throws IOException {
		
		// clear cache for reuse
		importedStyle.clear();
		importedFont.clear();

		workbook = createPoiBook(is);
		book = SBooks.createBook(bookName);
//		book.setDefaultCellStyle(importCellStyle(workbook.getCellStyleAt((short) 0), false)); //ZSS-780
		//ZSS-854
		importDefaultCellStyles();
//		importNamedStyles();
		
		setBookType(book);

		//ZSS-715: Enforce internal Locale.US Locale so formula is in consistent internal format
		Locale old = Locales.setThreadLocal(Locale.US);
		SBookSeries bookSeries = book.getBookSeries();
		boolean isCacheClean = bookSeries.isAutoFormulaCacheClean();
		try {
			bookSeries.setAutoFormulaCacheClean(false);// disable it to avoid
														// unnecessary clean up
														// during importing

			importExternalBookLinks();
			int numberOfSheet = workbook.getNumberOfSheets();
			for (int i = 0; i < numberOfSheet; i++) {
				Sheet poiSheet = workbook.getSheetAt(i);
				importSheet(poiSheet, i);
				SSheet sheet = book.getSheet(i);
				importTables(poiSheet, sheet); //ZSS-855, ZSS-1011
			}
			importNamedRange();
			for (int i = 0; i < numberOfSheet; i++) {
				SSheet sheet = book.getSheet(i);
				Sheet poiSheet = workbook.getSheetAt(i);
				for (Row poiRow : poiSheet) {
					importRow(poiRow, sheet);
				}
				importColumn(poiSheet, sheet);
				importMergedRegions(poiSheet, sheet);
				importDrawings(poiSheet, sheet);
				importValidation(poiSheet, sheet);
				importAutoFilter(poiSheet, sheet);
				importSheetProtection(poiSheet, sheet); //ZSS-576
			}
		} finally {
			book.getBookSeries().setAutoFormulaCacheClean(isCacheClean);
			Locales.setThreadLocal(old);
		}

		return book;
	}

	//ZSS-854
	private void importDefaultCellStyles() {
		((AbstractBookAdv)book).clearDefaultCellStyles();
		for (CellStyle poiStyle : workbook.getDefaultCellStyles()) {
			book.addDefaultCellStyle(importCellStyle(poiStyle, false));
		}
		// in case of XLS files which we have not support defaultCellStyles 
		if (book.getDefaultCellStyles().isEmpty()) {
			((AbstractBookAdv)book).initDefaultCellStyles();
		}
	}

	private void importAutoFilter(Sheet poiSheet, SSheet sheet) {
		AutoFilter poiAutoFilter = poiSheet.getAutoFilter();
		if (poiAutoFilter != null) {
			CellRangeAddress filteringRange = poiAutoFilter.getRangeAddress();
			SAutoFilter autoFilter = sheet.createAutoFilter(new CellRegion(filteringRange.formatAsString()));
			int numberOfColumn = filteringRange.getLastColumn() - filteringRange.getFirstColumn() + 1;
			importAutoFilterColumns(poiAutoFilter, autoFilter, numberOfColumn); //ZSS-1019
		}
	}
	
}
