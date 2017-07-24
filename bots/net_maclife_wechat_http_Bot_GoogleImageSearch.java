import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

import org.apache.commons.io.*;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

import com.fasterxml.jackson.databind.*;

public class net_maclife_wechat_http_Bot_GoogleImageSearch extends net_maclife_wechat_http_Bot
{
	public static String GOOGLE_BASE_URL = "https://www.google.com.hk";
	public static String GOOGLE_IMAGE_SEARCH_URL = GOOGLE_BASE_URL + "/searchbyimage";
	public static boolean USE_GFW_PROXY = net_maclife_wechat_http_BotApp.ParseBoolean (net_maclife_wechat_http_BotApp.GetConfig ().getString ("google.useGFWProxy"), true);
	public static String GFW_PROXY_TYPE = StringUtils.upperCase (net_maclife_wechat_http_BotApp.GetConfig ().getString ("app.gfw.proxy.type"));
	public static String GFW_PROXY_HOST = net_maclife_wechat_http_BotApp.GetConfig ().getString ("app.gfw.proxy.host");
	public static int GFW_PROXY_PORT = net_maclife_wechat_http_BotApp.GetConfig ().getInt ("app.gfw.proxy.port");
	static Proxy gfwProxy = null;
	//static HttpHost gfwProxy_forApacheHttpCore = null;
	static
	{
		if (USE_GFW_PROXY)
		{
			gfwProxy = new Proxy (Proxy.Type.valueOf (GFW_PROXY_TYPE), new InetSocketAddress(GFW_PROXY_HOST, GFW_PROXY_PORT));
            //gfwProxy_forApacheHttpCore = new HttpHost (GFW_PROXY_HOST, GFW_PROXY_PORT, GFW_PROXY_TYPE);
		}
	}

	static final String sMultipartBoundary = "JsoupDoesNotSupportFormDataWell, and, ApacheHCDoesNotSupportSOCKSProxy";

