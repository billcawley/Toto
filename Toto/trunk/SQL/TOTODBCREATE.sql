-- phpMyAdmin SQL Dump
-- version 3.4.10.1deb1
-- http://www.phpmyadmin.net
--
-- Host: localhost
-- Generation Time: Dec 16, 2013 at 05:36 PM
-- Server version: 5.5.34
-- PHP Version: 5.3.10-1ubuntu3.8

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT = @@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS = @@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION = @@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;

--
-- Database: `toto`
--

-- --------------------------------------------------------

--
-- Table structure for table `name`
--

CREATE TABLE IF NOT EXISTS `name` (
  `id`   INT(11) NOT NULL AUTO_INCREMENT,
  `json` TEXT    NOT NULL,
  PRIMARY KEY (`id`)
)
  ENGINE =InnoDB
  DEFAULT CHARSET =utf8
  COLLATE =utf8_unicode_ci
  AUTO_INCREMENT =1;


CREATE TABLE IF NOT EXISTS `value` (
  `id`   INT(11) NOT NULL AUTO_INCREMENT,
  `json` TEXT    NOT NULL,
  PRIMARY KEY (`id`)
)
  ENGINE =InnoDB
  DEFAULT CHARSET =utf8
  COLLATE =utf8_unicode_ci
  AUTO_INCREMENT =1;


CREATE TABLE IF NOT EXISTS `provenance` (
  `id`   INT(11) NOT NULL AUTO_INCREMENT,
  `json` TEXT    NOT NULL,
  PRIMARY KEY (`id`)
)
  ENGINE =InnoDB
  DEFAULT CHARSET =utf8
  COLLATE =utf8_unicode_ci
  AUTO_INCREMENT =1;


/*!40101 SET CHARACTER_SET_CLIENT = @OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS = @OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION = @OLD_COLLATION_CONNECTION */;