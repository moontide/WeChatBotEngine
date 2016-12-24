import java.util.*;

import org.apache.commons.lang3.*;

import com.fasterxml.jackson.databind.*;

/**
 * 当有人发位置消息时，回复一段“导弹已就位”/“导弹已发射”的搞笑文字。
 * @author liuyan
 *
 */
public class net_maclife_wechat_http_Bot_MissileLaunched_JustForFun extends net_maclife_wechat_http_Bot
{
	@Override
	public int OnGeoLocationMessageReceived
		(
			JsonNode jsonFrom, String sFromAccount, String sFromName,
			JsonNode jsonFrom_RoomMember, String sFromAccount_RoomMember, String sFromName_RoomMember,
			JsonNode jsonFrom_Person, String sFromAccount_Person, String sFromName_Person,
			JsonNode jsonTo, String sToAccount, String sToName,
			JsonNode jsonMessage, String sLocation, String sLongtitude, String sLatitude
		)
	{
		try
		{
			String sReply = null;
			List<String> listReplies = net_maclife_wechat_http_BotApp.GetConfig ().getList (String.class, "bot.missile-launched.replies");
			if (listReplies!=null && listReplies.size ()>0)
			{
				int iRandom = net_maclife_wechat_http_BotApp.random.nextInt (listReplies.size ());
				sReply = listReplies.get (iRandom);
			}

			SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, "经度: " + sLongtitude + "\n纬度: " + sLatitude + "\n位置: " + sLocation + (StringUtils.isEmpty (sReply) ? "" : "\n" + sReply));
			return
				net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
				| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}
}
