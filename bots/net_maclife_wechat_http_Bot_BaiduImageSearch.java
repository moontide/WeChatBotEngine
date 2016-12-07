import java.io.*;
import java.net.*;

import org.apache.commons.io.*;
import org.jsoup.nodes.*;

import com.fasterxml.jackson.databind.*;

public class net_maclife_wechat_http_Bot_BaiduImageSearch extends net_maclife_wechat_http_Bot
{
	@Override
	public int OnImageMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_NickName, String sTo_EncryptedAccount, String sTo_NickName, JsonNode jsonMessage, String sContent, File fMedia, String sImageURL)
	{
		if (! fMedia.exists ())
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

		Document doc = null;
		org.jsoup.Connection jsoup_conn = null;
		try
		{
			String sURL = "https://image.baidu.com/n/image?fr=html5&target=pcSearchImage&needJson=true&id=WU_FILE_0&name=" + URLEncoder.encode (fMedia.getName (), net_maclife_wechat_http_BotApp.utf8) + "&type=" + "&lastModifiedDate=" + "&size=" + fMedia.length ();
			//jsoup_conn = org.jsoup.Jsoup.connect (sURL)
			//	.data ("", "", new FileInputStream (fMedia))
			//	;
			//doc = jsoup_conn.post ();
			byte[] arrayPostData = IOUtils.toByteArray (new FileInputStream (fMedia), fMedia.length ());
			String sJSONString = net_maclife_util_HTTPUtils.CURL_Post (sURL, arrayPostData);
/*
{
    "errno":0,
    "errmsg":"",
    "data":{
        "querySign":"1835526740,788942838",
        "imageUrl":"http:\/\/b.hiphotos.baidu.com\/image\/pic\/item\/2f738bd4b31c8701602dcef02e7f9e2f0708ff09.jpg",
        "pageUrl":"https:\/\/image.baidu.com\/n\/pc_search?rn=30&appid=0&tag=1&isMobile=0&queryImageUrl=http%3A%2F%2Fb.hiphotos.baidu.com%2Fimage%2Fpic%2Fitem%2F2f738bd4b31c8701602dcef02e7f9e2f0708ff09.jpg&querySign=1835526740%2C788942838&fromProduct=&productBackUrl="
    },
    "extra":[

    ]
} */
net_maclife_wechat_http_BotApp.logger.info ("\n" + sJSONString);
			JsonNode jsonUploadImageResult = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (sJSONString);
			int errno = net_maclife_wechat_http_BotApp.GetJSONInt (jsonUploadImageResult, "errno");
			if (errno == 0)
			{
				JsonNode jsonData = jsonUploadImageResult.get ("data");
				sURL = net_maclife_wechat_http_BotApp.GetJSONText (jsonData, "pageUrl");
				doc = org.jsoup.Jsoup.connect (sURL).timeout (net_maclife_util_HTTPUtils.DEFAULT_READ_TIMEOUT_SECOND * 1000).get ();
				String sImageInfo = doc.select ("#guessInfo").text ();
				SendTextMessage (sFrom_EncryptedRoomAccount, sFrom_EncryptedAccount, sFrom_NickName, "图片信息:\n" + sImageInfo);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	@Override
	public int OnEmotionMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_NickName, String sTo_EncryptedAccount, String sTo_NickName, JsonNode jsonMessage, String sContent, File fMedia, String sImageURL)
	{
		return OnImageMessageReceived (sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_NickName, sTo_EncryptedAccount, sTo_NickName, jsonMessage, sContent, fMedia, sImageURL);
	}
}
