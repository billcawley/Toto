package com.azquo.admin.controller;

import com.azquo.ExternalConnector;
import com.azquo.admin.StandardDAO;
import com.azquo.dataimport.*;
import com.csvreader.CsvWriter;
import com.ecwid.maleorang.MailchimpException;
import com.extractagilecrm.ExtractContacts;
import com.extractappointedd.ExtractAppointedd;
import com.extractdotdigital.ExtractDotDigital;
import com.extractmailchimp.ExtractMailchimp;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.github.rcaller.rstuff.RCaller;
import com.github.rcaller.rstuff.RCode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.util.PDFTextStripper;
import org.apache.pdfbox.util.TextPosition;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.zkoss.poi.xssf.usermodel.XSSFRow;
import org.zkoss.poi.xssf.usermodel.XSSFSheet;
import org.zkoss.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Created by EFC on 17/11/20.
 */
@Controller
@RequestMapping("/Test")
public class TestController {

    private static class XYPair {
        private float x;
        private float y;

        public XYPair(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public float getX() {
            return x;
        }

        public float getY() {
            return y;
        }
    }


    class PrintTextLocator extends PDFTextStripper {

        final Map<String, XYPair> skuCoordinates;

        public PrintTextLocator(Map<String, XYPair> skuCoordinates) throws IOException {
            super();
            this.skuCoordinates = skuCoordinates;
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            super.writeString(text, textPositions);
            if (text.startsWith("Product Code:")) {
//                System.out.print(text);
                TextPosition textPosition = textPositions.get(0);
                float x = textPosition.getX();
                float y = textPosition.getY();
                String sku = text.substring("Product Code:".length()).trim();
                if (sku.contains(" ")) {
                    sku = sku.substring(0, sku.indexOf(" "));
                }
                skuCoordinates.put(sku, new XYPair(x, y));
            }
        }
    }


