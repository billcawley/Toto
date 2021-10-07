package com.extractdotdigital;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.core.MultivaluedMap;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ExtractDotDigital {

    public static void extractDotDigital() throws IOException {

        List<String> localconfig = Files.readAllLines(Paths.get("/home/edward/Downloads/dotdigital.txt"), Charset.defaultCharset());

        String destinationPath = "/home/edward/Downloads/dotdigital/";
        String baseUrl = localconfig.get(0);
        String userEmail = localconfig.get(1);
        String password = localconfig.get(2);

        String since = "2020-01-01";

        String campaignActivityUrl = "/v2/campaigns/with-activity-since/" + since;

        String campaignOpensUrl = "/v2/campaigns/{0}/opens/since-date/" + since;
        String campaignClicksUrl = "/v2/campaigns/{0}/clicks/since-date/" + since;
        String campaignPageViewsUrl = "/v2/campaigns/{0}/page-views/since-date/" + since;

        String campaignSummaryUrl = "/v2/campaigns/{0}/summary";

        String contactsUrl = "/v2/contacts/created-since/" + since;

        String contactPreferencesUrl = "/v2/contacts/{0}/preferences";

        String programsUrl = "/v2/programs";

        String programEnrolmentUrl = "/v2/programs/enrolments/{0}";

        ClientConfig config = new DefaultClientConfig();

        Client client = Client.create(config);
//	client.addFilter(new LoggingFilter(System.out));
        WebResource resource = client.resource(baseUrl);

        resource.addFilter(new HTTPBasicAuthFilter(userEmail, password));

        MultivaluedMap<String, String> multivaluedMap = new MultivaluedMapImpl();
        multivaluedMap.putSingle("WithFullData", "true");
        List<String> results = queryPaged(resource, contactsUrl, multivaluedMap, false);
        for (String result : results){
            FileUtils.writeStringToFile(new File(destinationPath + "contacts" + System.currentTimeMillis() + ".json"), result);
            JSONArray contacts = new JSONArray(result);
            for (Object object : contacts) {
                JSONObject jsonObject = (JSONObject) object;
                String preferences = queryWithTries(resource.path(contactPreferencesUrl.replace("{0}", jsonObject.toMap().get("id").toString())));
                FileUtils.writeStringToFile(new File(destinationPath + "preferences" + System.currentTimeMillis() + ".json"), preferences);
            }
        }

        results = queryPaged(resource, programsUrl);
        for (String result : results) {
            FileUtils.writeStringToFile(new File(destinationPath + "programs" + System.currentTimeMillis() + ".json"), result);
/*            JSONArray programs = new JSONArray(result);
            for (Object object : programs) {
                JSONObject jsonObject = (JSONObject) object;
                String preferences = resource.path(programEnrolmentUrl.replace("{0}", jsonObject.toMap().get("id").toString())).get(String.class);
                FileUtils.writeStringToFile(new File(destinationPath + "programEnrolment" + System.currentTimeMillis() + ".json"), preferences);
            }*/
        }


        results = queryPaged(resource, campaignActivityUrl);
        for (String result : results) {
            JSONArray jsonArray = new JSONArray(result);
            while (jsonArray.length() > 0) {
                for (Object object : jsonArray) {
                    JSONObject jsonObject = (JSONObject) object;
                    String campaignId = jsonObject.toMap().get("id") + "";
                    System.out.println("campaign ID " + jsonObject.toMap().get("id"));

                    List<String> result2 = queryPaged(resource, campaignOpensUrl.replace("{0}", jsonObject.toMap().get("id").toString()));
                    List<String> result3 = queryPaged(resource,campaignClicksUrl.replace("{0}", jsonObject.toMap().get("id").toString()));
                    List<String> result4 = queryPaged(resource,campaignPageViewsUrl.replace("{0}", jsonObject.toMap().get("id").toString()));
                    for (String r : result2){
                        FileUtils.writeStringToFile(new File(destinationPath + "campaignopens(campaignid=" + campaignId + ")" + System.currentTimeMillis() + ".json"), r);
                    }
                    for (String r : result3){
                        FileUtils.writeStringToFile(new File(destinationPath + "campaignclicks(campaignid=" + campaignId + ")" + System.currentTimeMillis() + ".json"), r);
                    }
                    for (String r : result4){
                        FileUtils.writeStringToFile(new File(destinationPath + "campaignpageviews(campaignid=" + campaignId + ")" + System.currentTimeMillis() + ".json"), r);
                    }

                    String campaignSummary = queryWithTries(resource.path(campaignSummaryUrl.replace("{0}", jsonObject.toMap().get("id").toString())));
                    FileUtils.writeStringToFile(new File(destinationPath + "campaignsummary(campaignid=" + campaignId + ")" + System.currentTimeMillis() + ".json"), campaignSummary);

                }
            }
        }

    }
    static List<String> queryPaged(WebResource resource, String url) {
        return queryPaged(resource,url,null,false);

    }

    static String queryWithTries(WebResource resource){
        int tries = 0;
        while (tries < 5){
            try {
                return resource.get(String.class);
            } catch (Exception ignored){
                System.out.println("tries : " + tries);
            }
            tries++;
        }
        return null;
    }

    static List<String> queryPaged(WebResource resource, String url, MultivaluedMap<String, String> multivaluedMap, boolean one) {
        List<String> toReturn = new ArrayList<>();
        int select = 1000;
        int skip = 0;
        if (multivaluedMap == null){
            multivaluedMap = new MultivaluedMapImpl();
        }
        multivaluedMap.putSingle("select", select + "");
        multivaluedMap.putSingle("skip", skip + "");
        String result = queryWithTries(resource.path(url).queryParams(multivaluedMap));

        JSONArray jsonArray = new JSONArray(result);
        while (jsonArray.length() > 0) {
            System.out.println("paging " + skip);
            toReturn.add(result);
            if (one){
                break;
            }
            skip += select;
            multivaluedMap.putSingle("select", select + "");
            multivaluedMap.putSingle("skip", skip + "");
            result =  queryWithTries(resource.path(url).queryParams(multivaluedMap));
            jsonArray = new JSONArray(result);
        }
        return toReturn;
    }
}