	@Override
	public int OnImageMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, File fMedia, String sImageURL
		)
	{
		if ((fMedia == null || ! fMedia.exists ()) && StringUtils.isEmpty (sImageURL))
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

		String sURL = GOOGLE_IMAGE_SEARCH_URL;
		Document doc = null;
		org.jsoup.Connection jsoup_conn = null;
		InputStream fis = null;
		String sResponseBody = null;
		try
		{
			if (StringUtils.isNotEmpty (sImageURL))
			{	// GET 方法访问
				sURL = sURL + "?hl=zh-CN&image_url=" + URLEncoder.encode (sImageURL, net_maclife_wechat_http_BotApp.utf8);
			}
			else
			{	// POST 方法访问
				sURL = sURL + "/upload";
			}

			/*
			jsoup_conn = org.jsoup.Jsoup.connect (sURL);
			jsoup_conn.timeout (net_maclife_util_HTTPUtils.DEFAULT_READ_TIMEOUT_SECOND * 1000);
			if (USE_GFW_PROXY)
			{
				jsoup_conn.proxy (gfwProxy);
			}
			jsoup_conn
				.followRedirects (false)
				//.referrer ("https://www.google.com.hk/")
				.userAgent ("Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:50.0) Gecko/20100101 Firefox/1234 Firefox is versioning emperor #2, Chrome is versioning emperor #1!!!")
				.header ("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3")
				//.header ("Accept-Encoding", "gzip, deflate, br")
				//.header ("Cookie", "NID=91=eLR2Xt0oeN-XCDP3lQkbfBLqFU0fTxLq5ocj7lYBbkKKQBqnmvTJy-y9v3Y73nPQc_PIx59ir3T7hqmyPFAH02xSg6cCp9wqTiSTVGb0HuHqWd8U75jxpKeF47FK8DKl59mUX0WsfGjDzFvzsllfF6_HfPcBW54OATKvBBgseC-yBbJPE30YmK_z3KTWFiRRdWzWYAMTgeXERmDXBqFFpHZQLNebcQHkCTLLuBuAe5MsC2PIJs1TV8iYda_kbGFVvvvboNJTBw0eKwK9sPPt5NODU5s; SID=EwM1NpOSROI0ddQDNSMzCdQV7PF1NsutdbHv1QnVNhf2qSP3LtF-dkfUuJuBCZU5bXaMmQ.; HSID=AHHYjmOQYposTbxEx; APISID=S_Ga4t7dY6Xj_2IY/ATJD3hDdWt0OYN88-; SSID=AhuyWEzQ4qpQEbiW1; SAPISID=c91O3a08aWSgUrKP/AVd_0zQa4fzUH9adu; DV=grlNNAF72VBKxtBGytCv9RBrGlCGsQpb23j9sCB7RwAAAGqv7e54uemJFAAAAJa5wcBxIXwPCQAAAA")
				//.header ("Connection", "keep-alive")
				//.header ("Upgrade-Insecure-Requests", "1")
				;
			if (! StringUtils.isNotEmpty (sImageURL))
			{
				fis = new FileInputStream (fMedia);
				//jsoup_conn.data ("image_url", "");
				jsoup_conn.data ("encoded_image", fMedia.getName (), fis);	// 在浏览器开发工具里看，Google 图片搜索在 http 请求头里并未设置 Content-Type，而是在消息体里设置的。但是 jsoup 就设置在了请求头里，然后请求消息体里少了这部分，导致返回的数据不正确

				//byte[] arrayImg = IOUtils.toByteArray (fis);
				//jsoup_conn.data ("image_content", Base64.encodeBase64String (arrayImg));
				//jsoup_conn.data ("filename", fMedia.getName ());

				jsoup_conn.data ("hl", "zh-CN");
				doc = jsoup_conn.post ();
				fis.close ();
			}
			else
			{
				doc = jsoup_conn.get ();
			}
System.out.println (jsoup_conn.response ().header ("Location"));
System.out.println (doc);
			//*/

			//
			// HttpClient 不支持 SOCKS 代理
			// http://stackoverflow.com/questions/22937983/how-to-use-socks-5-proxy-with-apache-http-client-4 <-- 绝不这么做 I'm not doing this crap
			//
			/*
			MultipartEntityBuilder meb = MultipartEntityBuilder.create ();
			FormBodyPartBuilder fbpb = FormBodyPartBuilder.create ();
			//fbpb.addField (name, value)
			//FormBodyPart bodyPart = fbpb.build ();
			meb.addTextBody ("image_url", "");
			meb.addBinaryBody ("encoded_image", fMedia);
			meb.addTextBody ("image_content", "");
			meb.addTextBody ("filename", "");
			meb.addTextBody ("hl", "zh-CN");

			//MultipartEntity entity = new MultipartEntity();
			//entity.addPart("user", new StringBody("user"));
			//entity.addPart("password", new StringBody("12345"));
			//entity.addPart("encoded_image", new FileBody(fMedia));

			RequestConfig config = RequestConfig.custom()
				.setConnectTimeout (net_maclife_util_HTTPUtils.DEFAULT_CONNECT_TIMEOUT_SECOND * 1000)
				.setConnectionRequestTimeout (net_maclife_util_HTTPUtils.DEFAULT_READ_TIMEOUT_SECOND * 1000)
				//.setProxy (gfwProxy_forApacheHttpCore)
		        .build();
			HttpPost post = new HttpPost (sURL);
			post.setConfig (config);
			post.setEntity (meb.build ());
//System.out.println (EntityUtils.toString (entity));

			HttpClient httpClient = HttpClients.createDefault ();	//new DefaultHttpClient();
			HttpResponse response = httpClient.execute (post);
			sResponseBody = EntityUtils.toString (response.getEntity());
			//*/

			// 自己构造 multipart/form-data 消息体
			//*
			OutputStream os = null;
			byte[] arrayPostData = null;
			Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
			mapRequestHeaders.put ("Content-Type", "");	// Java HttpURLConnection 你妈的能不能彻底删除 Content-Type 消息头啊
			mapRequestHeaders.put ("Content-Length", "");	// Java HttpURLConnection 你妈的能不能彻底删除 Content-Length 消息头啊
			mapRequestHeaders.put ("User-Agent", "Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:50.0) Gecko/20100101 Firefox/1234 Firefox is versioning emperor #2, Chrome is versioning emperor #1!!!");	// 经过多次测试，User-Agent 和/或 Accept-Language 头是必须要的，否则返回不了正确响应
			mapRequestHeaders.put ("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3");
			if (StringUtils.isNotEmpty (sImageURL))
			{	// GET 方法访问
				if (USE_GFW_PROXY)
					sResponseBody = net_maclife_util_HTTPUtils.CURL_ViaProxy (sURL, mapRequestHeaders, GFW_PROXY_TYPE, GFW_PROXY_HOST, GFW_PROXY_PORT);
				else
					sResponseBody = net_maclife_util_HTTPUtils.CURL (sURL, mapRequestHeaders);
			}
			else
			{	// POST 方法访问
				ByteArrayOutputStream baos = new ByteArrayOutputStream ();
				//net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "image_url", "");

				String sImageContentType = Files.probeContentType (fMedia.toPath ());
				net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "encoded_image", fMedia, sImageContentType);

				//InputStream is = new FileInputStream (fMedia);
				//byte[] arrayImg = IOUtils.toByteArray (is);
				//is.close ();
				//net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "image_content", Base64.encodeBase64String (arrayImg));
				//net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "filename", fMedia.getName ());

				net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "hl", "zh-CN");
				net_maclife_util_HTTPUtils.FillMultipartSimplelyEnd (baos, sMultipartBoundary);
				baos.flush ();

				ByteArrayOutputStream baos_multipart = null;
				//baos_multipart = new ByteArrayOutputStream ();
				//baos_multipart.write (("Content-Type: multipart/form-data; boundary=" + sMultipartBoundary + "\r\nContent-Length: " + baos.size () + "\r\n\r\n").getBytes ());
				//baos.writeTo (baos_multipart);
				//baos_multipart.flush ();
				mapRequestHeaders.put ("Content-Type", "multipart/form-data; boundary=" + sMultipartBoundary);
				mapRequestHeaders.put ("Content-Length", String.valueOf (baos.size ()));
				baos_multipart = baos;

				arrayPostData = baos_multipart.toByteArray ();
//os = new FileOutputStream ("google-image-search-post.data");
//IOUtils.write (arrayPostData, os);
//os.close ();

				URLConnection http = null;

				if (USE_GFW_PROXY)
				{
					//sResponseBody = net_maclife_util_HTTPUtils.CURL_Post_ViaProxy (sURL, mapRequestHeaders, arrayPostData, GFW_PROXY_TYPE, GFW_PROXY_HOST, GFW_PROXY_PORT);
					http = (URLConnection) net_maclife_util_HTTPUtils.CURL
									("POST", sURL, mapRequestHeaders, arrayPostData, true, true, null, false /* 不跟随重定向 */, 0, 0,
										GFW_PROXY_TYPE, GFW_PROXY_HOST, GFW_PROXY_PORT,
										true, true, null, null, null, null, null, null
									);
				}
				else
				{
					//sResponseBody = net_maclife_util_HTTPUtils.CURL_Post (sURL, mapRequestHeaders, arrayPostData);
					http = (URLConnection) net_maclife_util_HTTPUtils.CURL
									("POST", sURL, mapRequestHeaders, arrayPostData, true, true, null, false /* 不跟随重定向 */, 0, 0,
										null, null, 0,
										true, true, null, null, null, null, null, null
									);
				}
				if (http != null)
				{
					int iResponseCode = ((HttpURLConnection)http).getResponseCode();
					String sStatusLine = http.getHeaderField(0);	// HTTP/1.1 200 OK、HTTP/1.1 404 Not Found

					int iMainResponseCode = iResponseCode/100;
					if (iMainResponseCode == 3)
					{
						// 之前测试几天都返回不了正确的结果，是因为默认设置了“跟随重定向”，
						// 可是，openjdk 在自动重定向到该网址时，却丢掉了前面设置的 Accept-Language User-Agent 请求头信息，导致 Google 返回不了预期的结果。
						// 所以，在这里，要截获重定向的网址，加上请求头后再次访问
						String sRedirectedURL = http.getHeaderField ("Location");

						String sSetCookie = http.getHeaderField ("Set-Cookie");

						mapRequestHeaders.remove ("Content-Type");
						mapRequestHeaders.remove ("Content-Length");
						if (StringUtils.isNotEmpty (sSetCookie))
						{
							mapRequestHeaders.put ("Cookie", sSetCookie);
						}

						if (USE_GFW_PROXY)
							sResponseBody = net_maclife_util_HTTPUtils.CURL_ViaProxy (sRedirectedURL, mapRequestHeaders, GFW_PROXY_TYPE, GFW_PROXY_HOST, GFW_PROXY_PORT);
						else
							sResponseBody = net_maclife_util_HTTPUtils.CURL (sRedirectedURL, mapRequestHeaders);
					}
					else if (iMainResponseCode == 2)
					{
net_maclife_wechat_http_BotApp.logger.warning ("就目前的 Google 图片搜索来说，应该不会直接出现 2XX");
					}
					else
					{
net_maclife_wechat_http_BotApp.logger.severe ("http 响应代码是 " + iResponseCode + ", url = " + sURL);
					}
				}
			}

