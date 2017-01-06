CREATE TABLE wechat_simple_address_book
(
	微信群昵称 VARCHAR(100) NOT NULL DEFAULT '' COMMENT '通讯录根据群昵称，来决定群聊时，只能查哪个群的通讯录',
	群联系人姓名 VARCHAR(50) NOT NULL DEFAULT '',
	电话号码 VARCHAR(100) NOT NULL DEFAULT '',
	工作单位 VARCHAR(100) NOT NULL DEFAULT '',
	住址 VARCHAR(100) NOT NULL DEFAULT '',

	/* 下面这些仅仅对应微信群内的昵称 */
	群联系人昵称 VARCHAR(50) NOT NULL DEFAULT '',
	群联系人群昵称 VARCHAR(50) DEFAULT '' COMMENT '就是“在微信群中的昵称”/“显示名”',
	群联系人微信号 VARCHAR(50) DEFAULT '',

	PRIMARY KEY PK__wechat_simple_address_book (微信群昵称, 群联系人姓名)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COMMENT='非常简单的微信群通讯录，用于 WeChatBotEngine 的简易通讯录机器人小程序';
