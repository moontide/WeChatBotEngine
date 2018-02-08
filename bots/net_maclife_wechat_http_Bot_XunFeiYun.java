import java.io.*;
import java.util.*;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.*;
import org.apache.commons.io.*;
import org.apache.commons.lang3.*;

//import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

/**
 * 利用讯飞云的语音听写（免费） API 实现的 语音识别（语音转文字） 功能机器人。
<p>
限制：“语音听写”只提供对语音时长在一分钟之内的语音进行识别。对时长更长的语音的识别需要用另外一个 API （收费）。
</p>
<p>
	百度访问令牌 (AccessToken) 返回的数据格式
<pre>
{
    "access_token":"***...***",
    "session_key":"***...***",
    "scope":"public audio_voice_assistant_get audio_tts_post wise_adapt lebo_resource_base lightservice_public hetu_basic lightcms_map_poi kaidian_kaidian",	// 类似这样
    "refresh_token":"***...***",
    "session_secret":"***...***",
    "expires_in":NNNNNNN
}
</pre>

语音识别结果
<pre>
{
    "corpus_no":"******",
    "err_msg":"*****.",
    "err_no":0,	// 0 是成功，其他为失败
    "result":	// 最多 5 个结果，或者根本不存在（出错时）
    [
        "账号已暂停使用，"
    ],
    "sn":"*****"
}
</pre>
</p>

 * @author liuyan
 *
 */
public class net_maclife_wechat_http_Bot_XunFeiYun extends net_maclife_wechat_http_Bot
{
	public static final String XFYUN_ASR_API_URL = "http://api.xfyun.cn/v1/aiui/v1/iat";

	static String sXunFeiYunAppID = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.xfyun.app.id");
	static String sXunFeiYunAppKey = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.xfyun.app.key");

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
			ProcessSpeechRecognition (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, fMedia);

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
			ProcessSpeechRecognition (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, fMedia);

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
net_maclife_wechat_http_BotApp.logger.finest ("AppID: " + sXunFeiYunAppID);
net_maclife_wechat_http_BotApp.logger.finest ("AppKey: " + sXunFeiYunAppKey);
net_maclife_wechat_http_BotApp.logger.finest ("Tstmp: " + sCurrentUnixTimestamp);
net_maclife_wechat_http_BotApp.logger.finest ("Param: " + sXfyunParam_Base64);
net_maclife_wechat_http_BotApp.logger.finest ("HTBdy: " + sHTTPBody_Base64);
net_maclife_wechat_http_BotApp.logger.finest ("Hash : " + sValidationHashString);

		String sResponseBodyContent = net_maclife_util_HTTPUtils.CURL_Post (XFYUN_ASR_API_URL, mapRequestHeaders, sHTTPBody_Base64.getBytes ());
		JsonNode jsonResult = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (sResponseBodyContent);
net_maclife_wechat_http_BotApp.logger.info (GetName() + " 机器人获取讯飞云语音识别 (ASR) 的 http 响应消息体:");
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
			bot.ProcessSpeechRecognition (null, null, null, null, fMedia);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
