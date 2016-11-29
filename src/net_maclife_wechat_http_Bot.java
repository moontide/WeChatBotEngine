import java.io.*;

import com.fasterxml.jackson.databind.*;

/**
 * 该类是所有其他机器人小程序的基类。
 * @author liuyan
 *
 */
public abstract class net_maclife_wechat_http_Bot
{
	protected net_maclife_wechat_http_BotApp.BotEngine engine;

	public void SetEngine (net_maclife_wechat_http_BotApp.BotEngine engine)
	{
		this.engine = engine;
	}
	public net_maclife_wechat_http_BotApp.BotEngine GetEngine ()
	{
		return engine;
	}

	////////////////////////////////
	// 登录事件
	////////////////////////////////
	public int OnLoggedIn ()
	{
		return net_maclife_wechat_http_BotApp.BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// 登出事件
	////////////////////////////////
	public int OnLoggedOut ()
	{
		return net_maclife_wechat_http_BotApp.BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// 登出事件
	////////////////////////////////
	public int OnShutdown ()
	{
		return net_maclife_wechat_http_BotApp.BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// “消息收到”事件总入口
	////////////////////////////////
	public int OnMessageReceived (String sFrom_RoomAccountHash, String sFrom_RoomNickName, String sFrom_AccountHash, String sFrom_NickName, String sTo_AccountHash, String sTo_NickName, JsonNode jsonMessage)
	{
		return net_maclife_wechat_http_BotApp.BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// AddMsgList 节点处理
	////////////////////////////////
	/**
	 *
	 * @param sMessage
	 */
	public int OnTextMessageReceived (String sFrom_RoomAccountHash, String sFrom_RoomNickName, String sFrom_AccountHash, String sFrom_NickName, String sTo_AccountHash, String sTo_NickName, String sMessage)
	{
		return net_maclife_wechat_http_BotApp.BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}
	public int OnImageMessageReceived (String sFrom_RoomAccountHash, String sFrom_RoomNickName, String sFrom_AccountHash, String sFrom_NickName, String sTo_AccountHash, String sTo_NickName, File fMedia)
	{
		return net_maclife_wechat_http_BotApp.BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}
	public int OnVoiceMessageReceived (String sFrom_RoomAccountHash, String sFrom_RoomNickName, String sFrom_AccountHash, String sFrom_NickName, String sTo_AccountHash, String sTo_NickName, File fMedia)
	{
		return net_maclife_wechat_http_BotApp.BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}
	public int OnVideoMessageReceived (String sFrom_RoomAccountHash, String sFrom_RoomNickName, String sFrom_AccountHash, String sFrom_NickName, String sTo_AccountHash, String sTo_NickName, File fMedia)
	{
		return net_maclife_wechat_http_BotApp.BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}
	public int OnEmotionMessageReceived (String sFrom_RoomAccountHash, String sFrom_RoomNickName, String sFrom_AccountHash, String sFrom_NickName, String sTo_AccountHash, String sTo_NickName, File fMedia)
	{
		return net_maclife_wechat_http_BotApp.BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// ModContactList 节点处理
	////////////////////////////////
	public int OnContactChanged (JsonNode jsonMessage)
	{
		return net_maclife_wechat_http_BotApp.BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// DelContactList 节点处理
	////////////////////////////////
	public int OnContactDeleted (JsonNode jsonMessage)
	{
		return net_maclife_wechat_http_BotApp.BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	////////////////////////////////
	// ModChatRoomMemberList 节点处理
	// 群聊成员变动
	////////////////////////////////
	public int OnChatRoomMemberChanged (JsonNode jsonMessage)
	{
		return net_maclife_wechat_http_BotApp.BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

}
