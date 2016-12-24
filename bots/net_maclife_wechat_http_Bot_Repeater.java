import java.io.*;
import java.security.*;
import java.security.cert.*;

import org.apache.commons.lang3.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

public class net_maclife_wechat_http_Bot_Repeater extends net_maclife_wechat_http_Bot
{
	@Override
	public int OnTextMessageReceived
		(
			JsonNode jsonFrom, String sFromAccount, String sFromName,
			JsonNode jsonFrom_RoomMember, String sFromAccount_RoomMember, String sFromName_RoomMember,
			JsonNode jsonFrom_Person, String sFromAccount_Person, String sFromName_Person,
			JsonNode jsonTo, String sToAccount, String sToName,
			JsonNode jsonMessage, String sMessage,
			boolean bMentionedMeInRoomChat, boolean bMentionedMeFirstInRoomChat
		)
	{
		boolean bRepeatMyOwnMessage = net_maclife_wechat_http_BotApp.ParseBoolean (net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.repeater.repeat-my-own-message", "no"), false);
		if (!bRepeatMyOwnMessage && engine.IsMe (sFromAccount))
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

		try
		{
			SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, sMessage);
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
