-- as a general rule any non linking tables need an auto increment id as defined in adminentities.StandardEntity


CREATE TABLE IF NOT EXISTS `business` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `active` boolean NOT NULL,
  `start_date` timestamp NOT NULL,
  `business_name` varchar(255) NOT NULL,
  `parent_id` int(11) NOT NULL,
  `business_details` text NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `business_name` (`business_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=1 ;

CREATE TABLE IF NOT EXISTS `user` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `active` boolean NOT NULL,
  `start_date` timestamp NOT NULL,
  `email` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `status` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `seed` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=1 ;

CREATE TABLE IF NOT EXISTS `database` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `active` boolean NOT NULL,
  `start_date` timestamp NOT NULL,
  `business_id` int(11) NOT NULL,
  `name` varchar(255) NOT NULL,
  `name_count` int(11) NOT NULL,
  `value_count` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=1 ;

CREATE TABLE IF NOT EXISTS `access` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `active` boolean NOT NULL,
  `start_date` timestamp NOT NULL,
  `user_id` int(11) NOT NULL,
  `database_id` int(11) NOT NULL,
  `read_list` text NOT NULL,
  `write_list` text NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=1 ;

CREATE TABLE IF NOT EXISTS `upload_record` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `date` timestamp NOT NULL,
  `business_id` int(11) NOT NULL,
  `database_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `file_name` varchar (255) NOT NULL,
  `file_type` text NOT NULL,
  `comments` text NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=1 ;

