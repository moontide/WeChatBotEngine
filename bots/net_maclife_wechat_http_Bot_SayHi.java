import java.util.*;

import org.apache.commons.lang3.*;

import com.fasterxml.jackson.databind.*;

public class net_maclife_wechat_http_Bot_SayHi extends net_maclife_wechat_http_Bot
{
	String sHiMessage = net_maclife_wechat_http_BotApp.config.getString ("bot.hi.message.started");
	String sByeMessage = net_maclife_wechat_http_BotApp.config.getString ("bot.hi.message.stopped");

	List<String> listTargetAliases = null;
	List<String> listTargetRemarkNames = null;
	List<String> listTargetNickNames = null;

	public net_maclife_wechat_http_Bot_SayHi ()
	{
		listTargetAliases     = net_maclife_wechat_http_BotApp.config.getList (String.class, "bot.hi.message.target.aliases");
		listTargetRemarkNames = net_maclife_wechat_http_BotApp.config.getList (String.class, "bot.hi.message.target.RemarkNames");
		listTargetNickNames   = net_maclife_wechat_http_BotApp.config.getList (String.class, "bot.hi.message.target.NickNames");
	}

	int ProcessMessage (String sMessage)
	{
		boolean bProcessed = false;
		JsonNode jsonContact = null;
		try
		{
			String sTemp;
			for (int i=0; i<listTargetAliases.size (); i++)
			{
				sTemp = listTargetAliases.get (i);
				if (StringUtils.isEmpty (sTemp))
					continue;
				jsonContact = engine.SearchForSingleContact (null, sTemp, null, null);
				if (jsonContact == null)
					continue;
				engine.SendTextMessage (net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "UserName"), sMessage + "\n\n" + new java.sql.Timestamp(System.currentTimeMillis ()));
				bProcessed = true;
			}
			for (int i=0; i<listTargetRemarkNames.size (); i++)
			{
				sTemp = listTargetAliases.get (i);
				if (StringUtils.isEmpty (sTemp))
					continue;
				jsonContact = engine.SearchForSingleContact (null, null, listTargetRemarkNames.get (i), null);
				if (jsonContact == null)
					continue;
				engine.SendTextMessage (net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "UserName"), sMessage + "\n\n" + new java.sql.Timestamp(System.currentTimeMillis ()));
				bProcessed = true;
			}
			for (int i=0; i<listTargetNickNames.size (); i++)
			{
				sTemp = listTargetAliases.get (i);
				if (StringUtils.isEmpty (sTemp))
					continue;
				jsonContact = engine.SearchForSingleContact (null, null, null, listTargetNickNames.get (i));
				if (jsonContact == null)
					continue;
				engine.SendTextMessage (net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "UserName"), sMessage + "\n\n" + new java.sql.Timestamp(System.currentTimeMillis ()));
				bProcessed = true;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return (bProcessed ? net_maclife_wechat_http_BotApp.BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED : 0)
				| net_maclife_wechat_http_BotApp.BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	@Override
	public int OnLoggedIn ()
	{
		return ProcessMessage (sHiMessage);
	}

	@Override
	public int OnLoggedOut ()
	{
		return ProcessMessage (sByeMessage);
	}
}
