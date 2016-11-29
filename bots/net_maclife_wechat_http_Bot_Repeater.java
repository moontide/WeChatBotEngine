import java.io.*;
import java.security.*;
import java.security.cert.*;

import org.apache.commons.lang3.*;

import com.fasterxml.jackson.core.*;

public class net_maclife_wechat_http_Bot_Repeater extends net_maclife_wechat_http_Bot
{
	@Override
	public int OnTextMessageReceived (String sFrom_RoomAccountHash, String sFrom_RoomNickName, String sFrom_AccountHash, String sFrom_NickName, String sTo_AccountHash, String sTo_NickName, String sMessage)
	{
		//boolean isFromRoomOrChannel = isChatRoomAccount (sFrom_AccountHash);
		//boolean isToRoomOrChannel = isChatRoomAccount (sTo_AccountHash);
		try
		{
			engine.SendTextMessage (sFrom_RoomAccountHash, sFrom_AccountHash, sFrom_NickName, sMessage + "\n\n" + new java.sql.Timestamp(System.currentTimeMillis ()));
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}

		return
			  net_maclife_wechat_http_BotApp.BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
			| net_maclife_wechat_http_BotApp.BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}
}
