package com.azquo.util;

import java.io.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 21/01/14.
 *
 * currently unused but no harm in it being here
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
}