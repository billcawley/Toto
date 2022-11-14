package com.azquo.admin.database;

import com.azquo.admin.StandardEntity;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.user.User;
import com.azquo.admin.user.UserDAO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Copyright (C) 2018 Azquo Ltd.
 * <p>
 * Created by cawley on 04/02/2018
 * probably only used by Ed Broking
 * <p>
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `business_id` int(11) NOT NULL,
 `identifier` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
 `team` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
 `text` text COLLATE utf8_unicode_ci DEFAULT NULL,
 PRIMARY KEY (`id`)

 */
public final class Comment extends StandardEntity {

    final private int businessId;
    final private String identifier;
    final private String team;
    private String text;

    public Comment(int id, int businessId, String identifier, String team, String text) {
        this.id = id;
        this.businessId = businessId;
        this.identifier = identifier;
        this.team = team;
        this.text = text;
    }

    public int getBusinessId() {
        return businessId;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getTeam() {
        return team;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}