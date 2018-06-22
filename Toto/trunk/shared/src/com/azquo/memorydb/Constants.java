package com.azquo.memorydb;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 20/05/15.
 *
 * String literals shared across the client and server. Maybe move to StringLiterals and just share that?
 */
public class Constants {
    public static final String DEFAULT_DISPLAY_NAME = "DEFAULT_DISPLAY_NAME";
    public static String IN_SPREADSHEET = "in spreadsheet"; // We'll do this by string literal for the moment - might reconsider later. This is shared as currently it's part of the drilldown syntax and provenance, I'm not sure about this!
    public static int UKDATE = 1;
    public static int USDATE = 2;
}