    void classifySKUPagePositions(Map<String, XYPair> skuCoordinates, Map<String, String> classifications, Map<String, Integer> pagePercentages) {
        // todo - add % of page taken up
        skuCoordinates = new HashMap<>(skuCoordinates); // copy as the combining will change the map
        // ok if two skus are within 15% of each other then we combine them

        boolean combined = false;
        boolean runCombineCheck = true;
        while (runCombineCheck) {
            String combine1 = null;
            String combine2 = null;
            for (String sku1 : skuCoordinates.keySet()) {
                for (String sku2 : skuCoordinates.keySet()) {
                    XYPair coordinates1 = skuCoordinates.get(sku1);
                    XYPair coordinates2 = skuCoordinates.get(sku2);
                    if (coordinates1 != coordinates2 &&
                            (Math.abs(coordinates1.getX() - coordinates2.getX()) + Math.abs(coordinates1.getY() - coordinates2.getY()) < .15)
                    ) { // then combine them
                        combine1 = sku1;
                        combine2 = sku2;
                        break;
                    }
                }
                if (combine1 != null) {
                    break;
                }
            }
            if (combine1 != null) {
                combined = true;
                skuCoordinates.put(combine1 + "&&" + combine2, new XYPair((skuCoordinates.get(combine1).getX() + skuCoordinates.get(combine2).getX()) / 2, (skuCoordinates.get(combine1).getY() + skuCoordinates.get(combine2).getY()) / 2));
                skuCoordinates.remove(combine1);
                skuCoordinates.remove(combine2);
            } else {
                runCombineCheck = false;
            }
        }


        if (skuCoordinates.size() == 1) {
            classifications.put(skuCoordinates.keySet().iterator().next(), "FullPage");
            pagePercentages.put(skuCoordinates.keySet().iterator().next(), 100);
        }
        if (skuCoordinates.size() == 2) {
            Iterator<String> iterator = skuCoordinates.keySet().iterator();
            String sku1 = iterator.next();
            String sku2 = iterator.next();

            int sku1X = Math.round(skuCoordinates.get(sku1).getX() * 100);
            int sku1Y = Math.round(skuCoordinates.get(sku1).getY() * 100);
            int sku2X = Math.round(skuCoordinates.get(sku2).getX() * 100);
            int sku2Y = Math.round(skuCoordinates.get(sku2).getY() * 100);

            if (sku1X == sku2X) {
                if (sku1Y < 50 && sku2Y > 50) {
                    classifications.put(sku1, "Top");
                    classifications.put(sku2, "Bottom");
                }
                if (sku2Y < 50 && sku1Y > 50) {
                    classifications.put(sku2, "Top");
                    classifications.put(sku1, "Bottom");
                }
            }
            if (sku1Y == sku2Y) {
                if (sku1X < 50 && sku2X > 50) {
                    classifications.put(sku1, "Left");
                    classifications.put(sku2, "Right");
                }
                if (sku2X < 50 && sku1X > 50) {
                    classifications.put(sku2, "Left");
                    classifications.put(sku1, "Right");
                }
            }
            if (!classifications.isEmpty()) {
                pagePercentages.put(sku1, 50);
                pagePercentages.put(sku2, 50);
            }
        }

        // .45 based on careco thresholds, y adjusted to .47
        if (skuCoordinates.size() == 4 || skuCoordinates.size() == 3) {
            // check for a basic split 4 ways
            for (String sku : skuCoordinates.keySet()) {
                pagePercentages.put(sku, 25);
                if (skuCoordinates.get(sku).getX() > .45) { // right
                    if (skuCoordinates.get(sku).getY() > .47) { // bottom
                        classifications.put(sku, "BottomRight");
                    } else {
                        classifications.put(sku, "TopRight");
                    }
                } else { // left
                    if (skuCoordinates.get(sku).getY() > .47) { // bottom
                        classifications.put(sku, "BottomLeft");
                    } else {
                        classifications.put(sku, "TopLeft");
                    }
                }
            }
            if (new HashSet<>(classifications.values()).size() != skuCoordinates.size()) { // then there were duplicates e.g. two top lefts, no good for this methodology, clear the set
                classifications.clear();
                pagePercentages.clear();
            } else if (skuCoordinates.size() == 3) { // then one is a half, just a top or bottom, need to find which one
                int bottoms = 0;
                int tops = 0;
                for (String classification : classifications.values()) {
                    if (classification.contains("Top")) {
                        tops++;
                    }
                    if (classification.contains("Bottom")) {
                        bottoms++;
                    }
                }
                // we assume that half pages are horizontal
                for (String sku : classifications.keySet()) {
                    if (classifications.get(sku).contains("Top") && tops == 1) {
                        classifications.put(sku, "Top");
                        pagePercentages.put(sku, 50);
                    }
                    if (classifications.get(sku).contains("Bottom") && bottoms == 1) {
                        classifications.put(sku, "Bottom");
                        pagePercentages.put(sku, 50);
                    }
                }
            }
        }
        if (classifications.isEmpty()) { // try for a three row page, making a judgement call of .35 - .65 for middle
            for (String sku : skuCoordinates.keySet()) {
                pagePercentages.put(sku, 17);
                if (skuCoordinates.get(sku).getX() > .45) { // right
                    if (skuCoordinates.get(sku).getY() > .65) { // bottom
                        classifications.put(sku, "BottomRight");
                    } else if (skuCoordinates.get(sku).getY() > .35) { // middle
                        classifications.put(sku, "MiddleRight");
                    } else {// top
                        classifications.put(sku, "TopRight");
                    }
                } else { // left
                    if (skuCoordinates.get(sku).getY() > .65) { // bottom
                        classifications.put(sku, "BottomLeft");
                    } else if (skuCoordinates.get(sku).getY() > .35) { // middle
                        classifications.put(sku, "MiddleLeft");
                    } else {// top
                        classifications.put(sku, "TopLeft");
                    }
                }
            }
            if (new HashSet<>(classifications.values()).size() != skuCoordinates.size()) { // then there were duplicates e.g. two top lefts, no good for this methodology, clear the set
                classifications.clear();
                pagePercentages.clear();
            } else if (skuCoordinates.size() < 6) { // at least one is spanning a row
                int bottoms = 0;
                int tops = 0;
                int middles = 0;
                for (String classification : classifications.values()) {
                    if (classification.contains("Top")) {
                        tops++;
                    }
                    if (classification.contains("Middle")) {
                        middles++;
                    }
                    if (classification.contains("Bottom")) {
                        bottoms++;
                    }
                }
                // we assume that half pages are horizontal
                for (String sku : classifications.keySet()) {
                    if (classifications.get(sku).contains("Top") && tops == 1) {
                        classifications.put(sku, "Top");
                        pagePercentages.put(sku, 33);
                    }
                    if (classifications.get(sku).contains("Middle") && middles == 1) {
                        classifications.put(sku, "Middle");
                        pagePercentages.put(sku, 33);
                    }
                    if (classifications.get(sku).contains("Bottom") && bottoms == 1) {
                        classifications.put(sku, "Bottom");
                        pagePercentages.put(sku, 33);
                    }
                }
            }
        }
        // need to do 8 and 10, there will be some copy pasting. Probably will be able to factor later but it will involve some thinking
        if (classifications.isEmpty()) {
            for (String sku : skuCoordinates.keySet()) {
                pagePercentages.put(sku, 12);
                if (skuCoordinates.get(sku).getX() > .45) { // right
                    if (skuCoordinates.get(sku).getY() > .70) { // bottom
                        classifications.put(sku, "BottomRight");
                    } else if (skuCoordinates.get(sku).getY() > .50) {
                        classifications.put(sku, "LowerRight");
                    } else if (skuCoordinates.get(sku).getY() > .30) {
                        classifications.put(sku, "UpperRight");
                    } else {// top
                        classifications.put(sku, "TopRight");
                    }
                } else { // left
                    if (skuCoordinates.get(sku).getY() > .70) { // bottom
                        classifications.put(sku, "BottomLeft");
                    } else if (skuCoordinates.get(sku).getY() > .50) { // bottom middle
                        classifications.put(sku, "LowerLeft");
                    } else if (skuCoordinates.get(sku).getY() > .30) { // top middle
                        classifications.put(sku, "UpperLeft");
                    } else {// top
                        classifications.put(sku, "TopLeft");
                    }
                }
            }
            if (new HashSet<>(classifications.values()).size() != skuCoordinates.size()) { // then there were duplicates e.g. two top lefts, no good for this methodology, clear the set
                classifications.clear();
                pagePercentages.clear();
            } else if (skuCoordinates.size() < 6) { // at least one is spanning a row
                int bottoms = 0;
                int lowers = 0;
                int uppers = 0;
                int tops = 0;
                for (String classification : classifications.values()) {
                    if (classification.contains("Top")) {
                        tops++;
                    }
                    if (classification.contains("Lower")) {
                        lowers++;
                    }
                    if (classification.contains("Upper")) {
                        uppers++;
                    }
                    if (classification.contains("Bottom")) {
                        bottoms++;
                    }
                }
                // we assume that half pages are horizontal
                for (String sku : classifications.keySet()) {
                    if (classifications.get(sku).contains("Top") && tops == 1) {
                        classifications.put(sku, "Top");
                        pagePercentages.put(sku, 20);
                    }
                    if (classifications.get(sku).contains("Upper") && uppers == 1) {
                        classifications.put(sku, "Upper");
                        pagePercentages.put(sku, 20);
                    }
                    if (classifications.get(sku).contains("Lower") && lowers == 1) {
                        classifications.put(sku, "Lower");
                        pagePercentages.put(sku, 20);
                    }
                    if (classifications.get(sku).contains("Bottom") && bottoms == 1) {
                        classifications.put(sku, "Bottom");
                        pagePercentages.put(sku, 20);
                    }
                }
            }
        }


        while (combined) {
            boolean removed = false;
            for (String sku : new ArrayList<>(classifications.keySet())) {
                if (sku.contains("&&")) {
                    classifications.put(sku.substring(0, sku.indexOf("&&")), (classifications.get(sku) + "Shared").replace("SharedShared", "Shared"));
                    classifications.put(sku.substring(sku.indexOf("&&") + 2), (classifications.get(sku) + "Shared").replace("SharedShared", "Shared"));
                    classifications.keySet().remove(sku);
                    pagePercentages.put(sku.substring(0, sku.indexOf("&&")), pagePercentages.get(sku));
                    pagePercentages.put(sku.substring(sku.indexOf("&&") + 2), pagePercentages.get(sku));
                    classifications.keySet().remove(sku);
                    pagePercentages.keySet().remove(sku);
                    removed = true;
                }
            }
            combined = removed;
        }
    }

