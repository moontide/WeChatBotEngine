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
		String sBotName = net_maclife_wechat_http_BotApp.config.getString ("bot." + this.getClass ().getName () + ".name");
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
	public void SendTextMessage (String sTo_EncryptedRoomAccount, String sTo_EncryptedAccount, String sTo_Name, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		engine.BotSendTextMessage (this, sTo_EncryptedRoomAccount, sTo_EncryptedAccount, sTo_Name, sMessage);
	}
	public void SendTextMessage (String sTo_EncryptedAccount, String sTo_Name, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		engine.BotSendTextMessage (this, sTo_EncryptedAccount, sTo_Name, sMessage);
	}
	public void SendTextMessage (String sTo_EncryptedAccount, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		engine.BotSendTextMessage (this, sTo_EncryptedAccount, sMessage);
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
	// “消息收到”事件总入口
	////////////////////////////////
	public int OnMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_Name, String sTo_EncryptedAccount, String sTo_Name, JsonNode jsonMessages)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// AddMsgList 节点处理
	////////////////////////////////
	/**
	 * 当收到了文本消息时……
	 * @param sFrom_EncryptedRoomAccount
	 * @param sFrom_RoomNickName
	 * @param sFrom_EncryptedAccount
	 * @param sFrom_Name
	 * @param sTo_EncryptedAccount
	 * @param sTo_Name
	 * @param jsonMessage 原始 json 消息
	 * @param sMessage 文本内容
	 * @param bMentionedMeInRoomChat 该消息是否提到了我（仅群聊时才会设置）
	 * @param bMentionedMeFirstInRoomChat 该消息是否在消息开头提到了我，即：指名道姓对我发的消息（仅群聊时才会设置）
	 * @return
	 */
	public int OnTextMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_Name, String sTo_EncryptedAccount, String sTo_Name, JsonNode jsonMessage, String sMessage, boolean bMentionedMeInRoomChat, boolean bMentionedMeFirstInRoomChat)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了地理位置消息时…… （注意，这个本质上也是一个文本消息）
	 * @param sFrom_EncryptedRoomAccount
	 * @param sFrom_RoomNickName
	 * @param sFrom_EncryptedAccount
	 * @param sFrom_Name
	 * @param sTo_EncryptedAccount
	 * @param sTo_Name
	 * @param jsonMessage 原始 json 消息
	 * @param sLocation 位置
	 * @param sLongtitude 经度
	 * @param sLatitude 纬度
	 * @return
	 */
	public int OnGeoLocationMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_Name, String sTo_EncryptedAccount, String sTo_Name, JsonNode jsonMessage, String sLocation, String sLongtitude, String sLatitude)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了链接分享消息时……
	 * @param sFrom_EncryptedRoomAccount
	 * @param sFrom_RoomNickName
	 * @param sFrom_EncryptedAccount
	 * @param sFrom_Name
	 * @param sTo_EncryptedAccount
	 * @param sTo_Name
	 * @param jsonMessage 原始 json 消息
	 * @param sLocation 位置
	 * @param sLongtitude 经度
	 * @param sLatitude 纬度
	 * @return
	 */
	public int OnURLMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_Name, String sTo_EncryptedAccount, String sTo_Name, JsonNode jsonMessage, Element xmlMsg)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了图片消息时……
	 * @param sFrom_EncryptedRoomAccount
	 * @param sFrom_RoomNickName
	 * @param sFrom_EncryptedAccount
	 * @param sFrom_Name
	 * @param sTo_EncryptedAccount
	 * @param sTo_Name
	 * @param jsonMessage 原始 JsonNode 信息
	 * @param sContent 文本内容
	 * @param fMedia 已经下载下来的图片文件，不会是 null
	 * @param sImageURL 图片消息自身并没有提供图片 URL 地址 (<code>null</code>)，但是表情图消息会提供，表情图的处理可能是简单的调用图片消息处理接口
	 * @return
	 */
	public int OnImageMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_Name, String sTo_EncryptedAccount, String sTo_Name, JsonNode jsonMessage, String sContent, File fMedia, String sImageURL)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了语音消息时……
	 * @param sFrom_EncryptedRoomAccount
	 * @param sFrom_RoomNickName
	 * @param sFrom_EncryptedAccount
	 * @param sFrom_Name
	 * @param sTo_EncryptedAccount
	 * @param sTo_Name
	 * @param jsonMessage 原始 JsonNode 信息
	 * @param sContent 文本内容
	 * @param fMedia 已经下载下来的音频文件（目前发现个人版微信只有 mp3 格式，但微信公众号里收到的是 amr 格式），不会是 null
	 * @return
	 */
	public int OnVoiceMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_Name, String sTo_EncryptedAccount, String sTo_Name, JsonNode jsonMessage, String sContent, File fMedia)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了视频消息时……
	 * @param sFrom_EncryptedRoomAccount
	 * @param sFrom_RoomNickName
	 * @param sFrom_EncryptedAccount
	 * @param sFrom_Name
	 * @param sTo_EncryptedAccount
	 * @param sTo_Name
	 * @param jsonMessage 原始 JsonNode 信息
	 * @param sContent 文本内容
	 * @param fMedia 已经下载下来的视频文件，不会是 null
	 * @return
	 */
	public int OnVideoMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_Name, String sTo_EncryptedAccount, String sTo_Name, JsonNode jsonMessage, String sContent, File fMedia)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了表情图消息时……
	 * @param sFrom_EncryptedRoomAccount
	 * @param sFrom_RoomNickName
	 * @param sFrom_EncryptedAccount
	 * @param sFrom_Name
	 * @param sTo_EncryptedAccount
	 * @param sTo_Name
	 * @param jsonMessage 原始 JsonNode 信息
	 * @param sContent 文本内容
	 * @param fMedia 已经下载下来的表情图片文件，不会是 null
	 * @return
	 */
	public int OnEmotionMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_Name, String sTo_EncryptedAccount, String sTo_Name, JsonNode jsonMessage, String sContent, File fMedia, String sImageURL)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了“打开了聊天窗口”消息时……
	 * @param sFrom_EncryptedRoomAccount
	 * @param sFrom_RoomNickName
	 * @param sFrom_EncryptedAccount
	 * @param sFrom_Name
	 * @param sTo_EncryptedAccount
	 * @param sTo_Name
	 * @param jsonMessage 原始 JsonNode 信息
	 * @param sContent 文本内容（xml 格式的）
	 * @param sTargetAccount 聊天窗口对方（个体或者群）的没加密的帐号
	 * @return
	 */
	public int OnChatWindowOpenedMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_Name, String sTo_EncryptedAccount, String sTo_Name, JsonNode jsonMessage, String sContent, String sTargetAccount)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 当收到了名片消息时……
	 * @param sFrom_EncryptedRoomAccount
	 * @param sFrom_RoomNickName
	 * @param sFrom_EncryptedAccount
	 * @param sFrom_Name
	 * @param sTo_EncryptedAccount
	 * @param sTo_Name
	 * @param jsonMessage 原始 JsonNode 信息
	 * @param sContent 文本内容（xml 格式的）
	 * @param jsonRecommenedInfo jsonMessage 里面的 RecommenedInfo 节点
	 * @param xmlMsg sContent 解析为 xml 后的 <code>msg</code> Element
	 * @return
	 */
	public int OnVCardMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_Name, String sTo_EncryptedAccount, String sTo_Name, JsonNode jsonMessage, String sContent, JsonNode jsonRecommenedInfo, Element xmlMsg)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// ModContactList 节点处理
	////////////////////////////////
	public int OnContactChanged (JsonNode jsonMessage)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// DelContactList 节点处理
	////////////////////////////////
	public int OnContactDeleted (JsonNode jsonMessage)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// ModChatRoomMemberList 节点处理
	// 群聊成员变动
	////////////////////////////////
	public int OnChatRoomMemberChanged (JsonNode jsonMessage)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

}
