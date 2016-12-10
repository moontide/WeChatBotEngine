import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;

import org.apache.commons.codec.digest.*;
import org.apache.commons.io.*;
import org.apache.commons.lang3.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

/**
 * 百度翻译机器人小程序。
 * @author liuyan
 *
 */
public class net_maclife_wechat_http_Bot_BaiduTranslate extends net_maclife_wechat_http_Bot
{
	public static final String BAIDU_TRANSLATE_HTTP_URL  = "http://api.fanyi.baidu.com/api/trans/vip/translate";
	public static final String BAIDU_TRANSLATE_HTTPS_URL = "https://fanyi-api.baidu.com/api/trans/vip/translate";

	@Override
	public int OnTextMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFromName, String sTo_EncryptedAccount, String sTo_NickName, JsonNode jsonMessage, String sMessage, boolean bMentionedMeInRoomChat, boolean bMentionedMeFirstInRoomChat)
	{
		List<String> listCommands = net_maclife_wechat_http_BotApp.config.getList (String.class, "bot.baidu.translate.commands");
		if (listCommands==null || listCommands.isEmpty ())	// 如果未配置命令，则不处理
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

		String sFromLanguage = net_maclife_wechat_http_BotApp.config.getString ("bot.baidu.translate.from-language");
		String sToLanguage = net_maclife_wechat_http_BotApp.config.getString ("bot.baidu.translate.to-language");
		if (StringUtils.equalsIgnoreCase (sToLanguage, "auto"))
		{
net_maclife_wechat_http_BotApp.logger.warning ("百度翻译机器设置的目标语言不能为 auto");
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
		}

		try
		{
			String[] arrayMessages = sMessage.split (" +", 2);
			if (arrayMessages==null || arrayMessages.length<2)
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

			for (int i=0; i<listCommands.size (); i++)
			{
				String sCommand = listCommands.get (i);
				if (StringUtils.startsWithIgnoreCase (arrayMessages[0], sCommand))
				{
					//sMessage = StringUtils.substring (sMessage, sCommand.length ());
					sMessage = StringUtils.trimToEmpty (arrayMessages[1]);
					if (StringUtils.isEmpty (sMessage))
					{
						SendTextMessage (sFrom_EncryptedRoomAccount, sFrom_EncryptedAccount, sFromName, "百度翻译 需要指定要翻译的内容");
					}
					else
					{
						String sTranslation = null;
						JsonNode jsonResult = GetTranslation (sMessage, sFromLanguage, sToLanguage);
						if (jsonResult == null)
							return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
						JsonNode jsonErrorCode = jsonResult.get ("error_code");
						if (jsonErrorCode != null && !jsonErrorCode.isNull ())
						{
net_maclife_wechat_http_BotApp.logger.warning (GetName() + " 返回错误结果: " + net_maclife_wechat_http_BotApp.GetJSONText (jsonResult, "error_code") + ": " + net_maclife_wechat_http_BotApp.GetJSONText (jsonResult, "error_msg"));
							return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
						}
						JsonNode jsonTransResults = jsonResult.get ("trans_result");
						if (jsonTransResults.size () == 1)
						{
							JsonNode jsonTransResult = jsonTransResults.get (0);
							sTranslation = net_maclife_wechat_http_BotApp.GetJSONText (jsonTransResult, "dst");
							SendTextMessage (sFrom_EncryptedRoomAccount, sFrom_EncryptedAccount, sFromName, sTranslation);
						}
						else
						{
							StringBuilder sb = new StringBuilder ();
							for (int j=0; j<jsonTransResults.size (); j++)
							{
								JsonNode jsonTransResult = jsonTransResults.get (j);
								//net_maclife_wechat_http_BotApp.GetJSONText (jsonTransResult, "src");
								sb.append (j+1);
								sb.append (". ");
								sb.append (net_maclife_wechat_http_BotApp.GetJSONText (jsonTransResult, "dst"));
								sb.append ("\n");
							}
							SendTextMessage (sFrom_EncryptedRoomAccount, sFrom_EncryptedAccount, sFromName, sTranslation);
						}
					}
					break;
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}

		return
			  net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
			| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	public static JsonNode GetTranslation (String sSource, String sFromLanguage, String sToLanguage)
	{
		String sAppID = net_maclife_wechat_http_BotApp.config.getString ("bot.baidu.translate.app.id");
		String sAppKey = net_maclife_wechat_http_BotApp.config.getString ("bot.baidu.translate.app.key");
		int iSalt随机数佐料 = net_maclife_wechat_http_BotApp.random.nextInt ();
		String sSign = DigestUtils.md5Hex (sAppID + sSource + iSalt随机数佐料 + sAppKey);
		try
		{
			String sQueryString = "q=" + URLEncoder.encode (sSource, net_maclife_wechat_http_BotApp.utf8) + "&from=" + sFromLanguage + "&to=" + sToLanguage + "&appid=" + sAppID + "&salt=" + iSalt随机数佐料 + "&sign=" + sSign;
			byte[] arrayPostData = sQueryString.getBytes (net_maclife_wechat_http_BotApp.utf8);
			String sJSONString = net_maclife_util_HTTPUtils.CURL_Post (BAIDU_TRANSLATE_HTTPS_URL, arrayPostData);

			JsonNode jsonResult = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (sJSONString);
			return jsonResult;
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}

	public static void main (String[] args)
	{
		if (args.length < 1)
		{
			net_maclife_wechat_http_BotApp.logger.severe ("参数 1 需要指定翻译的内容，参数 2 指定源语言代码（默认为 auto），参数 3 指定目的语言代码（默认为 zh）");
			return;
		}
		String sQuery = args[0];
		String sFrom = "auto";
		String sTo = "zh";
		if (args.length >= 2 && StringUtils.isNotEmpty (args[1]))
			sFrom = args[1];
		if (args.length >= 3 && StringUtils.isNotEmpty (args[2]))
			sTo = args[2];
		//net_maclife_wechat_http_Bot bot = new net_maclife_wechat_http_Bot_BaiduTranslate ();

		JsonNode jsonResult = GetTranslation (sQuery, sFrom, sTo);
		if (! jsonResult.get ("error_code").isNull ())
		{
System.err.println (net_maclife_wechat_http_BotApp.GetJSONText (jsonResult, "error_code") + ": " + net_maclife_wechat_http_BotApp.GetJSONText (jsonResult, "error_msg"));
			return;
		}
		JsonNode jsonTransResults = jsonResult.get ("trans_result");
		for (int i=0; i<jsonTransResults.size (); i++)
		{
			JsonNode jsonTransResult = jsonTransResults.get (i);
System.out.println (net_maclife_wechat_http_BotApp.GetJSONText (jsonTransResult, "src"));
System.out.println (net_maclife_wechat_http_BotApp.GetJSONText (jsonTransResult, "dst"));
		}
	}
}
