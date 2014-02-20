package com.azquo.util;

import java.io.*;

/**
 * Created by cawley on 21/01/14.
 * Bit like ftpaccount tools in Feefo
 * OK, as it stands Bill found a way to upload from Excel, hence we may not need this code but leave it here for the moment
 */
public class CommandLineCalls {

    // to GPG I think we'd still need to command line but not yet
    // we may not actually need the FTP in the end either

    public boolean runCommand(String command, boolean systemCopy)
            throws Exception {
        return runCommand(command, systemCopy, null);
    }

    public boolean runCommand(String command, boolean systemCopy, String input)
            throws Exception {
        return runCommand(command, null, systemCopy, input);
    }


    public boolean runCommand(String command, String[] commandArray, boolean systemCopy, String input)
            throws Exception {
        Runtime rt = Runtime.getRuntime();
        Process proc;
        if (commandArray != null) {
            proc = rt.exec(commandArray);
        } else {
            proc = rt.exec(command);
        }

        // new bit . .
        if (input != null) {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()));
            try {
                out.write(input);
                out.close();
            } catch (IOException io) {
                System.out.println("Exception at write! " + io.getMessage());
                return false;
            }
        }

        InputStream stin = proc.getInputStream();
        InputStreamReader isr = new InputStreamReader(stin);
        BufferedReader br = new BufferedReader(isr);
        String line;
        System.out.println("input");
        while ((line = br.readLine()) != null) {
            if (systemCopy) {
                System.out.println(line);
            }
        }
        stin = proc.getErrorStream();
        isr = new InputStreamReader(stin);
        br = new BufferedReader(isr);
        System.out.println("error");
        while ((line = br.readLine()) != null) {
            if (systemCopy) {
                System.out.println(line);
            }
        }
        int exitVal = proc.waitFor();
        if (systemCopy) {
            System.out.println("Process exitValue: " + exitVal);
        }
        return exitVal == 0;
    }

    public boolean addFTPAccount(String accountName, String password)
            throws Exception {
        if (accountName != null && accountName.length() > 5
                && password != null && password.length() > 5 && password.length() <= 25) {
            // I don't think we need to sudo? Tomcat running as root.
            //return runCommand("/usr/local/bin/add-ftp-user.sh " + getAccountNameForFTP(accountName) + " " + password, true);
            return runCommand("add-ftp-user.sh " + getAccountNameForFTP(accountName) + " " + password, true);
        }
        return false;
    }

    public String getAccountNameForFTP(String accountName) {
        String toReturn = accountName.replace('.', '-').replace('/', '-').trim();
        if (toReturn.length() > 25) {
            return toReturn.substring(0, 25);
        } else {
            return toReturn;
        }
    }

    public boolean removeFTPAccount(String accountName)
            throws Exception {
        //return runCommand("/usr/local/bin/remove-ftp-user.sh " + accountName, false);
        return runCommand("remove-ftp-user.sh " + accountName, false);
    }


}
