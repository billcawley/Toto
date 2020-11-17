package com.azquo.admin.controller;

import com.azquo.dataimport.*;
import com.azquo.spreadsheet.transport.json.ExcelJsonRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rcaller.rstuff.RCaller;
import com.github.rcaller.rstuff.RCode;
import com.microsoft.aad.adal4jsample.HttpClientHelper;
import com.microsoft.aad.adal4jsample.JSONHelper;
import org.json.JSONArray;
import org.json.JSONObject;
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
order source
cancelled
customer

 */
//                url = new URL("http://jbi.shopwaretest.de/api/v3/order");
//                url = new URL("http://jbi.shopwaretest.de/api/v3/order/dba074b5514b4b27821c203d5ff2c3d3/lineItems");
//                url = new URL("http://jbi.shopwaretest.de/api/v3/order/dba074b5514b4b27821c203d5ff2c3d3/addresses");
                url = new URL("http://jbi.shopwaretest.de/api/v3/order");

                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", oauthResponse.access_token);
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                stringBuffer = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    stringBuffer.append(line);
                }

                return stringBuffer.toString();




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