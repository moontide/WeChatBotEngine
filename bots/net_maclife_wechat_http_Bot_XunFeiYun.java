import java.io.*;
import java.util.*;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.*;
import org.apache.commons.io.*;
import org.apache.commons.lang3.*;

//import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

/**
 * 利用讯飞云的<a href='http://aiui.xfyun.cn/help/devDoc#3-3-3'>语音听写（免费）</a>、<a href='http://aiui.xfyun.cn/help/devDoc#3-3-2'>文本语义理解</a>、<a href='http://aiui.xfyun.cn/help/devDoc#3-3-4'>语音语义理解</a> 等 API 实现的 语音识别（语音转文字）、语义理解（理解用户意图并给出回复/答案）功能机器人。
<p>
限制：“语音听写”功能只提供对语音时长在一分钟之内的语音进行识别。对时长更长的语音的识别需要用另外一个 API （收费）。
</p>

<p>
讯飞云语音识别结果示例：
<pre>
{
    "code": "00000",
    "desc": "成功",
    "data": {
        "ret": 0,
        "result": "今天星期几。",
        "sid": "watb37fe700@ch47730ce51e04477300"
    },
    "sid":"rwa8066ef80@cha4320da12234000100"
}
</pre>
</p>

讯飞云 语义理解（文本、语音的语义理解结果格式相同） 结果示例：
<pre>
{
  "code": "00000",
  "desc": "成功",
  "data": {
    "answer": {
      "text": "今天是2017年08月08日 丁酉年六月十七 星期二",
      "type": "T"
    },
    "match_info": {
      "type": "gparser_path",
      "value": "-----"
    },
    "operation": "ANSWER",
    "rc": 0,
    "service": "datetime",
    "text": "今天星期几",
    "uuid": "atn00210ce6@un782b0ce4cac76f2601",
    "sid": "rwa2ac04d1c@chfca30da12150000100"
  },
  "sid":"rwa2ac04d1c@chfca30da12150000100"
}
</pre>

 * @author liuyan
 *
 */
public class net_maclife_wechat_http_Bot_XunFeiYun extends net_maclife_wechat_http_Bot
{
	public static final String XFYUN_ASR_API_URL = "http://api.xfyun.cn/v1/aiui/v1/iat";
	public static final String XFYUN_TEXT_SEMANTIC_API_URL = "http://api.xfyun.cn/v1/aiui/v1/text_semantic";
	public static final String XFYUN_VOICE_SEMANTIC_API_URL = "http://api.xfyun.cn/v1/aiui/v1/voice_semantic";

