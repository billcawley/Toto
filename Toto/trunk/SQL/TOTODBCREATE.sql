-- phpMyAdmin SQL Dump
-- version 3.4.10.1deb1
-- http://www.phpmyadmin.net
--
-- Host: localhost
-- Generation Time: Dec 16, 2013 at 05:36 PM
-- Server version: 5.5.34
-- PHP Version: 5.3.10-1ubuntu3.8

SET SQL_MODE="NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;

--
-- Database: `toto`
--

-- --------------------------------------------------------

--
-- Table structure for table `name`
--

CREATE TABLE IF NOT EXISTS `name` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `provenance_id` int(11) NOT NULL,
  `name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `additive` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=1 ;

-- --------------------------------------------------------

--
-- Table structure for table `name_attribute`
--

CREATE TABLE IF NOT EXISTS `name_attribute` (
  `name_id` int(11) NOT NULL,
  `attribute_name` varchar(40) COLLATE utf8_unicode_ci NOT NULL,
  `attribute_value` varchar(255) COLLATE utf8_unicode_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `name_set_definition`
--

CREATE TABLE IF NOT EXISTS `name_set_definition` (
  `parent_id` int(11) NOT NULL,
  `child_id` int(11) NOT NULL,
  `position` smallint(5) NOT NULL DEFAULT '0',
  PRIMARY KEY (`parent_id`,`child_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `peer_set_definition`
--

CREATE TABLE IF NOT EXISTS `peer_set_definition` (
  `name_id` int(11) NOT NULL,
  `peer_id` int(11) NOT NULL,
  `position` smallint(5) NOT NULL DEFAULT '0',
  `additive` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`name_id`,`peer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `provenance`
--

CREATE TABLE IF NOT EXISTS `provenance` (
  `id` int(11) NOT NULL,
  `user` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `method` varchar(40) COLLATE utf8_unicode_ci NOT NULL,
  `name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `row_headings` text COLLATE utf8_unicode_ci,
  `column_headings` text COLLATE utf8_unicode_ci,
  `context` text COLLATE utf8_unicode_ci,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `value`
--

CREATE TABLE IF NOT EXISTS `value` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `provenance_id` int(11) NOT NULL,
  `double` double DEFAULT NULL,
  `text` text COLLATE utf8_unicode_ci,
  `deleted_info` text COLLATE utf8_unicode_ci,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=1 ;

-- --------------------------------------------------------

--
-- Table structure for table `value_name`
--

CREATE TABLE IF NOT EXISTS `value_name` (
  `value_id` int(11) NOT NULL,
  `name_id` int(11) NOT NULL,
  PRIMARY KEY (`name_id`,`value_id`),
  KEY `value_id` (`value_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;