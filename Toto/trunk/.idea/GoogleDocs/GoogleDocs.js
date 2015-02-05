/**
 * Created by bill on 19/03/14.
 */

//This code,  && all code associated with the Azquo workbook, copyright Azquo Ltd 2013 (except where specified).  Programmed by Bill Cawley

function onOpen() {
    //var sheet = SpreadsheetApp.getActiveSpreadsheet();
    //var entries = [{
    //  name : "Read Data",
    //  functionName : "readRows"
    //}];
    //sheet.addMenu("Script Center Menu", entries);
    traverseRegions("az_Choice",setChoice);
};


var azError
var azConnectionId
var azNameChosen
var azResponse


function ZapSheet(SheetName){

    var sheets = SpreadsheetApp.getActiveSpreadsheet().getSheets();
    var sum = 0;
    for (var i = 0; i < sheets.length ; i++ ) {
        var sheet = sheets[i];
        if (sheet.getName() == SheetName){
            deletesheet(sheet)
        }
    }
}


function AddSheet(SheetName){
    ZapSheet (SheetName)
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    ss.insertSheet(1);
    ss.getActiveSheet().setName(SheetName);
}



function CheckRange(RangeName){

    if (SpreadsheetApp.getActiveSpreadsheet().getRangeByName(RangeName) == null){
        SpreadsheetApp.getUi().alert('The range ' + RangeName + ' does not exist');
    }
}


function checkWidth(ArraySent){
    var pos = 0;
    var eol = ArraySent.indexOf("\n");
    if (eol < 0){
        eol = ArraySent.length();
    }
    var Count = 1;

    while (ArraySent.indexOf("\t",pos) > 0 && pos < eol){
        pos = ArraySent.indexOf("\t", pos) + 1;
        Count++;
    }
    return Count;
}


function checkHeight(ArraySent){
    var pos = 0
    var Count = 1
    while (ArraySent.indexOf("\n", pos) > 0 && pos < eol){
        pos = Find("\t", ArraySent, pos) + 1;
        Count++;
    }
    return Count;
}


function LoadData(){

    traverseRegions("az_DataRegion", LoadDataRegion)
}

function traverseRegions(rangeName, functionName, Target){

    //this should be more flexible, but I can!find a way of detecting all the named ranges in a workbook
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    if (ss.getRangeByName(rangeName)){
        if (arguments.length == 2){
            functionName("");
        }else{
            if (functionName("", Target) > "") return functionName("", Target);
        }
    }
    var i = 1;
    while (ss.getRangeByName(rangeName + i) != null){

        if (arguments.length == 2){
            functionName(i + "");
        }else{
            if (functionName(i + "", Target) > "") return functionName(i + "", Target);
        }
        i++;
    }
    return false;
}


function ShowProgress(Region, Activity){
    //Application.ScreenUpdating = True
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    if (ss.getRangeByName("az_UpdateRegion") != null){
        ss.getRangeByName("az_UpdateRegion").setValue(Region + " " + Activity);
    }
    //Application.ScreenUpdating = False
}


function LoadDataRegion(Region){

    ShowProgress(Region, "")
    //Application.ScreenUpdating = False
    var Chart = FindChart(Region);
    ClearDataRegion (Region);
    var azError = FillRowHeadings(Region);
    if (azError == ""){
        azError = FillColumnHeadings(Region);
    }
    if (azError == ""){
        azError = FillData(Region);
        if (azError == ""){
            ShowProgress(Region, "Options")
            //if (hasOption(Region, "hiderows") > ""){
            //  HideRows (Region);
            //}
            if (hasOption(Region, "Percentage") > ""){
                PercentageRows (Region);
            }
            if (hasOption(Region, "sort") > ""){
                SortRows (Region);
            }
            if (hasOption(Region, "maxrows") > ""){
                MaxRows (Region);
            }
            //TrimRowHeadings (Region);
            //TrimColumnHeadings (Region);
            if (Chart!=null){
                SetChartData (Region, Chart);
            }
        }
        ShowProgress(Region, "Complete")
    }

    //ActiveWorkbook.Names("copyregion").Delete

    ZapSheet ("Azquo Clipboard")
    //Application.ScreenUpdating = True
    return false;
}

