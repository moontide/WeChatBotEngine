import java.io.*;
import java.net.*;

import org.apache.commons.io.*;
import org.apache.commons.lang3.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

import com.fasterxml.jackson.databind.*;

public class net_maclife_wechat_http_Bot_BaiduImageSearch extends net_maclife_wechat_http_Bot
{
	@Override
	public int OnImageMessageReceived
		(
			JsonNode jsonFrom, String sFromAccount, String sFromName,
			JsonNode jsonFrom_RoomMember, String sFromAccount_RoomMember, String sFromName_RoomMember,
			JsonNode jsonFrom_Person, String sFromAccount_Person, String sFromName_Person,
			JsonNode jsonTo, String sToAccount, String sToName,
			JsonNode jsonMessage, String sContent,
			File fMedia, String sImageURL
		)
	{
		if (! fMedia.exists () && StringUtils.isEmpty (sImageURL))
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

		Document doc = null;
		org.jsoup.Connection jsoup_conn = null;
		try
		{
			String sURL = null;

			if (StringUtils.isNotEmpty (sImageURL))
			{
				sURL = "http://image.baidu.com/n/pc_search?queryImageUrl=" + URLEncoder.encode (sImageURL, net_maclife_wechat_http_BotApp.utf8) + "&uptype=urlsearch";
net_maclife_wechat_http_BotApp.logger.info (GetName() + " 按图片网址搜索，搜索网址为：\n" + sURL);

			}
			else
			{
				sURL = "https://image.baidu.com/n/image?fr=html5&target=pcSearchImage&needJson=true&id=WU_FILE_0&name=" + URLEncoder.encode (fMedia.getName (), net_maclife_wechat_http_BotApp.utf8) + "&type=" + "&lastModifiedDate=" + "&size=" + fMedia.length ();
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
net_maclife_wechat_http_BotApp.logger.info (GetName() + " 上传图片后返回的 JSON\n" + sJSONString);
				JsonNode jsonUploadImageResult = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (sJSONString);
				int errno = net_maclife_wechat_http_BotApp.GetJSONInt (jsonUploadImageResult, "errno");
				if (errno == 0)
				{
					JsonNode jsonData = jsonUploadImageResult.get ("data");
					sURL = net_maclife_wechat_http_BotApp.GetJSONText (jsonData, "pageUrl");
net_maclife_wechat_http_BotApp.logger.info (GetName() + " 上传图片后返回的 JSON 中的图片搜索网页的网址\n" + sURL);
				}
				else
				{
					return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
				}
			}

			doc = org.jsoup.Jsoup.connect (sURL).timeout (net_maclife_util_HTTPUtils.DEFAULT_READ_TIMEOUT_SECOND * 1000).get ();
			Elements e图片猜测 = doc.select ("#guessInfo");
			if (e图片猜测.isEmpty ())
			{
net_maclife_wechat_http_BotApp.logger.info (GetName() + " 找不到 #guessInfo，也许，搜索出错了？  " + doc.select (".error-text").text ());
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
			}
			Elements e图片猜测词语链接 = e图片猜测.select (".guess-info-text a.guess-info-word-link");
			if (e图片猜测词语链接.isEmpty ())
			{
net_maclife_wechat_http_BotApp.logger.info (GetName() + " 找不到 .guess-info-text a.guess-info-word-link，也许，没有结果？  " + e图片猜测.text ());
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
			}

			StringBuilder sbInfo = new StringBuilder ();
			sbInfo.append (e图片猜测词语链接.text ());
			sbInfo.append ("\n");
			sbInfo.append (e图片猜测词语链接.first ().absUrl ("href"));
			Elements e图片来源 = doc.select ("#sourceCard");
			if (! e图片来源.isEmpty ())
			{
				sbInfo.append ("\n\n" + e图片来源.select (".source-card-header-count").first ().text ());	// "发现 N 条图片来源"
				Elements e图片来源标题链接 = e图片来源.select (".source-card-topic-title-link");
				for (int i=0; i<e图片来源标题链接.size (); i++)
				{
					Element e = e图片来源标题链接.get (i);
					sbInfo.append ("\n\n");
					sbInfo.append (i+1);
					sbInfo.append (". ");
					sbInfo.append (e.text ());
					sbInfo.append ("\n");
					sbInfo.append (e.absUrl ("href"));
				}
				Element e查看更多图片来源 = e图片来源.select ("#websource-bottom").first ();
				if (e查看更多图片来源 != null)
				{
					sbInfo.append ("\n\n");
					sbInfo.append (e查看更多图片来源.text ());
					sbInfo.append ("\n");
					sbInfo.append (e查看更多图片来源.absUrl ("href"));
				}
			}
			SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, sbInfo.toString ());
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED | net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	@Override
	public int OnEmotionMessageReceived
		(
			JsonNode jsonFrom, String sFromAccount, String sFromName,
			JsonNode jsonFrom_RoomMember, String sFromAccount_RoomMember, String sFromName_RoomMember,
			JsonNode jsonFrom_Person, String sFromAccount_Person, String sFromName_Person,
			JsonNode jsonTo, String sToAccount, String sToName,
			JsonNode jsonMessage, String sContent,
			File fMedia, String sImageURL
		)
	{
		return OnImageMessageReceived (jsonFrom, sFromAccount, sFromName, jsonFrom_RoomMember, sFromAccount_RoomMember, sFromName_RoomMember, jsonFrom_Person, sFromAccount_Person, sFromName_Person, jsonTo, sToAccount, sToName, jsonMessage, sContent, fMedia, sImageURL);
	}
}
