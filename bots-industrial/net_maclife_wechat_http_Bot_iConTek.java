import java.io.*;
import java.net.*;
import java.util.*;

import org.apache.commons.lang3.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

/**
 * 基于 http 协议的 iConTek 客服机器人。
 *
 * <p>
 * 与捷通华声 HCICloudCSR （灵云智能客服）机器人类似，该机器人也是利用 iConTek 的 HTTP 接口，将文字（或者语音？）发给其引擎，返回特定的结果，完成机器人客服对话的功能。
 * </p>
 * <p>
 * iConTek 有两套引擎，一个是基于语音对话的 (Sandroid)、一个是基于纯文本的 (Tandroid)，由于微信网页版目前无法发语音消息，所以，只能使用 iConTek-Tandroid 引擎来处理。
 * </p>
 *
 * @author liuyan
 *
 */
public class net_maclife_wechat_http_Bot_iConTek extends net_maclife_wechat_http_Bot
{
	String iConTek_Tandroid_SERVER_HOST      = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.iConTek.Tandroid.server.host");
	int iConTek_Tandroid_SERVER_PORT         = net_maclife_wechat_http_BotApp.GetConfig ().getInt ("bot.iConTek.Tandroid.server.port");
	static String iConTek_Tandroid_SERVER_SCHEME    = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.iConTek.Tandroid.server.scheme");
	String iConTek_Tandroid_BaseURL__Query       = (StringUtils.equalsIgnoreCase (iConTek_Tandroid_SERVER_SCHEME, "https") ? "https" : "http") + "://" + iConTek_Tandroid_SERVER_HOST + ":" + iConTek_Tandroid_SERVER_PORT + "/queryj";

	String iConTek_CHARSET_ENCODING = net_maclife_wechat_http_BotApp.utf8;

	/**
	 * 会话列表（会话，对应于请求 Tandroid 时 URL 中的 queryid）。
	 * 由于 iConTek Tandroid 是基于知识库的问答机器人引擎，对于顺序型（Stepping）、深挖型（细化型，DrillDown）、树状型（Branching）这些问题类型，一定是一个问题接上一个问题来问答的，这样会导致一个问题：什么时候问题结束，不知道（API 不提供这样的接口？）。
	 * 所以，需要手工维护一个会话列表，用单独的命令开启新会话。系统还要做个定时任务，定时清理过期（比如：5 分钟内无问答？）
	 * <p>
	 * 	<code>Map</code> 中的 Key 说明：
	 * <dl>
	 * 	<dt>session-id<dt>
	 * 	<dd>会话 ID，一般是微信昵称加上一个数字（Timestamp？）<dd>

	 * 	<dt>a</dt>
	 * 	<dd></dd>

	 * 	<dt>b</dt>
	 * 	<dd></dd>

	 * 	<dt>last-active-time</dt>
	 * 	<dd>最后活动时间。<code>long</code> 类型。清理会话的任务将根据该</dd>

	 * </dl>
	 * </p>
	 */
	Map<String, Map<String, Object>> mapSessions = new HashMap<String, Map<String, Object>> ();

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
		List<String> listCommands = net_maclife_wechat_http_BotApp.GetConfig ().getList (String.class, "bot.iConTek.commands");
		if (listCommands==null || listCommands.isEmpty ())	// 如果未配置命令，则不处理
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

