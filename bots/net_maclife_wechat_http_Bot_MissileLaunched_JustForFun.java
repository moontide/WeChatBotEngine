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
	public int OnGeoLocationMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_NickName, String sTo_EncryptedAccount, String sTo_NickName, JsonNode jsonMessage, String sLocation, String sLongtitude, String sLatitude)
	{
		try
		{
			String sReply = null;
			List<String> listReplies = net_maclife_wechat_http_BotApp.config.getList (String.class, "bot.missilelaunched.replies");
			if (listReplies!=null && listReplies.size ()>0)
			{
				int iRandom = net_maclife_wechat_http_BotApp.random.nextInt (listReplies.size ());
				sReply = listReplies.get (iRandom);
			}

			SendTextMessage (sFrom_EncryptedRoomAccount, sFrom_EncryptedAccount, sFrom_NickName, "经度: " + sLongtitude + "\n纬度: " + sLatitude + "\n位置: " + sLocation + (StringUtils.isEmpty (sReply) ? "" : "\n" + sReply));
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
