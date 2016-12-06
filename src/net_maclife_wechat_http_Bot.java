import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.concurrent.*;

import org.apache.commons.lang3.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

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

	//
	// 对 engine.BotSendTextMessage 的封装，免得每次都传递 Bot 参数
	//
	public void SendTextMessage (String sTo_EncryptedRoomAccount, String sTo_EncryptedAccount, String sTo_NickName, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		engine.BotSendTextMessage (this, sTo_EncryptedRoomAccount, sTo_EncryptedAccount, sTo_NickName, sMessage);
	}
	public void SendTextMessage (String sTo_EncryptedAccount, String sTo_NickName, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		engine.BotSendTextMessage (this, sTo_EncryptedAccount, sTo_NickName, sMessage);
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
	public int OnMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_NickName, String sTo_EncryptedAccount, String sTo_NickName, JsonNode jsonMessage)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// AddMsgList 节点处理
	////////////////////////////////
	/**
	 *
	 * @param sMessage
	 */
	public int OnTextMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_NickName, String sTo_EncryptedAccount, String sTo_NickName, String sMessage)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}
	public int OnImageMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_NickName, String sTo_EncryptedAccount, String sTo_NickName, File fMedia)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}
	public int OnVoiceMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_NickName, String sTo_EncryptedAccount, String sTo_NickName, File fMedia)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}
	public int OnVideoMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_NickName, String sTo_EncryptedAccount, String sTo_NickName, File fMedia)
	{
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}
	public int OnEmotionMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_NickName, String sTo_EncryptedAccount, String sTo_NickName, File fMedia)
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