		try
		{
			if (! net_maclife_wechat_http_BotApp.hasCommandPrefix (sContent))
			{
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
			}
			sContent = net_maclife_wechat_http_BotApp.StripOutCommandPrefix (sContent);


			// 解析命令行
			String[] arrayMessages = sContent.split ("\\s+", 2);
			if (arrayMessages==null || arrayMessages.length<1)
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

			String sCommandInputed = arrayMessages[0];
			String sCommandParametersInputed = null;
			if (arrayMessages.length >= 2)
				sCommandParametersInputed = arrayMessages[1];

			String[] arrayCommandAndOptions = sCommandInputed.split ("\\" + net_maclife_wechat_http_BotApp.COMMAND_OPTION_SEPARATOR + "+", 2);
			sCommandInputed = arrayCommandAndOptions[0];
			String sCommandOptionsInputed = null;
			//String[] arrayCommandOptions = null;	// 该 Bot 只支持一个命令选项： new-session （/icontek.new-session），所以，不再尝试解析多个 options
			if (arrayCommandAndOptions.length >= 2)
			{
				sCommandOptionsInputed = arrayCommandAndOptions[1];
				//arrayCommandOptions = sCommandOptionsInputed.split ("\\" + net_maclife_wechat_http_BotApp.COMMAND_OPTION_SEPARATOR + "+");
			}

			// 检查命令有效性
			boolean bValidCommand = false;
			for (int i=0; i<listCommands.size (); i++)
			{
				String sCommand = listCommands.get (i);
				if (StringUtils.equalsIgnoreCase (sCommandInputed, sCommand))
				{
					bValidCommand = true;
					break;
				}
			}
			if (! bValidCommand)
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

			// 执行
			if (StringUtils.equalsIgnoreCase (sCommandOptionsInputed, "new-session"))
			{
				NewSession (sReplyToAccount_Person);
			}
			Map<String, Object> mapSession = GetSession (sReplyToAccount_Person);	// 根据说话“人”的帐号，获取会话
			JsonNode jsonTandroidResponse = GetTandroidReponse (mapSession, sCommandParametersInputed);
			if (jsonTandroidResponse == null)
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

			String sResponse = ParseTandroidResponse (mapSession, jsonTandroidResponse);
			if (StringUtils.isNotEmpty (sResponse))
			{
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, sResponse);
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

	Map<String, Object> GetSession (String sFromAccount)
	{
		Map<String, Object> mapSession = mapSessions.get (sFromAccount);
		if (mapSession != null)
			return mapSession;
		else
			return NewSession (sFromAccount);
	}

	Map<String, Object> NewSession (String sFromAccount)
	{
		Map<String, Object> mapSession = new HashMap<String, Object> ();
		mapSession.put ("last-active-time", System.currentTimeMillis ());
		mapSession.put ("account", sFromAccount);
		mapSession.put ("session-id", sFromAccount + "-" + mapSession.get ("last-active-time"));
		mapSessions.put (sFromAccount, mapSession);
		return mapSession;
	}

	public JsonNode GetTandroidReponse (Map<String, Object> mapSession, String sInput)
	{
		try
		{
			String sURL = iConTek_Tandroid_BaseURL__Query + "/x/" + (mapSession.get ("follow-up-question-id")==null ? "" : URLEncoder.encode ((String)mapSession.get ("follow-up-question-id"), iConTek_CHARSET_ENCODING) + "/") + mapSession.get ("session-id") + "/" + URLEncoder.encode (sInput, iConTek_CHARSET_ENCODING);
net_maclife_wechat_http_BotApp.logger.finer (GetName() + " 请求的网址: " + sURL);
			Document doc = Jsoup.connect (sURL)
				.ignoreContentType (true)
				.validateTLSCertificates (false)
				//.header ("Content-Type", "application/json")
				//.header (name, value)
				.get ();

			String sResult = doc.text ();
//net_maclife_wechat_http_BotApp.logger.finer (GetName() + " 获取到的消息:\n" + sResult);
			JsonNode jsonCSRResponse = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (sResult);
			return jsonCSRResponse;
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
		return null;
	}

	public String ParseTandroidResponse (Map<String, Object> mapSession, JsonNode jsonTandroidResponse)
	{
net_maclife_wechat_http_BotApp.logger.finer ("\n" + jsonTandroidResponse);
		if (jsonTandroidResponse == null || jsonTandroidResponse.isNull())
			return "";

		String sResponseID = net_maclife_wechat_http_BotApp.GetJSONText (jsonTandroidResponse, "responseId");

		// 如果是响应「标准型」、「枚举型」的「分类」, answer 会是目标知识点内的答案, 但如果找其他上下文功能的「分类」, 会有以下特别的输出:
		//  「顺序型问卷」,「树状型问卷」及「细化型」分类, answer 会输出 followUp 所指定的「分类」中特殊知识点内的 answer。
		String sAnswer     = net_maclife_wechat_http_BotApp.GetJSONText (jsonTandroidResponse, "answer");

		String sConfidence = net_maclife_wechat_http_BotApp.GetJSONText (jsonTandroidResponse, "confidence");

		// y 是知识点 ID, 亦即是 FAQID 或意图 ID。
		String sFAQID      = net_maclife_wechat_http_BotApp.GetJSONText (jsonTandroidResponse, "y");

		// followUp: 是系统认为开发者应该再访问的下一个「分类」位置, 只会出现在指定查找「顺序型问卷」、「树状型问卷」、「细化型」的「分类」才会出现, 查找「枚举型」或「标准型」的「分类」是不会出现 followUp。
		String sFollowUp   = net_maclife_wechat_http_BotApp.GetJSONText (jsonTandroidResponse, "followUp");
		if (StringUtils.isEmpty (sFollowUp))
		{
			mapSession.remove ("follow-up-question-id");
		}
		else
		{
			mapSession.put ("follow-up-question-id", sFollowUp);
		}

		String sResult = sAnswer;
		return sResult;
	}
}
