import java.net.*;
import java.util.*;

import org.apache.commons.codec.digest.*;
import org.apache.commons.lang3.*;

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

	//public static final String REGEXP_LanguageOptions = "(\\w*)(2*)(\\w*)";
	//public static final Pattern PATTERN_LanguageOptions = Pattern.compile (REGEXP_LanguageOptions);

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
		List<String> listCommands = net_maclife_wechat_http_BotApp.GetConfig ().getList (String.class, "bot.baidu-translate.commands");
		if (listCommands==null || listCommands.isEmpty ())	// 如果未配置命令，则不处理
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

		String sFromLanguage = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.baidu-translate.from-language");
		String sToLanguage = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.baidu-translate.to-language");

		try
		{
			String[] arrayMessages = sMessage.split ("\\s+", 2);
			if (arrayMessages==null || arrayMessages.length<1)
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

			String sCommandInputed = arrayMessages[0];
			String sCommandParametersInputed = null;
			if (arrayMessages.length >= 2)
				sCommandParametersInputed = arrayMessages[1];

			String[] arrayCommandOptions = sCommandInputed.split ("\\.+", 2);
			sCommandInputed = arrayCommandOptions[0];
			String sCommandOptionsInputed = null;
			if (arrayCommandOptions.length >= 2)
				sCommandOptionsInputed = arrayCommandOptions[1];

			for (int i=0; i<listCommands.size (); i++)
			{
				String sCommand = listCommands.get (i);
				if (StringUtils.equalsIgnoreCase (sCommandInputed, sCommand))
				{
					// 只有命令时，打印帮助信息
					if (StringUtils.isEmpty (sCommandParametersInputed))
					{
						SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, GetName() + " 需要指定要翻译的内容。\n\n用法:\n" + sCommand + "[.可选的语言代码选项]  <必填的要翻译的原文>\n\n可选的语言代码选项的格式：\n  - .原文语言代码\n  - .2译文语言代码\n  - .原文语言代码2译文语言代码\n\n具体能用哪些语言代码，请参照： http://api.fanyi.baidu.com/api/trans/product/apidoc#languageList 给出的语言代码列表");
						return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					}

					// 解析命令“翻译语言选项”：.src2dst、.src、.2dst
					if (StringUtils.isNotEmpty (sCommandOptionsInputed))
					{
						//Matcher matcher = PATTERN_LanguageOptions.matcher (sCommandOptionsInputed);
						//if (sCommandOptionsInputed.matches ()
						//不用规则表达式来解析了，还不如用简单的字符串格式判断
						if (StringUtils.contains (sCommandOptionsInputed, "2"))
						{
							if (sCommandOptionsInputed.startsWith ("2"))
							{	// 只指定了译文语言代码
								sToLanguage = sCommandOptionsInputed.substring (1);
							}
							else
							{
								String[] arrayFromTo = sCommandOptionsInputed.split ("2");
								sFromLanguage = arrayFromTo[0];
								sToLanguage = arrayFromTo[1];
							}
						}
						else
						{	// 只指定了原文的语言代码
							sFromLanguage = sCommandOptionsInputed;
						}
					}
					if (StringUtils.isEmpty (sFromLanguage) || StringUtils.isEmpty (sToLanguage))
					{
						String sErrorInfo = GetName() + " 的原文、译文的语言代码不能为空";
//net_maclife_wechat_http_BotApp.logger.warning (sErrorInfo);
						SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, sErrorInfo);
						return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					}
					if (StringUtils.equalsIgnoreCase (sToLanguage, "auto"))
					{
						String sErrorInfo = GetName() + "机器人设置的目标语言不能为 auto";
//net_maclife_wechat_http_BotApp.logger.warning (sErrorInfo);
						SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, sErrorInfo);
						return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					}


					// 命令行命令格式没问题，现在开始查询数据库
					String sTranslation = null;
					JsonNode jsonResult = GetTranslation (sCommandParametersInputed, sFromLanguage, sToLanguage);
					if (jsonResult == null)
						return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					JsonNode jsonErrorCode = jsonResult.get ("error_code");
					if (jsonErrorCode != null && !jsonErrorCode.isNull ())
					{
						String sErrorInfo = GetName() + " 返回错误结果: " + net_maclife_wechat_http_BotApp.GetJSONText (jsonResult, "error_code") + ": " + net_maclife_wechat_http_BotApp.GetJSONText (jsonResult, "error_msg");
//net_maclife_wechat_http_BotApp.logger.warning (sErrorInfo);
						SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, sErrorInfo);
						return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					}
					JsonNode jsonTransResults = jsonResult.get ("trans_result");
					if (jsonTransResults.size () == 1)
					{
						JsonNode jsonTransResult = jsonTransResults.get (0);
						sTranslation = net_maclife_wechat_http_BotApp.GetJSONText (jsonTransResult, "dst");
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
						sTranslation = sb.toString ();
					}
					SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, sTranslation);
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
		String sAppID = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.baidu-translate.app.id");
		String sAppKey = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.baidu-translate.app.key");
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
		String sQuery = StringEscapeUtils.unescapeJava (args[0]);
		String sFrom = "auto";
		String sTo = "zh";
		if (args.length >= 2 && StringUtils.isNotEmpty (args[1]))
			sFrom = args[1];
		if (args.length >= 3 && StringUtils.isNotEmpty (args[2]))
			sTo = args[2];
		//net_maclife_wechat_http_Bot bot = new net_maclife_wechat_http_Bot_BaiduTranslate ();

		JsonNode jsonResult = GetTranslation (sQuery, sFrom, sTo);
System.err.println (jsonResult);
		if (jsonResult.get ("error_code") != null && ! jsonResult.get ("error_code").isNull ())
		{
System.err.println (net_maclife_wechat_http_BotApp.GetJSONText (jsonResult, "error_code") + ": " + net_maclife_wechat_http_BotApp.GetJSONText (jsonResult, "error_msg"));
			return;
		}
		JsonNode jsonTransResults = jsonResult.get ("trans_result");
		for (int i=0; i<jsonTransResults.size (); i++)
		{
			JsonNode jsonTransResult = jsonTransResults.get (i);
System.out.println ((i+1) + ". " + net_maclife_wechat_http_BotApp.GetJSONText (jsonTransResult, "src"));
System.out.println (net_maclife_wechat_http_BotApp.GetJSONText (jsonTransResult, "dst"));
System.out.println ();
		}
	}
}
