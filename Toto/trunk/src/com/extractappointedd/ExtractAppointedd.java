package com.extractappointedd;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ExtractAppointedd {

    public static void extract() throws IOException {

/*        List<String> localconfig = Files.readAllLines(Paths.get("/home/edward/Downloads/dotdigital.txt"), Charset.defaultCharset());
        String baseUrl = localconfig.get(0);
        String userEmail = localconfig.get(1);
        String password = localconfig.get(2);*/


        ClientConfig config = new DefaultClientConfig();

        Client client = Client.create(config);
//	client.addFilter(new LoggingFilter(System.out));
        WebResource resource = client.resource("https://api.appointedd.com");

        //resource.addFilter(new HTTPBasicAuthFilter("", password));

        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("limit", "100");

        String result = resource.path("/v1/bookings").queryParams(params)
                .header("X-API-KEY", "TmpFeU5tSTBaRFJqWkRJeVlXRXdNelV3Tnpsa09HVXo6ZUZjelVHRldZMHRUTVhkaWFVZEpaMm81ZVVKMGNHVlA=")
                .get(String.class);

        FileUtils.writeStringToFile(new File("/home/edward/Downloads/" + System.currentTimeMillis() + "(preprocessor=Charter House Bookings Preprocessor)bookings.json"), result);

        result = resource.path("/v1/resources").queryParams(params)
                .header("X-API-KEY", "TmpFeU5tSTBaRFJqWkRJeVlXRXdNelV3Tnpsa09HVXo6ZUZjelVHRldZMHRUTVhkaWFVZEpaMm81ZVVKMGNHVlA=")
                .get(String.class);

        FileUtils.writeStringToFile(new File("/home/edward/Downloads/" + System.currentTimeMillis() + "(preprocessor=Charter House Resources Preprocessor)resources.json"), result);

        result = resource.path("/v1/customers").queryParams(params)
                .header("X-API-KEY", "TmpFeU5tSTBaRFJqWkRJeVlXRXdNelV3Tnpsa09HVXo6ZUZjelVHRldZMHRUTVhkaWFVZEpaMm81ZVVKMGNHVlA=")
                .get(String.class);

        FileUtils.writeStringToFile(new File("/home/edward/Downloads/" + System.currentTimeMillis() + "(preprocessor=Charter House Customer Preprocessor)customers.json"), result);

        System.out.println("extract appointedd result : " + result);


    }
}