//os = new FileOutputStream("google-image-search-result.html");
//IOUtils.write (sResponseBody, os, net_maclife_wechat_http_BotApp.utf8);
//os.close ();

			doc = Jsoup.parse (sResponseBody, GOOGLE_BASE_URL);
			//*/

			Elements eTopStuff = doc.select ("#topstuff");
			if (eTopStuff.isEmpty ())
			{
net_maclife_wechat_http_BotApp.logger.info (GetName() + " 找不到 #topstuff，也许，搜索出错了？  " + eTopStuff.text ());
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
			}
			Element e图片猜测词语链接 = eTopStuff.select ("a._gUb").first ();
			if (e图片猜测词语链接 == null)
			{
net_maclife_wechat_http_BotApp.logger.info (GetName() + " 找不到 ._gUb，也许，没有结果？  " + eTopStuff.text ());
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
			}

			StringBuilder sbInfo = new StringBuilder ();
			sbInfo.append (e图片猜测词语链接.text ());
			sbInfo.append ("\n");
			sbInfo.append (e图片猜测词语链接.absUrl ("href"));
			Elements e图片来源 = doc.select ("div.normal-header");
			if (! e图片来源.isEmpty ())
			{
				sbInfo.append ("\n\n" + e图片来源.select (".rg-header").first ().text ());	// "包含匹配图片的页面"
				Elements e图片来源标题链接 = e图片来源.select ("h3.r > a");
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
			}
