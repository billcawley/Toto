package com.extractappointedd;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.ws.rs.core.MultivaluedMap;
import java.io.File;
import java.io.IOException;

public class ExtractAppointedd {

    /* todo

string <date-time>
Filters the returned bookings by the date and time they were last updated on, returning only those that were last updated on or after this date and time.
     */
    public static void extract(String baseUrl, String path, String restAPIKey, String destination) throws IOException {

/*        List<String> localconfig = Files.readAllLines(Paths.get("/home/edward/Downloads/dotdigital.txt"), Charset.defaultCharset());
        String baseUrl = localconfig.get(0);
        String userEmail = localconfig.get(1);
        String password = localconfig.get(2);*/


        ClientConfig config = new DefaultClientConfig();

        Client client = Client.create(config);
//	client.addFilter(new LoggingFilter(System.out));
        WebResource resource = client.resource(baseUrl);

        //resource.addFilter(new HTTPBasicAuthFilter("", password));
        StringBuilder sb = new StringBuilder();

        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("limit", "100");

        String result = resource.path(path).queryParams(params)
                .header("X-API-KEY", restAPIKey)
                .get(String.class);
        sb.append(result);
        JSONObject activity = new JSONObject(result);

        while (activity.get("next") != JSONObject.NULL){
            params.remove("start");
            params.add("start", activity.get("next").toString());
            result = resource.path(path).queryParams(params)
                    .header("X-API-KEY", restAPIKey)
                    .get(String.class);
            activity = new JSONObject(result);
            sb.append(result);
        }
        FileUtils.writeStringToFile(new File(destination + System.currentTimeMillis()), sb.toString());


    }
}
