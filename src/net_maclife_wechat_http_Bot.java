import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.concurrent.*;

import org.apache.commons.lang3.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import nu.xom.*;

/**
 * 该类是所有其他机器人小程序的基类。
 * @author liuyan
 *
 */
public abstract class net_maclife_wechat_http_Bot
{
	public static final String OFFICIAL_BOT_CLASSNAME_PREFIX = "net_maclife_wechat_http_Bot_";
	protected net_maclife_wechat_http_BotEngine engine;

	protected Future<?> botTask = null;

	private String name = null;

	public net_maclife_wechat_http_Bot ()
	{
		String sBotName = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot." + this.getClass ().getName () + ".name");
		String sClassName = this.getClass ().getName ();
		if (StringUtils.startsWithIgnoreCase (sClassName, OFFICIAL_BOT_CLASSNAME_PREFIX))
			sClassName = StringUtils.substring (sClassName, OFFICIAL_BOT_CLASSNAME_PREFIX.length ());
		SetName (StringUtils.isEmpty (sBotName) ? sClassName : sBotName);
	}

	public void SetEngine (net_maclife_wechat_http_BotEngine engine)
	{
		this.engine = engine;
	}
	public net_maclife_wechat_http_BotEngine GetEngine ()
	{
		return engine;
	}

	protected void SetName (String sName)
	{
		name = sName;
	}
	public String GetName ()
	{
		return name;
	}

	/**
		对 engine.BotSendTextMessage 的封装，免得每次都传递 Bot 参数
	*/
	public void SendTextMessage (String sToAccount, String sToName, String sToAccount_RoomMember, String sToName_RoomMember, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		if (engine != null)
			engine.BotSendTextMessage (this, sToAccount, sToName, sToAccount_RoomMember, sToName_RoomMember, sMessage);
	}
	public void SendTextMessage (String sToAccount, String sToName, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		if (engine != null)
			engine.BotSendTextMessage (this, sToAccount, sToName, sMessage);
	}
	public void SendTextMessage (String sToAccount, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		if (engine != null)
			engine.BotSendTextMessage (this, sToAccount, sMessage);
	}

	/**
	 * 启动机器人。假设机器人的实现是需要启动新线程的，则，需要用 Start 来启动该线程 (Start() 是由 Engine 来调用的)
	 */
	public void Start ()
	{

	}

