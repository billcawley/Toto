package com.azquo.dataimport;

import org.springframework.scheduling.annotation.Scheduled;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 20/04/15.
 * <p>
 * Currently unused but it may be useful so I'm leaving it here
 */
public class DBCron {
    @Scheduled(cron = "0 * * * * *")
    public void demoServiceMethod() {
//        System.out.println("every minute?" + LocalDateTime.now());
    }
}