package com.extractmailchimp;

import com.ecwid.maleorang.MailchimpClient;
import com.ecwid.maleorang.MailchimpException;
import com.ecwid.maleorang.MailchimpMethod;
import com.ecwid.maleorang.MailchimpObject;
import com.ecwid.maleorang.annotation.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.exolab.castor.types.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class ExtractMailchimp {

    private static class MergeFields extends MailchimpObject {
        @Field
        public String FNAME, LNAME;
    }

        @Method(httpMethod = HttpMethod.PUT, version = APIVersion.v3_0, path = "/lists/{list_id}/members/{subscriber_hash}")
    private static class TestRequest extends MailchimpMethod<SubscribeResponse> {
        /**
         * This param will be included into the endpoint path.
         */
         @PathParam
         public final String list_id;

         /**
          * This param will be included into the endpoint path.
          */
         @PathParam
         public final String subscriber_hash;

         /**
          * This param will be included into the query string.
          * It is not a part of the method API and is added here just for demonstration.
         */
        @QueryStringParam
        public final String dummy = "dummy";

        /**
         * This param will be included into the request body.
         * */
         @Field
         public final String email_address;

         /**
          * This param will be included into the request body.
         @Field
         public String status;
          */

         /**
          * This param will be included into the request body.
          */

         @Field
         public MergeFields merge_fields;

        public TestRequest(String listId, String email) {
            this.list_id = listId;
            this.subscriber_hash = DigestUtils.md5Hex(email.toLowerCase());
            this.email_address = email;
        }
    }

    @Method(httpMethod = HttpMethod.GET, version = APIVersion.v3_0, path = "/lists")
    private static class ListsGet extends MailchimpMethod<ListResponse> {
    }

    @Method(httpMethod = HttpMethod.GET, version = APIVersion.v3_0, path = "/lists/{list_id}/members")
    private static class MembersGet extends MailchimpMethod<MembersResponse> {

        /**
         * This param will be included into the query string.
         * It is not a part of the method API and is added here just for demonstration.
         */
        @QueryStringParam
        public final int count;

        @QueryStringParam
        public final int offset;

        @PathParam
        public final String list_id;

        private MembersGet(String list_id, int count, int offset) {
            this.count = count;
            this.offset = offset;
            this.list_id = list_id;
        }
    }

    @Method(httpMethod = HttpMethod.GET, version = APIVersion.v3_0, path = "/lists/{list_id}/activity")
    private static class ActivityGet extends MailchimpMethod<ListResponse> {
        @PathParam
        public final String list_id;

        private ActivityGet(String list_id) {
            this.list_id = list_id;
        }
    }

    @Method(httpMethod = HttpMethod.GET, version = APIVersion.v3_0, path = "/lists/{list_id}/members/{subscriber_hash}/activity-feed")
    private static class MemberActivityGet extends MailchimpMethod<ListResponse> {
        @PathParam
        public final String list_id;

        @PathParam
        public final String subscriber_hash;

        private MemberActivityGet(String list_id, String subscriber_hash) {
            this.list_id = list_id;
            this.subscriber_hash = subscriber_hash;
        }
    }



    @Method(httpMethod = HttpMethod.GET, version = APIVersion.v3_0, path = "/campaigns/{campaign_id}")
    private static class CampaignGet extends MailchimpMethod<ListResponse> {
        @PathParam
        public final String campaign_id;

        public CampaignGet(String campaign_id) {
            this.campaign_id = campaign_id;
        }
    }

    @Method(httpMethod = HttpMethod.GET, version = APIVersion.v3_0, path = "/reports/{campaign_id}/open-details")
    private static class OpenDetailsGet extends MailchimpMethod<MembersResponse> {
        @PathParam
        public final String campaign_id;

        public OpenDetailsGet(String campaign_id) {
            this.campaign_id = campaign_id;
        }
    }

    @Method(httpMethod = HttpMethod.GET, version = APIVersion.v3_0, path = "/reports/{campaign_id}/click-details")
    private static class ClickDetailsGet extends MailchimpMethod<ListResponse> {
        @PathParam
        public final String campaign_id;

        public ClickDetailsGet(String campaign_id) {
            this.campaign_id = campaign_id;
        }
    }

    @Method(httpMethod = HttpMethod.GET, version = APIVersion.v3_0, path = "/reports/{campaign_id}/unsubscribed")
    private static class UnsubscribedGet extends MailchimpMethod<ListResponse> {
        @PathParam
        public final String campaign_id;

        public UnsubscribedGet(String campaign_id) {
            this.campaign_id = campaign_id;
        }
    }

    @Method(httpMethod = HttpMethod.GET, version = APIVersion.v3_0, path = "/campaigns")
    private static class CampaignsGet extends MailchimpMethod<CampaignsResponse> {
    }

    @Method(httpMethod = HttpMethod.POST, version = APIVersion.v3_0, path = "/batches")
    private static class BatchSend extends MailchimpMethod<MailchimpObject> {
        @Field
        public final List operations;

        private BatchSend(List operations) {
            this.operations = operations;
        }
    }

    @Method(httpMethod = HttpMethod.GET, version = APIVersion.v3_0, path = "/batches")
    private static class BatchCheck extends MailchimpMethod<MailchimpObject> {
    }

    @Method(httpMethod = HttpMethod.GET, version = APIVersion.v3_0, path = "/batches/{batch_id}")
    private static class BatchStatusCheck extends MailchimpMethod<MailchimpObject> {
        @PathParam
        public final String batch_id;

        public BatchStatusCheck(String batch_id) {
            this.batch_id = batch_id;
        }
    }

    public static void extractData(String apiKey) throws IOException, MailchimpException, InterruptedException {
        try (MailchimpClient client = new MailchimpClient(apiKey)) {
            long start = System.currentTimeMillis();
//            String tempPath = "c:\\users\\test\\Downloads\\";
            String tempPath = "/home/edward/Downloads/";
/*            ListResponse response = client.execute(new ListsGet());
            for (Map<String, Object> listItem : response.lists){
                System.out.print("List id : " + listItem.get("id"));
                ListResponse response1 = client.execute(new ListGet((String) listItem.get("id")));
                System.out.print("response 1 : " + response1);
            }*/
            String outFile = tempPath + "activity";
            File writeFile = new File(outFile);
            writeFile.delete(); // to avoid confusion
            BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8));
            Map<String, String> subscriberIdNameMap = new HashMap<>();
            fileWriter.write("Campaign\tName\tTimestamp\tAction\tLink\n");


            CampaignsResponse response = client.execute(new CampaignsGet());
            Set<String> loadedLists = new HashSet<>();
            List<Map> operations = new ArrayList<>(); // attempting mailchimp batch processing
            for (Map<String, Object> campaign : response.campaigns){
                ListResponse response1 = client.execute(new CampaignGet((String) campaign.get("id")));
                String list_id = (String) ((MailchimpObject) response1.mapping.get("recipients")).mapping.get("list_id");
                int page = 250;
                int offset = 0;
                if (loadedLists.add(list_id)){
                    System.out.println("calling members, offset : " + offset);
                    try {
                        MembersResponse response2 = client.execute(new MembersGet(list_id, page, offset));
                        while (!response2.members.isEmpty()){
                            for (Map member:response2.members){
                                Map operation = new HashMap();
                                operation.put("method","GET");
                                operation.put("path", "/lists/" + list_id + "/members/" + member.get("id") + "/activity-feed");
                                operations.add(operation);
//                            String subscriberid = (String) ;
                                String subscriberName = (String) member.get("email_address");
                                subscriberIdNameMap.put((String) member.get("id"), subscriberName);
                            }
                            offset += page;
                            System.out.println("calling members, offset : " + offset);
                            response2 = client.execute(new MembersGet(list_id, page, offset));
                        }

                    } catch (Exception e){
                        System.out.println("exception on list : " + list_id + " " + e.getMessage());
                    }
                }
            }
            /*


            // now run the batch operation

            System.out.println("total operations size : " + operations.size());
            MailchimpObject br = client.execute(new BatchSend(operations));
            System.out.println("batch response" + br.toString());


//            MailchimpObject batchCheck = client.execute(new BatchCheck());
            String zipUrl = null;
            int totalTries = 0;
            while (zipUrl == null){
                if (totalTries > 10){
                    break;
                }
                Thread.sleep(5000);

                MailchimpObject batchStatus = client.execute(new BatchStatusCheck("844f2a47d5"));
                //MailchimpObject batchStatus = client.execute(new BatchStatusCheck(br.mapping.get("id") + ""));
                System.out.println("batch status : " + batchStatus.mapping.get("status"));
                if ("finished".equals(batchStatus.mapping.get("status"))){
                    zipUrl = (String) batchStatus.mapping.get("response_body_url");
                }
                totalTries++;
            }

            if (zipUrl != null){
                // efc note : for the zipped response maybe use a different pattern?
                ClientConfig config = new DefaultClientConfig();
                Client clientForBinary = Client.create(config);
                WebResource resource = clientForBinary.resource(zipUrl);
                ClientResponse zipResponse = resource.get(ClientResponse.class);
                InputStream in = zipResponse.getEntityInputStream();
                File targetFile = new File("/home/edward/Downloads/" + System.currentTimeMillis() + "mailchimptest.tar.gz"); // read the filename or extension from the url?
                FileUtils.copyInputStreamToFile(in, targetFile);
                in.close();
                // todo - delete the zip from MC? Do we care?
                // todo - parse the json!
            }

            System.out.println("total seconds execute : " + ((System.currentTimeMillis() - start)/ 1000));
*/
            try (Stream<Path> list = Files.list(Paths.get("/home/edward/Downloads/microscooter"))) {
                list.forEach(path -> {
                    if (path.toString().endsWith(".json")){
                        try {
                            JSONArray jsonArray = new JSONArray(FileUtils.readFileToString(path.toFile()));
                            for (Object o : jsonArray){
                                JSONObject jsonObject = (JSONObject)o;
                                String response1 = (String) jsonObject.get("response");
                                JSONObject activity = new JSONObject(response1);
                                JSONArray activities = activity.getJSONArray("activity");
                                String  emailId = activity.getString("email_id");
                                for (Object object2 : activities){
                                    JSONObject activityRecord = (JSONObject) object2;
                                    fileWriter.write((activityRecord.has("campaign_title") ? activityRecord.get("campaign_title") : "") + "\t" + subscriberIdNameMap.get(emailId) + "\t" + (String) activityRecord.get("created_at_timestamp") + "\t" + (String) activityRecord.get("activity_type") + "\t" + (activityRecord.has("link_clicked") ? activityRecord.get("link_clicked") : "") + "\n");

                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

/*                            ListResponse activityresponse = client.execute(new MemberActivityGet(list_id, subscriberid));
                            List<MailchimpObject> activities = (List<MailchimpObject>)activityresponse.mapping.get("activity");
                            for (MailchimpObject activity:activities){
                                fileWriter.write((String) activity.mapping.get("campaign_title") + "\t" + subscriberName + "\t" + (String) activity.mapping.get("created_at_timestamp") + "\t" + (String) activity.mapping.get("activity_type") + "\t" + (String) activity.mapping.get("link_clicked") + "\n");
                            }*/
            fileWriter.flush();
            fileWriter.close();

        }
    }

    private static class ListResponse extends MailchimpObject {
        @Field
        public ArrayList<Map<String, Object>> lists;
    }
    private static class CampaignsResponse extends MailchimpObject {
        @Field
        public ArrayList<Map<String, Object>> campaigns;
    }
    private static class MembersResponse extends MailchimpObject {
        @Field
        public ArrayList<Map<String, Object>> members;
    }
    private static class SubscribeResponse extends MailchimpObject {
        @Field
        public String id;

        @Field
        public String email_address;

        @Field
        public String status;

        @Field
        public MergeFields merge_fields;
    }
    private static class BatchResponse extends MailchimpObject {
        @Field
        public String id, status;

        @Field
        public Integer total_operations, finished_operations, errored_operations;

        @Field
        public DateTime submitted_at, completed_at;

        @Field
        public String response_body_url;
    }
}
