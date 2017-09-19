package com.azquo.util;

import com.sun.mail.imap.IMAPFolder;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.HtmlEmail;

import javax.mail.*;
import javax.mail.internet.MimeBodyPart;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 16/01/14.
 * Quick google showed the apache libraries to be a pretty easy way to do things. Email so unimportant compared to Feefo.
 * I've not made the functions static, I don't know if there will ever be multiple implementations but meh, why not
 * <p>
 * Configuration like SpreadsheetService, not ideal but I want mail server details out of here
 */
public class AzquoMailer {

    private static final Properties azquoProperties = new Properties();

    static {
        System.out.println("attempting properties load from classpath");
        try {
            azquoProperties.load(AzquoMailer.class.getClassLoader().getResourceAsStream("azquo.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final String MAILPREFIX = "mail.";
    private static final String MAILSERVER = "mail.server";
    private static final String MAILUSER = "mail.user";
    private static final String MAILPASSWORD = "mail.password";
    private static final String MAILFROMADDRESS = "mail.fromaddress";
    private static final String MAILFROMNAME = "mail.fromname";

    // for reading in data, could well be a different server
    private static final String MAILBOXIP = "mailbox.ip";
    private static final String MAILBOXUSER = "mailbox.user";
    private static final String MAILBOXPASSWORD = "mailbox.password";

    public static boolean sendEMail(String toEmail, String toName, String subject, String body) {
        return sendEMail(toEmail, toName, subject, body, null, null);
    }

    public static boolean sendEMail(String toEmail, String toName, String subject, String body, List<File> attachments, List<EmailAttachment> emailAttachments) {
        try {
            HtmlEmail email = new HtmlEmail();
            //email.setDebug(true); // useful stuff if things go wrong
            // ok there was a bunch of stuff hardcoded, have moved it to azquo.properties
            // nasty hack as env won't list property keys. This should only be set up once.
            String user = "";
            String password = "";
            String fromaddress = "";
            String fromname = "";
            int port = 587; // could set from properties?
            for (String key : azquoProperties.stringPropertyNames()) {
                if (key.startsWith(MAILPREFIX)) {
                    switch (key) {
                        case MAILSERVER:
                            email.setHostName(azquoProperties.getProperty(key));
                            break;
                        case MAILUSER:
                            user = azquoProperties.getProperty(key);
                            break;
                        case MAILPASSWORD:
                            password = azquoProperties.getProperty(key);
                            break;
                        case MAILFROMADDRESS:
                            fromaddress = azquoProperties.getProperty(key);
                            break;
                        case MAILFROMNAME:
                            fromname = azquoProperties.getProperty(key);
                            break;
                        default:
                            System.setProperty(key, azquoProperties.getProperty(key)); // push extras through
                    }
                }
            }
            email.setAuthenticator(new DefaultAuthenticator(user, password));
            email.setStartTLSEnabled(true);
            email.setSmtpPort(port);
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
            email.setFrom(fromaddress, fromname);
            email.setSubject(subject);
            //  embed the image and get the content id
            //  URL url = new URL("http://www.azquo.com/wp-content/uploads/2013/12/logo42.png");
            //  String cid = email.embed(url, "Azquo logo");
            email.setHtmlMsg(body);
            // set the plain text message - so simple compared to the arse before!
            email.setTextMsg("Your email client does not support HTML messages");
            if (attachments != null) {
                for (File attachment : attachments) {
                    email.attach(attachment);
                }
            }
            if (emailAttachments != null) {
                for (EmailAttachment emailAttachment : emailAttachments) {
                    email.attach(emailAttachment);
                }
            }
            email.send();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // yoinked from tinternet and modified
    public static void readGoogleAnalyticsEmail() {
        Store store = null;
        IMAPFolder folder = null;
        try {
            Properties props = System.getProperties();
            props.setProperty("mail.store.protocol", "imap");
            Session session = Session.getDefaultInstance(props, null);
            store = session.getStore("imap");
            String mailboxIp = azquoProperties.getProperty(MAILBOXIP);
            String mailboxUser = azquoProperties.getProperty(MAILBOXUSER);
            String mailboxPassword = azquoProperties.getProperty(MAILBOXPASSWORD);

            store.connect(mailboxIp, mailboxUser, mailboxPassword);
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
                System.out.println("To: " + Arrays.toString(msg.getAllRecipients()));
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