function GetHideColumn(){

    if (!rangeExists("az_HideColumn")){
        SpreadsheetApp.getUi().alert("To hide blank rows, you need a hidden column to the left of the screen with a cell range named 'az_HideColumn'.  Put a formula in this column which, if (zero, will hide the row")

        throw { name: 'FatalError', message: 'Please restart' };
    }
    return SpreadsheetApp.getActiveSpreadsheet().getRangeByName("az_HideColumn").getColumn();


}


function getRange(RangeName){
    return SpreadsheetApp.getActiveSpreadsheet().getRangeByName(RangeName) ;

}


function Cells(RowNo, ColNo){
    return SpreadsheetApp.getActiveSpreadsheet().getRange(String.fromCharCode(64 + ColNo) + RowNo).getValue();
}

function HideRows(Region){

    var strHideCount = hasOption(Region, "hiderows");
    var HideCount = 1;
    if (strHideCount != "True"){
        HideCount = strHideCount;
    }
    var ss = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
    var TotalCol = GetHideColumn();
    var DataRegion = getRange("az_DataRegion" + Region);
    var VisibleRow = 0;
    var DataRows = DataRegion.getNumRows();
    var RowNo = DataRegion.getRow();
    var LastRow = DataRegion.getRow() + DataRows - 1;
    while (RowNo <= LastRow){

        Total = 0;
        for (Count = 0;Count < HideCount - 1; Count++){
            Total = Total + Cells(RowNo + Count, TotalCol);
        }
        if (Total = 0){
            //never delete the bottom row, ||leave less than 3 rows
            if (DataRows <= 3 ||DataRows <= HideCount || RowNo == LastRow){
                for (Count = 0; Count < HideCount - 1; Count++){
                    ss.hideRow(RowNo + Count);
                }
            }else{
                for (Count = 0; Count < HideCount - 1; Count++){
                    ss.deleteRow(RowNo);
                }
                LastRow = LastRow - HideCount;
                DataRows = DataRows - HideCount;
                RowNo = RowNo - HideCount;
            }
        }
        RowNo = RowNo + HideCount;
    }
}

function PercentageRows(Region){

    var DataRegion = getRange("az_DataRegion" + Region);
    TotalRow = DataRegion.getRow() + DataRegion.getNumRows();
    for (var ColNo = DataRegion.getColumn(); ColNo < DataRegion.getColumn() + DataRegion.getNumColumns() - 1; ColNo++){
        if (Cells(TotalRow, ColNo) > 0){
            Total = Cells(TotalRow, ColNo);
            for (var RowNo = DataRegion.getRow(); RowNo <  TotalRow - 1; RowNo++){
                Cells(RowNo, ColNo) = Cells(RowNo, ColNo) / Total;
            }
        }
    }

}

function SortRows(Region){

    var TotalCol = GetHideColumn();
    var DataRegion = getRange("az_DataRegion" + Region);
    var ss = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
    var SortRegion = ss.getRange(DataRegion.getRow(), 1, DataRegion.getNumRows(), DataRegion.getColumn() + DataRegion.getNumColumns());

    if (hasOption(Region, "sort desc") > ""){
        SortRegion.sort({column: TotalCol, ascending: false});
    }else{
        SortRegion.sort({column: TotalCol, ascending: true});
    }
}

function MaxRows(Region){


    var strRowCount = hasOption(Region, "maxrows");
    RowCount = parseInt(strRowCount);
    var DataRegion = getRange("az_DataRegion" + Region);
    if (DataRegion.getNumRows() > RowCount){
        var ss = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
        ss.deleteRows(DataRegion.getRow() + RowCount,DataRegion.getRow() + DataRegion.getNumRows() - 1);
    }


}


