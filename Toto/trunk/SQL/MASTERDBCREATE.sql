-- phpMyAdmin SQL Dump
-- version 4.2.10
-- http://www.phpmyadmin.net
--
-- Host: localhost
-- Generation Time: Sep 22, 2015 at 11:38 AM
-- Server version: 5.6.25
-- PHP Version: 5.6.1

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";

--
-- Database: `master_db`
--

-- --------------------------------------------------------

--
-- Table structure for table `business`
--

CREATE TABLE IF NOT EXISTS `business` (
  `id` int(11) NOT NULL,
  `business_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `business_details` text COLLATE utf8_unicode_ci NOT NULL,
  `banner_color` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `logo` varchar(255) COLLATE utf8_unicode_ci NOT NULL
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `database`
--

CREATE TABLE IF NOT EXISTS `database` (
                                        `id` int(11) NOT NULL,
                                        `business_id` int(11) NOT NULL,
                                        `user_id` int(11) NOT NULL DEFAULT '0',
                                        `name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
                                        `mysql_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
                                        `database_type` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
                                        `name_count` int(11) NOT NULL,
                                        `value_count` int(11) NOT NULL,
                                        `database_server_id` int(11) NOT NULL,
                                        `created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        `last_provenance` text COLLATE utf8_unicode_ci,
                                        `auto_backup` tinyint(1) DEFAULT '0'
) ENGINE=InnoDB AUTO_INCREMENT=698 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
-- --------------------------------------------------------

--
-- Table structure for table `database_server`
--

CREATE TABLE IF NOT EXISTS `database_server` (
  `id` int(11) NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ip` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sftp_url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `database` CHANGE `last_provenance` `last_provenance` TEXT CHARACTER SET utf8 COLLATE utf8_unicode_ci NULL DEFAULT NULL;
-- --------------------------------------------------------

--
-- Table structure for table `online_report`

CREATE TABLE IF NOT EXISTS `online_report` (
  `id` int(11) NOT NULL,
  `date_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `business_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `report_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `filename` varchar(512) COLLATE utf8_unicode_ci NOT NULL,
  `explanation` text COLLATE utf8_unicode_ci NOT NULL

) ENGINE=InnoDB AUTO_INCREMENT=223 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;



