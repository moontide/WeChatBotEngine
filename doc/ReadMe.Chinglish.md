<div style='text-align:right;'><a href='/doc/ReadMe.中文.md'>中文</a> | <span>Chinglish</span></div>

----

*Warning: It could caused your [wechat account forbidden to login web edition anymore if you use WeChatBotEngine](https://github.com/moontide/WeChatBotEngine/issues/12), hence you'll unable to run WeChatBotEngine properly anymore!*

# About #

WeChatBotEngine is a bot engine/framework based on HTTP protocol of WeChat Web Edition.
WeChatBotEngine deals the communication with WeChat server itself, developers can develop bot based on that, so developers can build new bots to expand the power of WeChatBotEngine。

![text QR code](https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/text-QR-code.png)

## Official bots shipped with the engine ##
WeChatBotEngine project ships with several official bots, to demonstrate how to build a bot or you can just to run it.
Those bots are:

### Bots shipped with WeChatBotEngine ###
Legend:
* 📌 - Recommeded
* ✳ - Useful

#### Common Bots ####
<dl>
	<dt>📌 <a href='/doc/bot-WebSoup.md'><strong>WebSoup</strong>: Fetch HTML/XML/JSON from web, extract information from it.</a></dt>
	<dd>HTML/XML: Use <a href='https://jsoup.org/apidocs/org/jsoup/select/Selector.html'>CSS Selector</a> to extract information from fetched HTML/XML resource, send it to WeChat friend; JSON: Use JavaScript to extract data from JSON data, send it to WeChat friend.
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-web-soup-50%25.png'/>
	</dd>
	<dt>📌 <strong>Relay</strong>: A bot which relay messages from outside to a WeChat friend in your contacts (Incomig only, no outgoing)</dt>
	<dd>
		Receive message in JSON format from outside via Socket (TCP/IP), and send it to a friend (person or room) in your WeChat contacts according the target given in the message. Other applications can send message to WeChat via this relay bot, hence expanded the power of WeChatBotEngine, such as:
		<br/>
		<ul>
			<li>When download task of Transmission completed, a script will run (configurable in Transmission)，the script send message to WeChat via this relay bot, hence Download-Completed-Notification function is implemented.
				<br/>
				<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-relay.notify-transmission-download-complete-50%25.png'/>
			</li>
			<li>Using crontab to implement a Hourly-Time-Announcement function. (Of course you can write a bot to do it)</li>
			<li>Fetch information from a web page periodically, and send it to WeChat, to implement Scheduled-Information/Message-Push function.
				<br/>
				<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-relay.message-push-qiushibaike.png'/>
			</li>
			<li>Using crontab to 'sign in' to some public accounts to get membership points.
				<br/>
				<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-relay.scheduled-sign-50%25.png'/>
			</li>
			<li>You must have good ideas, please write it here ...</li>
			<li>...</li>
		</ul>
	</dd>
	<dt>📌 <strong>ShellCommand</strong>: A bot execute shell command</dt>
	<dd>
		Accept command line from text message, run it, return the result of command. Example: <code>/cmd ls -l /bin/</code>
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-shell-command-50%25.png'/>
	</dd>
	<dt>📌 <a href='/doc/bot-ActiveDirectoryAddressBook.md'><strong>ActiveDirectoryAddressBook</strong>: Use Active Directory (or Samba 4.x in Linux) as address book for company</a></dt>
	<dd>Address book using Active Directory as backend: According room name or nickname of friend, search the keyword (name or account or email address or telephone number) from the Active Directories bounded to this name for contact information. Example: Send <code>/adab KEYWORD</code> in <code>My Company 1</code> room will search for the contact information which contains <code>KEYWORD</code> from the active directories bounded to <code>My Company 1</code> name.
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-active-directory-address-book-50%25.png'/>
	</dd>
	<dt>✳ <strong>SimpleAddressBook</strong>: A simple address book bot for WeChat chat room</dt>
	<dd>
		This is a simple address book bot which relied on MySQL database for WeChat chat room. It will query the database according the room nickname and return the contact information of the specified contact name. Example: send <code>/sab Alice</code> in chat room <code>My Company 1</code> will return contact information of <code>Alice</code> of <code>My Company 1</code> address book.
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-simple-address-book-50%25.png'/>
	</dd>
	<dt>✳ <strong>MakeFriend</strong>: A MakeFriend agent bot</dt>
	<dd>In chat room, use <code>/addme</code> command to send a request of MakeFriend to the command issuer.
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-make-friend-addme-50%25.png'/>
		<br/>
		When a request of MakeFriend message is received, automatically accept the request according a keyword/password.
	</dd>
	<dt>✳ <strong>Manager</strong>: A remote manager bot</dt>
	<dd>This is just a reimplementation of some console commands, to let you manage bot engine if you are not in front of your computer. Currently, the following commands are implemented: /LoadBot, /UnloadBot, /ListBots (everyone can use this command), /LogLevel (View or Set log level), /Topic (Change room name like change topic in IRC channel), /Invite (Invite a contact to a chat room), /Kick (Kick a member from chat room, Kick operation works only if you're the administrator of that room)。
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-manager-50%25.png'/>
	</dd>
	<dt>✳ <strong>BaiduImageSearch</strong>: Baidu image search</dt>
	<dd>
		When an image message posted, send the image to baidu image search, return the guess information and possible image sources.
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-baidu-image-search-50%25.png'/>
	</dd>
	<dt>✳ <strong>BaiduVoice</strong>: Baidu Automatic Speech Recognition (ASR)</dt>
	<dd>
		When an audio or short video message is posted, send the audio to baidu ASR api, and return the text result. -- Because you can only use text keywords to search chat history, so it's very useful.
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-baidu-voice-50%25.png'/>
	</dd>
	<dt>✳ <strong>BaiduTranslate</strong>: Baidu translate</dt>
	<dd>
		Provide translation service via Baidu translate API.
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-baidu-translate-50%25.png'/>
	</dd>
	<dt>✳ <strong>GoogleImageSearch</strong>: Google Image Search</dt>
	<dd>
		When an image message posted, send the image to Google image search, return the guess information and possible image sources.
	</dd>
	<dt>✳ <strong>Emoji</strong>: Emoji bot</dt>
	<dd>
		Get emoji characters from database according keywords. <em>Due to different OS implemented their own support of different Unicode standard version, some emoji characters may not be displayed well.</em>
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-emoji-50%25.png'/>
	</dd>
	<dt><strong>MissileLaunched</strong>: Send a 'missile launched' message when someone shared a geographic position (just for fun)</dt>
	<dd>
		When someone send a geographic position message, read the longtitude and latitude value, and append a random customable joke.
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-missile-launched-50%25.png'/>
	</dd>
	<dt><strong>Repeater</strong>: A bot which repeat sender's message</dt>
	<dd>
		Repeat the message sent by sender. This bot is only for demonstration or test purpose, running for long term is not recommended.
	</dd>
	<dt><strong>SayHi</strong>: A bot which say Hi and Goodbye</dt>
	<dd>
		Send a Hi and Goodbye message when logged in or logged out. The Hi message and Goodbye message is configurable.
	</dd>
</dl>

#### Industrial Bots ####
<dl>
	<dt><strong>HCICloudCSR</strong>: HCICloud customer service representative chat bot</dt>
	<dd>
		Using the HTTP protocol from HCICloud CSR, fetch the answer from CSR bot, then reply to user.
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-hcicloud-csr-50%25.png'/>
	</dd>
	<dt><strong>iConTek</strong>: iConTek NLP (Natual Language Processing) customer service chat bot</dt>
	<dd>
		Using the HTTP protocol of iConTek, fetch the answer from iConTek NLP engine, then reply to user.
		<br/>
		<img src='https://github.com/moontide/WeChatBotEngine/raw/master/doc/img/bot-iConTek-50%25.png'/>
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

# How to create your own bot #
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
- `OnRequestToMakeFriendMessageReceived` Triggered when a RequestToMakeFriend message received.
- `OnContactChanged` Triggered when a contact was changed.
- `OnContactDeleted` Triggered when a contact was deleted.
- `OnRoomMemberChanged` Triggered when a room member changed.

# Why your class name named like this: `net_maclife_wechat_http_Bot` #
I don't like the layout of how Java organize the sources files and classes files -- directories in directories, so I replace all `.` (dot) in canonical/full class names to `_` (underline). This will avoid the Java style layout, it's simple & effective, you don't need to go deep in directories to get a file.