function TrimRowHeadings(Region){


    var ss = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
    if (!rangeExists("az_DisplayRowHeadings" + Region)){
        return;
    }
    var RowHeadings = getRange("az_DisplayRowHeadings" + Region);
    for (var ColNo = 1; ColNo <= RowHeadings.getNumColumns() ; ColNo++){
        var VisibleRow = 1;
        for (var RowNo = 2; RowNo <=  RowHeadings.getNumRows(); RowNo++){
            if (RowHeadings.getCell(RowNo, ColNo).getValue() == RowHeadings.getCell(VisibleRow, ColNo).getValue()){
                RowHeadings.getCell(RowNo, ColNo).clearContent();
            }else{
                VisibleRow = RowNo;
            }

        }
    }
}


function TrimColumnHeadings(Region){

    if (!rangeExists("az_DisplayColumnHeadings" + Region)){
        return;
    }
    var ColumnHeadings = getRange("az_DisplayColumnHeadings" + Region);
    for (var RowNo = 1; RowNo <= ColumnHeadings.getNumRows(); RowNo++){
        var VisibleColumn = 1
        for (var ColNo = 2 ; ColNo <= ColumnHeadings.getNumColumns(); ColNo++){
            if (ColumnHeadings.getCell(RowNo, ColNo).getValue() == ColumnHeadings.getCell(RowNo, VisibleColumn).getValue()){
                ColumnHeadings.getCell(RowNo, ColNo).clearContent();
            }else{
                VisibleColumn = ColNo;
            }
        }
    }




}





function FillRowHeadings(Region){


    ShowProgress(Region, "Row headings");
    CheckRange ("az_RowHeadings" + Region);
    //'CheckRange ("az_DisplayRowHeadings" + Region);

    var azResponse = AzquoPost("Value", "\"rowheadings\":\"" + RangeText("az_RowHeadings" + Region) + "\",\"region\":\"" + Region + "\"");
    if (!rangeExists("az_DisplayRowHeadings" + Region) || hasOption(Region, "hiderows") > "" ){
        return "";
    }

    FillTheRange(azResponse, "az_DisplayRowHeadings" + Region);
    return "";
    // Rows(DisplayRows.getRow() & ":" & DisplayRows.getRow() + DisplayRows.getNumRows() - 1).EntireRow.AutoFit
}


function FillColumnHeadings(Region){

    ShowProgress(Region, "Column headings")
    CheckRange ("az_ColumnHeadings" + Region);
    var azResponse = AzquoPost("Value",  "\"columnheadings\":\"" + RangeText("az_ColumnHeadings" + Region) + "\",\"region\":\"" + Region + "\"");

    if (!rangeExists("az_DisplayColumnHeadings" + Region)){
        return "";
    }
    FillTheRange(azResponse, "az_DisplayColumnHeadings" + Region);
    return "";
    //Rows(DIsplayColumns.getRow() & ":" & DIsplayColumns.getRow() + DIsplayColumns.getNumRows() - 1).EntireRow.AutoFit


}

function FillData(Region){

    ShowProgress(Region, "Data");
    CheckRange ("az_Context" + Region);
    CheckRange ("az_DataRegion" + Region);
    var filtercount = hasOption(Region, "hiderows");
    if (filtercount > "") {
        filtercount = ",\"filtercount\":\"" + filtercount + "\"";
    }
    Logger.log("Filtercount" + filtercount);
    var azResponse = AzquoPost("Value",  "\"context\":\"" + RangeText("az_Context" + Region) + "\",\"region\":\"" + Region + "\"" + filtercount);
    if (filtercount > ""){
        var azHeadings = AzquoPost("Value","\"rowheadings\":\"\",\"region\":\"" + Region + "\"" + filtercount);
        FillTheRange(azHeadings,"az_DisplayRowHeadings" + Region);
    }
    //Logger.log("data:  " + azResponse);
    var DataRegion = getRange("az_DataRegion" + Region);
    // copy the formulae in the data region, so as to copy them back after the data region has been filled
    var formulae = DataRegion.getFormulas();
    FillTheRange(azResponse, "az_DataRegion" + Region);


    //DataRegion.setFormulas(formulae);


    //Application.DisplayAlerts = True
    //FillData = ""
    //Cells(1, 1).Select
    return "";

}


