import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

/**
 * 基于 http 协议的捷通华声 HCICloud CSR (灵云智能客服) 对话机器人。
 *
 * @author liuyan
 *
 */
public class net_maclife_wechat_http_Bot_HCICloudCSR extends net_maclife_wechat_http_Bot
{
	String HCICLOUD_SERVER_ADDRESS   = net_maclife_wechat_http_BotApp.config.getString ("bot.hcicloud.csr.server.address");
	int HCICLOUD_SERVER_PORT         = net_maclife_wechat_http_BotApp.config.getInt ("bot.hcicloud.csr.server.port");
	String HCICLOUD_CSR_URL__Query          = "http://" + HCICLOUD_SERVER_ADDRESS + (HCICLOUD_SERVER_PORT==80 ? "" : ":" + HCICLOUD_SERVER_PORT) + "/CSRBroker/queryAction";

	String HCICLOUD_APP_KEY          = net_maclife_wechat_http_BotApp.config.getString ("bot.hcicloud.csr.app.key");

	String HCICLOUD_CSR_ROBOT_ID     = net_maclife_wechat_http_BotApp.config.getString ("bot.hcicloud.csr.robot.id");
	String HCICLOUD_CSR_ROBOT_CHANNEL_NUMBER = net_maclife_wechat_http_BotApp.config.getString ("bot.hcicloud.csr.robot.channel.number");
	String HCICLOUD_CSR_TALKER_ID    = net_maclife_wechat_http_BotApp.config.getString ("bot.hcicloud.csr.robot.talker.id");
	String HCICLOUD_CSR_RECEIVER_ID  = net_maclife_wechat_http_BotApp.config.getString ("bot.hcicloud.csr.robot.receiver.id");
	String HCICLOUD_CHARSET_ENCODING = net_maclife_wechat_http_BotApp.utf8;

