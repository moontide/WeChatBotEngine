import java.io.*;
import java.security.*;
import java.security.cert.*;

import org.apache.commons.lang3.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

public class net_maclife_wechat_http_Bot_Repeater extends net_maclife_wechat_http_Bot
{
	@Override
	public int OnTextMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_NickName, String sTo_EncryptedAccount, String sTo_NickName, JsonNode jsonMessage, String sMessage, boolean bMentionedMeInRoomChat, boolean bMentionedMeFirstInRoomChat)
	{
		boolean bRepeatMyOwnMessage = net_maclife_wechat_http_BotApp.ParseBoolean (net_maclife_wechat_http_BotApp.config.getString ("bot.repeater.repeatMyOwnMessage", "no"), false);
		if (!bRepeatMyOwnMessage && engine.IsMe (sFrom_EncryptedAccount))
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

		try
		{
			SendTextMessage (sFrom_EncryptedRoomAccount, sFrom_EncryptedAccount, sFrom_NickName, sMessage);
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}

		return
			  net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
			| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}
}
