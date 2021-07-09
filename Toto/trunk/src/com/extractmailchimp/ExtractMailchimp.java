package com.extractmailchimp;

import com.ecwid.maleorang.MailchimpClient;
import com.ecwid.maleorang.MailchimpException;
import com.ecwid.maleorang.MailchimpMethod;
import com.ecwid.maleorang.MailchimpObject;
import com.ecwid.maleorang.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private static class MembersGet extends MailchimpMethod<ListResponse> {
        @PathParam
        public final String list_id;

        private MembersGet(String list_id) {
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

    public static void extractData(String apiKey, String listName) throws IOException, MailchimpException {
        try (MailchimpClient client = new MailchimpClient(apiKey)) {
            String tempPath = "c:\\users\\test\\Downloads\\";
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
            fileWriter.write("Campaign\tName\tTimestamp\tAction\tLink\n");
            //CampaignsResponse response = client.execute(new CampaignsGet());
            ListResponse resp = client.execute(new ListsGet());
            for (Map<String, Object> list : resp.lists){
                if (list.get("name").equals(listName)){
                    ListResponse response2 = client.execute(new MembersGet((String)list.get("id")));

                    List<MailchimpObject> members = (List<MailchimpObject>)response2.mapping.get("members");
                    for (MailchimpObject member:members){
                        String subscriberid = (String) member.mapping.get("id");
                        String subscriberName = (String) member.mapping.get("email_address");
                        ListResponse activityresponse = client.execute(new MemberActivityGet((String) list.get("id"), subscriberid));
                        List<MailchimpObject> activities = (List<MailchimpObject>)activityresponse.mapping.get("activity");
                        for (MailchimpObject activity:activities){
                            fileWriter.write((String) activity.mapping.get("campaign_title") + "\t" + subscriberName + "\t" + (String) activity.mapping.get("created_at_timestamp") + "\t" + (String) activity.mapping.get("activity_type") + "\t" + (String) activity.mapping.get("link_clicked") + "\n");
                        }
                    }
                }
            }
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
}