	/**
	 * 停止机器人。假设机器人的实现是需要启动新线程的，则，需要用 Stop 来结束该线程
	 */
	public void Stop ()
	{
		if (botTask != null && !botTask.isCancelled ())
		{
			botTask.cancel (true);
		}
	}
	////////////////////////////////
	// 登录事件
	////////////////////////////////
	public int OnLoggedIn ()
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// 登出事件
	////////////////////////////////
	public int OnLoggedOut ()
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// 登出事件
	////////////////////////////////
	public int OnShutdown ()
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// “消息包收到”事件总入口
	////////////////////////////////
	public int OnMessagePackageReceived (JsonNode jsonMessagePackage)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// AddMsgList 节点处理
	////////////////////////////////
	/**
	 * 当收到了文本消息时……
	 * @param jsonMessage 原始 JSON 消息对象
	 * @param jsonFrom 发送者 JSON 对象。可能是自己（在其他设备上，比如手机上发出的。这点与 IRC 不同 -- IRC 不会收到自己发出的消息）、可能是群、可能是其他人
	 * @param sFromAccount 发送者帐号/ID
	 * @param sFromName 发送者姓名
	 * @param isFromMe 发送者是否是自己
	 * @param jsonTo 接收者 JSON 对象。可能是自己、可能是群（在其他设备上，比如手机上发出的）、可能是其他人（在其他设备上，比如手机上发出的）
	 * @param sToAccount 接收者帐号/ID
	 * @param sToName 接收者姓名
	 * @param isToMe 接收者是否是自己
	 * @param jsonReplyTo 回复到 JSON 对象。如果是自己发出的，则回复到接收者；不是自己发出的，则回复到发送者。
	 * @param sReplyToAccount
	 * @param sReplyToName
	 * @param isReplyToRoom 是否回复到聊天室/对方是否是聊天室
	 * @param jsonReplyTo_RoomMember 回复到群成员。仅仅对方是群时，才有可能会有群成员。但即使对方是群，也有可能没有群成员，比如收到群里红包【系统消息】时。
	 * @param sReplyToAccount_RoomMember
	 * @param sReplyToName_RoomMember
	 * @param jsonReplyTo_Person
	 * @param sReplyToAccount_Person
	 * @param sReplyToName_Person
	 * @param sContent 文本内容
	 * @param bMentionedMeInRoomChat 该消息是否提到了我（仅群聊时才会设置）
	 * @param bMentionedMeFirstInRoomChat 该消息是否在消息开头提到了我，即：指名道姓对我发的消息（仅群聊时才会设置）
	 * @return
	 */
	public int OnTextMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, boolean isContentMentionedMe, boolean isContentMentionedMeFirst
		)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了地理位置消息时…… （注意，这个本质上也是一个文本消息）
	 * @param sLocation 位置
	 * @param sLongtitude 经度
	 * @param sLatitude 纬度
	 * @return
	 */
	public int OnGeoLocationMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sLocation, String sLongtitude, String sLatitude
		)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了链接分享消息时……
	 * @param xmlMsg 文本内容解析出来 XML 元素
	 * @return
	 */
	public int OnURLMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			Element xmlMsg
		)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了图片消息时……
	 * @param sContent 文本内容
	 * @param fMedia 已经下载下来的图片文件，有可能是 null
	 * @param sImageURL 图片消息自身并没有提供图片 URL 地址 (<code>null</code>)，但是表情图消息会提供，表情图的处理可能是简单的调用图片消息处理接口
	 * @return
	 */
	public int OnImageMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, File fMedia, String sImageURL
		)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了语音消息时……
	 * @param sContent 文本内容
	 * @param fMedia 已经下载下来的音频文件（目前发现个人版微信只有 mp3 格式，但微信公众号里收到的是 amr 格式），不会是 null
	 * @return
	 */
	public int OnVoiceMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, File fMedia
		)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了视频消息时……
	 * @param sContent 文本内容
	 * @param fMedia 已经下载下来的视频文件，不会是 null
	 * @return
	 */
	public int OnVideoMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, File fMedia
		)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了表情图消息时……
	 * @param sContent 文本内容
	 * @param fMedia 已经下载下来的表情图片文件，有可能是 null （别人发的表情图，在自己这里没有，就会是 null）
	 * @param sImageURL 图片网址
	 * @return
	 */
	public int OnEmotionMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, File fMedia, String sImageURL
		)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了“打开了聊天窗口”消息时……
	 * @param sContent 文本内容（xml 格式的）
	 * @param sTargetAccount 聊天窗口对方（个体或者群）的没加密的帐号
	 * @return
	 */
	public int OnChatWindowOpenedMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, String sTargetAccount
		)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了名片消息时……
	 * @param sContent 文本内容（xml 格式的）
	 * @param jsonRecommenedInfo jsonMessage 里面的 RecommenedInfo 节点
	 * @param xmlMsg sContent 解析为 xml 后的 <code>msg</code> Element
	 * @return
	 */
	public int OnVCardMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, JsonNode jsonRecommenedInfo, Element xmlMsg
		)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了请求加好友消息时……
	 * @param sContent 文本内容（xml 格式的）
	 * @param jsonRecommenedInfo jsonMessage 里面的 RecommenedInfo 节点
	 * @param xmlMsg sContent 解析为 xml 后的 <code>msg</code> Element
	 * @return
	 */
	public int OnRequestToAddFriendMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, JsonNode jsonRecommenedInfo, Element xmlMsg
		)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到系统消息时……
	 * @param sContent 文本内容
	 * @return
	 */
	public int OnSystemMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent
		)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到“消息被撤回”消息时……
	 * @param sContent 文本内容（xml 格式的）
	 * @param sRevokedMsgID 被撤回的原消息 ID
	 * @param sReplacedByMsg 替换成的消息
	 * @return
	 */
	public int OnMessageIsRevokedMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, String sRevokedMsgID, String sReplacedByMsg
		)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// ModContactList 节点处理
	////////////////////////////////
	public int OnContactChanged (JsonNode jsonNode)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// DelContactList 节点处理
	////////////////////////////////
	public int OnContactDeleted (JsonNode jsonNode)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// ModChatRoomMemberList 节点处理
	// 群聊成员变动
	////////////////////////////////
	public int OnRoomMemberChanged (JsonNode jsonNode)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

}
