package com.azquo.util;

import com.sun.mail.imap.IMAPFolder;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.HtmlEmail;

import javax.mail.*;
import javax.mail.internet.MimeBodyPart;
import java.io.File;
import java.io.IOException;
import java.util.Properties;


/**
 * Created by cawley on 16/01/14.
 * Quick google showed the apache libraries to be a pretty easy way to do things. Email so unimportant compared to Feefo.
 * I've not made the functions static, I don't know if there will ever be multiple implementations but meh, why not
 */
public class AzquoMailer {

    public boolean sendEMail(String toEmail, String toName, String subject, String body) {
        return sendEMail(toEmail, toName, subject, body, null);
    }

    public boolean sendEMail(String toEmail, String toName, String subject, String body, File attachment) {
        try {
            HtmlEmail email = new HtmlEmail();
            //email.setDebug(true); // useful stuff if things go wrong
            email.setSSLOnConnect(true);
            email.setAuthenticator(new DefaultAuthenticator("app@azquo.com", "Yd44d8R4"));
            email.setHostName("logichound.servers.eqx.misp.co.uk");
            email.setSmtpPort(465);
            if (toEmail.contains(",")) {// ignore name
                for (String to : toEmail.split(",")) {
                    email.addTo(to, to);
                }
            } else if (toEmail.contains(";")) {
                for (String to : toEmail.split(";")) {
                    email.addTo(to, to);
                }
            } else {
                email.addTo(toEmail, toName != null ? toName : toEmail);
            }
            email.setFrom("info@azquo.com", "Azquo Support");
            email.setSubject(subject);
            // embed the image and get the content id
            //  URL url = new URL("http://www.azquo.com/wp-content/uploads/2013/12/logo42.png");
            //  String cid = email.embed(url, "Azquo logo");
            email.setHtmlMsg(body);
            // set the plain text message - so simple compared to the arse before!
            email.setTextMsg("Your email client does not support HTML messages");
            if (attachment != null) {
                email.attach(attachment);
            }
            email.send();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // yoinked from tinternet and modified

    public void readGoogleAnalyticsEmail() {
        Store store = null;
        IMAPFolder folder = null;
        try {
            Properties props = System.getProperties();
            props.setProperty("mail.store.protocol", "imap");

            Session session = Session.getDefaultInstance(props, null);

            store = session.getStore("imap");
//            store.connect("logichound.servers.eqx.misp.co.uk", "edd@azquo.com", "qPF34d95zzZq");

            store.connect("192.168.1.10", "nic2", "salasana");
            folder = (IMAPFolder) store.getFolder("inbox");


            if (!folder.isOpen())
                folder.open(Folder.READ_WRITE);
//            Message[] messages = folder.getMessages(folder.getMessageCount() - 20, folder.getMessageCount());
            Message[] messages = folder.getMessages();
            System.out.println("No of Messages : " + folder.getMessageCount());
            System.out.println("No of Unread Messages : " + folder.getUnreadMessageCount());
            System.out.println(messages.length);
            for (int i = 0; i < messages.length; i++) {

                Message msg = messages[i];
                //System.out.println(msg.getMessageNumber());
                //Object String;
                //System.out.println(folder.getUID(msg)
                String subject = msg.getSubject();
                System.out.println("MESSAGE " + (i + 1) + ":");
                System.out.println("Subject: " + subject);
                System.out.println("From: " + msg.getFrom()[0]);
                System.out.println("To: " + msg.getAllRecipients());
                System.out.println("Date: " + msg.getReceivedDate());
                System.out.println("Size: " + msg.getSize());
                System.out.println(msg.getFlags());
                System.out.println("Body: \n" + msg.getContent());

                if (subject != null && subject.startsWith("Google Analytics")) {
                    String contentType = msg.getContentType();
/*                    System.out.println("MESSAGE " + (i + 1) + ":");
                    System.out.println("Subject: " + subject);
                    System.out.println("From: " + msg.getFrom()[0]);
                    System.out.println("To: "+msg.getAllRecipients()[0]);
                    System.out.println("Date: "+msg.getReceivedDate());
                    System.out.println("Size: "+msg.getSize());
                    System.out.println(msg.getFlags());
                    System.out.println("Body: \n"+ msg.getContent());
                    System.out.println(contentType);*/
                    if (contentType.contains("multipart")) {
                        Multipart multiPart = (Multipart) msg.getContent();
                        for (int partNo = 0; partNo < multiPart.getCount(); partNo++) {
                            MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(partNo);
                            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                                part.saveFile("/home/edward/Downloads/" + part.getFileName());
                            }
                        }
                    }

                }
            }
        } catch (MessagingException | IOException e) {
            e.printStackTrace();
        } finally {
            if (folder != null && folder.isOpen()) {
                try {
                    folder.close(true);
                } catch (MessagingException e) {
                    e.printStackTrace();
                }
            }
            if (store != null) {
                try {
                    store.close();
                } catch (MessagingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
