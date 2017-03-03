<div style='text-align:right;'><span>中文</span> | <a href='/doc/ReadMe.Chinglish.md'>Chinglish</a></div>

----

*由于微信官方未公开 Web 版通信协议，微信 Web 版本也有可能像 QQ Web 版那样停止运营，因此不建议将本引擎用于商业用途（前景未知）*

#关于#

WeChatBotEngine 是一个基于微信 Web 版通信协议的机器人引擎/机器人框架。
WeChatBotEngine 自身处理了与微信后台的通信，开发者只需要在此基础上开发自己的 Bot 小程序，即可打造、扩展 WeChatBotEngine 的机器人功能。

![文字二维码截图](https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/text-QR-code.png)

##WeChatBotEngine 自带的几个机器人小程序##
WeChatBotEngine 自带了几个机器人小程序，一些出于演示的目的，一些出于给开发者以参考的目的。这些机器人有：

### 自带的机器人小程序列表
<dl>
	<dt><strong>SayHi</strong>: 问候/再见机器人</dt>
	<dd>主要用于机器人上线、下线时发出通知。问候语、再见语可配置。</dd>

	<dt><strong>Repeater</strong>: 复读机机器人</dt>
	<dd>重复消息发送者的消息。该机器人仅用于演示、测试用途，正式环境不建议使用。</dd>

	<dt><strong>Relay</strong>: 消息中继机器人 (仅转入，不转出)</dt>
	<dd>从 Socket 接收 JSON 格式的消息，根据消息中指定的接收人转发到微信群/微信号中。其他程序通过这种方式来把消息转发到微信。
		利用这一点，可以把强大的外部应用程序的部分功能通过消息中继机器人将功能扩展，比如
		<br/>
		<ul>
			<li>Transmission 下载任务完成后，执行脚本（需在 Transmission 中配置），脚本通过消息中继发送到微信，以达到通知的目的。
				<br/>
				<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-relay.notify-transmission-download-complete.png'/>
			</li>
			<li>利用 crontab，实现一个简单的整点报时的功能。（当然你也可以单独写一个 Bot 小程序来实现）</li>
			<li>利用 crontab，定时获取一个网页的信息，将内容发到微信，达到定期推送消息的目的。
				<br/>
				<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-relay.message-push-qiushibaike.png'/>
			</li>
			<li>你一定还有其他好的点子，可在此处补充…</li>
			<li>…</li>
		</ul>
	</dd>

	<dt><strong>ShellCommand</strong>: 系统命令 / 命令行 机器人</dt>
	<dd>从文本消息中接收命令输入，执行命令，返回命令输出结果。用法举例： <code>cmd ls -l /bin/</code>
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-shell-command.png'/>
	</dd>

	<dt><strong>SimpleAddressBook</strong>: 微信群简易通讯录机器人</dt>
	<dd>基于 MySQL 数据库的微信群简易通讯录：根据微信群名称查询该群名下的指定的某个名称的联系信息。用法举例： 在 <code>我的公司1</code> 群中发送 <code>txl 张三</code> 将会查询 <code>我的公司1</code> 通讯录中 <code>张三</code> 的联系信息。
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-simple-address-book.png'/>
	</dd>

	<dt><strong>HCICloudCSR</strong>: 捷通华声 灵云智能客服 (CSR) 对话机器人</dt>
	<dd>利用灵云智能客服提供的 http 接口，从智能客服机器人获取一条答案，回复给用户。
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-hcicloud-csr.png'/>
	</dd>

	<dt><strong>BaiduImageSearch</strong>: 百度图片搜索（百度识图）机器人</dt>
	<dd>当有人发了图片时，提交给百度图片搜索，给出百度图片搜索的结果、可能的图片来源。
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-baidu-image-search.png'/>
	</dd>

	<dt><strong>BaiduVoice</strong>: 百度语音识别、语音合成机器人</dt>
	<dd>当有人发了音频、视频时，将音频、视频中的音频提交给百度语音识别（语音转文字），给出识别后的文字结果。 -- 因为在搜索聊天记录只能用文字来搜索，所以，非常实用。
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-baidu-voice.png'/>
	</dd>

	<dt><strong>BaiduTranslate</strong>: 百度翻译机器人</dt>
	<dd>利用百度翻译接口，提供翻译功能。
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-baidu-translate.png'/>
	</dd>

	<dt><strong>GoogleImageSearch</strong>: Google 图片搜索机器人</dt>
	<dd>当有人发了图片时，提交给 Google 图片搜索，给出 Google 图片搜索的结果、可能的图片来源。
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-google-image-search.png'/>
	</dd>

	<dt><strong>Emoji</strong>: Emoji 表情字符机器人</dt>
	<dd>根据关键字，从数据库中查询对应的 emoji 字符。<em>因为不同系统对 Unicode 支持的不同，个别 emoji 字符可能无法正常显示。</em>
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-emoji.png'/>
	</dd>

	<dt><strong>MissileLaunched</strong>: 用来恶搞地理位置信息的机器人</dt>
	<dd>当有人发了地理位置信息时，把经纬度报出来，然后加上一句随机可设置的“导弹准备就绪”的恶搞话语…… = =
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-missile-launched.png'/>
	</dd>
