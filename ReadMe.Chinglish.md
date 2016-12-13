<div style='text-align:right;'><a href='ReadMe.中文.md'>中文</a> | <span>Chinglish</span></div>

----

*WeChat does not officially publish the HTTP protocol of WeChat Web Edition, so WeChat Web Edition could be stopped like QQ Web Edition, running this BotEngine for business is not recommended.*

#About#

WeChatBotEngine is a bot engine/framework based on HTTP protocol of WeChat Web Edition.
WeChatBotEngine deals the communication with WeChat server itself, developers can develop bot applet based on that, so developers can build new bots to expand the power of WeChatBotEngine。

## Official bot applets shipped with the engine ##
WeChatBotEngine project ships with several official bot applets, to demonstrate how to build a bot applet or you can just to run it.
Those bot applets are:

<dl>
	<dt><strong>SayHi</strong>: A bot which say Hi and Goodbye</dt>
	<dd>Send a Hi and Goodbye message when logged in or logged out. The Hi message and Goodbye message is configurable.</dd>

	<dt><strong>Repeater</strong>: A bot which repeat sender's message</dt>
	<dd>Repeat the message sent by sender. This bot is only for demonstration or test purpose, running for long term is not recommended.</dd>

	<dt><strong>Relay</strong>: A bot which relay messages from outside to a WeChat friend in your contacts</dt>
	<dd>Receive message from outside via Socket (TCP/IP), and send it to a friend (person or room) in your WeChat contacts according the target given in the message. Other applications can send message to WeChat via this relay bot, hence expanded the power of WeChatBotEngine, such as:
		<br/>
		<ul>
			<li>When download task of Transmission completed, a script will run (configurable in Transmission)，the script send message to WeChat via this relay bot, hence Download-Completed-Notification function is implemented</li>
			<li>Using crontab to implement a Hourly-Time-Announcement function. (Of course you can write a bot applet to do it)</li>
			<li>Fetch information from a web page periodically, and send it to WeChat, to implement Scheduled-Information/Message-Push function.</li>
			<li>You must have good ideas, please write it here ...</li>
			<li>...</li>
		</ul>
	</dd>

	<dt><strong>HCICloudCSR</strong>: HCICloud customer service representative chat bot</dt>
	<dd>Using the HTTP protocol from HCICloud CSR, fetch the answer from CSR bot, then reply to user.</dd>

	<dt><strong>BaiduImageSearch</strong>: Baidu image search</dt>
	<dd>When an image message posted, send the image to baidu image search, return the guess information and possible image sources.</dd>

	<dt><strong>BaiduVoice</strong>: Baidi Automatic Speech Recognition (ASR) and Text To Speech (TTS)</dt>
	<dd>When an audio or short video message is posted, send the audio to baidu ASR api, and return the text result. -- Because you can only use text when searching chat history, so it's very useful.</dd>

	<dt><strong>BaiduTranslate</strong>: Baid translate</dt>
	<dd>Provide translation service via baidu translate API.</dd>

	<dt><strong>GoogleImageSearch</strong>: Google Image Search</dt>
	<dd>When an image message posted, send the image to Google image search, return the guess information and possible image sources.</dd>

	<dt><strong>MissileLaunched</strong>: Send a 'missile launched' message when someone shared a geographic position (just for fun)</dt>
	<dd>When someone send a geographic position message, read the longtitude and latitude value, and append a random customable joke</dd>
</dl>

#How to build a Bot Applet by myself#
Very easy, just extends/inherit `net_maclife_wechat_http_Bot` class, implements any (or zero) interface you wantted.
Currently, the following interfaces/event are planned:

- `OnLoggedIn`
- `OnLoggedOut`
- `OnShutdown`
- `OnMessageReceived` Triggered when a message received. This is the entrance of all the following *`Message` event. If you want to analyze or modify the message by yourself, you can do it here.
- `OnTextMessageReceived` Triggered when a Text message received.
- `OnImageMessageReceived` Triggered when an Image message received.
- `OnVoiceMessageReceived` Triggered when a Voice message received.
- `OnVideoMessageReceived` Triggered when a Video message received.
- `OnEmotionMessageReceived` Triggered when a Emotion message received.

#Why your class name like this: `net_maclife_wechat_http_Bot` #
I don't like the layout of how Java organize the sources files and classes files -- directories in directories, so I replace all `.` (dot) in canonical/full class names to `_` (underline). This will avoid the Java style layout, it's simple & effective, you don't need to go deep in directories to get a file.
