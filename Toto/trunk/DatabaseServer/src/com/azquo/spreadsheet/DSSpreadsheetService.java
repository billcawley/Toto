package com.azquo.spreadsheet;

import com.azquo.StringLiterals;
import com.azquo.dataimport.BatchImporter;
import com.azquo.dataimport.DSImportService;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.*;
import com.azquo.memorydb.service.*;
import com.azquo.spreadsheet.view.CellForDisplay;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.view.RegionOptions;
import net.openhft.koloboke.collect.set.hash.HashObjSets;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.*;
/*
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created as a result of the report server/database server split, was paired roughly with SpreadsheetService on the report side.
 *
 * 28th Oct, much code factored off to shrink the class to higher level functions generally called by the RMIImplementation.
 */

public class DSSpreadsheetService {

    private static final Logger logger = Logger.getLogger(DSSpreadsheetService.class);

    // should be called before each report request

    public static void clearLog(DatabaseAccessToken databaseAccessToken) throws Exception {
        AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken).clearUserLog();
    }

    // to try to force an exception to stop execution
    // todo - check interruption is not the thing here, probably not but check

    public static void sendStopMessageToLog(DatabaseAccessToken databaseAccessToken) throws Exception {
        AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken).setStopInUserLog();
    }

    public static void addToLog(DatabaseAccessToken databaseAccessToken, String message) throws Exception {
        AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken).addToUserLog(message);
    }

    /* function that can be called by the front end to deliver the data and headings
    Region name as defined in the Excel. valueId if it's to be the default selected cell. Row and Column headings and context as parsed straight off the sheet (2d array of cells).
      Filtercount is to remove sets of blank rows, what size chunks we look for. Highlightdays means highlight data where the provenance is less than x days old.
     */

    public static CellsAndHeadingsForDisplay getCellsAndHeadingsForDisplay(DatabaseAccessToken databaseAccessToken, String regionName, int valueId, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, RegionOptions regionOptions, boolean quiet) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        List<List<AzquoCell>> data = AzquoCellService.getDataRegion(azquoMemoryDBConnection, regionName, rowHeadingsSource, colHeadingsSource, contextSource
                , regionOptions, databaseAccessToken.getLanguages(), valueId, quiet);
        if (data.size() == 0) {
            //when contextSource = null there is an error on attempting to save
            return new CellsAndHeadingsForDisplay(regionName, colHeadingsSource, null, new ArrayList<>(), rowHeadingsSource, colHeadingsSource, contextSource, azquoMemoryDBConnection.getDBLastModifiedTimeStamp(), regionOptions, null);
        }
        List<List<CellForDisplay>> displayData = new ArrayList<>(data.size());
        // todo, think about race conditions here
        Set<Value> toLock = new HashSet<>(); // change to koloboke?
        Set<String> lockCheckResult = new HashSet<>();
        boolean checkLocks = azquoMemoryDBConnection.getAzquoMemoryDB().hasLocksAsideFromThisUser(databaseAccessToken.getUserId());
        for (List<AzquoCell> sourceRow : data) {
            List<CellForDisplay> displayDataRow = new ArrayList<>(sourceRow.size());
            displayData.add(displayDataRow);
            for (AzquoCell sourceCell : sourceRow) {
                // I suppose a little overhead from this - if it's a big problem can store lists of ignored rows and cols above and use that
                // ignored just means space I think, as in allow the cell to be populated by a spreadsheet formula for example
                boolean ignored = false;
                //todo check this null heading business
                for (DataRegionHeading dataRegionHeading : sourceCell.getColumnHeadings()) {
                    if (dataRegionHeading == null || ".".equals(dataRegionHeading.getAttribute())) {
                        ignored = true;
                    }
                }
                for (DataRegionHeading dataRegionHeading : sourceCell.getRowHeadings()) {
                    if (dataRegionHeading == null || ".".equals(dataRegionHeading.getAttribute())) {
                        ignored = true;
                    }
                }
                if (sourceCell.isSelected()) {
                    System.out.println("selected cell");
                }
                if (checkLocks && !sourceCell.isLocked() && sourceCell.getListOfValuesOrNamesAndAttributeName() != null && sourceCell.getListOfValuesOrNamesAndAttributeName().getValues() != null) { // user locking is a moot point if the cell is already locked e.g. it's the result of a function
                    String result = azquoMemoryDBConnection.getAzquoMemoryDB().checkLocksForValueAndUser(databaseAccessToken.getUserId(), sourceCell.getListOfValuesOrNamesAndAttributeName().getValues());
                    if (result != null) { // it is locked
                        lockCheckResult.add(result); // collate lock message
                        sourceCell.setLocked(true); // and lock the cell!
                    }
                }
                // I can only add a comment here if it is a single value or single name
                Provenance p = null;
                if (sourceCell.getListOfValuesOrNamesAndAttributeName() != null){ // can it be? It seems so
                    if (sourceCell.getListOfValuesOrNamesAndAttributeName().getValues() != null && sourceCell.getListOfValuesOrNamesAndAttributeName().getValues().size() == 1){
                        p = sourceCell.getListOfValuesOrNamesAndAttributeName().getValues().get(0).getProvenance();
                    } else if (sourceCell.getListOfValuesOrNamesAndAttributeName().getNames() != null && sourceCell.getListOfValuesOrNamesAndAttributeName().getNames().size() == 1){
                        p = sourceCell.getListOfValuesOrNamesAndAttributeName().getNames().get(0).getProvenance();
                    }
                }
                String comment = null;
                if (p != null && p.getMethod() != null && p.getMethod().contains("comment :")){ // got to stop these kinds of string literals
                    comment = p.getMethod().substring(p.getMethod().indexOf("comment :") + "comment :".length()).trim();
                }
                displayDataRow.add(new CellForDisplay(sourceCell.isLocked(), sourceCell.getStringValue(), sourceCell.getDoubleValue(), sourceCell.isHighlighted(), sourceCell.getUnsortedRow(), sourceCell.getUnsortedCol(), ignored, sourceCell.isSelected(), comment));
                if (regionOptions.lockRequest && lockCheckResult.size() == 0) { // if we're going to lock gather all relevant values, stop gathering if we found data already locked
                    if (sourceCell.getListOfValuesOrNamesAndAttributeName() != null && sourceCell.getListOfValuesOrNamesAndAttributeName() != null && sourceCell.getListOfValuesOrNamesAndAttributeName().getValues() != null
                            && !sourceCell.getListOfValuesOrNamesAndAttributeName().getValues().isEmpty()) {
                        toLock.addAll(sourceCell.getListOfValuesOrNamesAndAttributeName().getValues());
                    }
                }
            }
        }
        if (lockCheckResult.size() == 0 && !toLock.isEmpty()) { // then we can lock
            azquoMemoryDBConnection.getAzquoMemoryDB().setValuesLockForUser(toLock, databaseAccessToken.getUserId());
        }

        //AzquoMemoryDB.printAllCountStats();
        //AzquoMemoryDB.clearAllCountStats();
        // this is single threaded as I assume not much data should be returned. Need to think about this.
        String lockCheckResultString = null;
        if (!lockCheckResult.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String result : lockCheckResult) {
                sb.append(result);
                sb.append("\n");
            }
            lockCheckResultString = sb.toString();
        }
        return new CellsAndHeadingsForDisplay(regionName, DataRegionHeadingService.convertDataRegionHeadingsToStrings(DataRegionHeadingService.getColumnHeadingsAsArray(data), databaseAccessToken.getLanguages())
                , DataRegionHeadingService.convertDataRegionHeadingsToStrings(DataRegionHeadingService.getRowHeadingsAsArray(data), databaseAccessToken.getLanguages()), displayData, rowHeadingsSource, colHeadingsSource, contextSource, azquoMemoryDBConnection.getDBLastModifiedTimeStamp(), regionOptions, lockCheckResultString);
    }


    // like above to find the relevant cell BUT in this case we want as much detail about how the cell was made. I'm just going to return a string here
    public static String getDebugForCell(DatabaseAccessToken databaseAccessToken, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource, int unsortedRow, int unsortedCol) throws Exception {
        int maxDebugLength = 2_000_000; // two million, a bit arbritrary for the moment
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        StringBuilder debugInfo = new StringBuilder();
        getSingleCellFromRegion(azquoMemoryDBConnection, rowHeadingsSource, colHeadingsSource, contextSource, unsortedRow, unsortedCol, databaseAccessToken.getLanguages(), debugInfo);
        if (debugInfo.length() > maxDebugLength) {
            return debugInfo.substring(0, maxDebugLength);
        } else {
            return debugInfo.toString();
        }
    }

    // create a file to import from a populated region in the spreadsheet
    private static void importDataFromSpreadsheet(AzquoMemoryDBConnection azquoMemoryDBConnection, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, String user) throws Exception {
        //write the column headings and data to a temporary file, then import it
        String fileName = "temp_" + user;
        File temp = File.createTempFile(fileName + ".csv", "csv");
        String tempPath = temp.getPath();
        BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
        StringBuffer sb = new StringBuffer();
        List<String> colHeadings = cellsAndHeadingsForDisplay.getColumnHeadings().get(0);
        boolean firstCol = true;
        for (String heading : colHeadings) {
            if (!firstCol) {
                sb.append("\t");
            } else {
                firstCol = false;
            }
            sb.append(heading);
        }
        bw.write(sb.toString());
        bw.newLine();
        List<List<CellForDisplay>> data = cellsAndHeadingsForDisplay.getData();
        for (List<CellForDisplay> row : data) {
            sb = new StringBuffer();
            firstCol = true;
            boolean blankLine = true;
            for (CellForDisplay cellForDisplay : row) {
                if (!firstCol) sb.append("\t");
                else firstCol = false;
                // A manual 0 will put a new string value in, a formulae one won't. Hence manual 0 should add a value and formulae 0 should not
                // todo - what about rules from Excel (as opposed to ZK, old logic)? This might just jam 0 in regardless
                // use string if we have it,otherwise double if it's not 0 or explicitly changed (0 allowed if manually entered). Otherwise blank.
                // this WAS only checking when there was a new string value being added - tripped up by formual calculations having new doubles but not new strings
                String val = (cellForDisplay.getNewStringValue() != null && cellForDisplay.getNewStringValue().length() > 0) ? cellForDisplay.getNewStringValue() : cellForDisplay.getNewDoubleValue() != 0 ? cellForDisplay.getNewDoubleValue() + "" : "";
                //for the moment we're passsing on cells that have not been entered as blanks which are ignored in the importer - this does not leave space for deleting values or attributes
                if (val.length() > 0) {
                    blankLine = false;
                    sb.append(val); // no point appending it if it's not there!
                }
            }
            if (!blankLine) {
                bw.write(sb.toString());
                bw.newLine();
            }
        }
        bw.flush();
        bw.close();
        DSImportService.readPreparedFile(azquoMemoryDBConnection, tempPath, "csv", Collections.singletonList(Constants.DEFAULT_DISPLAY_NAME), true, true);
        if (!temp.delete()) {// see no harm in this here. Delete on exit has a problem with Tomcat being killed from the command line. Why is intelliJ shirty about this?
            System.out.println("Unable to delete " + temp.getPath());
        }
    }

    public static void persistDatabase(DatabaseAccessToken databaseAccessToken) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        azquoMemoryDBConnection.persist();
    }

    public static void unlockData(DatabaseAccessToken databaseAccessToken) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        azquoMemoryDBConnection.getAzquoMemoryDB().removeValuesLockForUser(databaseAccessToken.getUserId());
    }

    // it's easiest just to send the CellsAndHeadingsForDisplay back to the back end and look for relevant changed cells
    // could I derive context from cells and headings for display? Also region. Worth considering . . .
    public static String saveData(DatabaseAccessToken databaseAccessToken, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, String user, String reportName, String context, boolean persist) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        int numberOfValuesModified = 0;
        synchronized (azquoMemoryDBConnection.getAzquoMemoryDB()) { // we don't want concurrent saves on a single database
            azquoMemoryDBConnection.getAzquoMemoryDB().removeValuesLockForUser(databaseAccessToken.getUserId()); // todo - is this the palce to unlock? It's probably fair
            boolean modifiedInTheMeanTime = azquoMemoryDBConnection.getDBLastModifiedTimeStamp() != cellsAndHeadingsForDisplay.getTimeStamp(); // if true we need to check if someone else changed the data
            // ad hoc saves regardless of changes in the mean time. Perhaps not the best plan . . .
            azquoMemoryDBConnection.setProvenance(user, "in spreadsheet", reportName, context);
            if (cellsAndHeadingsForDisplay.getRowHeadings() == null && cellsAndHeadingsForDisplay.getData().size() > 0) {
                importDataFromSpreadsheet(azquoMemoryDBConnection, cellsAndHeadingsForDisplay, user);
                if (persist) {
                    azquoMemoryDBConnection.persist();
                }
                return "true";
            }
            // check we're not getting cellsAndHeadingsForDisplay.getTimeStamp() = 0 here, it should only happen due tio ad hoc which should have returned by now . . .
            boolean changed = false;
            String toReturn = "";
            //modifiedInTheMeanTime = true;
            if (modifiedInTheMeanTime) { // then we need to compare data as sent to what it is now before trying to save - assuming this is not relevant to the import style above
                List<List<AzquoCell>> currentData = AzquoCellService.getDataRegion(azquoMemoryDBConnection, cellsAndHeadingsForDisplay.getRegion(), cellsAndHeadingsForDisplay.getRowHeadingsSource(), cellsAndHeadingsForDisplay.getColHeadingsSource(), cellsAndHeadingsForDisplay.getContextSource()
                        , cellsAndHeadingsForDisplay.getOptions(), databaseAccessToken.getLanguages(), 0, true);
                List<List<CellForDisplay>> sentData = cellsAndHeadingsForDisplay.getData();
                if (currentData.size() != sentData.size()) {
                    toReturn = "Data region " + cellsAndHeadingsForDisplay.getRegion() + " has changed size!";
                    changed = true;
                } else {
                    for (int y = 0; y < currentData.size(); y++) {
                        List<AzquoCell> currentRow = currentData.get(y);
                        List<CellForDisplay> sentRow = sentData.get(y);
                        if (currentRow.size() != sentRow.size()) {
                            changed = true;
                            toReturn = "Data region " + cellsAndHeadingsForDisplay.getRegion() + " has changed size!";
                            break;
                        } else {
                            for (int x = 0; x < currentRow.size(); x++) {
                                if (!compareCells(currentRow.get(x).getStringValue(), sentRow.get(x).getStringValue())) { // then I think data changed in the mean time? Need to test against blank areas etc
                                    final ListOfValuesOrNamesAndAttributeName listOfValuesOrNamesAndAttributeName = currentRow.get(x).getListOfValuesOrNamesAndAttributeName();
                                    // need to check provenance - if it's the same user then we don't flag the changes, could be an overlapping data region
                                    boolean sameUser = false;
                                    if (listOfValuesOrNamesAndAttributeName != null && listOfValuesOrNamesAndAttributeName.getValues() != null && listOfValuesOrNamesAndAttributeName.getValues().size() == 1) {
                                        if (listOfValuesOrNamesAndAttributeName.getValues().get(0).getProvenance().getUser().equals(user)) { // it's the same user!
                                            sameUser = true;
                                        }
                                    }
                                    if (!sameUser) {
                                        changed = true;
                                        toReturn = "Data in region " + cellsAndHeadingsForDisplay.getRegion() + " modified ";// - cell  " + x + ", " + y;
                                        if (listOfValuesOrNamesAndAttributeName != null && listOfValuesOrNamesAndAttributeName.getValues() != null && !listOfValuesOrNamesAndAttributeName.getValues().isEmpty()) {
                                            Provenance provenance = listOfValuesOrNamesAndAttributeName.getValues().iterator().next().getProvenance();
                                            toReturn += " by " + provenance.getUser() + " Dated: " + provenance.getTimeStamp();
                                            //toReturn += " provenance  " + listOfValuesOrNamesAndAttributeName.getValues().iterator().next().getProvenance();
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                        if (changed) {
                            break;
                        }
                    }
                }
            }
            if (changed) {
                return toReturn;
            }
            int rowCounter = 0;
            for (List<CellForDisplay> row : cellsAndHeadingsForDisplay.getData()) {
                int columnCounter = 0;
                for (CellForDisplay cell : row) {
                    // todo. sprt provenance for comments
                    if (!cell.isLocked() && cell.isChanged()) {
                        //logger.info(orig + "|" + edited + "|"); // we no longer know the original value unless we jam it in AzquoCell
                        // note, if there are many cells being edited this becomes inefficient as headings are being repeatedly looked up
                        AzquoCell azquoCell = getSingleCellFromRegion(azquoMemoryDBConnection, cellsAndHeadingsForDisplay.getRowHeadingsSource()
                                , cellsAndHeadingsForDisplay.getColHeadingsSource(), cellsAndHeadingsForDisplay.getContextSource()
                                , cell.getUnsortedRow(), cell.getUnsortedCol(), databaseAccessToken.getLanguages());
                        if (azquoCell != null && !azquoCell.isLocked()) {
                            numberOfValuesModified++;
                            // this save logic is the same as before but getting necessary info from the AzquoCell
                            final ListOfValuesOrNamesAndAttributeName valuesForCell = azquoCell.getListOfValuesOrNamesAndAttributeName();
                            if (valuesForCell != null) {
                                Provenance originalProvenance = azquoMemoryDBConnection.getProvenance();
                                if (cell.getComment() != null && !cell.getComment().isEmpty()){
                                    azquoMemoryDBConnection.setProvenance(user, "in spreadsheet, comment : " + cell.getComment(), reportName, context);
                                }
                                logger.info(columnCounter + ", " + rowCounter + " not locked and modified");
                                // one thing about these store functions to the value spreadsheet, they expect the provenance on the logged in connection to be appropriate
                                // first align text and numbers where appropriate
                        /* edd commenting 07/03/2016, this was stopping deleting a cell and I think it makes no sense looking at the ZK code that happens on editing, maybe a hangover from Aspose?
                        try {
                            if (cell.getNewDoubleValue() != 0.0) {
                                cell.setStringValue(cell.getNewDoubleValue() + "");
                            }
                        } catch (Exception ignored) {
                        }*/
                                //a cell can have a double value without having a string value.....
                                try {
                                    double d = cell.getNewDoubleValue();
                                    if (d != 0) {
                                        String numericValue = d + "";
                                        if (numericValue.endsWith(".0")) {
                                            numericValue = numericValue.substring(0, numericValue.length() - 2);
                                        }
                                        cell.setNewStringValue(numericValue);
                                    }
                                } catch (Exception ignored) {
                                }
                                if (cell.getNewStringValue() != null && cell.getNewStringValue().endsWith("%")) {
                                    String percent = cell.getNewStringValue().substring(0, cell.getNewStringValue().length() - 1);
                                    try {
                                        double d = Double.parseDouble(percent) / 100;
                                        cell.setNewStringValue(d + "");
                                    } catch (Exception e) {
                                        //do nothing
                                    }
                                }
                                final Set<DataRegionHeading> headingsForCell = HashObjSets.newMutableSet(azquoCell.getColumnHeadings().size() + azquoCell.getRowHeadings().size());
                                headingsForCell.addAll(azquoCell.getColumnHeadings());
                                headingsForCell.addAll(azquoCell.getRowHeadings());
                                headingsForCell.addAll(azquoCell.getContexts());
                                Name splitName = null;
                                for (DataRegionHeading heading : headingsForCell) {
                                    if (heading != null && heading.getSuffix() == DataRegionHeading.SUFFIX.SPLIT) {
                                        splitName = heading.getName(); // I suppose could be assigned null but this would be a nonsensical heading
                                        break;
                                    }
                                }
                                if (valuesForCell.getValues() != null) { // this assumes empty values rather than null if the populating code couldn't find any (as opposed to attribute cell that would be null values)
                                    // check for split first
                                    if (splitName != null) { // get the lowest level names and see if we can split the value among them
                                        try {
                                            double valueToSplit = 0;
                                            if (cell.getNewStringValue() != null && cell.getNewStringValue().length() > 0) {
                                                valueToSplit = Double.parseDouble(cell.getNewStringValue().replace(",", ""));
                                            }
                                            final List<Name> names = DataRegionHeadingService.namesFromDataRegionHeadings(headingsForCell);
                                            names.remove(splitName);
                                            List<Name> lowestChildren = new ArrayList<>();
                                            for (Name child : splitName.findAllChildren()) {
                                                if (!child.hasChildren()) {
                                                    lowestChildren.add(child);
                                                }
                                            }
                                            double splitValue = valueToSplit / lowestChildren.size();
                                            // ok now try to spread them around
                                            for (Name child : lowestChildren) {
                                                Set<Name> nameSet = new HashSet<>(names);
                                                nameSet.add(child); // so we now have the cells names except the split one but the child of the split one instead.
                                                // we want an exact match
                                                final List<Value> forNames = ValueService.findForNames(nameSet);
                                                if (forNames.size() > 1) {
                                                    System.out.println("multiple values found for a split, this should not happen! " + forNames);
                                                } else if (forNames.size() == 1) {
                                                    Value v = forNames.get(0);
                                                    if (splitValue == 0) { // we'll consider 0 OR blank deleting in this context
                                                        v.delete();
                                                        numberOfValuesModified++;
                                                    } else { // overwrite!
                                                        ValueService.overWriteExistingValue(azquoMemoryDBConnection, v, splitValue + ""); // a double to a string and back, hacky but that's the call for the mo
                                                    }
                                                } else { // new value!
                                                    if (splitValue != 0) { // then something to store
                                                        ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, splitValue + "", nameSet);
                                                        numberOfValuesModified++;
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                            System.out.println("unable to split value : " + e.getMessage());
                                        }
                                    } else { // normal behavior, most of the time
                                        if (valuesForCell.getValues().size() == 1) {
                                            final Value theValue = valuesForCell.getValues().get(0);
                                            if (cell.getNewStringValue() != null && cell.getNewStringValue().length() > 0) {
                                                //sometimes non-existent original values are stored as '0'
                                                ValueService.overWriteExistingValue(azquoMemoryDBConnection, theValue, cell.getNewStringValue());
                                                numberOfValuesModified++;
                                            } else {
                                                theValue.delete();
                                                numberOfValuesModified++;
                                            }
                                        } else if (valuesForCell.getValues().isEmpty() && cell.getNewStringValue() != null && cell.getNewStringValue().length() > 0) {
                                            List<Name> cellNames = DataRegionHeadingService.namesFromDataRegionHeadings(headingsForCell);
                                            ValueService.storeValueWithProvenanceAndNames(azquoMemoryDBConnection, cell.getNewStringValue(), new HashSet<>(cellNames));
                                            numberOfValuesModified++;
                                        }
                                        // warning on multiple values?
                                    }
                                } else {
                                    // added not null checks - can names or attributes be null here? Best check - todo
                                    if (valuesForCell.getNames() != null && valuesForCell.getNames().size() == 1
                                            && valuesForCell.getAttributeNames() != null && valuesForCell.getAttributeNames().size() == 1) { // allows a simple attribute store
                                        Name toChange = valuesForCell.getNames().get(0);
                                        String attribute = valuesForCell.getAttributeNames().get(0).substring(1).replace(StringLiterals.QUOTE + "", "");//remove the initial '.' and any `
                                        Name attSet = NameService.findByName(azquoMemoryDBConnection, attribute);
                                        if (attSet != null && attSet.hasChildren() && !azquoMemoryDBConnection.getAzquoMemoryDBIndex().attributeExistsInDB(attribute)) {
                                    /* right : when populating attribute based data findParentAttributes can be called internally in Name. DSSpreadsheetService is not aware of it but it means (in that case) the data
                                    returned is not in fact attributes but the name of an intermediate set in the hierarchy - suppose you want the category of a product the structure is
                                    all categories -> category -> product and .all categories is the column heading and the products are row headings then you'll get the category for the product as the cell value
                                     So attSet following that example is "All Categories", category is a (possibly) new name that's a child of all categories and then we need to add the product
                                     , named toChange at the moment to that category.
                                    */
                                            //logger.info("storing " + toChange.getDefaultDisplayName() + " to children of  " + cell.getNewStringValue() + " within " + attribute);
                                            Name category = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, cell.getNewStringValue(), attSet, true);
                                            category.addChildWillBePersisted(toChange);
                                        } else {// simple attribute set
                                            //logger.info("storing attribute value on " + toChange.getDefaultDisplayName() + " attribute " + attribute);
                                            toChange.setAttributeWillBePersisted(attribute, cell.getNewStringValue());
                                        }
                                    }
                                }
                                // switch provenance back if it was overridden due to a comment
                                if (originalProvenance != null){
                                    azquoMemoryDBConnection.setProvenance(originalProvenance);
                                }
                            }
                        }
                    }
                    columnCounter++;
                }
                rowCounter++;
            }
        } // the close of the block synchronised on the database, close it here before persisting since that is synchronized on the same object - if anything inside the block synchronizes on the database we'll find out pretty quickly!
        if (numberOfValuesModified > 0) {
            if (persist) {
                azquoMemoryDBConnection.persist();
            }
        }
        // clear the caches after, if we do before then some will be recreated as part of saving.
        // Is this a bit overkill given that it should clear as it goes? I suppose there's the query and count caches, plus parents of the changed names
        azquoMemoryDBConnection.getAzquoMemoryDB().clearCaches();
        return "true";
    }

    private static boolean compareCells(String a1, String a2) {
        if (a1.equals(a2)) {
            return true;
        }
        if (BatchImporter.isADate(a1) != null && BatchImporter.isADate(a1) == BatchImporter.isADate(a2)) {
            return true;
        }
        return false;
    }

    public static List<String> nameAutoComplete(DatabaseAccessToken databaseAccessToken, String s, int limit) throws Exception {
        Collection<Name> names = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken).getAzquoMemoryDBIndex().getNamesWithAttributeStarting(Constants.DEFAULT_DISPLAY_NAME, s);
        List<String> toReturn = new ArrayList<>();
        if (names == null || names.size() == 0) {//maybe it is a query
            names = NameQueryParser.parseQuery(AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken), s);
            if (names.size() > 0) {
                toReturn.add("QUERY RESULTS");
            }
        }
        int count = 0;
        for (Name name : names) {
            if (count >= limit) {
                break;
            }
            toReturn.add(name.getDefaultDisplayName());
            count++;
        }
        return toReturn;
    }

    public static AzquoCell getSingleCellFromRegion(AzquoMemoryDBConnection azquoMemoryDBCOnnection, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , int unsortedRow, int unsortedCol, List<String> languages) throws Exception {
        return getSingleCellFromRegion(azquoMemoryDBCOnnection, rowHeadingsSource, colHeadingsSource, contextSource, unsortedRow, unsortedCol, languages, null);
    }
    // when doing things like saving/provenance the client needs to say "here's a region description and original position" to locate a cell server side

    private static AzquoCell getSingleCellFromRegion(AzquoMemoryDBConnection azquoMemoryDBCOnnection, List<List<String>> rowHeadingsSource
            , List<List<String>> colHeadingsSource, List<List<String>> contextSource
            , int unsortedRow, int unsortedCol, List<String> languages, StringBuilder debugInfo) throws Exception {
        // these 25 lines or so are used elsewhere, maybe normalise?
        final List<DataRegionHeading> contextHeadings = DataRegionHeadingService.getContextHeadings(azquoMemoryDBCOnnection, contextSource, languages);
        DataRegionHeading.SUFFIX contextSuffix = null;
        for (DataRegionHeading contextHeading : contextHeadings) {
            if (contextHeading != null && (contextHeading.getSuffix() == DataRegionHeading.SUFFIX.LOCKED || contextHeading.getSuffix() == DataRegionHeading.SUFFIX.UNLOCKED)) {
                contextSuffix = contextHeading.getSuffix();
            }
        }
        Collection<Name> sharedNames = AzquoCellService.getSharedNames(contextHeadings);//sharedNames only required for permutations
        final List<List<List<DataRegionHeading>>> rowHeadingLists = DataRegionHeadingService.createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBCOnnection, rowHeadingsSource, languages, contextSuffix, false); // don't surpress errors, will this be a problem?
        final List<List<DataRegionHeading>> rowHeadings = DataRegionHeadingService.expandHeadings(rowHeadingLists, sharedNames);
        final List<List<List<DataRegionHeading>>> columnHeadingLists = DataRegionHeadingService.createHeadingArraysFromSpreadsheetRegion(azquoMemoryDBCOnnection, colHeadingsSource, languages, AzquoCellService.COL_HEADINGS_NAME_QUERY_LIMIT, contextSuffix, false); // same as standard limit for col headings
        final List<List<DataRegionHeading>> columnHeadings = DataRegionHeadingService.expandHeadings(MultidimensionalListUtils.transpose2DList(columnHeadingLists), sharedNames);
        if (columnHeadings.size() == 0 || rowHeadings.size() == 0) {
            return null;
        }
        // now onto the bit to find the specific cell - the column headings were transposed then expanded so they're in the same format as the row headings
        // that is to say : the outside list's size is the number of columns or headings. So, do we have the row and col?
        if (unsortedRow < rowHeadings.size() && unsortedCol < columnHeadings.size()) {
            return AzquoCellResolver.getAzquoCellForHeadings(azquoMemoryDBCOnnection, rowHeadings.get(unsortedRow), columnHeadings.get(unsortedCol), contextHeadings, unsortedRow, unsortedCol, languages, 0, null, debugInfo);
        }
        return null; // no headings match the row/col passed
    }
}