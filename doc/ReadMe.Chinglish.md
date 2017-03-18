<div style='text-align:right;'><a href='/doc/ReadMe.中文.md'>中文</a> | <span>Chinglish</span></div>

----

*WeChat does not officially publish the HTTP protocol of WeChat Web Edition, so WeChat Web Edition could be stopped like QQ Web Edition, running this BotEngine for business is not recommended.*

# About #

WeChatBotEngine is a bot engine/framework based on HTTP protocol of WeChat Web Edition.
WeChatBotEngine deals the communication with WeChat server itself, developers can develop bot applet based on that, so developers can build new bots to expand the power of WeChatBotEngine。

![text QR code](https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/text-QR-code.png)

## Official bot applets shipped with the engine ##
WeChatBotEngine project ships with several official bot applets, to demonstrate how to build a bot applet or you can just to run it.
Those bot applets are:

<dl>
	<dt><strong>SayHi</strong>: A bot which say Hi and Goodbye</dt>
	<dd>
		Send a Hi and Goodbye message when logged in or logged out. The Hi message and Goodbye message is configurable.
	</dd>
	<dt><strong>Repeater</strong>: A bot which repeat sender's message</dt>
	<dd>
		Repeat the message sent by sender. This bot is only for demonstration or test purpose, running for long term is not recommended.
	</dd>
	<dt><strong>Relay</strong>: A bot which relay messages from outside to a WeChat friend in your contacts (Incomig only, no outgoing)</dt>
	<dd>
		Receive message in JSON format from outside via Socket (TCP/IP), and send it to a friend (person or room) in your WeChat contacts according the target given in the message. Other applications can send message to WeChat via this relay bot, hence expanded the power of WeChatBotEngine, such as:
		<br/>
		<ul>
			<li>When download task of Transmission completed, a script will run (configurable in Transmission)，the script send message to WeChat via this relay bot, hence Download-Completed-Notification function is implemented.
				<br/>
				<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-relay.notify-transmission-download-complete.png'/>
			</li>
			<li>Using crontab to implement a Hourly-Time-Announcement function. (Of course you can write a bot applet to do it)</li>
			<li>Fetch information from a web page periodically, and send it to WeChat, to implement Scheduled-Information/Message-Push function.
				<br/>
				<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-relay.message-push-qiushibaike.png'/>
			</li>
			<li>You must have good ideas, please write it here ...</li>
			<li>...</li>
		</ul>
	</dd>
	<dt><strong>ShellCommand</strong>: A bot execute shell command</dt>
	<dd>
		Accept command line from text message, run it, return the result of command. Example: <code>cmd ls -l /bin/</code>
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-shell-command.png'/>
	</dd>
	<dt><strong>SimpleAddressBook</strong>: A simple address book bot for WeChat group chat</dt>
	<dd>
		This is a simple address book bot for WeChat [group chat / chat room] relied on MySQL database. It will query the database according the group nickname and return the contact information of the specified contact name. Example: send <code>sab Alice</code> in chat room <code>My Company 1</code> will return contact information of <code>Alice</code> of <code>My Company 1</code> address book.
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-simple-address-book.png'/>
	</dd>
	<dt><strong>HCICloudCSR</strong>: HCICloud customer service representative chat bot</dt>
	<dd>
		Using the HTTP protocol from HCICloud CSR, fetch the answer from CSR bot, then reply to user.
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-hcicloud-csr.png'/>
	</dd>
	<dt><strong>BaiduImageSearch</strong>: Baidu image search</dt>
	<dd>
		When an image message posted, send the image to baidu image search, return the guess information and possible image sources.
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-baidu-image-search.png'/>
	</dd>
	<dt><strong>BaiduVoice</strong>: Baidu Automatic Speech Recognition (ASR) and Text To Speech (TTS)</dt>
	<dd>
		When an audio or short video message is posted, send the audio to baidu ASR api, and return the text result. -- Because you can only use text when searching chat history, so it's very useful.
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-baidu-voice.png'/>
	</dd>
	<dt><strong>BaiduTranslate</strong>: Baidu translate</dt>
	<dd>
		Provide translation service via Baidu translate API.
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-baidu-translate.png'/>
	</dd>
	<dt><strong>GoogleImageSearch</strong>: Google Image Search</dt>
	<dd>
		When an image message posted, send the image to Google image search, return the guess information and possible image sources.
	</dd>
	<dt><strong>Emoji</strong>: Emoji bot</dt>
	<dd>
		Get emoji characters from database according keywords. <em>Due to different OS implemented their own support of different Unicode standard version, some emoji characters may not be displayed well.</em>
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-emoji.png'/>
	</dd>
	<dt><strong>MissileLaunched</strong>: Send a 'missile launched' message when someone shared a geographic position (just for fun)</dt>
	<dd>
		When someone send a geographic position message, read the longtitude and latitude value, and append a random customable joke.
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-missile-launched.png'/>
	</dd>
</dl>

### load/unload bots dynamically ###
	/listbots
	2016-12-16 17:43:27.714 [信息] net_maclife_wechat_http_BotEngine ListBots: 简易通讯录 (net_maclife_wechat_http_Bot_SimpleAddressBook)
	2016-12-16 17:43:27.715 [信息] net_maclife_wechat_http_BotEngine ListBots: 百度翻译 (net_maclife_wechat_http_Bot_BaiduTranslate)
	2016-12-16 17:43:27.716 [信息] net_maclife_wechat_http_BotEngine ListBots: Google 图片搜索 (net_maclife_wechat_http_Bot_GoogleImageSearch)
	2016-12-16 17:43:27.717 [信息] net_maclife_wechat_http_BotEngine ListBots: 消息中继 (net_maclife_wechat_http_Bot_Relay)

	/unloadbot  net_maclife_wechat_http_Bot_GoogleImageSearch
	2016-12-16 17:43:41.517 [信息] net_maclife_wechat_http_BotEngine UnloadBot: Google 图片搜索 机器人已被卸载

	/loadbot net_maclife_wechat_http_Bot_BaiduImageSearch
	2016-12-16 17:44:03.795 [信息] net_maclife_wechat_http_BotEngine LoadBot: 百度识图 机器人已创建并加载

# How to create your own bot applet #
Very easy, just extends/inherit `net_maclife_wechat_http_Bot` class, implements any (or zero) interface you wantted.
Currently, the following interfaces/events are planned:

- `OnLoggedIn`
- `OnLoggedOut`
- `OnShutdown`
- `OnMessagePackageReceived` Triggered when a message package received. This is the entrance of all the following `On***MessageReceived` event. If you want to analyze or modify the message by yourself, you can do it here.
- `OnTextMessageReceived` Triggered when a Text message received.
- `OnImageMessageReceived` Triggered when an Image message received.
- `OnVoiceMessageReceived` Triggered when a Voice message received.
- `OnVideoMessageReceived` Triggered when a Video message received.
- `OnEmotionMessageReceived` Triggered when an Emotion message received.
- `OnSystemMessageReceived` Triggered when a System message received.
- `OnMessageIsRevokedMessageReceived` Triggered when a MessageIsRevoked message received.
- `OnContactChanged` Triggered when a contact was changed.
- `OnContactDeleted` Triggered when a contact was deleted.
- `OnRoomMemberChanged` Triggered when a room member changed.

# Why your class name like this: `net_maclife_wechat_http_Bot` #
I don't like the layout of how Java organize the sources files and classes files -- directories in directories, so I replace all `.` (dot) in canonical/full class names to `_` (underline). This will avoid the Java style layout, it's simple & effective, you don't need to go deep in directories to get a file.
