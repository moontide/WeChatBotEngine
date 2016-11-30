<div style='text-align:right;'><a href='ReadMe.Chinglish.md'>Chinglish</a> <span>中文</span></div>

*由于微信官方未公开 Web 版通信协议，微信 Web 版本也有可能像 QQ Web 版那样停止运营，因此不建议将本引擎用于商业用途（前景未知）*

#关于#

WeChatBotEngine 是一个基于微信 Web 版通信协议的机器人引擎/机器人框架。
WeChatBotEngine 自身处理了与微信后台的通信，开发者只需要在此基础上开发自己的 Bot 小程序，即可打造、扩展 WeChatBotEngine 的机器人功能。

##WeChatBotEngine 自带的几个机器人小程序##
WeChatBotEngine 自带了几个机器人小程序，一些出于演示的目的，一些出于给开发者以参考的目的。这些机器人有：

<dl>
	<dt><strong>SayHi</strong>: 问候/再见机器人</dt>
	<dd>主要用于机器人上线、下线时发出通知。问候语、再见语可配置。</dd>

	<dt><strong>Repeater</strong>: 复读机机器人</dt>
	<dd>重复消息发送者的消息。该机器人仅用于演示、测试用途，正式环境不建议使用。</dd>

	<dt><strong>Relay</strong>: 消息中继机器人</dt>
	<dd>从 Socket 接收消息，根据消息中指定的接收人转发到微信群/微信号中。其他程序通过这种方式来把消息转发到微信 (比如，一个长时间任务完成的消息、告警消息等等…)</dd>

	<dt><strong>HCICloudCSR</strong>: 捷通华声 灵云智能客服 (CSR) 对话机器人/dt>
	<dd>利用灵云智能客服提供的 http 接口，从智能客服机器人获取一条答案，回复给用户。</dd>
</dl>

#怎样开发自己的 Bot#
灰常简单：继承 `net_maclife_wechat_http_Bot`，实现该类的任意一个你想要实现的 (0 个也可以 -- 啥都不做的机器人) 接口。
目前已规划的接口有：

- `OnLoggedIn`
- `OnLoggedOut`
- `OnShutdown`
- `OnMessageReceived` 当有消息收到时触发。该接口是下面几个 Message 接口的总入口，如果你要自己解析收到的微信消息，可以在此接口入手。
- `OnTextMessageReceived` 当有文本消息收到时触发。
- `OnImageMessageReceived` 当有图片消息收到时触发。
- `OnVoiceMessageReceived` 当有语音消息收到时触发。
- `OnVideoMessageReceived` 当有视频消息收到时触发。
- `OnEmotionMessageReceived` 当有表情图消息收到时触发。

#为什么你的类名是 `net_maclife_wechat_http_Bot` 这样子#
不喜欢 java 默认的一层套一层文件夹的组织方式，所以，把类的全名中的小数点 `.` 替换成 `_`，这样可以避免多层文件夹的组织方式 -- 简单、高效，找个文件不用来回切换文件夹。
