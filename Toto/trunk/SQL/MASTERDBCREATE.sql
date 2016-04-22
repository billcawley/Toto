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
  `business_details` text COLLATE utf8_unicode_ci NOT NULL
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `database`
--

CREATE TABLE IF NOT EXISTS `database` (
  `id` int(11) NOT NULL,
  `business_id` int(11) NOT NULL,
  `name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `mysql_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `database_type` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
  `name_count` int(11) NOT NULL,
  `value_count` int(11) NOT NULL,
  `database_server_id` int(11) NOT NULL
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

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

-- --------------------------------------------------------

--
-- Table structure for table `login_record`
--

CREATE TABLE IF NOT EXISTS `login_record` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `database_id` int(11) NOT NULL,
  `time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB AUTO_INCREMENT=190 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `online_report`
--

CREATE TABLE IF NOT EXISTS `online_report` (
  `id` int(11) NOT NULL,
  `date_created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `business_id` int(11) NOT NULL,
  `report_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `report_category` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
  `filename` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `explanation` text COLLATE utf8_unicode_ci NOT NULL,
  `renderer` int(11) NOT NULL DEFAULT '0',
  `active` tinyint(1) NOT NULL DEFAULT '1'
) ENGINE=InnoDB AUTO_INCREMENT=223 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

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
-- Table structure for table `permission`
--

CREATE TABLE IF NOT EXISTS `permission` (
  `id` int(11) NOT NULL,
  `report_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `database_id` int(11) NOT NULL,
  `read_list` text COLLATE utf8_unicode_ci NOT NULL,
  `write_list` text COLLATE utf8_unicode_ci NOT NULL
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

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
  `comments` text COLLATE utf8_unicode_ci NOT NULL
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
  `created_by` varchar(255) COLLATE utf8_unicode_ci NOT NULL
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
-- Indexes for table `login_record`
--
ALTER TABLE `login_record`
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
-- Indexes for table `permission`
--
ALTER TABLE `permission`
ADD PRIMARY KEY (`id`);

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


CREATE TABLE IF NOT EXISTS `invoice_details` (
  `id` int(11) NOT NULL,
  `customer_reference` varchar(255) COLLATE utf8_unicode_ci NOT NULL default '',
  `service_description` text COLLATE utf8_unicode_ci,
  `quantity` int(11) NOT NULL default '0',
  `unit_cost` int(11) NOT NULL default '0',
  `payment_terms` int(11) NOT NULL default '0',
  `po_reference` varchar(255) COLLATE utf8_unicode_ci NOT NULL default '',
  `invoice_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `invoice_period` varchar(255) COLLATE utf8_unicode_ci NOT NULL default '',
  `invoice_no` varchar(255) COLLATE utf8_unicode_ci NOT NULL default '',
  `invoice_address` text COLLATE utf8_unicode_ci,
  `no_vat` TINYINT(1) NOT NULL DEFAULT '0',
  `send_to` varchar(255) COLLATE utf8_unicode_ci NOT NULL default ''
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

ALTER TABLE `invoice_details`
ADD PRIMARY KEY (`id`),
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT,AUTO_INCREMENT=1;

CREATE TABLE IF NOT EXISTS `invoice_sent` (
  `id` int(11) NOT NULL,
  `customer_reference` varchar(255) COLLATE utf8_unicode_ci NOT NULL default '',
  `service_description` text COLLATE utf8_unicode_ci,
  `quantity` int(11) NOT NULL default '0',
  `unit_cost` int(11) NOT NULL default '0',
  `payment_terms` int(11) NOT NULL default '0',
  `po_reference` varchar(255) COLLATE utf8_unicode_ci NOT NULL default '',
  `invoice_date` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `invoice_period` varchar(255) COLLATE utf8_unicode_ci NOT NULL default '',
  `invoice_no` varchar(255) COLLATE utf8_unicode_ci NOT NULL default '',
  `invoice_address` text COLLATE utf8_unicode_ci,
  `no_vat` TINYINT(1) NOT NULL DEFAULT '0',
  `send_to` varchar(255) COLLATE utf8_unicode_ci NOT NULL default '',
  `date_time_sent` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

ALTER TABLE `invoice_sent`
ADD PRIMARY KEY (`id`),
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT,AUTO_INCREMENT=1;


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
-- AUTO_INCREMENT for table `login_record`
--
ALTER TABLE `login_record`
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
-- AUTO_INCREMENT for table `permission`
--
ALTER TABLE `permission`
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