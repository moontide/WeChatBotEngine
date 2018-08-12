CREATE TABLE wechat_message_packages
(
	package_id INT UNSIGNED AUTO_INCREMENT,
	package_json TEXT NOT NULL DEFAULT '' COMMENT 'HTTP 接口收到的原始消息包',

	AddMsgCount INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '新消息数量 AddMsgCount',
	ModContactCount INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '联系人变更数量 ModContactCount',
	DelContactCount INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '联系人删除数量',
	ModChatRoomMemberCount INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '群成员变更数量',

	PRIMARY KEY PK__package_id (package_id)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COMMENT='接受到消息包。这是包含了文字';

/*
CREATE TABLE wechat_messages
(
	package_id INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '此 ID 为本表自己产生的 ID，非消息包',
	message_id,
	id_wechat VARCHAR(50) NOT NULL DEFAULT '' COMMENT '这是消息中自带的 ID，非自增长类型，也不确定是否唯一',
	type INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '消息类型： 1=文本消息, 51=, 10000=系统消息',
	content TEXT NOT NULL DEFAULT '' COMMENT '注意：此消息内容已经过处理：',
	from_account,
	from_name NOT NULL DEFAULT '' COMMENT '注意，消息本身不包含此信息，这个名称只是根据 account 从内部缓存的通讯录中获取到的',
	to_account,
	to_name NOT NULL DEFAULT '' COMMENT '注意，消息本身不包含此信息，这个名称只是根据 account 从内部缓存的通讯录中获取到的',
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COMMENT='接受到文本消息包。';
*/
