package com.azquo.memorydb;

import java.util.Collections;
import java.util.List;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 20/05/15.
 *
 * String literals shared across the client and server. Maybe move to StringLiterals and just share that?
 */
public class Constants {
    public static final String DEFAULT_DISPLAY_NAME = "DEFAULT_DISPLAY_NAME";
    public static final String DATABASE_UNMODIFIED = "DATABASE UNMODIFIED";
    public static final List<String> DEFAULT_DISPLAY_NAME_AS_LIST = Collections.singletonList(DEFAULT_DISPLAY_NAME);
    public static String IN_SPREADSHEET = "in spreadsheet"; // We'll do this by string literal for the moment - might reconsider later. This is shared as currently it's part of the drilldown syntax and provenance, I'm not sure about this!
    public static int UKDATE = 1;
    public static int USDATE = 2;
}
