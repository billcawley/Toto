package com.azquo.util;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.HtmlEmail;


/**
 * Created by cawley on 16/01/14.
 * Quick google showed the apache libraries to be a pretty easy way to do things. Email so unimportant compared to Feefo.
 * I've not made the functions static, I don't know if there will ever be multiple implementations but meh, why not
 */
public class AzquoMailer {

    public boolean sendEMail(String toEmail, String toName, String subject, String body) {
        try {
            HtmlEmail email = new HtmlEmail();
            //email.setDebug(true); // useful stuff if things go wrong
            email.setSSLOnConnect(true);
            email.setAuthenticator(new DefaultAuthenticator("bill@azquo.com", "afo822"));
            email.setHostName("logichound.servers.eqx.misp.co.uk");
            email.setSmtpPort(465);
            email.addTo(toEmail, toName);
            email.setFrom("info@azquo.com", "Azquo Support");
            email.setSubject(subject);
            // embed the image and get the content id
            //  URL url = new URL("http://www.azquo.com/wp-content/uploads/2013/12/logo42.png");
            //  String cid = email.embed(url, "Azquo logo");
            email.setHtmlMsg(body);
            // set the plain text message - so simple compared to the arse before!
            email.setTextMsg("Your email client does not support HTML messages");
            email.send();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}
