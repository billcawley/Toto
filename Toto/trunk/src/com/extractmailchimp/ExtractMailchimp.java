package com.extractmailchimp;

import com.ecwid.maleorang.MailchimpClient;
import com.ecwid.maleorang.MailchimpException;
import com.ecwid.maleorang.MailchimpMethod;
import com.ecwid.maleorang.MailchimpObject;
import com.ecwid.maleorang.annotation.*;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.util.ArrayList;
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
    private static class ListGet extends MailchimpMethod<ListResponse> {
        @PathParam
        public final String list_id;

        private ListGet(String list_id) {
            this.list_id = list_id;
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
    private static class OpenDetailsGet extends MailchimpMethod<ListResponse> {
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

    public static void extractData(String apiKey) throws IOException, MailchimpException {
        try (MailchimpClient client = new MailchimpClient(apiKey)) {
/*            ListResponse response = client.execute(new ListsGet());
            for (Map<String, Object> listItem : response.lists){
                System.out.print("List id : " + listItem.get("id"));
                ListResponse response1 = client.execute(new ListGet((String) listItem.get("id")));
                System.out.print("response 1 : " + response1);
            }*/
            CampaignsResponse response = client.execute(new CampaignsGet());
            for (Map<String, Object> campaign : response.campaigns){
                System.out.print("Campaigns id : " + campaign.get("id"));
                //ListResponse response1 = client.execute(new CampaignGet((String) campaign.get("id")));
//                ListResponse response1 = client.execute(new OpenDetailsGet((String) campaign.get("id")));
//                ListResponse response1 = client.execute(new ClickDetailsGet((String) campaign.get("id")));
                ListResponse response1 = client.execute(new UnsubscribedGet((String) campaign.get("id")));
                System.out.print("response 1 : " + response1);
            }
           //System.err.println("response" + response.lists);

//            SubscribeRequest request = new SubscribeRequest("ed.lennox@azquo.com");
            //request.status = "subscribed";
/*            request.merge_fields = new MergeFields();
            request.merge_fields.FNAME = "Vasya";
            request.merge_fields.LNAME = "Pupkin";*/

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
