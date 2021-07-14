package com.extractdotdigital;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ExtractDotDigital {

    public static void extractDotDigital() throws IOException {

        List<String> localconfig = Files.readAllLines(Paths.get("/home/edward/Downloads/dotdigital.txt"), Charset.defaultCharset());
        String baseUrl = localconfig.get(0);
        String userEmail = localconfig.get(1);
        String password = localconfig.get(2);


        String campaignActivityUrl = "/v2/campaigns/with-activity-since/2018-01-01";

        String campaignActivitiesUrl = "/v2/campaigns/{0}/activities/since-date/2018-01-01";

        String campaignOpensUrl = "/v2/campaigns/{0}/opens/since-date/2018-01-01";
        String campaignClicksUrl = "/v2/campaigns/{0}/clicks/since-date/2018-01-01";
        String campaignPageViewsUrl = "/v2/campaigns/{0}/page-views/since-date/2018-01-01";

        ClientConfig config = new DefaultClientConfig();

        Client client = Client.create(config);
//	client.addFilter(new LoggingFilter(System.out));
        WebResource resource = client.resource(baseUrl);

        resource.addFilter(new HTTPBasicAuthFilter(userEmail, password));

        String result = resource.path(campaignActivityUrl).get(String.class);

        JSONArray jsonArray = new JSONArray(result);

        for (Object object : jsonArray) {
            JSONObject jsonObject = (JSONObject) object;
            System.out.println("campaign ID " + jsonObject.toMap().get("id"));
//            String result2 = resource.path(campaignActivitiesUrl.replace("{0}", jsonObject.toMap().get("id").toString())).get(String.class);

            String result2 = resource.path(campaignOpensUrl.replace("{0}", jsonObject.toMap().get("id").toString())).get(String.class);
            String result3 = resource.path(campaignClicksUrl.replace("{0}", jsonObject.toMap().get("id").toString())).get(String.class);
            String result4 = resource.path(campaignPageViewsUrl.replace("{0}", jsonObject.toMap().get("id").toString())).get(String.class);

            FileUtils.writeStringToFile(new File("/home/edward/Downloads/" + System.currentTimeMillis() + "campaignopens.json"), result2);
            FileUtils.writeStringToFile(new File("/home/edward/Downloads/" + System.currentTimeMillis() + "campaignclicks.json"), result3);
            FileUtils.writeStringToFile(new File("/home/edward/Downloads/" + System.currentTimeMillis() + "campaignpageviews.json"), result4);

            JSONArray jsonArray2 = new JSONArray(result2);
            for (Object object2 : jsonArray2) {
                JSONObject jsonObject2 = (JSONObject) object2;
                System.out.println("campaign activity " + jsonObject2);


            }
        }

//        System.out.println("dotmailer result : " + result);


/*        var campaignsWithActivity = httpClient.GetStringAsync(campaignActivityUrl).Result;

        var campaignsWithActivityArray = JsonConvert.DeserializeObject<CampaignActivity.Campaign[]>(campaignsWithActivity);

        //Loop through all campaigns that have been active since the date specified in the URL endpoint
        for (int i = 0; i < campaignsWithActivityArray.Length; i++)
        {
            var ID = campaignsWithActivityArray[i].ID;

            //Pass the ID of each returned campaign to the next endpoint to get the contact activity of each campaign
            var contactActivities = httpClient.GetStringAsync(string.Format(campaignActivitiesUrl, ID)).Result;
            Console.WriteLine(contactActivities);

            //Do something with the data that is returned
            //contactActivities contains an array of contact activity objects (https://developer.dotmailer.com/v2/docs/get-campaign-activity-since-date)
        }*/
    }
}
