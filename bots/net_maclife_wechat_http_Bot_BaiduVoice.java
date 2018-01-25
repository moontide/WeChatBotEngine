import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;

import org.apache.commons.io.*;
import org.apache.commons.lang3.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

/**
 * 利用百度语音 API 实现的 语音识别（语音转文字）、语音合成（文字转语音 -- “说”话）机器人。

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
public class net_maclife_wechat_http_Bot_BaiduVoice extends net_maclife_wechat_http_Bot
{
	public static final String BAIDU_OAUTH_ACCESS_TOKEN_URL  = "https://openapi.baidu.com/oauth/2.0/token";
	public static final String BAIDU_ASR_API_URL             = "http://vop.baidu.com/server_api";
	public static final String BAIDU_TTS_API_URL             = "http://tsn.baidu.com/text2audio";

	static String sBaiduOAuthAccessTokenFileInJSONFormat = net_maclife_wechat_http_BotApp.cacheDirectory + "/" + net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.baidu.oauth.accessTokenFile");
	static String sBaiduCloudAppID           = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.baidu.voice.app.id");
	static String sBaiduCloudAppKey          = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.baidu.voice.app.key");
	static String sBaiduCloudAppPassword     = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.baidu.voice.app.password");

	static String sMACAddress = "BaiduVoiceBotApplet";
	static
	{
		try
		{
			// 确保百度 AccessToken 目录存在
			File fBaiduDir = new File (sBaiduOAuthAccessTokenFileInJSONFormat).getParentFile ();
			fBaiduDir.mkdirs ();

			// 获取本机 MAC 地址
			//
			InetAddress ip = InetAddress.getLocalHost();
net_maclife_wechat_http_BotApp.logger.info ("本机 IP 地址: " + ip.getHostAddress());
			NetworkInterface network = NetworkInterface.getByInetAddress (ip);
			if (network != null)
			{
				byte[] arrayMAC = network.getHardwareAddress();
				if (arrayMAC != null)
				{
					StringBuilder sb = new StringBuilder ();
					for (int i=0; i<arrayMAC.length; i++)
					{
						if (i!=0)
							sb.append ("-");
						sb.append (String.format ("%02X", arrayMAC[i]));
					}
					sMACAddress = sb.toString ();
				}
net_maclife_wechat_http_BotApp.logger.info ("    MAC 地址: " + sMACAddress);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
	}

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
			JsonNode jsonCSRResponse = null;
			//GetTTSReponse (sFromAccount, sContent);
			if (jsonCSRResponse == null)
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

			//String sResponse = ParseASRResponse (jsonCSRResponse);
			// 上传语音媒体文件到微信服务器，获得媒体 ID
			if (StringUtils.isNotEmpty (""))
			{
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "");
			}
			// 然后将该媒体文件（媒体ID）发到微信【好友/群】
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
-------------------------------------------------------------------------------
 语音识别参数

    - 语音识别接口支持 POST 方式
    - 目前 API 仅支持整段语音识别的模式，即需要上传整段语音进行识别
    - 语音数据上传方式有两种：隐示发送和显示发送
    - 原始语音的录音格式目前只支持评测 8k/16k 采样率 16bit 位深的单声道语音
    - 压缩格式支持：pcm（不压缩）、wav、opus、speex、amr、x-flac
    - 系统支持语言种类：中文（zh）、粤语（ct）、英文（en）
    - 正式地址：http://vop.baidu.com/server_api


隐式发送

	语音数据和其他参数通过标准 JSON 格式串行化 POST 上传， JSON 里包括的参数：

	字段名     数据类型    可需    描述
	format     sting   必填    语音压缩的格式，请填写上述格式之一，不区分大小写
	rate   int     必填    采样率，支持 8000 或者 16000
	channel    int     必填    声道数，仅支持单声道，请填写 1
	cuid   string  必填    用户唯一标识，用来区分用户，填写机器 MAC 地址或 IMEI 码，长度为60以内
	token  string  必填    开放平台获取到的开发者 access_token

	ptc    int     选填    协议号，下行识别结果选择，默认 nbest 结果
	lan    string  选填    语种选择，中文=zh、粤语=ct、英文=en，不区分大小写，默认中文
	url    string  选填    语音下载地址
	callback   string  选填    识别结果回调地址
	speech     string  选填    真实的语音数据 ，需要进行base64 编码
	len    int     选填    原始语音长度，单位字节


显式发送

	语音数据直接放在 HTTP-BODY 中，控制参数以及相关统计信息通过 REST 参数传递，REST参数说明：
	字段名     数据类型    可需    描述
	cuid   string  必填    用户 ID，推荐使用设备mac 地址/手机IMEI 等设备唯一性参数
	token  string  必填    开发者身份验证密钥
	lan    string  选填    语种选择，中文=zh、粤语=ct、英文=en，不区分大小写，默认中文
	ptc    int     选填    协议号，下行识别结果选择，默认 nbest 结果
-------------------------------------------------------------------------------

*/
		try
		{
			fMedia = ConvertAudioToAMRFormat (fMedia);
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

	int ProcessSpeechRecognition
		(
			String sReplyToAccount, String sReplyToName,
			String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			File fMedia
		) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		String sAccessToken = GetBaiduAccessToken ();
		if (StringUtils.isEmpty (sAccessToken))
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

		//try
		{	// 显式发送
			String sURL = BAIDU_ASR_API_URL + "?cuid=" + sMACAddress + "&token=" + sAccessToken + "&lan=zh";
//logger.fine ("WebWeChatGetContacts 的 URL:");
//logger.fine ("	" + sURL);

			Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
			mapRequestHeaders.put ("Content-Type", "audio/amr; rate=8000");
//logger.finer  ("发送 WebWeChatGetContacts 的 http 请求消息头 (Content-Type):");
//logger.finer  ("	" + mapRequestHeaders);

//String sRequestBody_JSONString = MakeFullBaseRequestJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID ());
//logger.finer  ("发送 WebWeChatGetContacts 的 http 请求消息体:");
//logger.finer  ("	" + sRequestBody_JSONString);
			InputStream is = new FileInputStream (fMedia);
			byte[] arrayPostData = IOUtils.toByteArray (is, fMedia.length ());
			is.close ();

			String sResponseBodyContent = net_maclife_util_HTTPUtils.CURL_Post (sURL, mapRequestHeaders, arrayPostData);
			JsonNode jsonNode = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (sResponseBodyContent);
net_maclife_wechat_http_BotApp.logger.info (GetName() + " 机器人获取百度语音识别 (ASR) 的 http 响应消息体:");
net_maclife_wechat_http_BotApp.logger.info ("	" + sResponseBodyContent);

			int err_no = net_maclife_wechat_http_BotApp.GetJSONInt (jsonNode, "err_no");
			String err_msg = net_maclife_wechat_http_BotApp.GetJSONText (jsonNode, "err_msg");
			switch (err_no)
			{
				case 0:
					JsonNode jsonResults = jsonNode.get ("result");
					if (jsonResults.size () == 1)
					{
						SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, (StringUtils.isEmpty (sReplyToAccount_RoomMember) ? sReplyToName : sReplyToName_RoomMember) + " 说道:\n" + jsonResults.get (0).asText ());
						return
							net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
							| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					}

					StringBuilder sb = new StringBuilder ();
					for (int i=0; i<jsonResults.size (); i++)
					{
						sb.append (i+1);
						sb.append (". ");
						sb.append (jsonResults.get (i).asText ());
						sb.append ("\n");
					}
					SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, (StringUtils.isEmpty (sReplyToAccount_RoomMember) ? sReplyToName : sReplyToName_RoomMember) + " 可能说了下面某句话:\n" + jsonResults.get (0).asText ());
					return
						net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
						| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					//break;
				case 3300:
net_maclife_wechat_http_BotApp.logger.warning (GetName() + " " + err_no + " " + err_msg + " 输入参数不正确");
					//SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "输入参数不正确");
					break;
				case 3301:
net_maclife_wechat_http_BotApp.logger.warning (GetName() + " " + err_no + " " + err_msg + " 识别错误");
					//SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "识别错误");
					break;
				case 3302:
net_maclife_wechat_http_BotApp.logger.warning (GetName() + " " + err_no + " " + err_msg + " 验证失败");
					//SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "验证失败");
					break;
				case 3303:
net_maclife_wechat_http_BotApp.logger.warning (GetName() + " " + err_no + " " + err_msg + " 语音服务器后端问题");
					//SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "语音服务器后端问题");
					break;
				case 3304:
net_maclife_wechat_http_BotApp.logger.warning (GetName() + " " + err_no + " " + err_msg + " 请求 GPS 过大，超过限额");
					//SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "请求 GPS 过大，超过限额");
					break;
				case 3305:
net_maclife_wechat_http_BotApp.logger.warning (GetName() + " " + err_no + " " + err_msg + " 产品线当前日请求数超过限额");
					//SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "产品线当前日请求数超过限额");
					break;
				default:
net_maclife_wechat_http_BotApp.logger.warning (GetName() + " " + err_no + " " + err_msg + " 听不清 " + (StringUtils.isEmpty (sReplyToAccount_RoomMember) ? sReplyToName : sReplyToName_RoomMember) + " 说了些啥");
					//SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "听不清 " + (StringUtils.isEmpty (sReplyToAccount_RoomMember) ? sReplyToName : sReplyToName_RoomMember) + " 说了些啥: " + err_msg);
					break;
			}
		}
		//catch (Exception e)
		//{
		//	e.printStackTrace ();
		//}
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	public static String GetBaiduAccessToken ()
	{
		File f = new File (sBaiduOAuthAccessTokenFileInJSONFormat);
		if (f.exists ())
		{
			long nFileModifiedTime_Millisecond = f.lastModified ();
			try
			{
				JsonNode jsonAccessToken = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (f);
				int nExpireDuration_Seconds = net_maclife_wechat_http_BotApp.GetJSONInt (jsonAccessToken, "expires_in");
				long now = System.currentTimeMillis ();
				if (now <= (nFileModifiedTime_Millisecond + nExpireDuration_Seconds*1000))
					return net_maclife_wechat_http_BotApp.GetJSONText (jsonAccessToken, "access_token");
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		// 已过期，或者从未获取过
		return GetNewBaiduAccessToken ();
	}

	public static String GetNewBaiduAccessToken ()
	{
		// 使用 Client Credentials 方式获得百度访问令牌
		// 参见: http://developer.baidu.com/wiki/index.php?title=docs/oauth/client
		String sPostData = "grant_type=client_credentials&client_id=" + sBaiduCloudAppKey + "&client_secret=" + sBaiduCloudAppPassword;
		try
		{
			String sResponseBodyContent = net_maclife_util_HTTPUtils.CURL_Post (BAIDU_OAUTH_ACCESS_TOKEN_URL, sPostData.getBytes ());
			FileWriter fw = new FileWriter (sBaiduOAuthAccessTokenFileInJSONFormat);
			fw.write (sResponseBodyContent);
			fw.close ();

			JsonNode jsonAccessToken = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (sResponseBodyContent);
			return net_maclife_wechat_http_BotApp.GetJSONText (jsonAccessToken, "access_token");
		}
		catch (KeyManagementException | UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException e)
		{
			e.printStackTrace();
		}
		return null;
	}

	public static File ConvertAudioToAMRFormat (File sSourceAudio) throws IOException, InterruptedException
	{
net_maclife_wechat_http_BotApp.logger.info ("ConvertAudioToAMRFormat 将 " + sSourceAudio + " 【转换】为 amr 格式");
		String sAMRFileName = sSourceAudio + ".amr";
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
				, sAMRFileName
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
		return new File (sAMRFileName);
	}

	public static File StripAudioFromVideo (File sSourceVideo) throws IOException, InterruptedException
	{
net_maclife_wechat_http_BotApp.logger.info ("StripAudioFromVideo 将视频 " + sSourceVideo + " 中的音频提取出来 (amr 格式)");
		String sAMRFileName = sSourceVideo + ".amr";
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
				, sAMRFileName
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
		return new File (sAMRFileName);
	}

	public static void main (String[] args)
	{
		if (args.length < 1)
		{
			net_maclife_wechat_http_BotApp.logger.severe ("参数 1 需要指定 amr 音频文件");
			return;
		}

		File fMedia = new File (args[0]);
		net_maclife_wechat_http_Bot_BaiduVoice bot = new net_maclife_wechat_http_Bot_BaiduVoice ();
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