	static String sXunFeiYunAppID = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.xfyun.app.id");
	static String sXunFeiYunAppKey = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.xfyun.app.key");

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
		try
		{
			if (net_maclife_wechat_http_BotApp.ParseBoolean (net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.xfyun.text-semantic.enabled")) && (!isReplyToRoom || isContentMentionedMeFirst))
				return ProcessTextSemantic (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, sReplyToAccount_Person, sReplyToName_Person, sContent);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	@Override
	public int OnVoiceMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, File fMedia
		)
	{
		if (! fMedia.exists ())
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
/*

*/
		try
		{
			fMedia = ConvertAudioToWavFormat (fMedia);
			if (fMedia == null)
			{
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
			}
			if (net_maclife_wechat_http_BotApp.ParseBoolean (net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.xfyun.voice-semantic.enabled")))
				return ProcessVoiceSemantic (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, sReplyToAccount_Person, sReplyToName_Person, fMedia);
			else
				return ProcessSpeechRecognition (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, fMedia);
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	@Override
	public int OnVideoMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, File fMedia
		)
	{
		if (! fMedia.exists ())
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

		try
		{
			fMedia = StripAudioFromVideo (fMedia);
			if (fMedia == null)
			{
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
			}
			if (net_maclife_wechat_http_BotApp.ParseBoolean (net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.xfyun.voice-semantic.enabled")))
				return ProcessVoiceSemantic (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, sReplyToAccount_Person, sReplyToName_Person, fMedia);
			else
				return ProcessSpeechRecognition (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, fMedia);
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}

		// 从视频中提取出语音，然后再语音识别
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
处理语音识别。
<br/>
Bash 版本
<pre>
#!/bin/bash
appid=
appkey=
t=$(date +%s)
param=$(echo -n '{"auf":"8k","aue":"raw","scene":"main"}' | base64)
http_body=data=AAAAAAAAAAAA
hash=$(echo -n "$appkey$t$param$http_body" | md5sum)
curl -v api.xfyun.cn/v1/aiui/v1/iat -H "X-Appid: $appid " -H "X-CurTime: $t" -H "X-Param: $param" -H "X-CheckSum: $hash" -d "$http_body"
</pre>
	 */
	int ProcessSpeechRecognition
		(
			String sReplyToAccount, String sReplyToName,
			String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			File fMedia
		) throws Exception
	{
		InputStream is = new FileInputStream (fMedia);
		byte[] arrayFileData = IOUtils.toByteArray (is, fMedia.length ());
		is.close ();
		String sFileData_Base64 = Base64.encodeBase64String (arrayFileData);
		//String sFileData_Base64_URLEncoded = URLEncoder.encode (sFileData_Base64, net_maclife_wechat_http_BotApp.utf8);
		String sHTTPBody_Base64 = "data=" + sFileData_Base64;
		//String sHTTPBody_Base64_URLEncoded = "data=" + sFileData_Base64_URLEncoded;

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
		mapRequestHeaders.put ("X-Appid", sXunFeiYunAppID);
		String sCurrentUnixTimestamp = String.valueOf (System.currentTimeMillis () / 1000);
		mapRequestHeaders.put ("X-CurTime", sCurrentUnixTimestamp);
		String sXfyunParam_Base64 = Base64.encodeBase64String ("{\"auf\":\"8k\",\"aue\":\"raw\",\"scene\":\"main\"}".getBytes ());
		mapRequestHeaders.put ("X-Param", sXfyunParam_Base64);
		String sValidationHashString = DigestUtils.md5Hex (sXunFeiYunAppKey + sCurrentUnixTimestamp + sXfyunParam_Base64 + sHTTPBody_Base64);
		mapRequestHeaders.put ("X-CheckSum", sValidationHashString);
net_maclife_wechat_http_BotApp.logger.finest ("讯飞云 语音听写/语音识别");
net_maclife_wechat_http_BotApp.logger.finest ("AppID: " + sXunFeiYunAppID);
net_maclife_wechat_http_BotApp.logger.finest ("AppKey: " + sXunFeiYunAppKey);
net_maclife_wechat_http_BotApp.logger.finest ("Tstmp: " + sCurrentUnixTimestamp);
net_maclife_wechat_http_BotApp.logger.finest ("Param: " + sXfyunParam_Base64);
//net_maclife_wechat_http_BotApp.logger.finest ("HTBdy: " + sHTTPBody_Base64);
net_maclife_wechat_http_BotApp.logger.finest ("Hash : " + sValidationHashString);

		String sResponseBodyContent = net_maclife_util_HTTPUtils.CURL_Post (XFYUN_ASR_API_URL, mapRequestHeaders, sHTTPBody_Base64.getBytes ());
		JsonNode jsonResult = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (sResponseBodyContent);
net_maclife_wechat_http_BotApp.logger.info (GetName() + " 机器人获取讯飞云【语音识别 (ASR)】的 http 响应消息体:");
net_maclife_wechat_http_BotApp.logger.info ("	" + sResponseBodyContent);

		String  sRC = net_maclife_wechat_http_BotApp.GetJSONText (jsonResult, "code");
			// 10002 过期请求  有可能是本地时间不对
		//String sSID = net_maclife_wechat_http_BotApp.GetJSONText (jsonResult, "sid");
		String sDesc = net_maclife_wechat_http_BotApp.GetJSONText (jsonResult, "desc");
		JsonNode jsonData = jsonResult.get ("data");
		int nRet = net_maclife_wechat_http_BotApp.GetJSONInt (jsonData, "ret");
		switch (nRet)
		{
			case 0:
				String sResult = net_maclife_wechat_http_BotApp.GetJSONText (jsonData, "result");
				if (StringUtils.isEmpty(sResult))
					return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, (StringUtils.isEmpty (sReplyToAccount_RoomMember) ? sReplyToName : sReplyToName_RoomMember) + " 说道:\n" + sResult);
					return
						net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
						| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
				default:
net_maclife_wechat_http_BotApp.logger.warning (GetName() + " " + sRC + " " + sDesc + " 听不清 " + (StringUtils.isEmpty (sReplyToAccount_RoomMember) ? sReplyToName : sReplyToName_RoomMember) + " 说了些啥");
				//SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "听不清 " + (StringUtils.isEmpty (sReplyToAccount_RoomMember) ? sReplyToName : sReplyToName_RoomMember) + " 说了些啥: " + err_msg);
				break;
		}

		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	public static File ConvertAudioToWavFormat (File sSourceAudio) throws IOException, InterruptedException
	{
net_maclife_wechat_http_BotApp.logger.info ("ConvertAudioToWavFormat 将 " + sSourceAudio + " 【转换】为 wav 格式");
		String sWavFileName = sSourceAudio + ".wav";
		// ffmpeg -i test.mp3 -ar 8000 -ac 1 test.mp3.amr
		ProcessBuilder pb = new ProcessBuilder
			(
				net_maclife_wechat_http_BotApp.GetConfig ().getString ("app.external-utils.ffmpeg.path") + File.separator + "ffmpeg"
				, "-i"
				, sSourceAudio.toString ()
				, "-ar"
				, "8000"
				, "-ac"
				, "1"
				, sWavFileName
			);
		Process p = pb.start ();
		InputStream in = p.getInputStream ();
		InputStream err = p.getErrorStream ();
		while (-1 != in.read ());
		while (-1 != err.read ());
		int rc = p.waitFor ();
		assert (rc == 0);
		if (rc != 0)
		{
net_maclife_wechat_http_BotApp.logger.severe ("语音格式转换失败");
			return null;
		}
		return new File (sWavFileName);
	}

	public static File StripAudioFromVideo (File sSourceVideo) throws IOException, InterruptedException
	{
net_maclife_wechat_http_BotApp.logger.info ("StripAudioFromVideo 将视频 " + sSourceVideo + " 中的音频提取出来 (wav 格式)");
		String sWavFileName = sSourceVideo + ".wav";
		// ffmpeg -i test.mp4 -vn -ar 8000 -ac 1 test.mp4.amr
		ProcessBuilder pb = new ProcessBuilder
			(
				net_maclife_wechat_http_BotApp.GetConfig ().getString ("app.external-utils.ffmpeg.path") + File.separator + "ffmpeg"
				, "-i"
				, sSourceVideo.toString ()
				, "-vn"
				, "-ar"
				, "8000"
				, "-ac"
				, "1"
				, sWavFileName
			);
		Process p = pb.start ();
		InputStream in = p.getInputStream ();
		InputStream err = p.getErrorStream ();
		while (-1 != in.read ());
		while (-1 != err.read ());
		int rc = p.waitFor ();
		assert (rc == 0);
		if (rc != 0)
		{
net_maclife_wechat_http_BotApp.logger.severe ("视频提取音频失败");
			return null;
		}
		return new File (sWavFileName);
	}

	int ProcessTextSemantic
		(
			String sReplyToAccount, String sReplyToName,
			String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent
		) throws Exception
	{
		String sFileData_Base64 = Base64.encodeBase64String (sContent.getBytes (net_maclife_wechat_http_BotApp.utf8));
		//String sFileData_Base64_URLEncoded = URLEncoder.encode (sFileData_Base64, net_maclife_wechat_http_BotApp.utf8);
		String sHTTPBody_Base64 = "text=" + sFileData_Base64;
		//String sHTTPBody_Base64_URLEncoded = "data=" + sFileData_Base64_URLEncoded;

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
		mapRequestHeaders.put ("X-Appid", sXunFeiYunAppID);
		String sCurrentUnixTimestamp = String.valueOf (System.currentTimeMillis () / 1000);
		mapRequestHeaders.put ("X-CurTime", sCurrentUnixTimestamp);
		String sXfyunParam_Base64 = Base64.encodeBase64String (("{\"scene\":\"main\",\"userid\":\"" + StringUtils.strip (sReplyToAccount_Person, "@") + "\"}").getBytes (net_maclife_wechat_http_BotApp.utf8));
		mapRequestHeaders.put ("X-Param", sXfyunParam_Base64);
		String sValidationHashString = DigestUtils.md5Hex (sXunFeiYunAppKey + sCurrentUnixTimestamp + sXfyunParam_Base64 + sHTTPBody_Base64);
		mapRequestHeaders.put ("X-CheckSum", sValidationHashString);
net_maclife_wechat_http_BotApp.logger.finest ("讯飞云 文本语义理解");
net_maclife_wechat_http_BotApp.logger.finest ("AppID: " + sXunFeiYunAppID);
net_maclife_wechat_http_BotApp.logger.finest ("AppKey: " + sXunFeiYunAppKey);
net_maclife_wechat_http_BotApp.logger.finest ("Tstmp: " + sCurrentUnixTimestamp);
net_maclife_wechat_http_BotApp.logger.finest ("Param: " + sXfyunParam_Base64);
net_maclife_wechat_http_BotApp.logger.finest ("HTBdy: " + sHTTPBody_Base64);
net_maclife_wechat_http_BotApp.logger.finest ("Hash : " + sValidationHashString);

		String sResponseBodyContent = net_maclife_util_HTTPUtils.CURL_Post (XFYUN_TEXT_SEMANTIC_API_URL, mapRequestHeaders, sHTTPBody_Base64.getBytes ());
		JsonNode jsonResult = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (sResponseBodyContent);
net_maclife_wechat_http_BotApp.logger.info (GetName() + " 机器人获取讯飞云【文本语义理解】的 http 响应消息体:");
net_maclife_wechat_http_BotApp.logger.info ("	" + sResponseBodyContent);

		return ProcessSemanticResult (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, sReplyToAccount_Person, sReplyToName_Person, jsonResult);
	}

	int ProcessVoiceSemantic
		(
			String sReplyToAccount, String sReplyToName,
			String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			String sReplyToAccount_Person, String sReplyToName_Person,
			File fMedia
		) throws Exception
	{
		InputStream is = new FileInputStream (fMedia);
		byte[] arrayFileData = IOUtils.toByteArray (is, fMedia.length ());
		is.close ();
		String sFileData_Base64 = Base64.encodeBase64String (arrayFileData);
		//String sFileData_Base64_URLEncoded = URLEncoder.encode (sFileData_Base64, net_maclife_wechat_http_BotApp.utf8);
		String sHTTPBody_Base64 = "text=" + sFileData_Base64;
		//String sHTTPBody_Base64_URLEncoded = "data=" + sFileData_Base64_URLEncoded;

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
		mapRequestHeaders.put ("X-Appid", sXunFeiYunAppID);
		String sCurrentUnixTimestamp = String.valueOf (System.currentTimeMillis () / 1000);
		mapRequestHeaders.put ("X-CurTime", sCurrentUnixTimestamp);
		String sXfyunParam_Base64 = Base64.encodeBase64String (("{\"auf\":\"8k\",\"aue\":\"raw\",\"scene\":\"main\",\"userid\":\"" + StringUtils.strip (sReplyToAccount_Person, "@") + "\"}").getBytes (net_maclife_wechat_http_BotApp.utf8));
		mapRequestHeaders.put ("X-Param", sXfyunParam_Base64);
		String sValidationHashString = DigestUtils.md5Hex (sXunFeiYunAppKey + sCurrentUnixTimestamp + sXfyunParam_Base64 + sHTTPBody_Base64);
		mapRequestHeaders.put ("X-CheckSum", sValidationHashString);
net_maclife_wechat_http_BotApp.logger.finest ("讯飞云 语音语义理解");
net_maclife_wechat_http_BotApp.logger.finest ("AppID: " + sXunFeiYunAppID);
net_maclife_wechat_http_BotApp.logger.finest ("AppKey: " + sXunFeiYunAppKey);
net_maclife_wechat_http_BotApp.logger.finest ("Tstmp: " + sCurrentUnixTimestamp);
net_maclife_wechat_http_BotApp.logger.finest ("Param: " + sXfyunParam_Base64);
//net_maclife_wechat_http_BotApp.logger.finest ("HTBdy: " + sHTTPBody_Base64);
net_maclife_wechat_http_BotApp.logger.finest ("Hash : " + sValidationHashString);

		String sResponseBodyContent = net_maclife_util_HTTPUtils.CURL_Post (XFYUN_VOICE_SEMANTIC_API_URL, mapRequestHeaders, sHTTPBody_Base64.getBytes ());
		JsonNode jsonResult = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (sResponseBodyContent);
net_maclife_wechat_http_BotApp.logger.info (GetName() + " 机器人获取讯飞云【语音语义理解】的 http 响应消息体:");
net_maclife_wechat_http_BotApp.logger.info ("	" + sResponseBodyContent);

		return ProcessSemanticResult (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, sReplyToAccount_Person, sReplyToName_Person, jsonResult);
	}
	int ProcessSemanticResult
		(
			String sReplyToAccount, String sReplyToName,
			String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			String sReplyToAccount_Person, String sReplyToName_Person,
			JsonNode jsonSemanticResult
		) throws Exception
	{
		String  sRC = net_maclife_wechat_http_BotApp.GetJSONText (jsonSemanticResult, "code");
		//String sSID = net_maclife_wechat_http_BotApp.GetJSONText (jsonResult, "sid");
		String sDesc = net_maclife_wechat_http_BotApp.GetJSONText (jsonSemanticResult, "desc");
		JsonNode jsonData = jsonSemanticResult.get ("data");
		String sTextInputed = net_maclife_wechat_http_BotApp.GetJSONText (jsonData, "text");
		String sVendor = net_maclife_wechat_http_BotApp.GetJSONText (jsonData, "vendor");
		String sService = net_maclife_wechat_http_BotApp.GetJSONText (jsonData, "service");
		ArrayNode jsonSemantics = (ArrayNode)jsonData.get ("semantic");
		JsonNode jsonFirstSemantic = null;
		if (jsonSemantics != null)
			jsonFirstSemantic = jsonSemantics.get (0);
		String sFirstSementic_Intent = "";
		ArrayNode jsonFirstSemantic_Slots = null;
		if (jsonFirstSemantic != null)
		{
			sFirstSementic_Intent = net_maclife_wechat_http_BotApp.GetJSONText (jsonFirstSemantic, "intent");
			jsonFirstSemantic_Slots = (ArrayNode)jsonFirstSemantic.get ("slots");
		}
		JsonNode jsonDataData = jsonData.get ("data");
		JsonNode jsonDataDataResult = null;
		JsonNode jsonFirstDataDataResult = null;
		if (jsonDataData!=null)
		{
			jsonDataDataResult = jsonDataData.get ("result");
			if (jsonDataDataResult != null && ((ArrayNode)jsonDataDataResult).size () > 0)
				jsonFirstDataDataResult = ((ArrayNode)jsonDataDataResult).get (0);
		}
		JsonNode jsonAnswer = jsonData.get ("answer");
		String sAnswer = net_maclife_wechat_http_BotApp.GetJSONText (jsonAnswer, "text");
		String sDialogStat = net_maclife_wechat_http_BotApp.GetJSONText (jsonData, "dialog_stat");
		JsonNode jsonMoreResults = jsonData.get ("moreResults");
		String sDataSID = net_maclife_wechat_http_BotApp.GetJSONText (jsonData, "sid");
		int nReturnCode = net_maclife_wechat_http_BotApp.GetJSONInt (jsonData, "rc");
		//if (StringUtils.equalsIgnoreCase (sRC, "00000"))
		//{
		switch (nReturnCode)
		{
			case 0:
				StringBuilder sbResult = new StringBuilder ();
				sbResult.append ("问: " + sTextInputed + "\n");
				if (StringUtils.isNotEmpty (sAnswer))
					sbResult.append ("答: " + sAnswer + "\n");
				if (StringUtils.isNotEmpty (sService))
					sbResult.append ("服务: " + sService + "\n");
				if (StringUtils.isNotEmpty (sVendor))
					sbResult.append ("服务商: " + sVendor + "\n");
				if (jsonFirstSemantic != null)
				{
					sbResult.append ("语义意图: " + sFirstSementic_Intent + "\n");
					if (jsonFirstSemantic_Slots != null && jsonFirstSemantic_Slots.size ()>0)
					{
						sbResult.append ("语义分解:\n");
						for (int i=0; i<jsonFirstSemantic_Slots.size (); i++)
						{
							JsonNode jsonTempNode = jsonFirstSemantic_Slots.get (i);
							sbResult.append ("    " + net_maclife_wechat_http_BotApp.GetJSONText (jsonTempNode, "name") + ": " + net_maclife_wechat_http_BotApp.GetJSONText (jsonTempNode, "value") + "\n");
						}
					}
				}
				if (jsonFirstDataDataResult!=null)
				{
					sbResult.append ("数据条数: " + ((ArrayNode)jsonDataDataResult).size () + "\n");
					sbResult.append ("首条数据: " + jsonFirstDataDataResult + "\n");
				}
				String sResult = sbResult.toString ();
net_maclife_wechat_http_BotApp.logger.info (sResult);
				if (StringUtils.isEmpty(sResult))
					return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, sResult);
					return
						net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
						| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
			default:
net_maclife_wechat_http_BotApp.logger.warning (GetName() + " " + sRC + " " + sDesc + " data.rc=" + nReturnCode);
				//SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "听不清 " + (StringUtils.isEmpty (sReplyToAccount_RoomMember) ? sReplyToName : sReplyToName_RoomMember) + " 说了些啥: " + err_msg);
				break;
		}
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}
	public static void main (String[] args)
	{
		if (args.length < 1)
		{
			net_maclife_wechat_http_BotApp.logger.severe ("参数 1 需要指定 wav 音频文件(8K赫兹, 16位, 单声道)");
			return;
		}

		File fMedia = new File (args[0]);
		net_maclife_wechat_http_Bot_XunFeiYun bot = new net_maclife_wechat_http_Bot_XunFeiYun ();
		try
		{
			//bot.ProcessSpeechRecognition (null, null, null, null, fMedia);
			bot.ProcessTextSemantic (null, null, null, null, null, null, args[0]);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
