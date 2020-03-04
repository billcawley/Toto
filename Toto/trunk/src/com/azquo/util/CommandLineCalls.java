package com.azquo.util;

import com.azquo.dataimport.ImportService;
import org.apache.log4j.Logger;

import java.io.*;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 21/01/14.
 * <p>
 * currently unused but no harm in it being here
 */
public class CommandLineCalls {


    private static final Logger logger = Logger.getLogger(CommandLineCalls.class);

    // to GPG I think we'd still need to command line but not yet
    // we may not actually need the FTP in the end either

    public static boolean runCommand(String command, boolean systemCopy)
            throws Exception {
        return runCommand(command, systemCopy, null);
    }

    public static boolean runCommand(String command, boolean systemCopy, String input)
            throws Exception {
        return runCommand(command, null, systemCopy, input);
    }

    public static boolean runCommand(String command, String[] commandArray, boolean systemCopy, String input)
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
            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()))) {
                out.write(input);
            } catch (IOException io) {
                logger.warn("Exception at write! " + io.getMessage());
                return false;
            }
        }

        InputStream stin = proc.getInputStream();
        InputStreamReader isr = new InputStreamReader(stin);
        BufferedReader br = new BufferedReader(isr);
        String line;
        logger.warn("input");
        while ((line = br.readLine()) != null) {
            if (systemCopy) {
                logger.warn(line);
            }
        }
        stin = proc.getErrorStream();
        isr = new InputStreamReader(stin);
        br = new BufferedReader(isr);
        logger.warn("error");
        while ((line = br.readLine()) != null) {
            if (systemCopy) {
                logger.warn(line);
            }
        }
        int exitVal = proc.waitFor();
        if (systemCopy) {
            logger.warn("Process exitValue: " + exitVal);
        }
        return exitVal == 0;
    }
}