CREATE TABLE IF NOT EXISTS `importdata_usage` (
                                          `id` int(11) NOT NULL,
                                          `business_` int(11) NOT NULL,
                                          `importdata_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
                                          `report_id` int(11) NOT NULL

) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

ALTER TABLE `importdata_usage`
    ADD PRIMARY KEY (`id`);

ALTER TABLE `importdata_usage`
    MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;




CREATE TABLE IF NOT EXISTS `import_template` (
                                             `id` int(11) NOT NULL,
                                             `date_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                             `business_id` int(11) NOT NULL,
                                             `user_id` int(11) NOT NULL DEFAULT '0',
                                             `template_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
                                             `filename` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
                                             `notes` text COLLATE utf8_unicode_ci NOT NULL
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

ALTER TABLE `import_template`
  ADD PRIMARY KEY (`id`);

ALTER TABLE `import_template`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;
--
-- Table structure for table `online_report`
--

CREATE TABLE IF NOT EXISTS `database_report_link` (
  `database_id` int(11) NOT NULL,
  `report_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

ALTER TABLE `database_report_link`
  ADD KEY `database_id` (`database_id`), ADD KEY `report_id` (`report_id`);

-- --------------------------------------------------------

--
-- Table structure for table `open_database`
--

CREATE TABLE IF NOT EXISTS `open_database` (
  `id` int(11) NOT NULL,
  `database_id` int(11) NOT NULL,
  `open` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `close` datetime NOT NULL DEFAULT '0000-00-00 00:00:00'
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;


-- --------------------------------------------------------

--
-- Table structure for table `report_schedule`
--

CREATE TABLE IF NOT EXISTS `report_schedule` (
  `id` int(11) NOT NULL,
  `period` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `recipients` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `next_due` datetime DEFAULT NULL,
  `database_id` int(11) NOT NULL,
  `report_id` int(11) NOT NULL,
  `type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `parameters` text COLLATE utf8mb4_unicode_ci,
  `email_subject` varchar(255) COLLATE utf8_unicode_ci NOT NULL
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `upload_record`
--

CREATE TABLE IF NOT EXISTS `upload_record` (
  `id` int(11) NOT NULL,
  `date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `business_id` int(11) NOT NULL,
  `database_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `file_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `file_type` text COLLATE utf8_unicode_ci NOT NULL,
  `comments` longtext COLLATE utf8_unicode_ci NOT NULL,
  `temp_path` varchar(1024) COLLATE utf8_unicode_ci NOT NULL,
      `user_comment` longtext COLLATE utf8_unicode_ci NOT NULL
) ENGINE=InnoDB AUTO_INCREMENT=207 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `user`
--

CREATE TABLE IF NOT EXISTS `user` (
  `id` int(11) NOT NULL,
  `end_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `business_id` int(11) NOT NULL,
  `email` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `status` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `salt` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `created_by` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `database_id` int(11) NOT NULL,
  `report_id` int(11) NOT NULL,
  `selections` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
    `team` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL


) ENGINE=InnoDB AUTO_INCREMENT=101 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `user_choice`
--

CREATE TABLE IF NOT EXISTS `user_choice` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `choice_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `choice_value` text COLLATE utf8_unicode_ci NOT NULL,
  `time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB AUTO_INCREMENT=82 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `user_region_options`
--

CREATE TABLE IF NOT EXISTS `user_region_options` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `report_id` int(11) NOT NULL,
  `region` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `hide_rows` int(4) NOT NULL DEFAULT '0',
  `sortable` tinyint(1) NOT NULL DEFAULT '0',
  `row_limit` int(4) NOT NULL DEFAULT '0',
  `column_limit` int(4) NOT NULL DEFAULT '0',
  `sort_row` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_row_asc` tinyint(1) NOT NULL DEFAULT '0',
  `sort_column` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_column_asc` tinyint(1) NOT NULL DEFAULT '0',
  `highlight_days` int(3) NOT NULL DEFAULT '0'
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Made for Ed Broking - but perhaps useful for all

CREATE TABLE IF NOT EXISTS `pending_upload` (
                                              `id` int(11) NOT NULL AUTO_INCREMENT,
                                              `business_id` int(11) NOT NULL,
                                              `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                              `processed_date` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                              `file_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
                                              `file_path` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
                                              `created_by_user_id` int(11) NOT NULL,
                                              `processed_by_user_id` int(11) DEFAULT NULL,
                                              `database_id` int(11) NOT NULL,
                                              `import_result_path` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
                                              `team` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
                                              PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=120 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `importschedule`
--

CREATE TABLE IF NOT EXISTS `import_schedule` (
                                      `id` int(11) NOT NULL AUTO_INCREMENT,
                                      `name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
                                      `count` int(11) NOT NULL,
                                      `frequency` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
                                      `next_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      `business_id` int(11) NOT NULL,
                                      `database_id` int(11) NOT NULL,
                                      `connector_id` int(11) NOT NULL,
                                      `user_id` int(11) NOT NULL,
                                      `sql` text COLLATE utf8_unicode_ci NOT NULL,
                                      `template_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
                                      `output_connector_id` int(11) NOT NULL,
                                      `notes` text COLLATE utf8_unicode_ci NOT NULL


) ENGINE=InnoDB AUTO_INCREMENT=101 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;



--
-- Indexes for dumped tables
--

--
-- Indexes for table `business`
--
ALTER TABLE `business`
ADD PRIMARY KEY (`id`), ADD UNIQUE KEY `business_name` (`business_name`);

--
-- Indexes for table `database`
--
ALTER TABLE `database`
ADD PRIMARY KEY (`id`);

--
-- Indexes for table `database_server`
--
ALTER TABLE `database_server`
ADD PRIMARY KEY (`id`);

--
-- Indexes for table `online_report`
--
ALTER TABLE `online_report`
ADD PRIMARY KEY (`id`), ADD KEY `business_id` (`business_id`);

--
-- Indexes for table `open_database`
--
ALTER TABLE `open_database`
ADD PRIMARY KEY (`id`), ADD KEY `close` (`close`), ADD KEY `database_id` (`database_id`,`close`);


--
-- Indexes for table `report_schedule`
--
ALTER TABLE `report_schedule`
ADD PRIMARY KEY (`id`);

--
-- Indexes for table `upload_record`
--
ALTER TABLE `upload_record`
ADD PRIMARY KEY (`id`);

--
-- Indexes for table `user`
--
ALTER TABLE `user`
ADD PRIMARY KEY (`id`), ADD UNIQUE KEY `email` (`email`);

--
-- Indexes for table `user_choice`
--
ALTER TABLE `user_choice`
ADD PRIMARY KEY (`id`), ADD UNIQUE KEY `duplicatechecker` (`user_id`,`choice_name`), ADD KEY `user_id` (`user_id`);

--
-- Indexes for table `user_region_options`
--
ALTER TABLE `user_region_options`
ADD PRIMARY KEY (`id`);



--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `business`
--
ALTER TABLE `business`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;
--
-- AUTO_INCREMENT for table `database`
--
ALTER TABLE `database`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;
--
-- AUTO_INCREMENT for table `database_server`
--
ALTER TABLE `database_server`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;
--
-- AUTO_INCREMENT for table `online_report`
--
ALTER TABLE `online_report`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;
--
-- AUTO_INCREMENT for table `open_database`
--
ALTER TABLE `open_database`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;
--
-- AUTO_INCREMENT for table `report_schedule`
--
ALTER TABLE `report_schedule`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;
--
-- AUTO_INCREMENT for table `upload_record`
--
ALTER TABLE `upload_record`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;
--
-- AUTO_INCREMENT for table `user`
--
ALTER TABLE `user`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;
--
-- AUTO_INCREMENT for table `user_choice`
--
ALTER TABLE `user_choice`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;
--
-- AUTO_INCREMENT for table `user_region_options`
--
ALTER TABLE `user_region_options`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

-- Made for Ed Broking - not sure it will have use for anyone else. . .

CREATE TABLE IF NOT EXISTS `comment` (
                                              `id` int(11) NOT NULL AUTO_INCREMENT,
                                              `business_id` int(11) NOT NULL,
                                              `identifier` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
                                              `team` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
                                              `text` text COLLATE utf8_unicode_ci DEFAULT NULL,
                                              PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=120 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;


--
-- Table structure for table `upload_record`
--

CREATE TABLE IF NOT EXISTS `user_event` (
                                               `id` int(11) NOT NULL,
                                               `date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                               `business_id` int(11) NOT NULL,
                                               `user_id` int(11) NOT NULL,
                                               `report_id` int(11) NOT NULL,
                                               `event` varchar(255) COLLATE utf8_unicode_ci NOT NULL
) ENGINE=InnoDB AUTO_INCREMENT=207 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

