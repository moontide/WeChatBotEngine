CREATE TABLE wechat_contacts
(
	contact_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
	SessionID VARCHAR(32) CHARACTER SET ascii NOT NULL DEFAULT '' COMMENT '记录联系人时的会话的 ID',
	ContactAccountInThisSession VARCHAR(70) CHARACTER SET ascii NOT NULL DEFAULT '' COMMENT '此联系人在此会话的帐号ID',
	明文ID VARCHAR(100) NOT NULL DEFAULT '' COMMENT '微信明文 ID，不是那种每次会话都会变化的且经过加密后的ID。这个 ID 是需要<s>手工在手机端逐个打开联系人的聊天窗口后才会获取到</s>（这种方法已失效，微信已不返回该数据，现在改为从“消息被撤回”消息中获取该数据），所有，如果从未打开过某人的聊天窗口，这个信息可能会为空',
	微信号 VARCHAR(50) NOT NULL DEFAULT '' COMMENT '微信号。现在这个数据已经获取不到',
	昵称 VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '昵称',
	备注名 VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '备注名',
	签名 VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '签名',

	/*
	电话号码 VARCHAR(100) NOT NULL DEFAULT '' COMMENT '电话号码',
	描述 VARCHAR(100) NOT NULL DEFAULT '' COMMENT '描述/更多备注',
	标签 VARCHAR(100) NOT NULL DEFAULT '' COMMENT '标签',
	照片或名片 VARCHAR(100) NOT NULL DEFAULT '' COMMENT '照片或名片',
	*/

	性别 TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0-未设置; 1-男; 2-女',
	省 VARCHAR(100) NOT NULL DEFAULT '' COMMENT '省',
	市 VARCHAR(100) NOT NULL DEFAULT '' COMMENT '市',

	是否星标好友 TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否星标好友',
	是否已删除 TINYINT(1) NOT NULL DEFAULT 0 COMMENT '微信联系人中是否已删除，删除后，这里也许只是做个标志位更改，而不真正删除',
	删除时间 DATETIME,


	是否群 TINYINT(1) NOT NULL DEFAULT 0 COMMENT '群的明文 ID 是以 @chatroom 结尾的',
	群主UIN BIGINT NOT NULL DEFAULT 0 COMMENT '群主的 UIN ，类似 QQ 号的信息。这个信息，呃，简直比明文 ID 还透明，<s>这个信息只是在微信初始化接口的返回结果里看到过（仅仅最近联系的群信息中才有数据，其他联系人数据都是 0）</s>  没有了，最近联系人里的数据也是 0 了',
	群主昵称 VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '群主昵称',
	群成员数量 SMALLINT UNSIGNED NOT NULL DEFAULT 0,
	是否公众号 TINYINT(1) NOT NULL DEFAULT 0 COMMENT '公众号的明文 ID 是以 gh_ 开头的',
	是否企业号 TINYINT(1) NOT NULL DEFAULT 0,
	是否微信团队号 TINYINT(1) NOT NULL DEFAULT 0,

	数据来源 VARCHAR(10) NOT NULL DEFAULT '' COMMENT '数据从改哪里来的，可选取值：微信初始化（最近联系人）、微信通讯录。这个来源，基本对应引擎触发的某个事件',
	最后更新时间 DATETIME,

	PRIMARY KEY (contact_id),
	UNIQUE KEY UQ__昵称_备注名 (昵称, 备注名),
	INDEX IX__wechat_contacts_昵称 (昵称),
	INDEX IX__wechat_contacts_备注名 (备注名)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COMMENT='同步个人微信的通讯录。除了位于加入到微信通讯录的联系人，还包括未加入到通讯录的群';

CREATE TABLE wechat_contact_members
(
	contact_id INT UNSIGNED NOT NULL DEFAULT 0,
	序号 SMALLINT UNSIGNED NOT NULL DEFAULT 0,

	成员昵称 VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '成员昵称',
	成员群昵称 VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '成员的群昵称 - DisplayName',

	PRIMARY KEY (contact_id, 序号)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COMMENT='群聊里的成员';

CREATE VIEW v_wechat_contact_members
AS
	SELECT
		c.昵称 AS 群名,
		m.*
	FROM
		wechat_contact_members m
		LEFT JOIN wechat_contacts c ON m.contact_id = c.contact_id
	;
