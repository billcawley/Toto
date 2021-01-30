package com.azquo.admin.controller;

import com.azquo.dataimport.*;
import com.csvreader.CsvWriter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.github.rcaller.rstuff.RCaller;
import com.github.rcaller.rstuff.RCode;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Created by EFC on 17/11/20.
 */
@Controller
@RequestMapping("/Test")
public class TestController {

    @RequestMapping
    @ResponseBody
    public String handleRequest(HttpServletRequest request
            , @RequestParam(value = "something", required = false) String something
    ) {
        if ("xmlparse".equals(something)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder builder;
            try {
                builder = factory.newDocumentBuilder();
                Files.walkFileTree(Paths.get("/home/edward/Downloads/artest/"), new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path path, IOException e) throws IOException {
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
            return "xmlparse done";
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
            return result[0] + "";
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
                while (headingsIterator.hasNext()){
                    String heading = headingsIterator.next();
                    boolean hasData = false;
                    for (Map<String, String> map : ordersmaplist) {
                        String test = map.get(heading);
                        if (test != null && !test.isEmpty() && !test.equals("null")){
                            hasData = true;
                            break;
                        }
                    }
                    if (!hasData){
                        headingsIterator.remove();
                    }
                }

                headingsIterator = orderlinesheadings.iterator();
                while (headingsIterator.hasNext()){
                    String heading = headingsIterator.next();
                    boolean hasData = false;
                    for (Map<String, String> map : orderlinesmaplist) {
                        String test = map.get(heading);
                        if (test != null && !test.isEmpty() && !test.equals("null")){
                            hasData = true;
                            break;
                        }
                    }
                    if (!hasData){
                        headingsIterator.remove();
                    }
                }

                headingsIterator = productheadings.iterator();
                while (headingsIterator.hasNext()){
                    String heading = headingsIterator.next();
                    boolean hasData = false;
                    for (Map<String, String> map : productsmaplist) {
                        String test = map.get(heading);
                        if (test != null && !test.isEmpty() && !test.equals("null")){
                            hasData = true;
                            break;
                        }
                    }
                    if (!hasData){
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

                return "done";

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

    public static void writeCSV(String fileLocation, List<String> headings, List<Map<String, String>> data) throws IOException {
        CsvWriter csvW = new CsvWriter(fileLocation, '\t', StandardCharsets.UTF_8);
        csvW.setUseTextQualifier(false);
        for (String heading : headings) {
            csvW.write(heading);
        }
        csvW.endRecord();

        for (Map<String, String> map : data) {
            for (String heading : headings) {
                String val = map.get(heading) != null ? map.get(heading) : "";
                if (val.length() > 512){
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