</dl>

###Bot 的动态加载、卸载###
	/listbots
	2016-12-16 17:43:27.714 [信息] net_maclife_wechat_http_BotEngine ListBots: 简易通讯录 (net_maclife_wechat_http_Bot_SimpleAddressBook)
	2016-12-16 17:43:27.715 [信息] net_maclife_wechat_http_BotEngine ListBots: 百度翻译 (net_maclife_wechat_http_Bot_BaiduTranslate)
	2016-12-16 17:43:27.716 [信息] net_maclife_wechat_http_BotEngine ListBots: Google 图片搜索 (net_maclife_wechat_http_Bot_GoogleImageSearch)
	2016-12-16 17:43:27.717 [信息] net_maclife_wechat_http_BotEngine ListBots: 消息中继 (net_maclife_wechat_http_Bot_Relay)

	/unloadbot  net_maclife_wechat_http_Bot_GoogleImageSearch
	2016-12-16 17:43:41.517 [信息] net_maclife_wechat_http_BotEngine UnloadBot: Google 图片搜索 机器人已被卸载

	/loadbot net_maclife_wechat_http_Bot_BaiduImageSearch
	2016-12-16 17:44:03.795 [信息] net_maclife_wechat_http_BotEngine LoadBot: 百度识图 机器人已创建并加载


#怎样开发自己的 Bot#
灰常简单：继承 `net_maclife_wechat_http_Bot`，实现该类的任意一个你想要实现的 (0 个也可以 -- 啥都不做的机器人) 接口。
目前已规划的接口有：

- `OnLoggedIn`
- `OnLoggedOut`
- `OnShutdown`
- `OnMessagePackageReceived` 当收到【消息包】时触发。该接口是下面几个 `On***MessageReceived` 接口的总入口，如果你要自己解析、甚至修改收到的微信消息包，可以在此接口入手。
- `OnTextMessageReceived` 当收到【文本消息】时触发。
- `OnImageMessageReceived` 当收到【图片消息】时触发。
- `OnVoiceMessageReceived` 当收到【语音消息】时触发。
- `OnVideoMessageReceived` 当收到【视频消息】时触发。
- `OnEmotionMessageReceived` 当收到【表情图消息】时触发。
- `OnSystemMessageReceived` 当收到【系统消息】（比如“收到红包，请到手机上查看”）时触发。
- `OnMessageIsRevokedMessageReceived` 当收到【消息被撤回】消息时触发。
- `OnContactChanged` 当【联系人变更】时触发。
- `OnContactDeleted` 当【联系人被删除】时触发。
- `OnRoomMemberChanged` 当【群成员变更】时触发。

#为什么你的类名是 `net_maclife_wechat_http_Bot` 这样子#
不喜欢 java 默认的一层套一层文件夹的组织方式，所以，把类的全名中的小数点 `.` 替换成 `_`，这样可以避免多层文件夹的组织方式 -- 简单、高效，找个文件不用来回切换文件夹。