    @RequestMapping
    @ResponseBody
    public String handleRequest(
            @RequestParam(value = "something", required = false) String something
    ) {
        if ("snowflake".equals(something)){
                System.out.println(ExternalConnector.getData("", "select *  from TPCH_SF1.ORDERS INNER JOIN TPCH_SF1.CUSTOMER ON O_CUSTKEY = C_CUSTKEY AND O_ORDERPRIORITY = '5-LOW' limit 1000;"));
        }
        if ("bonza".equals(something)){
            ExtractContacts.extractContacts("https://bonzabfs.agilecrm.com/dev", "shaun.dodimead@azquo.com", "evjgnce8ou9hn77e4ma7uvjgcg", "/home/edward/Downloads/contacts(preprocessor = bonza contact preprocessor)", 183);
        }

        if ("mailchimp".equals(something)) {
            try {
// azquo                ExtractMailchimp.extractData("516f98d1f28e8cc97a2b8da9025d3b78-us1");
                ExtractMailchimp.extractData("7098a7c3111522fa3efcea3aff87d976-us19");
            } catch (IOException | MailchimpException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        if ("dotdigital".equals(something)) {
            try {
                ExtractDotDigital.extractDotDigital();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if ("agilecrm".equals(something)) {
//            DBCron.checkHourlyImport();
            DBCron.checkDailyImport();
        }

        if ("appointedd".equals(something)) {
            DBCron.check5MinsImport();
        }

        if ("pdfbox".equals(something)) {
            /*XSSFWorkbook wb = new XSSFWorkbook();
            XSSFSheet user_activity = wb.createSheet("User Activity");
            int rownum = 0;
            XSSFRow toprow = user_activity.createRow(rownum);
            toprow.createCell(0).setCellValue("Page");
            toprow.createCell(1).setCellValue("SKU");
            toprow.createCell(2).setCellValue("Location");
            toprow.createCell(3).setCellValue("Page %");*/

            try (Stream<Path> transferDir = Files.list(Paths.get("/home/edward/Downloads/tocheck"))) {
                Iterator<Path> transferDirIterator = transferDir.iterator();
                // go through the main directory looking for directories that match a DB name
                while (transferDirIterator.hasNext()) {
                    Path p = transferDirIterator.next();
                    if (p.toString().endsWith(".pdf")){
                        Map<Integer, Map<String, XYPair>> skuCoordinates = new HashMap<>();

                        PDDocument pdDoc = null;
                        PDFTextStripper pdfStripper;

                        String pdfName = p.toFile().getName();
                        File file = p.toFile();
                        try {
                            pdfStripper = new PDFTextStripper();
                            pdfStripper.setStartPage(1);
                            pdfStripper.setEndPage(1);
                            pdDoc = PDDocument.load(new FileInputStream(file));
                            XSSFWorkbook wb = new XSSFWorkbook();
                            XSSFSheet user_activity = wb.createSheet("Classsifications");
                            int rownum = 0;
                            XSSFRow toprow = user_activity.createRow(rownum);
                            toprow.createCell(0).setCellValue("Page");
                            toprow.createCell(1).setCellValue("SKU");
                            toprow.createCell(2).setCellValue("Location");
                            toprow.createCell(3).setCellValue("Percentage");



//                System.out.println(pdfStripper.getText(pdDoc));

                            List<PDPage> pages = pdDoc.getDocumentCatalog().getAllPages();
                            int pageNo = 1;
                            for (PDPage page : pages) {
                                Map<String, XYPair> skuCoordinatesForPage = new HashMap<>();
                                PrintTextLocator locator = new PrintTextLocator(skuCoordinatesForPage);
                                locator.setStartPage(pageNo);
                                locator.setEndPage(pageNo);
                                float height = page.getMediaBox().getHeight();
                                float width = page.getMediaBox().getWidth();
                                locator.getText(pdDoc);
                                Map<String, XYPair> percentageSkuCoordinatesForPage = new HashMap<>();
                                for (String sku : skuCoordinatesForPage.keySet()) {
                                    XYPair xyPair = skuCoordinatesForPage.get(sku);
                                    percentageSkuCoordinatesForPage.put(sku, new XYPair(xyPair.getX() / width, xyPair.getY() / height));
                                }
                                skuCoordinates.put(pageNo, percentageSkuCoordinatesForPage);
                                pageNo++;
                            }
                            NumberFormat percentInstance = NumberFormat.getPercentInstance();
                            for (Integer page : skuCoordinates.keySet()) {

                                System.out.println("page : " + page);
                                Map<String, String> skusclassifications = new HashMap<>();
                                Map<String, Integer> skuspercentages = new HashMap<>();
                                classifySKUPagePositions(skuCoordinates.get(page), skusclassifications, skuspercentages);
                                for (String sku : skuCoordinates.get(page).keySet()) {
                                    rownum++;
                                    XSSFRow row = user_activity.createRow(rownum);
                                    row.createCell(0).setCellValue(page);
                                    row.createCell(1).setCellValue(sku);
                                    row.createCell(2).setCellValue(skusclassifications.get(sku) != null ? skusclassifications.get(sku) : "X " + percentInstance.format(skuCoordinates.get(page).get(sku).getX()) + " Y " + percentInstance.format(skuCoordinates.get(page).get(sku).getY()));
                                    row.createCell(3).setCellValue(skuspercentages.get(sku) != null ? skuspercentages.get(sku) : 0);
/*                        System.out.print(page);
                        System.out.print(" ");
                        System.out.print(sku);
                        System.out.print(" ");
                        System.out.print(skusclassifications.get(sku) != null ? skusclassifications.get(sku) : "X " + percentInstance.format(skuCoordinates.get(page).get(sku).getX()) + " Y " + percentInstance.format(skuCoordinates.get(page).get(sku).getY()));
                        System.out.print(" ");
                        System.out.println("" + (skuspercentages.get(sku) != null ? skuspercentages.get(sku) : 0));*/
                                }
                            }
/*                response.setContentType("application/vnd.ms-excel"); // Set up mime type
                response.addHeader("Content-Disposition", "attachment; filename=catalogueparse.xlsx");
                OutputStream out = response.getOutputStream();
                wb.write(out);
                out.close();*/
                            wb.write(new FileOutputStream("/home/edward/Downloads/tocheck/" + pdfName.substring(0, pdfName.length() - 4) + ".xlsx"));
                            pdDoc.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                            try {
                                if (pdDoc != null)
                                    pdDoc.close();
                            } catch (Exception e1) {
                                e1.printStackTrace();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        if ("xmlparse".equals(something)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder builder;
            try {
                builder = factory.newDocumentBuilder();
                Files.walkFileTree(Paths.get("/home/edward/Downloads/artest/"), new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path path, IOException e) {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path testDir, IOException e) throws IOException {
                        Map<String, Map<String, String>> filesValues = new HashMap<>();// filename, values
                        AtomicReference<String> rootDocumentName = new AtomicReference<>();
                        Set<String> headings = new HashSet<>();
                        Stream<Path> list = Files.list(testDir);
                        list.forEach(path -> {
                            // Do stuff
                            if (!Files.isDirectory(path)) { // skip any directories
                                try {
                                    String origName = path.getFileName().toString();
                                    if (origName.toLowerCase().contains(".xml")) {
                                        String fileKey = origName.substring(0, origName.lastIndexOf("."));
                                        FileTime lastModifiedTime = Files.getLastModifiedTime(path);
                                        System.out.println("file : " + origName);
                                        // newer logic, start with the original sent data then add anything from brokasure on. Will help Bill/Nic to parse
                                        // further to this we'll only process files that have a corresponding temp file as Dev and UAT share directories so if there's no matching file in temp don't do anything
                                        DBCron.readXML(fileKey, filesValues, rootDocumentName, builder, path, headings, lastModifiedTime);
                                    }
                                } catch (Exception e1) {
                                    e1.printStackTrace();
                                }
                            }
                        });
                        if (!filesValues.isEmpty()) {
                            // now Hanover has been added there's an issue that files can come back and go in different databases
                            // so we may need to make more than one file for a block of parsed files, batch them up
                            List<Map<String, String>> lines = new ArrayList<>(filesValues.values());
                            // base the file name off the db name also
                            String csvFileName = System.currentTimeMillis() + "-eddtest" + rootDocumentName.get() + ".tsv";
                            BufferedWriter bufferedWriter = Files.newBufferedWriter(testDir.resolve(csvFileName), StandardCharsets.UTF_8);
                            for (String heading : headings) {
                                bufferedWriter.write(heading + "\t");
                            }
                            bufferedWriter.newLine();
                            for (Map<String, String> lineValues : lines) {
                                for (String heading : headings) {
                                    String value = lineValues.get(heading);
                                    bufferedWriter.write((value != null ? value : "") + "\t");
                                }
                                bufferedWriter.newLine();
                            }
                            bufferedWriter.close();
                        }

                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if ("rtest".equals(something)) {
            RCaller caller = RCaller.create();
            RCode code = RCode.create();

            double[] arr = new double[]{1.0, 2.0, 3.0};
            code.addDoubleArray("myarr", arr);
            code.addRCode("avg <- mean(myarr)");
            caller.setRCode(code);
            caller.runAndReturnResult("avg");
            double[] result = caller.getParser().getAsDoubleArray("avg");
//            return result[0] + "";
        }

        if ("shopwaretest".equals(something)) {
            try {
                // it seems there is no jar for shopware hence I need to roll my own JSON. This is a pain . . .
                final ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                URL url = new URL("http://jbi.shopwaretest.de/api/oauth/token");

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
/*                Map<String, List<String>> map2 = conn.getRequestProperties();
                for (Map.Entry<String, List<String>> entry : map2.entrySet()) {
                    System.out.println("Key : " + entry.getKey() +
                            " ,Value : " + entry.getValue());
                }*/
                OutputStreamWriter osw = new OutputStreamWriter(conn.getOutputStream());
                //System.out.println(jacksonMapper.writeValueAsString(new OAuthLogin("administration", "password", "read", "edlennox", "ruletheroof")));
                osw.write(jacksonMapper.writeValueAsString(new OAuthLogin("administration", "password", "write", "edlennox", "ruletheroof")));
                osw.flush();
                osw.close();
/*                Map<String, List<String>> map = conn.getHeaderFields();
                for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                    System.out.println("Key : " + entry.getKey() +
                            " ,Value : " + entry.getValue());
                }*/


                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder stringBuffer = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuffer.append(line);
                }

                OauthResponse oauthResponse = jacksonMapper.readValue(stringBuffer.toString(), OauthResponse.class);
/*

order ok - date constraint?
line items ok - can get in bulk? Perhaps not . . .
delivery address - again with the bulk question
returns - nothing there?
order source - slaes channel? Seems emptyn on first poc but ok
cancelled
customer - address enough?

 */
                url = new URL("http://jbi.shopwaretest.de/api/v3/order");
//                url = new URL("http://jbi.shopwaretest.de/api/v3/product");

                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", oauthResponse.access_token);
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                stringBuffer = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    stringBuffer.append(line);
                }
                reader.close();
// this may not be that efficient, just get it working first
                String json = stringBuffer.toString();
                Set<String> ordersheadings = new HashSet<>();
                Set<String> orderlinesheadings = new HashSet<>();
                Set<String> productheadings = new HashSet<>();
                JsonNode jsonNode = new ObjectMapper().readTree(json);
                List<Map<String, String>> ordersmaplist = new ArrayList<>();
                List<Map<String, String>> productsmaplist = new ArrayList<>();
                List<Map<String, String>> orderlinesmaplist = new ArrayList<>();
                if (jsonNode.isObject()) {
                    ObjectNode objectNode = (ObjectNode) jsonNode;
                    Iterator<Map.Entry<String, JsonNode>> iter = objectNode.fields();
                    while (iter.hasNext()) {
                        Map.Entry<String, JsonNode> entry = iter.next();
                        if (entry.getKey().equals("data")) {
                            JsonNode arrayNode = entry.getValue();
                            for (int i = 0; i < arrayNode.size(); i++) {
                                Map<String, String> map = new HashMap<>();
                                addKeys("", arrayNode.get(i), map);
                                // hack in the extra stuff we want from order

                                if (map.get("id") != null && !map.get("id").equals("null")) {
                                    url = new URL("http://jbi.shopwaretest.de/api/v3/order/" + map.get("id") + "/addresses");

                                    conn = (HttpURLConnection) url.openConnection();
                                    conn.setRequestProperty("Accept", "application/json");
                                    conn.setRequestProperty("Authorization", oauthResponse.access_token);
                                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                                    StringBuilder addressinfo = new StringBuilder();
                                    while ((line = reader.readLine()) != null) {
                                        addressinfo.append(line);
                                    }
                                    reader.close();
                                    JsonNode aJsonNode = new ObjectMapper().readTree(addressinfo.toString());
                                    if (aJsonNode.isObject()) {
                                        Iterator<Map.Entry<String, JsonNode>> iter1 = aJsonNode.fields();
                                        while (iter1.hasNext()) {
                                            Map.Entry<String, JsonNode> entry1 = iter1.next();
                                            if (entry1.getKey().equals("data")) {
                                                addKeys("address", entry1.getValue(), map);
                                            }
                                        }
                                    }
                                    url = new URL("http://jbi.shopwaretest.de/api/v3/order/" + map.get("id") + "/salesChannel");

                                    conn = (HttpURLConnection) url.openConnection();
                                    conn.setRequestProperty("Accept", "application/json");
                                    conn.setRequestProperty("Authorization", oauthResponse.access_token);
                                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                                    StringBuilder salesChannel = new StringBuilder();
                                    while ((line = reader.readLine()) != null) {
                                        salesChannel.append(line);
                                    }
                                    reader.close();
                                    JsonNode sJsonNode = new ObjectMapper().readTree(salesChannel.toString());
                                    if (sJsonNode.isObject()) {
                                        Iterator<Map.Entry<String, JsonNode>> iter1 = sJsonNode.fields();
                                        while (iter1.hasNext()) {
                                            Map.Entry<String, JsonNode> entry1 = iter1.next();
                                            if (entry1.getKey().equals("data")) {
                                                addKeys("saleschannel", entry1.getValue(), map);
                                            }
                                        }
                                    }

                                    // gonna put orderlines in their own file
                                    url = new URL("http://jbi.shopwaretest.de/api/v3/order/" + map.get("id") + "/lineItems");
                                    conn = (HttpURLConnection) url.openConnection();
                                    conn.setRequestProperty("Accept", "application/json");
                                    conn.setRequestProperty("Authorization", oauthResponse.access_token);
                                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                                    StringBuilder orderLines = new StringBuilder();
                                    while ((line = reader.readLine()) != null) {
                                        orderLines.append(line);
                                    }
                                    reader.close();

                                    JsonNode orderLinesNode = new ObjectMapper().readTree(orderLines.toString());
                                    if (orderLinesNode.isObject()) {
                                        ObjectNode orderLinesNode1 = (ObjectNode) orderLinesNode;
                                        Iterator<Map.Entry<String, JsonNode>> iter1 = orderLinesNode1.fields();
                                        while (iter1.hasNext()) {
                                            Map.Entry<String, JsonNode> entry1 = iter1.next();
                                            if (entry1.getKey().equals("data")) {
                                                JsonNode arrayNode1 = entry1.getValue();
                                                for (int j = 0; j < arrayNode1.size(); j++) {
                                                    Map<String, String> olMap = new HashMap<>();
                                                    addKeys("", arrayNode1.get(j), olMap);
                                                    orderlinesmaplist.add(olMap);
                                                    orderlinesheadings.addAll(olMap.keySet());
                                                }
                                            }
                                        }
                                    }
                                }
                                ordersmaplist.add(map);
                                ordersheadings.addAll(map.keySet());
                            }
                        }
                    }
                }

                // product- pretty simple hopefully

                // gonna put orderlines in their own file
                url = new URL("http://jbi.shopwaretest.de/api/v3/product/");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", oauthResponse.access_token);
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder productLines = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    productLines.append(line);
                }
                reader.close();

                JsonNode productLinesNode = new ObjectMapper().readTree(productLines.toString());
                if (productLinesNode.isObject()) {
                    ObjectNode productLinesNode1 = (ObjectNode) productLinesNode;
                    Iterator<Map.Entry<String, JsonNode>> iter1 = productLinesNode1.fields();
                    while (iter1.hasNext()) {
                        Map.Entry<String, JsonNode> entry1 = iter1.next();
                        if (entry1.getKey().equals("data")) {
                            JsonNode arrayNode1 = entry1.getValue();
                            for (int j = 0; j < arrayNode1.size(); j++) {
                                Map<String, String> map = new HashMap<>();
                                addKeys("", arrayNode1.get(j), map);
                                productsmaplist.add(map);
                                productheadings.addAll(map.keySet());
                            }
                        }
                    }
                }


                // knock off empty headings
                Iterator<String> headingsIterator = ordersheadings.iterator();
                while (headingsIterator.hasNext()) {
                    String heading = headingsIterator.next();
                    boolean hasData = false;
                    for (Map<String, String> map : ordersmaplist) {
                        String test = map.get(heading);
                        if (test != null && !test.isEmpty() && !test.equals("null")) {
                            hasData = true;
                            break;
                        }
                    }
                    if (!hasData) {
                        headingsIterator.remove();
                    }
                }

                headingsIterator = orderlinesheadings.iterator();
                while (headingsIterator.hasNext()) {
                    String heading = headingsIterator.next();
                    boolean hasData = false;
                    for (Map<String, String> map : orderlinesmaplist) {
                        String test = map.get(heading);
                        if (test != null && !test.isEmpty() && !test.equals("null")) {
                            hasData = true;
                            break;
                        }
                    }
                    if (!hasData) {
                        headingsIterator.remove();
                    }
                }

                headingsIterator = productheadings.iterator();
                while (headingsIterator.hasNext()) {
                    String heading = headingsIterator.next();
                    boolean hasData = false;
                    for (Map<String, String> map : productsmaplist) {
                        String test = map.get(heading);
                        if (test != null && !test.isEmpty() && !test.equals("null")) {
                            hasData = true;
                            break;
                        }
                    }
                    if (!hasData) {
                        headingsIterator.remove();
                    }
                }

                //Map<String, String> map = new HashMap<>();
                //addKeys("", new ObjectMapper().readTree(json), map);
                //maplist.add(map);
                //headings.addAll(map.keySet());


                CsvWriter csvW = new CsvWriter("/home/edward/Downloads/orders.tsv", '\t', StandardCharsets.UTF_8);
                csvW.setUseTextQualifier(false);
                writeCSV("/home/edward/Downloads/orders.tsv", new ArrayList<>(ordersheadings), ordersmaplist);
                writeCSV("/home/edward/Downloads/orderlines.tsv", new ArrayList<>(orderlinesheadings), orderlinesmaplist);
                writeCSV("/home/edward/Downloads/products.tsv", new ArrayList<>(productheadings), productsmaplist);

                //              return "done";

                /*

                url = new URL("http://jbi.shopwaretest.de/api/v3/search/product");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Authorization", oauthResponse.access_token);
                osw = new OutputStreamWriter(conn.getOutputStream());
                //System.out.println(jacksonMapper.writeValueAsString(new OAuthLogin("administration", "password", "read", "edlennox", "ruletheroof")));
                osw.write("{" +
                        "    \"includes\": {\n" +
                        "        \"product\": [\"id\", \"name\", \"manufacturer\", \"tax\"],\n" +
                        "        \"product_manufacturer\": [\"id\", \"name\"],\n" +
                        "        \"tax\": [\"id\", \"name\"]\n" +
                        "    }" +
                        "}"


                );
                osw.flush();
                osw.close();
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                stringBuffer = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    stringBuffer.append(line);
                }

                 */

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return "done";
    }

    public static void writeCSV(String fileLocation, List<String> headings, List<Map<String, String>> data) throws
            IOException {
        CsvWriter csvW = new CsvWriter(fileLocation, '\t', StandardCharsets.UTF_8);
        csvW.setUseTextQualifier(false);
        for (String heading : headings) {
            csvW.write(heading);
        }
        csvW.endRecord();

        for (Map<String, String> map : data) {
            for (String heading : headings) {
                String val = map.get(heading) != null ? map.get(heading) : "";
                if (val.length() > 512) {
                    val = val.substring(0, 512);
                }


                csvW.write(val.replace("\n", "\\\\n").replace("\r", "\\\\r").replace("\t", "\\\\t"));
            }
            csvW.endRecord();
        }
        csvW.close();

    }

    // yoinked from stack overflow what are the odds. Will need modifying thouh for the arrays. Need to think . . .
    private void addKeys(String currentPath, JsonNode jsonNode, Map<String, String> map) {
        if (jsonNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) jsonNode;
            Iterator<Map.Entry<String, JsonNode>> iter = objectNode.fields();
            String pathPrefix = currentPath.isEmpty() ? "" : currentPath + ".";

            while (iter.hasNext()) {
                Map.Entry<String, JsonNode> entry = iter.next();
                addKeys(pathPrefix + entry.getKey(), entry.getValue(), map);
            }
        } else if (jsonNode.isArray()) {
            ArrayNode arrayNode = (ArrayNode) jsonNode;
            for (int i = 0; i < arrayNode.size(); i++) {
                if (i == 0) {
                    addKeys(currentPath, arrayNode.get(i), map);
                } else {
                    addKeys(currentPath + "[" + i + "]", arrayNode.get(i), map);
                }
            }
        } else if (jsonNode.isValueNode()) {
            ValueNode valueNode = (ValueNode) jsonNode;
            map.put(currentPath, valueNode.asText());
        }
    }


    public static class OauthResponse implements Serializable {
        public String token_type;
        public int expires_in;
        public String access_token;
        public String refresh_token;
    }

    public static class OAuthLogin {
        public final String client_id;
        public final String grant_type;
        public final String scopes;
        public final String username;
        public final String password;

        public OAuthLogin(String client_id, String grant_type, String scopes, String username, String password) {
            this.client_id = client_id;
            this.grant_type = grant_type;
            this.scopes = scopes;
            this.username = username;
            this.password = password;
        }
    }

}