function FillTheRange(textsent, rangename){
    var ss= SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
    var totalCol = GetHideColumn();
    var textgiven = new String(textsent);
    if (textgiven.substring(textgiven.length-1) == "\n"){
        textgiven = textgiven.substring(0,textgiven.length - 1);
    }
    var range = getRange(rangename);
    var origFormulae = range.getFormulas();
    var rows = textgiven.split("\n");
    var colsgiven = rows[0].split("\t").length;
    var rowsgiven = rows.length;
    if (colsgiven > range.getNumColumns()){
        ss.insertColumns(range.getColumn() + range.getNumColumns() - 1, colsgiven - range.getNumColumns())

    }
    if (colsgiven < range.getNumColumns() && range.getNumColumns() > 3){
        if (colsgiven < 3) {
            colsgiven = 3;
        }
        ss.deleteColumns(range.getColumn() + colsgiven - 1, range.getNumColumns() - colsgiven)
    }
    if (rowsgiven > range.getNumRows()){
        var colformula = ss.getRange(range.getRow(), totalCol);
        var firstRow = range.getRow() + range.getNumRows() - 1;
        var insertRows = rowsgiven - range.getNumRows();
        ss.insertRows(firstRow, insertRows);
        colformula.copyValuesToRange(ss, totalCol, totalCol, firstRow, firstRow + insertRows);
        //for (var RowNo = firstRow; RowNo < firstRow + insertRows; RowNo++){
        //    colformula.copyTo(ss.getRange(RowNo, totalCol));
        //    if (RowNo < firstRow + 3){
        //      Browser.msgBox("copied formula");
        //    }
        // }
    }
    if (rowsgiven < range.getNumRows() && range.getNumRows() > 3){
        if (rowsgiven < 3) rowsgiven = 3;
        ss.deleteRows(range.getRow() + rowsgiven - 1, range.getNumRows() - rowsgiven)

    }
    //range.setFormulas(origFormulae);
    var data = [];

    for (var row= 0; row < rows.length; row++){
        var VisibleCol = 0
        var thisRow = rows[row].split("\t");
        for (var ColNo = 1; ColNo < thisRow.length; ColNo++){
            if (thisRow[ColNo] == thisRow[VisibleCol]){
                thisRow[ColNo] = "";
            }else{
                VisibleCol = ColNo;
            }
            if (ColNo > 0 && thisRow[ColNo] == thisRow[VisibleCol]){
                thisRow[ColNo] = "";
            }
            if (thisRow.length < colsgiven){
                for (var colNo = thisRow.length;colNo<colsgiven;colNo++){
                    thisRow[colNo] = "";
                }
            }
            data[row] = thisRow;
        }
    }

    for (var ColNo = 0;ColNo < colsgiven;colNo++){
        var VisibleRow = 0;
        for (var RowNo = 1; RowNo < rows; RowNo++){
            if (data[VisibleRow, ColNo] = data[RowNo, ColNo]){
                data[RowNo, ColNo] = "";
            }
        }
    }


    getRange(rangename).setValues(data);

 }




    function RangeText(RangeName){

        var azRange = getRange(RangeName)
        var RangeText = ""
        for (var RowNo = 1; RowNo <= azRange.getNumRows(); RowNo++){
            if (RowNo > 1){
                RangeText = RangeText + "\n";
            }
            for (var ColNo = 1; ColNo <= azRange.getNumColumns(); ColNo++){
                if (ColNo > 1){
                    RangeText = RangeText + "\t";
                }
                RangeText += azRange.getCell(RowNo, ColNo).getValue();
            }
        }
        //Logger.log("rangetext " + azRange.getNumRows() + " " + RangeText);
        return encodeURIComponent(RangeText);
    }

    function SaveData(){

        traverseRegions("az_DataRegion",SaveDataRegion);
    }



    function SaveRegionData(Region){


        var DataText = RangeText(Range("az_DataRegion" + Region))
        azResponse = AzquoPost("Value", "\"editeddata\":\"" + DataText + "\",\"region\":\"" + Region + "\"");
        return false;
    }



    function ClearRange(RangeName){
        if (rangeExists(RangeName)){
            getRange(RangeName).clearContent();
        }
    }

    function rangeExists(RangeName){

        if (SpreadsheetApp.getActiveSpreadsheet().getRangeByName(RangeName) == null){
            return false;
        }
        return true;
    }



    function hasOption(Region, OptionName){

        if (rangeExists("az_Options" + Region)){
            var Options = getRange("az_Options" + Region).getValue();
            var FoundPos = Options.toLowerCase().indexOf(OptionName.toLowerCase());

            if (FoundPos < 0){
                return "";
            }
            var hasOption =  Options.substr(FoundPos + OptionName.length).trim();
            FoundPos = hasOption.indexOf(",")
            if (FoundPos > 0){
                hasOption = hasOption.substr(0, FoundPos);
            }
            //remove any '=' char
            hasOption = hasOption.replace("=", "").trim();
            if (hasOption == ""){
                hasOption = "True";
            }
            return hasOption;
        }
        return "";
    }


    function ClearData(){

        traverseRegions("az_DataRegion", ClearDataRegion)

    }


    function ClearDataRegion(Region){

        LeftColumn = 0;
        var DataRegion = getRange("az_DataRegion" + Region);
        var ss = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
        for (var RowNo = 1; RowNo < DataRegion.getNumRows(); RowNo++){
            ss.unhideRow(DataRegion.getCell(RowNo, 1));
        }
        if (rangeExists("az_DisplayRowHeadings" + Region)){
            if (DataRegion.getNumRows() > 3){
                var LastRemove = DataRegion.getRow() + DataRegion.getNumRows() - 1
                var FirstRemove = DataRegion.getRow() + 2;
                ss.deleteRows(FirstRemove, LastRemove - FirstRemove);
            }
            ClearRange ("az_DisplayRowHeadings" + Region);
        }
        if (hasOption(Region, "trimcols") > ""){
            if (rangeExists("az_DisplayColumnHeadings" + Region)){
                if (DataRegion.getNumColumns() > 3){
                    LastRemove = DataRegion.getColumn() + DataRegion.getNumColumns() - 1;
                    FirstRemove = DataRegion.getColumn() + 2;
                    ss.deleteColumns(FirstRemove, LastRemove - FirstRemove);
                }
                ClearRange ("az_DisplayColumnHeadings" + Region);
            }
        }
        // copy the formulae
        var DataRegion = getRange("az_DataRegion" + Region);
        var formulae = DataRegion.getFormulas();
        DataRegion.ClearContents;
        DataRegion.setFormulas(formulae);
        return false;

    }


    function FindChart(Region){

        var ss=SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
        var DataRegion = getRange("az_DataRegion" + Region);
        var charts = ss.getCharts();
        for (var i in charts) {
            var chart = charts[i];
            var ranges = chart.getRanges();
            for (var j in ranges) {
                var range = ranges[j];
                if (range.getRow() <= DataRegion.getRow() && range.getRow() + range.getNumRows() >= DataRegion.getRow() + DataRegion.getNumRows()){
                    return chart;
                }
            }
        }
        return null;

    }


    function SetChartData(Region, chart){

        var builder = chart.modify();
        builder.addRange(getRange("az_DataRegion" + Region));
        if (RangeExists("ax_DisplayColumnHeadings" + Region)){
            builder.addRange(getRange("az_DisplayColumnHeadings" + Region));
        }
        if (RangeExists("ax_DisplayRowHeadings" + Region)){
            builder.addRange(getRange("az_DisplayRowHeadings" + Region));
        }
        var ss = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
        ss.updateChart(chart);

    }



    function inData(Target){
        return traverseRegions("az_DataRegion", inRegionData, Target);
    }

    function inRegionData(Region, Target){
        return inRange("az_DataRegion" + Region, Target);
    }


    function inRange(rangename, Target){
        var range = getRange(rangename);
        if (range == null) return "";
        if (Target.getRow() >= range.getRow() && Target.getColumn() >= range.getColumn() && Target.getRow() < range.getRow() + Range.getNumRows() && Target.getColumn() < range.getColumn() + range.getNumColumns()) then
        return ranemname;
        return "";

    }

    function inHeadings(Target){
        return traverseRegions("az_DataRegion",inRegionHeadings, Target);
    }


    function inRegionHeadings(Region, Target){
        var range = inRange("az_DisplayColumnHeadings" + Region,Target);
        if (range > "") return range;
        return inRange("az_DisplayRowHeadings" + Region,Target);
    }


    function Provenance(){

        var Target = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet().getActiveCell();
        if (inHeadings(Target) > ""){
            ProvenanceString = getProvenanceForName(Target);
        }

        DataRegionName = inData(Target)
        if (DataRegionName > ""){
            ProvenanceString = getProvenanceForValue((Target.getColumn() - getRange(DataRegionName).getColumn()), (Target.getRow() - getRange(DataRegionName).getRow()), Mid(DataRegionName, 14, 1000));
        }
    }


    function getProvenanceForName(ProvenanceName){
        getProvenanceForName = AzquoPost("Provenance", "\"name\":\"" + ProvenanceName + "\"");
    }

    function getProvenanceForValue(Col, Row, Region){
        getProvenanceForValue = AzquoPost("Provenance", "\"col\":\"" + Col + "\",\"row\":\"" + Row + "\",\"region\":\"" + Region + "\"");
    }


    function setChoice(Region){

        var NameChoice = getRange("az_Choice" + Region).getValue();
        var chosenCell = getRange("az_Chosen" + Region);

        var NameChoice = NameChoice.replace("\"", "\\\"");

        var azResponse = AzquoPost("Name", "\"operation\":\"namelist\",\"name\":\"" + encodeURIComponent(NameChoice) + "\"");
        az_showSet(azResponse,chosenCell);
    }


    function CheckConnectionId(){
        azConnectionId=AzquoPost1("Login","\"connectionid\":\"" + azConnectionId + "\"");
        //Logger.log("connection id = " + azConnectionId);
    }


    function AzquoPost(functionName, params){
        CheckConnectionId();
        return AzquoPost1(functionName, params);
    }

    function AzquoPost1(functionName, params){

        var url = "https://data.azquo.com/api/" + functionName;
        var payload = {
            json: "{" + params + ",\"connectionid\":\"" + azConnectionId +"\",\"user\":\"demo@user.com\",\"password\":\"password\",\"database\":\"export\"}",
        };
        var options = {
            method: 'POST',
            payload: payload
        }
        var response = UrlFetchApp.fetch(url, options);
        return response;


    }










    function  az_showSet(response, cell){

        var json = JSON.parse(response);
        var namesFound = json.names;
        var count = 0;
        var list = "";
        //NOTE - THIS DOES NOT LIKE COMMAS IN NAMES!   I cannot find a way of changing the separator to a tab.
        while (count < namesFound.length){
            var name = namesFound[count].name;
            if (count > 0) list+=",";
            list += name;
            count++;
        }
        var rule = SpreadsheetApp.newDataValidation().requireValueInList([list], false).build();
        cell.setDataValidation(rule);

    }

    function ShowProvenance(allprovenance){
        var provenance = "Provenance:<br/><br/>";
        var lastPerson = ""
        var lastTime = ""
        for (var i = 0;i <allprovenance.length;i++){
            var oneProv = allprovenance[i];

            if (oneProv.when != lastTime || oneProv.who != lastPerson){
                lastPerson = oneProv.who;
                lastTime = oneProv.when;
                provenance = provenance + "updated by: " + lastPerson +  " at " + lastTime + " - " + oneProv.how + " " + oneProv.where + "<br/>";
            }
            //provenance = provenance + "<br/>" +  oneProv.value + "  ";
            //var provNames = oneProv.names;
            //for (var j = 0; j< provNames.length; j++){
            //    provenance = provenance + provNames[j] + " ";
            //}
        }
        SpreadsheetApp.getUi().alert(provenance)
    }

