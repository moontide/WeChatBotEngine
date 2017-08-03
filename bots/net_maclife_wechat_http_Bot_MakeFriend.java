import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;

import org.apache.commons.lang3.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import nu.xom.*;

/**
 * 请求加好友、自动通过加好友请求的机器人。
 * 主要处理两个内容
 * <ul>
 * 	<li>在微信群中，其他人用 /addme 命令让本 Bot (自己的微信) 向命令发起者发起一个“请求加好友”的请求</li>
 * 	<li>如果收到别人发来的“请求加好友”的请求，则，根据“接头暗号”来决定是否自动通过该请求</li>
 * </ul>
 * @author liuyan
 *
 */
public class net_maclife_wechat_http_Bot_MakeFriend extends net_maclife_wechat_http_Bot
{
	@Override
	public int OnTextMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, boolean isContentMentionedMe, boolean isContentMentionedMeFirst
		)
	{
		List<String> listCommands = net_maclife_wechat_http_BotApp.GetConfig ().getList (String.class, "bot.make-friend.commands");
		if (listCommands==null || listCommands.isEmpty ())	// 如果未配置命令，则不处理
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

		try
		{
			String[] arrayMessages = sContent.split ("\\s+", 2);
			if (arrayMessages==null || arrayMessages.length<1)
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

			String sCommandInputed = arrayMessages[0];
			String sCommandParametersInputed = null;
			if (arrayMessages.length >= 2)
				sCommandParametersInputed = arrayMessages[1];

			if (StringUtils.isEmpty (sCommandParametersInputed))
			{
				sCommandParametersInputed = "你于 " + new java.sql.Timestamp (System.currentTimeMillis ()) + " 在【" + sReplyToName + "】群内请求加好友";
			}

			String[] arrayCommandOptions = sCommandInputed.split ("\\.+", 2);
			sCommandInputed = arrayCommandOptions[0];
			String sCommandOptionsInputed = null;
			if (arrayCommandOptions.length >= 2)
				sCommandOptionsInputed = arrayCommandOptions[1];

			for (int i=0; i<listCommands.size (); i++)
			{
				String sCommand = listCommands.get (i);
				if (! StringUtils.equalsIgnoreCase (sCommandInputed, sCommand))
					continue;

				if (! isReplyToRoom)
				{	// 不是在群里发的消息，则不处理
					try
					{
						SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "加好友命令只能在群中执行，且我们目前不是好友时才能有效执行");
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
					return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
				}

				//// 只有命令时，打印帮助信息
				//if (StringUtils.isEmpty (sCommandParametersInputed))
				//{
				//	SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, GetName() + " \n\n用法:\n" + sCommand + "[.可选的语言代码选项]  <必填的要翻译的原文>\n\n可选的语言代码选项的格式：\n  - .原文语言代码\n  - .2译文语言代码\n  - .原文语言代码2译文语言代码\n\n具体能用哪些语言代码，请参照： http://api.fanyi.baidu.com/api/trans/product/apidoc#languageList 给出的语言代码列表");
				//	return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
				//}

				// 解析“命令选项”：
				if (StringUtils.isNotEmpty (sCommandOptionsInputed))
				{
				}

				// 命令行命令格式没问题，再检查是否已经是好友了
				boolean bAlreadyBeFriend = engine.SearchForSingleContact (sReplyToAccount_RoomMember) != null;
				if (bAlreadyBeFriend)
				{
					SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "已经是好友，不需再次加好友");
					return
						  net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
						| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
				}

				// 现在向命令使用者发起“加好友请求”消息
				//int nGender = net_maclife_wechat_http_BotApp.GetJSONInt (jsonReplyTo_Person, "Sex");
				JsonNode jsonResult = engine.SendRequestToMakeFriend (sReplyToAccount_RoomMember, sCommandParametersInputed);
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "已向你【" + sReplyToName_RoomMember + "】发起“加好友请求”，携带的验证消息为：\n\n" + sCommandParametersInputed + "\n\n如果需要手工指定验证消息，请在命令后输入验证消息即可，如：\naddme " + sReplyToName_RoomMember + " 是坠棒的");
				break;
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


	@Override
	public int OnRequestToMakeFriendMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, JsonNode jsonRecommenedInfo, Element xmlMsg
		)
	{
		List<String> listAutoAccepts = net_maclife_wechat_http_BotApp.GetConfig ().getList (String.class, "bot.make-friend.auto-accepts");
		if (listAutoAccepts==null || listAutoAccepts.isEmpty ())	// 如果未配置命令，则不处理
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

		String s微信ID = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "UserName");
		//String s昵称 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "NickName");
		//String s微信号 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "Alias");
		//int n性别 = net_maclife_wechat_http_BotApp.GetJSONInt (jsonRecommenedInfo, "Sex");
		String s个性签名 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "Signature");
		//String s省 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "Province");
		//String s市 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "City");
		String s附加内容 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "Content");
		int nScene = net_maclife_wechat_http_BotApp.GetJSONInt (jsonRecommenedInfo, "Scene");	// 根据什么来请求加好友的？
		String sMakeFriendTicket = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "Ticket");
		int nOpCode = net_maclife_wechat_http_BotApp.GetJSONInt (jsonRecommenedInfo, "OpCode");	// 固定为 2 ？

		try
		{
			for (int i=0; i<listAutoAccepts.size (); i++)
			{
				boolean bMatched = false;
				String sAutoAccept1 = listAutoAccepts.get (i);
				String[] arrayAutoAccept = sAutoAccept1.split ("/", 2);
				String sKeyword = arrayAutoAccept [0];
				String sAutoReplyMessage = null;
				if (arrayAutoAccept.length > 1)
				{
					sAutoReplyMessage = arrayAutoAccept [1];
					sAutoReplyMessage = StringEscapeUtils.unescapeJava (sAutoReplyMessage);	// 允许用 \n 的方式发送多行文本消息
				}

				if (StringUtils.startsWithIgnoreCase (sKeyword, "*") && StringUtils.endsWithIgnoreCase (sKeyword, "*"))
				{
					sKeyword = StringUtils.substring (sKeyword, 1, sKeyword.length () - 1);
					bMatched = StringUtils.containsIgnoreCase (s附加内容, sKeyword);
				}
				else if (StringUtils.startsWithIgnoreCase (sKeyword, "*"))
				{
					sKeyword = StringUtils.substring (sKeyword, 1);
					bMatched = StringUtils.endsWithIgnoreCase (s附加内容, sKeyword);
				}
				else if (StringUtils.endsWithIgnoreCase (sKeyword, "*"))
				{
					sKeyword = StringUtils.substring (sKeyword, 0, sKeyword.length () - 1);
					bMatched = StringUtils.startsWithIgnoreCase (s附加内容, sKeyword);
				}
				else
				{
					bMatched = StringUtils.equalsIgnoreCase (s附加内容, sKeyword);
				}

				if (bMatched)
				{
					JsonNode jsonResult = engine.AcceptRequestToMakeFriend (sMakeFriendTicket, /*sReplyToAccount_RoomMember*/s微信ID, "暗号已对上，自动通过");
					if (StringUtils.isNotEmpty (sAutoReplyMessage))
					{
						SendTextMessage (sReplyToAccount_Person, sReplyToName_Person, sAutoReplyMessage);
					}
					break;
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}
}