	@Override
	public int OnTextMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_NickName, String sTo_EncryptedAccount, String sTo_NickName, JsonNode jsonMessage, String sMessage, Object useless)
	{
		try
		{
			JsonNode jsonCSRResponse = GetCSRReponse (sFrom_EncryptedAccount, sMessage);
			if (jsonCSRResponse == null)
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

			String sResponse = ParseCSRResponse (jsonCSRResponse);
			if (StringUtils.isNotEmpty (sResponse))
			{
				SendTextMessage (sFrom_EncryptedRoomAccount, sFrom_EncryptedAccount, sFrom_NickName, sResponse);
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

	public String ParseCSRResponse (JsonNode jsonCSRResponse)
	{
net_maclife_wechat_http_BotApp.logger.finer ("\n" + jsonCSRResponse);
		if (jsonCSRResponse == null || jsonCSRResponse.isNull())
			return "";

		int nProtocolId = net_maclife_wechat_http_BotApp.GetJSONInt (jsonCSRResponse, "protocolId");
		assert (nProtocolId == 6);

		int nResult = net_maclife_wechat_http_BotApp.GetJSONInt (jsonCSRResponse, "result");

		//JsonNode nodeSendTime = jsonResponse.get ("sendTime");
		//int nAnswerTypeID = net_maclife_wechat_http_BotApp.GetJSONInt (jsonCSRResponse, "answerTypeId");
		JsonNode nodeSingleNode = jsonCSRResponse.get ("singleNode");
		JsonNode nodeVagueNode = jsonCSRResponse.get ("vagueNode");

		String sResult = "";
		if (nResult == 0)
		{
			/*
			switch (nAnswerTypeID)
			{
				case 1:	// 系统错误
				case 2	// 有敏感词
				case 3:	// 无法回答

				case 4:	// 需要补问
				case 6:	// 能够回答 (正常情况)
				case 8:	// 模式匹配

				case 10:	// 聊天过频
					sResult = nodeSingleNode
					break;
				case 7:	// 无法回答
					sResult = "无法回答";
					break;
			}
			*/
			if (nodeSingleNode != null && !nodeSingleNode.isNull())
			{
				sResult = nodeSingleNode.get ("answerMsg").asText ();
			}
			if (nodeVagueNode != null && !nodeVagueNode.isNull())
			{
				JsonNode jsonItemList = nodeVagueNode.get ("itemList");
				if (jsonItemList!=null && !jsonItemList.isNull () && jsonItemList.size ()>0)
				{
					StringBuilder sbPromptMessages = new StringBuilder ();
					sbPromptMessages.append (nodeVagueNode.get ("promptVagueMsg").asText ());
					sbPromptMessages.append ("\n");
					for (JsonNode item : nodeVagueNode.get ("itemList"))
					{
						sbPromptMessages.append (item.get ("num").asInt ());
						sbPromptMessages.append (". ");
						sbPromptMessages.append (item.get ("question").asText ());
						sbPromptMessages.append (" (匹配分值 ");
						sbPromptMessages.append (item.get ("score").asInt ());
						sbPromptMessages.append (")\n");
					}
					sbPromptMessages.append (nodeVagueNode.get ("endVagueMsg").asText ());
					//sbResult.append ("\n");
					sResult = sResult + (StringUtils.isEmpty(sResult) ? "" : "\n\n") + sbPromptMessages.toString ();
				}
			}
			else
			{
				//throw new RuntimeException ("什么鬼，怎么会出这种错误");
			}
		}
		else
		{
			sResult = "调用机器人接口返回失败的结果";
		}
		return sResult;
	}

	public JsonNode GetCSRReponse (String sFrom_EncryptedAccount, String sInput)
	{
		try
		{
			Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
			mapRequestHeaders.put ("Content-Type", "application/json");

			if (sFrom_EncryptedAccount.length () > 40)
				sFrom_EncryptedAccount = StringUtils.left (sFrom_EncryptedAccount, 40);

			String sRequestBody_JSONString =
				"{\n" +
				"	\"protocolId\": 5,\n" +
				"	\"robotHashCode\": \"" + HCICLOUD_CSR_ROBOT_ID + "\",\n" +
				"	\"platformConnType\": \"" + HCICLOUD_CSR_ROBOT_CHANNEL_NUMBER + "\",\n" +
				"	\"userId\": \"" + sFrom_EncryptedAccount + "\",\n" +
				"	\"talkerId\": \"" + HCICLOUD_CSR_TALKER_ID + "\",\n" +
				"	\"receiverId\": \"" + HCICLOUD_CSR_RECEIVER_ID + "\",\n" +
				"	\"appKey\": \"" + HCICLOUD_APP_KEY + "\",\n" +
				"	\"sendTime\": " + System.currentTimeMillis() + ",\n" +
				"	\"type\": \"text\",\n" +
				"	\"isNeedClearHistory\": 1,\n" +
				"	\"isQuestionQuery\": 0,\n" +
				"	\"query\": \"" + sInput + "\",\n" +
				"	\"__LAST__\": 0\n" +
				"}";
net_maclife_wechat_http_BotApp.logger.finer ("\n" + sRequestBody_JSONString);
			// 如果不传递 RequestHeader，则返回：“访问来源变量未定义”
			// {"aiResult":null,"answerTypeId":1,"protocolId":6,"result":0,"sendTime":null,"serviceLogId":null,"singleNode":{"answerMsg":"访问来源变量未定义","cmd":null,"isRichText":0,"list":null,"question":null,"score":0.0,"standardQuestion":"","standardQuestionId":0},"vagueNode":null}

			// 如果用微信的加密帐号当 userId 传递，则返回：“访问来源名过长”
			// {"aiResult":null,"answerTypeId":1,"protocolId":6,"result":0,"sendTime":null,"serviceLogId":null,"singleNode":{"answerMsg":"访问来源名过长","cmd":null,"isRichText":0,"list":null,"question":null,"score":0.0,"standardQuestion":"","standardQuestionId":0},"vagueNode":null}

			InputStream is = net_maclife_util_HTTPUtils.CURL_Post_Stream (HCICLOUD_CSR_URL__Query, mapRequestHeaders, sRequestBody_JSONString.getBytes (HCICLOUD_CHARSET_ENCODING));

			JsonNode jsonCSRResponse = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (is);
			return jsonCSRResponse;
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
		return null;
	}
}