//System.out.println (sbInfo);
			SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, sbInfo.toString ());

			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED | net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
		}
		catch (Throwable e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (fis != null)
				try
				{
					fis.close ();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
		}
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	@Override
	public int OnEmotionMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, File fMedia, String sImageURL
		)
	{
		return OnImageMessageReceived (jsonMessage, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, fMedia, sImageURL);
	}

	public static void main (String[] args)
	{
		if (args.length < 1)
		{
			net_maclife_wechat_http_BotApp.logger.severe ("参数 1 需要指定图片文件名");
			return;
		}
		String sMediaFileNameOrURL = args[0];
		net_maclife_wechat_http_Bot bot = new net_maclife_wechat_http_Bot_GoogleImageSearch ();
		if (StringUtils.startsWithIgnoreCase (sMediaFileNameOrURL, "http"))
		{
			File fMedia = new File (sMediaFileNameOrURL);
			bot.OnImageMessageReceived (null, null, "", "", false, null, "", "", false, null, "", "", false, null, "", "", null, "", "", "", fMedia, sMediaFileNameOrURL);
		}
		else
		{
			File fMedia = new File (sMediaFileNameOrURL);
			bot.OnImageMessageReceived (null, null, "", "", false, null, "", "", false, null, "", "", false, null, "", "", null, "", "", "", fMedia, null);
		}
	}
}
