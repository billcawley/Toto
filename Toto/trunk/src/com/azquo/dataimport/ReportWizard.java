package com.azquo.dataimport;

/* copyright Azquo Holdings Ltd 2022
   WFC
 */

import com.azquo.StringLiterals;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.UserChoiceService;
import com.azquo.spreadsheet.transport.UploadedFile;
import com.azquo.spreadsheet.zk.BookUtils;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.*;
import org.apache.poi.xssf.usermodel.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;




public class ReportWizard {

    final static short noColor = IndexedColors.WHITE.getIndex();
    final static short choiceColor = IndexedColors.YELLOW.getIndex();
    final static short deepBlue = IndexedColors.DARK_BLUE.getIndex();
    final static short lightBlue = IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex();


    public static List<String> getPossibleHeadings(LoggedInUser loggedInUser, String currentChoice)throws Exception{
        return RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())

                .getPossibleHeadings(loggedInUser.getDataAccessToken(), currentChoice);
    }

    
    public static List<String> createList(List<String> options, String chosen, String exclude){
        String selected = chooseSuitable(options, chosen, exclude);
        List<String> toReturn = new ArrayList<>();
        for (String option:options){
            String[]clauses = option.split(";");
            String maxSizeString = clauses[0];
            String setName = clauses[1];
            //maxSizeString = maxSize, setName = setname
            if (setName.contains(StringLiterals.MEMBEROF)){
                setName += ";up to " + maxSizeString  + " items";    
            }else{
                setName += ";" + maxSizeString + " items";
            }

            if (!setName.equals(exclude)){
                if (selected.equals(setName)){
                    toReturn.add(setName + " selected");
                }else{
                    toReturn.add(setName);
                }
            }
        }
        Collections.sort(toReturn);
        return toReturn;
    }


    public static String chooseSuitable(List<String> options, String currentChosen, String exclude){
        //looks for the nearest size based on log values.
        double optimum = Math.log(10);
        if (exclude.length()>0 && exclude.equals(currentChosen)){
            return "";
        }
        String possible = "";
        double possibleDiff = optimum;
        List<String> toReturn = new ArrayList<>();
        for (String option:options){
            String[]clauses = option.split(";");
            String maxSizeString = clauses[0];
            String setName = clauses[1];
            if (setName.contains(StringLiterals.MEMBEROF)){
                setName += ";up to " + maxSizeString  + " items";
            }else{
                setName += ";" + maxSizeString + " items";
            }
            if (currentChosen!=null && currentChosen.equals(setName)){
                return currentChosen;
                
            }
            if (!setName.equals(exclude)){
                int maxSize = Integer.parseInt(maxSizeString);
                double log = Math.log(maxSize);
                if (log > optimum && log - optimum < possibleDiff){
                   possibleDiff = log - optimum;
                   possible = setName;
                }
                if (log <= optimum && optimum - log < possibleDiff){
                    possibleDiff = optimum - log;
                    possible = setName;
                }

            }
        }
        return possible;
    }

    public static List<String> getTemplateList(LoggedInUser loggedInUser){
        List<String>toReturn = new ArrayList<>();
        List<OnlineReport> templates = OnlineReportDAO.findTemplatesForBusinessId(loggedInUser.getBusiness().getId());
        for (OnlineReport onlineReport:templates){
            toReturn.add(onlineReport.getReportName());
        }
        return toReturn;

    }

    public static int createReport(@NotNull LoggedInUser loggedInUser, String dataItem, String function, String rows, String columns, String templateReport, String reportName)throws Exception{
        List<OnlineReport> templates = OnlineReportDAO.findTemplatesForBusinessId(loggedInUser.getBusiness().getId());
        OnlineReport templateFound = null;
        for (OnlineReport template:templates){
            if (template.getReportName().equals(templateReport)){
                templateFound = template;
                break;
            }
        }
        String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath +
                loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + templateFound.getFilenameForDisk();
        OPCPackage opcPackage = null;
        try (FileInputStream fi = new FileInputStream(bookPath)) { // this will hopefully guarantee that the file handler is released under windows
            opcPackage = OPCPackage.open(fi);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Cannot load template");
        }
        Workbook book = new XSSFWorkbook(opcPackage);
         //now customise
        int insertedLines =  bookPrepare(book,"row", rows, 0);
        insertedLines = bookPrepare(book,"column", columns,insertedLines);
        if (!"sum".equals(function)){
            dataItem = function + "(" + dataItem + ")";
        }
        setNamedRangeValue(book,"az_context1", dataItem, (short)0);
        String options = "";
        options += getLanguage(loggedInUser,"row", rows);
        options += getLanguage(loggedInUser,"column", columns);
        setNamedRangeValue(book,"az_options1", options, (short)0);
        setNamedRangeValue(book,"az_ReportName", reportName,(short)0);
        String tempPath =SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis()+".xlsx"; // timestamp the upload to stop overwriting with a file with the same name is uploaded after
        File tempFile = new File(tempPath); // timestamp the upload to stop overwriting with a file with the same name is uploaded after
        tempFile.delete(); // to avoid confusion

        OutputStream outputStream = new FileOutputStream(tempFile) ;
        book.write(outputStream);
        UploadedFile uploadedFile = new UploadedFile(tempPath, Collections.singletonList(reportName+".xlsx"),false);

        ImportService.uploadReport(loggedInUser,reportName,uploadedFile, book);
        OnlineReport or = OnlineReportDAO.findForNameAndBusinessId(reportName, loggedInUser.getBusiness().getId());
        if (or!=null){
            return or.getId();
        }
        return 0;


    }


    private static int bookPrepare(Workbook book, String dimension, String dimName, int insertedLines){
          int choiceRow = 4 + insertedLines;

        org.apache.poi.ss.usermodel.Sheet sheet = book.getSheetAt(0);
        int maxRow = sheet.getLastRowNum();
        String[] dimVals = dimName.substring(0,dimName.indexOf(";")).split(StringLiterals.MEMBEROF);
        String dimVal = dimVals[dimVals.length-1];
        String lastChoice = null;
        for (int i = 0;i<dimVals.length -1 ;i++){
            String choice = dimVals[i];
            shiftRows(sheet, choiceRow, maxRow,1);
             org.apache.poi.ss.usermodel.Name choiceName = book.createName();
            String choiceString = StringLiterals.QUOTE + choice + StringLiterals.QUOTE + " " + StringLiterals.CHILDREN;
            if (lastChoice!=null ){
                choiceString = "`"+ lastChoice + StringLiterals.MEMBEROF + "[" + lastChoice + "]` children * `" + choice + "`";
            }
            setCellValue(sheet,choiceRow,0,choiceString, choiceColor);
            choiceName.setNameName("az_"+choice.toLowerCase(Locale.ROOT).replace(" ","") + "Choice");
            choiceName.setRefersToFormula("'"+sheet.getSheetName()+"'!A" + (choiceRow + 1));
            org.apache.poi.ss.usermodel.Name chosenName = book.createName();
            chosenName.setNameName("az_" + choice.toLowerCase(Locale.ROOT).replace(" ","") + "Chosen");
            chosenName.setRefersToFormula("'" + sheet.getSheetName() + "'!E" + (choiceRow + 1));
            setCellValue(sheet,choiceRow,3,choice, deepBlue);
            setCellValue(sheet,choiceRow,4,"",lightBlue);
            lastChoice = choice;
            choiceRow++;
        }
        if (dimVal.equals("Day")){//customers can be called 'Day' - we may need to check other possibilities...
            dimVal = "Date->Day";
        }
         String setName = StringLiterals.QUOTE + dimVal + StringLiterals.QUOTE + " " + StringLiterals.CHILDREN + " from 1 to 100";
         if (dimVals.length>1){
             lastChoice = dimVals[dimVals.length - 2];

             setName  = "`"+ lastChoice + StringLiterals.MEMBEROF + "[" + lastChoice + "]` children * `"+ dimVal + "`";

        }

        setNamedRangeValue(book,"az_"+dimension+"headings1",  setName, choiceColor);


         return dimVals.length - 1;
    }


    private static void setNamedRangeValue(Workbook book, String rangeName, String cellValue, short color){
        org.apache.poi.ss.usermodel.Name region = BookUtils.getName(book, rangeName);
        if (region == null) return;

        AreaReference area = new AreaReference(region.getRefersToFormula(), null);
        setCellValue(book.getSheet(region.getSheetName()),area.getFirstCell().getRow(),area.getFirstCell().getCol(), cellValue, color);

    }

    private static void shiftRows(Sheet sheet, int startRow, int endRow, int rowCount) {
        sheet.shiftRows(startRow, endRow, rowCount, true, true);
        XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
        List<XSSFChart> charts = drawing.getCharts();
        for (XSSFChart chart : charts) {
            List<XDDFChartData> chartDataList = chart.getChartSeries();
            XDDFChartData chartData = chartDataList.get(0);

            List<XDDFChartData.Series> seriesList = chartData.getSeries();
            for (XDDFChartData.Series series : seriesList) {
                XDDFDataSource categoryData = series.getCategoryData();
                AreaReference catReference = new AreaReference(categoryData.getDataRangeReference(), null);
                CellReference firstCatCell = catReference.getFirstCell();
                CellReference lastCatCell = catReference.getLastCell();
                if (firstCatCell.getRow() >= startRow) {
                    int col = firstCatCell.getCol();
                    int lastRow = lastCatCell.getRow();
                    XDDFDataSource<String> category = XDDFDataSourcesFactory.fromStringCellRange(
                            (XSSFSheet) sheet,
                            new CellRangeAddress(firstCatCell.getRow() + rowCount, lastRow + rowCount, col, col));

                    XDDFNumericalDataSource valuesData = series.getValuesData();
                    AreaReference numReference = new AreaReference(valuesData.getDataRangeReference(), null);
                    CellReference firstNumCell = numReference.getFirstCell();
                    col = firstNumCell.getCol();

                    XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                            (XSSFSheet) sheet,
                            new CellRangeAddress(firstNumCell.getRow() + rowCount, lastRow + rowCount, col, col));

                    series.replaceData(category, values);

                }
            }
        }
    }

    private static String getLanguage(LoggedInUser loggedInUser, String dimension, String value){
        String setName = value.substring(0,value.indexOf(";")).toLowerCase(Locale.ROOT);
        if (setName.contains(StringLiterals.MEMBEROF)){
            setName=setName.substring(setName.lastIndexOf(StringLiterals.MEMBEROF)+2);
        }
        String language = null;
        if (setName.endsWith(" key") || setName.endsWith(" id") || setName.endsWith(" code")){
            try {
                String rootName = setName.substring(0,setName.lastIndexOf(" "));

                List<String> attributes = RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getAttributeList(loggedInUser.getDataAccessToken());
                for (String attribute:attributes) {
                    if (attribute.toLowerCase(Locale.ROOT).equals(rootName + " name") || attribute.toLowerCase(Locale.ROOT).equals(rootName)) {
                        language = attribute.toLowerCase(Locale.ROOT);
                    }

                }
            }catch(Exception e){
                return "";
            }
            if (language!=null)
               return dimension + " language=" + language + ",";


        }

        return "";
    }


        private static void setCellValue(org.apache.poi.ss.usermodel.Sheet sheet, int r, int c, String value, short color){
        Row row = sheet.getRow(r);
        if (row == null)
            row = sheet.createRow(r);
        Cell cell = row.getCell(c);
        if (cell == null) {
            cell = row.createCell(c);
        }
         cell.setCellValue(value);
          if (color != 0){
              cell.getCellStyle().setFillPattern(FillPatternType.SOLID_FOREGROUND);
              cell.getCellStyle().setFillBackgroundColor(color);

        }

    }
    public static String handleRequest(HttpServletRequest request, LoggedInUser loggedInUser)throws Exception{
        final ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        class Selection {
            String selName;
            List<String> selValues;

            Selection(String selName, List<String> selValues) {
                this.selName = selName;
                this.selValues = selValues;
            }
        }

        String dataChosen = request.getParameter("datachosen");
        String functionChosen = removeSelected(request.getParameter("functionchosen"));
        if (functionChosen==null || functionChosen.length()==0){
            functionChosen = "sum";
        }
        String rowChosen = removeSelected(request.getParameter("rowchosen"));
        String columnChosen = removeSelected(request.getParameter("columnchosen"));
        List<Selection> selections = new ArrayList<>();
        if (dataChosen.length() > 0) {

            List<String> setOptions = ReportWizard.getPossibleHeadings(loggedInUser, dataChosen);

            List<String> rowOptions = ReportWizard.createList(setOptions, rowChosen, "");
            rowChosen = ReportWizard.chooseSuitable(setOptions, rowChosen, columnChosen);

            if (dataChosen.toLowerCase(Locale.ROOT).contains("unit") && "sum".equals(functionChosen)){
                functionChosen = "count";

            }
            List<String>functionOptions = getFunctionOptions(functionChosen);
            Selection functions = new Selection("functions", functionOptions);
            selections.add(functions);
            Selection rows = new Selection("rows", rowOptions);
            selections.add(rows);
            List<String> columnOptions = ReportWizard.createList(setOptions, columnChosen, rowChosen);
            Selection columns = new Selection("columns", columnOptions);
            selections.add(columns);
            Selection templates = new Selection("templates", ReportWizard.getTemplateList(loggedInUser));
            selections.add(templates);
        }

        jacksonMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        String done = jacksonMapper.writeValueAsString(selections);
        return done;


    }

    private static List<String>getFunctionOptions(String chosen){
        String[]   FUNCTIONOPTIONS = {"sum", "count", "average", "max","min"};
        List<String> toReturn = new ArrayList<>();
        for (String function:FUNCTIONOPTIONS){
            if (function.equals(chosen)){
                toReturn.add(function + " selected");

            }else{
                toReturn.add(function);
            }
        }
        return toReturn;
    }

    private static String removeSelected(String value){
        if (value.endsWith(" selected")){
            return value.substring(0, value.length() - 9);
        }
        return value;
    }



}
