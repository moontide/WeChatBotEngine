import java.io.*;
import java.security.*;
import java.security.cert.*;

import org.apache.commons.lang3.*;

import com.fasterxml.jackson.core.*;

public class net_maclife_wechat_http_Bot_Repeater extends net_maclife_wechat_http_Bot
{
	boolean bRepeatMyOwnMessage = net_maclife_wechat_http_BotApp.ParseBoolean (net_maclife_wechat_http_BotApp.config.getString ("bot.repeater.repeatMyOwnMessage", "no"), false);

	@Override
	public int OnTextMessageReceived (String sFrom_RoomAccountHash, String sFrom_RoomNickName, String sFrom_AccountHash, String sFrom_NickName, String sTo_AccountHash, String sTo_NickName, String sMessage)
	{
		if (!bRepeatMyOwnMessage && engine.IsMe (sFrom_AccountHash))
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

		try
		{
			engine.SendTextMessage (sFrom_RoomAccountHash, sFrom_AccountHash, sFrom_NickName, sMessage);
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
