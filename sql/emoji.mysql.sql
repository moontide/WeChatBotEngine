CREATE TABLE emoji
(
	sn INT UNSIGNED NOT NULL DEFAULT 0,
	code VARCHAR(45) CHARACTER SET ascii NOT NULL DEFAULT '',
	emoji_char VARCHAR(5) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT 'emoji 字符。有的 emoji 是由两个甚至五个字符组成的，所以，长度不能设置为 1。而且很多 emoji 的 utf8 编码都是 4 字节的，所以此字段的字符集编码也要用 4 字节的 utf8: utf8mb4。还要注意： COLLATE 必须用 utf8mb4_bin，原因，默认的 COLLATION 不支持增补集字符，参见 http://mysqlserverteam.com/sushi-beer-an-introduction-of-utf8-support-in-mysql-8-0/  https://bugs.mysql.com/bug.php?id=76553',
	浏览器显示图 TEXT CHARACTER SET ascii NOT NULL,
	Apple显示图 TEXT CHARACTER SET ascii NOT NULL,
	Google显示图 TEXT CHARACTER SET ascii NOT NULL,
	Twitter显示图 TEXT CHARACTER SET ascii NOT NULL,
	EmojiOne显示图 TEXT CHARACTER SET ascii NOT NULL,
	Facebook显示图 TEXT CHARACTER SET ascii NOT NULL,
	FacebookMessenger显示图 TEXT CHARACTER SET ascii NOT NULL,
	Samsung显示图 TEXT CHARACTER SET ascii NOT NULL,
	Windows显示图 TEXT CHARACTER SET ascii NOT NULL,
	GMail显示图 TEXT CHARACTER SET ascii NOT NULL,
	SoftBank显示图 TEXT CHARACTER SET ascii NOT NULL,
	DCM显示图 TEXT CHARACTER SET ascii NOT NULL,
	KDDI显示图 TEXT CHARACTER SET ascii NOT NULL,
	英文名称 VARCHAR(100) NOT NULL DEFAULT '',
	日期 VARCHAR(20) NOT NULL DEFAULT '',
	tag_name VARCHAR(80) NOT NULL DEFAULT '',
	is_unique_tag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '此标签是不是能够唯一能取到单个 emoji 字符的。',

	PRIMARY KEY PK__emoji (emoji_char)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COMMENT='emoji 字符表。';
