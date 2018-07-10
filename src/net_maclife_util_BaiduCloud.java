import java.io.*;
import java.security.*;
import java.security.cert.*;

import com.fasterxml.jackson.databind.*;

/**
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

 * @author liuyan
 *
 */
public class net_maclife_util_BaiduCloud
{
	public static final String BAIDU_OAUTH_ACCESS_TOKEN_URL  = "https://openapi.baidu.com/oauth/2.0/token";

	public static String GetBaiduAccessToken (String sAppKey, String sAppPassword, String sAccessTokenCacheFile)
	{
		File f = new File (sAccessTokenCacheFile);
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
		return GetNewBaiduAccessToken (sAppKey, sAppPassword, sAccessTokenCacheFile);
	}

	public static String GetNewBaiduAccessToken (String sAppKey, String sAppPassword, String sAccessTokenCacheFile)
	{
		// 使用 Client Credentials 方式获得百度访问令牌
		// 参见: http://developer.baidu.com/wiki/index.php?title=docs/oauth/client
		String sPostData = "grant_type=client_credentials&client_id=" + sAppKey + "&client_secret=" + sAppPassword;
		try
		{
			String sResponseBodyContent = net_maclife_util_HTTPUtils.CURL_Post (net_maclife_util_BaiduCloud.BAIDU_OAUTH_ACCESS_TOKEN_URL, sPostData.getBytes ());
			FileWriter fw = new FileWriter (sAccessTokenCacheFile);
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
}
