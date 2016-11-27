import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;

import javax.script.*;
//import javax.xml.parsers.*;

import org.apache.commons.io.*;
import org.apache.commons.lang3.*;
//import org.w3c.dom.*;
//import org.xml.sax.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import nu.xom.*;

public class net_maclife_wechat_http_WechatBot implements Runnable
{
	public static ExecutorService executor = Executors.newFixedThreadPool (5);

	public static final int WECHAT_ACCOUNT_TYPE_MASK__Public = 0x08;	// 公众号
	public static final int WECHAT_ACCOUNT_TYPE_MASK__Tencent = 0x10;	// 腾讯自己的公众号
	public static final int WECHAT_ACCOUNT_TYPE_MASK__WeChat = 0x20;	// 腾讯自己的公众号 - 微信团队

	static CookieManager cookieManager = new CookieManager ();
	static
	{
		cookieManager.setCookiePolicy (CookiePolicy.ACCEPT_ALL);
	}

	static ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
	static ScriptEngine public_jse = scriptEngineManager.getEngineByName("JavaScript");
	static ScriptContext public_jsContext = (public_jse==null ? null : public_jse.getContext ());

	/*
	static DocumentBuilderFactory xmlBuilderFactory = DocumentBuilderFactory.newInstance ();
	static DocumentBuilder xmlBuilder = null;
	static
	{
		//xmlBuilderFactory.setValidating (false);
		xmlBuilderFactory.setIgnoringElementContentWhitespace (true);
		try
		{
			xmlBuilder = xmlBuilderFactory.newDocumentBuilder ();
		}
		catch (ParserConfigurationException e)
		{
			e.printStackTrace();
		}
	}
	*/
	static nu.xom.Builder xomBuilder = new nu.xom.Builder();

	static JsonFactory _JSON_FACTORY = new JsonFactory();


	BotEngine engine;
	public net_maclife_wechat_http_WechatBot ()
	{
		engine = new BotEngine ();
	}
	public BotEngine GetBotEngine ()
	{
		return engine;
	}

	public static String GetLoginID () throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, ScriptException
	{
		String sURL = "https://login.weixin.qq.com/jslogin?appid=wx782c26e4c19acffb&redirect_uri=https%3A%2F%2Fwx.qq.com%2Fcgi-bin%2Fmmwebwx-bin%2Fwebwxnewloginpage&fun=new&lang=en_US&_=" + System.currentTimeMillis ();

		String sContent = net_maclife_util_HTTPUtils.CURL (sURL);	// window.QRLogin.code = 200; window.QRLogin.uuid = "QegF7Tukgw==";
System.out.println ("获取 LoginID 的 http 响应消息体:");
System.out.println ("	[" + sContent + "]");

		String sLoginID = public_jse.eval (StringUtils.replace (sContent, "window.QRLogin.", "var ") + " uuid;").toString ();
System.out.println ("获取到的 LoginID:");
System.out.println ("	[" + sLoginID + "]");

		return sLoginID;
	}

	public static File GetLoginQRCodeImageFile (String sLoginID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		String sURL = "https://login.weixin.qq.com/qrcode/" + sLoginID;
		String sScanLoginURL = "https://login.weixin.qq.com/l/" + sLoginID;	// 二维码图片解码后的 URL
		String sFileName = "/tmp/wechat-login-qrcode-image-" + sLoginID + ".jpg";
		String sFileName_PNG = "/tmp/wechat-login-qrcode-image-" + sLoginID + "-10%.png";
		File fOutputFile = new File (sFileName);
		InputStream is = net_maclife_util_HTTPUtils.CURL_Stream (sURL);
		OutputStream os = new FileOutputStream (fOutputFile);
		IOUtils.copy (is, os);
System.out.println ("获取 LoginQRCode 的 http 响应消息体（保存到文件）:");
System.out.println ("	" + fOutputFile + "");
		//String sQRCode = "";
		return fOutputFile;
	}

	public static Object 等待二维码被扫描以便登录 (String sLoginID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, ScriptException, ValidityException, ParsingException, URISyntaxException
	{
		String sLoginURL = "";
		String sLoginResultCode = "";
		int nLoginResultCode = 0;
		int nLoopCount = 0;

		String sURL = null;
		String sContent = null;
	while_loop:
		do
		{
			nLoginResultCode = 0;
			nLoopCount ++;
			long nTimestamp = System.currentTimeMillis ();
			long r = ~ nTimestamp;	// 0xFFFFFFFFFFFFFFFFL ^ nTimestamp;
			boolean bLoginIcon = false;	// true;
			sURL = "https://login.weixin.qq.com/cgi-bin/mmwebwx-bin/login?loginicon=" + bLoginIcon + "&uuid=" + sLoginID + "&tip=0&r=" + r + "&_=" + nTimestamp;

System.out.println (String.format ("%3d", nLoopCount) + " 循环等待二维码被扫描以便登录 的 http 响应消息体:");
			// window.code=408;	未扫描/超时。只要未扫描就不断循环，但 web 端好像重复 12 次（每次 25054 毫秒）左右后，就重新刷新 LoginID
			// window.code=201;	已扫描
			// window.code=200;	已确认登录
			// window.redirect_uri="https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxnewloginpage?ticket=A8qwapRV_lQ44viWM0mZmnpm@qrticket_0&uuid=gYiEFqEQdw==&lang=en_US&scan=1479893365";
			sContent = net_maclife_util_HTTPUtils.CURL (sURL);
System.out.println ("	[" + sContent + "]");
			if (StringUtils.isEmpty (sContent))
			{
				nLoginResultCode = 400;
System.out.println ("	空内容，二维码可能已经失效");
				break;
			}

			String sJSCode = StringUtils.replace (sContent, "window.", "var ");
			sLoginResultCode = public_jse.eval (sJSCode + " code;").toString ();
			nLoginResultCode = Double.valueOf (sLoginResultCode).intValue ();
System.out.println ("	获取到的 LoginResultCode:");
System.out.println ("	[" + nLoginResultCode + "]");

			switch (nLoginResultCode)
			{
			case 408:	// 假设等同于 http 响应码 408: Request Time-out
System.out.println ("	请求超时");
				break;
			case 201:	// 假设等同于 http 响应码 201: Created
System.out.println ("	已扫描");
				break;
			case 200:	// 假设等同于 http 响应码 200: OK
				sLoginURL = public_jse.eval (sJSCode + " redirect_uri;").toString ();
System.out.println ("已确认登录，浏览器需要重定向到的登录页面网址为:");
System.out.println ("	[" + sLoginURL + "]");
				sLoginURL = sLoginURL + "&fun=new&version=v2";
System.out.println ("网址加上 &fun=new&version=v2:");
System.out.println ("	[" + sLoginURL + "]");
				break;
			case 400:	// 假设等同于 http 响应码 400: Bad Request
System.out.println ("	二维码已过期");
				//throw new RuntimeException ("二维码已过期");
				//break while_loop;
				return nLoginResultCode;
			default:
System.out.println ("	未知的响应代码");
				break while_loop;
			}
		} while (nLoginResultCode != 200);

		URLConnection http = net_maclife_util_HTTPUtils.CURL_Connection (sLoginURL);
		int iResponseCode = ((HttpURLConnection)http).getResponseCode();
		int iMainResponseCode = iResponseCode/100;
		if (iMainResponseCode==2)
		{
System.out.println ("登录页面设置的 Cookie:");
			Map<String, List<String>> mapHeaders = http.getHeaderFields ();
			cookieManager.put (new URI(sLoginURL), mapHeaders);
			for (String sHeaderName : mapHeaders.keySet ())
			{
				if (StringUtils.equalsIgnoreCase (sHeaderName, "Set-Cookie"))
				{
					List<String> listCookies = mapHeaders.get (sHeaderName);
System.out.println ("	[" + listCookies + "]");
				}
			}

			InputStream is = http.getInputStream ();
			//Document xml = xmlBuilder.parse (is);
			nu.xom.Document doc = xomBuilder.build (is);
			is.close ();
			nu.xom.Element eXML = doc.getRootElement ();
			//sContent = IOUtils.toString (is, net_maclife_util_HTTPUtils.UTF8_CHARSET);
System.out.println ("登录页面消息体:");
//System.out.println ("	[" + sContent + "]");
System.out.println ("	[" + eXML.toXML() + "]");
System.out.println ("	UIN=[" + eXML.getFirstChildElement ("wxuin").getValue () + "]");
System.out.println ("	SID=[" + eXML.getFirstChildElement ("wxsid").getValue () + "]");
System.out.println ("	SKEY=[" + eXML.getFirstChildElement ("skey").getValue () + "]");
System.out.println ("	TICKET=[" + eXML.getFirstChildElement ("pass_ticket").getValue () + "]");
			Map<String, Object> mapResult = new HashMap <String, Object> ();
			mapResult.put ("UserID", eXML.getFirstChildElement ("wxuin").getValue ());
			mapResult.put ("SessionID", eXML.getFirstChildElement ("wxsid").getValue ());
			mapResult.put ("SessionKey", eXML.getFirstChildElement ("skey").getValue ());
			mapResult.put ("PassTicket", eXML.getFirstChildElement ("pass_ticket").getValue ());
			mapResult.put ("LoginResultCode", nLoginResultCode);
			return mapResult;

		}
		return nLoginResultCode;
	}

	public static String MakeBaseRequestValueJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID)
	{
		return
			"	{\n" +
			"		\"Uin\": \"" + sUserID + "\",\n" +
			"		\"Sid\": \"" + sSessionID + "\",\n" +
			"		\"Skey\": \"" + sSessionKey + "\",\n" +
			"		\"DeviceID\": \"" + sDeviceID + "\"\n" +
			"	}" +
			"";
	}

	public static String MakeFullBaseRequestJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID)
	{
		return
		"{\n" +
		"	\"BaseRequest\":\n" + MakeBaseRequestValueJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + "\n" +
		"}\n";
	}

	static Random random = new Random ();
	public static String MakeDeviceID ()
	{
		long nRand = random.nextLong () & 0x7FFFFFFFFFFFFFFFL;
		return "e" + String.format ("%015d", nRand).substring (0, 15);	// System.currentTimeMillis ();
	}

	public static JsonNode WebWeChatInit (String sUserID, String sSessionID, String sSessionKey, String sPassTicket) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
		// https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxinit?r=1703974212&lang=zh_CN&pass_ticket=ZfvpI6wcO7N5PTkacmWK9zUTXpUOB3kqre%2BrkQ8IAtHDAIP2mc2psB5eDH8cwzsp
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxinit?r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + sPassTicket;
System.out.println ("WebWeChatInit 的 URL:");
System.out.println ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
System.out.println ("发送 WebWeChatInit 的 http 请求消息头 (Content-Type):");
System.out.println ("	" + mapRequestHeaders);

		String sRequestBody_JSONString = MakeFullBaseRequestJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID ());
System.out.println ("发送 WebWeChatInit 的 http 请求消息体:");
System.out.println (sRequestBody_JSONString);

		InputStream is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
		ObjectMapper om = new ObjectMapper ();
		JsonNode node = om.readTree (is);
System.out.println ("获取 WebWeChatInit 的 http 响应消息体:");
System.out.println ("	[" + node + "]");
		//
		return node;
	}

	static int nRecycledMessageID = 0;	// 0000 - 9999
	public static long GenerateLocalMessageID ()
	{
		if (nRecycledMessageID == 9999)
			nRecycledMessageID = 0;
		return System.currentTimeMillis () * 10000 + (nRecycledMessageID ++);
	}

	public static String MakeFullStatusNotifyRequestJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sMyAccountHashInThisSession)
	{
		return
		"{\n" +
		"	\"BaseRequest\":\n" + MakeBaseRequestValueJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + ",\n" +
		"	\"Code\": 3,\n" +
		"	\"FromUserName\": \"" + sMyAccountHashInThisSession + "\",\n" +
		"	\"ToUserName\": \"" + sMyAccountHashInThisSession + "\",\n" +
		"	\"ClientMsgId\": " + GenerateLocalMessageID () + "\n" +
		"}\n";
	}
	public static JsonNode WebWeChatStatusNotify (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, String sMyAccountHashInThisSession) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxstatusnotify?lang=zh_CN&pass_ticket=" + sPassTicket;
System.out.println ("WebWeChatStatusNotify 的 URL:");
System.out.println ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
System.out.println ("发送 WebWeChatStatusNotify 的 http 请求消息头 (Content-Type):");
System.out.println ("	" + mapRequestHeaders);

		String sRequestBody_JSONString = MakeFullStatusNotifyRequestJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID (), sMyAccountHashInThisSession);
System.out.println ("发送 WebWeChatStatusNotify 的 http 请求消息体:");
System.out.println (sRequestBody_JSONString);
		InputStream is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
		ObjectMapper om = new ObjectMapper ();
		JsonNode node = om.readTree (is);
System.out.println ("获取 WebWeChatStatusNotify 的 http 响应消息体:");
System.out.println ("	[" + node + "]");
		//
		return node;
	}

	public static JsonNode WebWeChatGetContacts (String sUserID, String sSessionID, String sSessionKey, String sPassTicket) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxgetcontact?r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + sPassTicket;
System.out.println ("WebWeChatGetContacts 的 URL:");
System.out.println ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
System.out.println ("发送 WebWeChatGetContacts 的 http 请求消息头 (Content-Type):");
System.out.println ("	" + mapRequestHeaders);

String sRequestBody_JSONString = MakeFullBaseRequestJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID ());
System.out.println ("发送 WebWeChatGetContacts 的 http 请求消息体:");
System.out.println ("	" + sRequestBody_JSONString);

		InputStream is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
		ObjectMapper om = new ObjectMapper ();
		JsonNode node = om.readTree (is);
System.out.println ("获取 WebWeChatGetContacts 的 http 响应消息体:");
System.out.println ("	[" + node + "]");
		//
		return node;
	}

	public static String MakeFullGetRoomContactRequestJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, JsonNode jsonContacts)
	{
		List<String> listRoomIDs = new ArrayList ();
		JsonNode jsonMemberList = jsonContacts.get ("MemberList");
		for (int i=0; i<jsonMemberList.size (); i++)
		{
			JsonNode jsonContact = jsonMemberList.get (i);
			String sUserHashID = jsonContact.get ("UserName").asText ();
			if (StringUtils.startsWith (sUserHashID, "@@"))
				listRoomIDs.add (sUserHashID);
		}
		StringBuilder sbBody = new StringBuilder ();
		sbBody.append ("{\n");
		sbBody.append ("	\"BaseRequest\":\n" + MakeBaseRequestValueJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + ",\n");
		sbBody.append ("	\"Count\": " + listRoomIDs.size () + ",\n");
		sbBody.append ("	\"List\":\n");
		sbBody.append ("	[\n");
		for (int i=0; i<listRoomIDs.size (); i++)
		{
			sbBody.append ("		{\n");
			sbBody.append ("			\"UserName\": \"" + listRoomIDs.get (i) + "\",\n");
			sbBody.append ("			\"EncryChatRoomId\": \"\"\n");
			sbBody.append ("		}");
			if (i != listRoomIDs.size ()-1)
			{
				sbBody.append (",");
			}
			sbBody.append ("\n");
		}
		sbBody.append ("	]\n");
		sbBody.append ("}\n");
		return sbBody.toString ();
	}

	public static JsonNode WebWeChatGetRoomContacts (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, JsonNode jsonContacts) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxbatchgetcontact?type=ex&r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + sPassTicket;
System.out.println ("WebWeChatGetRoomContacts 的 URL:");
System.out.println ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
		String sRequestBody_JSONString = MakeFullGetRoomContactRequestJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID (), jsonContacts);
System.out.println ("发送 WebWeChatGetRoomContacts 的 http 请求消息体:");
System.out.println ("	" + sRequestBody_JSONString);
		InputStream is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
		ObjectMapper om = new ObjectMapper ();
		JsonNode node = om.readTree (is);
System.out.println ("获取 WebWeChatGetRoomContacts 的 http 响应消息体:");
System.out.println ("	[" + node + "]");
		//
		return node;
	}

	/**
	 * 从 WebWeChatGetContacts 返回的 JsonNode 中的 MemberList 中找到一个符合条件的 Contact。
	 * @param jsonMemberList
	 * @param sUserIDInThisSession 类似  @********** filehelper weixin 等 ID，可以一一对应
	 * @param sAlias 如果 UserIDInThisSession 为空，之尝试根据 sAlias 获取
	 * @param sRemarkName 如果 Alias 也为空，则根据备注名称获取
	 * @param sNickName 如果 Alias 也为空，则根据昵称获取
	 * @return 搜索到的 Contact 的 JsonNode。正常情况下应该为 1 个，但也可能为空，也可能为多个。
	 */
	public static JsonNode GetContact (JsonNode jsonMemberList, String sUserIDInThisSession, String sAlias, String sRemarkName, String sNickName)
	{
		JsonNode jsonUser = null;
		for (int i=0; i<jsonMemberList.size (); i++)
		{
			JsonNode node = jsonMemberList.get (i);

			if (StringUtils.isNotEmpty (sUserIDInThisSession))
			{
				String sTemp = node.get ("UserName").asText ();
				if (StringUtils.equalsIgnoreCase (sUserIDInThisSession, sTemp))
				{
					jsonUser = node;
					break;
				}
			}

			if (StringUtils.isNotEmpty (sAlias))
			{
				String sTemp = node.get ("Alias").asText ();
				if (StringUtils.equalsIgnoreCase (sAlias, sTemp))
				{
					jsonUser = node;
					break;
				}
			}

			if (StringUtils.isNotEmpty (sRemarkName))
			{
				String sTemp = node.get ("RemarkName").asText ();
				if (StringUtils.equalsIgnoreCase (sRemarkName, sTemp))
				{
					jsonUser = node;
					break;
				}
			}

			if (StringUtils.isNotEmpty (sNickName))
			{
				String sTemp = node.get ("NickName").asText ();
				if (StringUtils.equalsIgnoreCase (sNickName, sTemp))
				{
					jsonUser = node;
					break;
				}
			}
		}
		return jsonUser;
	}

	public static JsonNode WebWeChatSendMessage (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_AccountHash, String sTo_AccountHash, int nMessageType, Object oMessage) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendmsg?r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + sPassTicket;
System.out.println ("WebWeChatSendMessage 的 URL:");
System.out.println ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
		String sRequestBody_JSONString = "";
		switch (nMessageType)
		{
		case 1:
			sRequestBody_JSONString = MakeFullTextMessageJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID (), sFrom_AccountHash, sTo_AccountHash, (String)oMessage);
			break;
		default:
			break;
		}
System.out.println ("发送 WebWeChatSendMessage 的 http 请求消息体:");
System.out.println ("	" + sRequestBody_JSONString);
		InputStream is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
		ObjectMapper om = new ObjectMapper ();
		JsonNode node = om.readTree (is);
System.out.println ("获取 WebWeChatSendMessage 的 http 响应消息体:");
System.out.println ("	[" + node + "]");
		//
		return node;
	}

	public static String MakeFullTextMessageJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sMessage)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		return
		"{\n" +
		"	\"BaseRequest\":\n" + MakeBaseRequestValueJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + ",\n" +
		"	\"Msg\":\n" +
		"	{\n" +
		"		\"Type\": 1,\n" +
		"		\"Content\": \"" + sMessage + "\",\n" +
		"		\"FromUserName\": \"" + sFrom + "\",\n" +
		"		\"ToUserName\": \"" + sTo + "\",\n" +
		"		\"LocalID\": \"" + nLocalMessageID + "\",\n" +
		"		\"ClientMsgId\": \"" + nLocalMessageID + "\"\n" +
		"	}\n" +
		"}\n";
	}
	public static JsonNode WebWeChatSendTextMessage (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_AccountHash, String sTo_AccountHash, String sMessage) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
		return WebWeChatSendMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sFrom_AccountHash, sTo_AccountHash, 1, sMessage);
	}

	public static String MakeSyncCheckKeys (JsonNode jsonSyncCheckKeys)
	{
		StringBuilder sbSyncCheckKeys = new StringBuilder ();
		JsonNode listKeys = jsonSyncCheckKeys.get ("List");
		for (int i=0; i<listKeys.size (); i++)
		{
			if (i != 0)
			{
				sbSyncCheckKeys.append ("%7C");	// %7C: |
			}
			JsonNode jsonKey = listKeys.get (i);
			sbSyncCheckKeys.append (jsonKey.get ("Key").asText ());
			sbSyncCheckKeys.append ("_");
			sbSyncCheckKeys.append (jsonKey.get ("Val").asText ());
		}
		return sbSyncCheckKeys.toString ();
	}
	public static String MakeFullWeChatSyncJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, JsonNode jsonSyncKey)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		return
		"{\n" +
		"	\"BaseRequest\":\n" + MakeBaseRequestValueJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + ",\n" +
		"	\"SyncKey\": " + jsonSyncKey + ",\n" +
		"	\"rr\": " + System.currentTimeMillis ()/1000 + "\n" +
		"}\n";
	}
	public static String MakeCookieValue (String sUserID, String sSessionID, String sAuthTicket, String sDataTicket, String s_webwxuvid, String s_wxloadtime, String s_mm_lang)
	{
		return
			"wxuin=" + sUserID +
			"; wxsid=" + sSessionID +
			"; webwx_auth_ticket=" + sAuthTicket +
			"; webwx_data_ticket=" + sDataTicket +
			"; webwxuvid=" + s_webwxuvid +
			"; wxloadtime=" + s_wxloadtime +
			"; mm_lang=" + s_mm_lang +
			";"
			;
	}
	public static String MakeCookieValue (List<HttpCookie> listCookies)
	{
		StringBuilder sbResult = new StringBuilder ();
		for (HttpCookie cookie : listCookies)
		{
			if (cookie.hasExpired ())	// 已过期的 Cookie 不再送 （虽然通常不会走到这一步）
				continue;

			sbResult.append (cookie.getName ());
			sbResult.append ("=");
			sbResult.append (cookie.getValue ());
			sbResult.append ("; ");
		}
		return sbResult.toString ();
	}
	public static JsonNode WebWeChatGetMessages (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, JsonNode jsonSyncCheckKeys) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, ScriptException, URISyntaxException
	{
		String sSyncCheckKeys = MakeSyncCheckKeys (jsonSyncCheckKeys);
		String sSyncCheckURL = "https://webpush.wx2.qq.com/cgi-bin/mmwebwx-bin/synccheck?r=" + System.currentTimeMillis () + "&skey=" + URLEncoder.encode (sSessionKey, "UTF-8") + "&sid=" + URLEncoder.encode (sSessionID, "UTF-8") + "&uin=" + sUserID + "&deviceid=" + MakeDeviceID () + "&synckey=" +  sSyncCheckKeys + "&_=" + System.currentTimeMillis ();
System.out.println ("WebWeChatGetMessages 中 synccheck 的 URL:");
System.out.println ("	" + sSyncCheckURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		CookieStore cookieStore = cookieManager.getCookieStore ();
		List<HttpCookie> listCookies = cookieStore.get (new URI(sSyncCheckURL));
		String sCookieValue = "";
		/*
		String cookie_wxDataTicket="", cookie_wxAuthTicket="", cookie_webwxuvid="", cookie_wxloadtime="", cookie_mm_lang="";
		for (HttpCookie cookie : listCookies)
		{
			if (cookie.hasExpired ())	// 已过期的 Cookie 不再送 （虽然通常不会走到这一步）
				continue;

			if (cookie.getName ().equalsIgnoreCase ("webwx_auth_ticket"))
				cookie_wxAuthTicket = cookie.getValue ();
			else if (cookie.getName ().equalsIgnoreCase ("webwx_data_ticket"))
				cookie_wxDataTicket = cookie.getValue ();
			else if (cookie.getName ().equalsIgnoreCase ("webwxuvid"))
				cookie_webwxuvid = cookie.getValue ();
			else if (cookie.getName ().equalsIgnoreCase ("wxloadtime"))
				cookie_wxloadtime = cookie.getValue ();
			else if (cookie.getName ().equalsIgnoreCase ("mm_lang"))
				cookie_mm_lang = cookie.getValue ();
		}
		sCookieValue = MakeCookieValue (sUserID, sSessionID, cookie_wxAuthTicket, cookie_wxDataTicket, cookie_webwxuvid, cookie_wxloadtime, cookie_mm_lang);
		*/
		sCookieValue = MakeCookieValue (listCookies);
		mapRequestHeaders.put ("Cookie", sCookieValue);	// 避免服务器返回 1100 1102 代码？
System.out.println ("发送 WebWeChatGetMessages 中 synccheck 的 http 请求消息头 (Cookie):");
System.out.println ("	[" + mapRequestHeaders + "]");

		String sContent = net_maclife_util_HTTPUtils.CURL (sSyncCheckURL, mapRequestHeaders);	// window.synccheck={retcode:"0",selector:"2"}
System.out.println ("获取 WebWeChatGetMessages 中 synccheck 的 http 响应消息体:");
System.out.println ("	[" + sContent + "]");

		String sJSCode = StringUtils.replace (sContent, "window.", "var ");
		String sSyncCheckReturnCode = public_jse.eval (sJSCode + "; synccheck.retcode;").toString ();
		String sSyncCheckSelector = public_jse.eval (sJSCode + "; synccheck.selector;").toString ();

		JsonNode jsonResult = null;
		if (StringUtils.equalsIgnoreCase (sSyncCheckReturnCode, "0"))
		{
			switch (sSyncCheckSelector)
			{
			case "2":	// 有新消息
				String sSyncURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsync?sid=" + URLEncoder.encode (sSessionID, "UTF-8") + "&skey" + URLEncoder.encode (sSessionKey, "UTF-8") + "&lang=zh_CN&pass_ticket=" +  sPassTicket;
System.out.println ("WebWeChatGetMessages 中 webwxsync 的 URL:");
System.out.println ("	" + sSyncURL);

				//mapRequestHeaders = new HashMap<String, Object> ();
				mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
				//mapRequestHeaders.put ("Cookie", sCookieValue);	// 避免服务器返回 "Ret": 1 代码
				String sRequestBody_JSONString = MakeFullWeChatSyncJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID (), jsonSyncCheckKeys);
System.out.println ("发送 WebWeChatGetMessages 中 webwxsync 的 http 请求消息头 (Cookie & Content-Type):");
System.out.println (mapRequestHeaders);
System.out.println ("发送 WebWeChatGetMessages 中 webwxsync 的 http 请求消息体:");
System.out.println (sRequestBody_JSONString);
				InputStream is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sSyncURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
				ObjectMapper om = new ObjectMapper ();
				JsonNode node = om.readTree (is);
System.out.println ("获取 WebWeChatGetMessages 中 webwxsync 的 http 响应消息体:");
System.out.println ("	[" + node + "]");
				jsonResult = node;
				break;
			case "0":	// nothing
				break;
			case "7":	// 进入离开聊天页面
			default:
				break;
			}
		}
		else if (StringUtils.equalsIgnoreCase (sSyncCheckReturnCode, "1100") || StringUtils.equalsIgnoreCase (sSyncCheckReturnCode, "1101") || StringUtils.equalsIgnoreCase (sSyncCheckReturnCode, "1102"))
		{
			throw new IllegalStateException ("微信被退出 / 被踢出了");
		}
		//else if (StringUtils.equalsIgnoreCase (sSyncCheckReturnCode, "1102"))	// 当 skey=*** 不小心输错变成 skey*** 时返回了 1102 错误
		{
			//throw new IllegalArgumentException ("参数错误");
		}
		//
		return jsonResult;
	}

	public static void Logout ()
	{
		// https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxlogout?redirect=1&type=1&skey=@crypt_1df7c02d_9effb9a7d4292af4681c79dab30b6a57	// 加上表单数据 uin=****&sid=**** ，POST

		// 被踢出后重新登录
		// https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxnewloginpage?ticket=A-1wUN8dm6D-nIJH8m8g7yfh@qrticket_0&uuid=YZzRE6skKQ==&lang=zh_CN&scan=1479978616 对比最初的登录参数，后面是新加的： &fun=new&version=v2&lang=zh_CN
		//    <error><ret>0</ret><message></message><skey>@crypt_1df7c02d_131d1d0335be6fd38333592c098a5b16</skey><wxsid>GrS6IjctQkOxs0PP</wxsid><wxuin>2100343515</wxuin><pass_ticket>T%2FduUWTWjODelhztGXZAO1b3u7S5Ddy8ya8fP%2BYhZlRjxR1ERMDXHKbaCs6x2mQP</pass_ticket><isgrayscale>1</isgrayscale></error>
	}

	class BotEngine implements Runnable
	{
		// 几种 Bot 链处理方式
		// 大于 0: 本 Bot 已处理，但请后面的 Bot 继续处理
		//      0: 本 Bot 没处理，但也请后面的 Bot 继续处理
		// 小于 0: 就此打住，后面的 Bot 别再处理了
		public static final int BOT_CHAIN_PROCESS_MODE__PROCESSED_AND_CONTINUE = 1;
		public static final int BOT_CHAIN_PROCESS_MODE__NOT_PROCESSED_BUT_CONTINUE = 0;
		public static final int BOT_CHAIN_PROCESS_MODE__STOP_HERE = -1;

		public static final int MSG_TYPE__TEXT = 1;
		public static final int MSG_TYPE__IMAGE = 3;
		public static final int MSG_TYPE__VOICE = 34;
		public static final int MSG_TYPE__VERIFY_MSG = 37;
		public static final int MSG_TYPE__POSSIBLE_FRIND_MSG = 40;
		public static final int MSG_TYPE__VCARD = 42;
		public static final int MSG_TYPE__VIDEO_CALL = 43;
		public static final int MSG_TYPE__EMOTION = 47;
		public static final int MSG_TYPE__GPS_POSITION = 48;
		public static final int MSG_TYPE__URL = 49;
		public static final int MSG_TYPE__VOIP_MSG = 50;
		public static final int MSG_TYPE__INIT = 51;
		public static final int MSG_TYPE__VOIP_NOTIFY = 52;
		public static final int MSG_TYPE__VOIP_INVITE = 53;
		public static final int MSG_TYPE__SHORT_VIDEO = 62;
		public static final int MSG_TYPE__SYSTEM_NOTICE = 9999;
		public static final int MSG_TYPE__SYSTEM = 10000;
		public static final int MSG_TYPE__REVOKE = 10002;

		List<Bot> listBots = new ArrayList<Bot> ();

		String sUserID     = null;
		String sSessionID  = null;
		String sSessionKey = null;
		String sPassTicket = null;
		String sMyAccountHashInThisSession = null;

		/**
		 * Bot 引擎线程： 不断循环尝试登录，直到登录成功。如果登录成功后被踢下线，依旧不断循环尝试登录…… 登录成功后，不断同步消息，直到被踢下线（同上，依旧不断循环尝试登录）
		 */
		@Override
		public void run ()
		{
			try
			{
				String sLoginID = null;
				File f = null;
				Map<String, Object> mapWaitLoginResult = null;
				Object o = null;
			_outer_loop:
				do
				{
					// 1. 获得登录 ID
					sLoginID = GetLoginID ();

					// 2. 根据登录 ID，获得登录地址的二维码图片 （暂时只能扫描图片，不能根据登录地址自动登录 -- 暂时无法截获手机微信 扫描二维码以及确认登录时 时带的参数，所以无法模拟自动登录）
					f = GetLoginQRCodeImageFile (sLoginID);

					// 3. 等待二维码扫描（）、以及确认登录
					do
					{
						o = 等待二维码被扫描以便登录 (sLoginID);
						if (o instanceof Integer)
						{
							int n = (Integer) o;
							if (n == 400)	// Bad Request / 二维码已失效
							{
								continue _outer_loop;
							}
							else	// 大概只有 200 才能出来：当是 200 时，但访问登录页面失败时，可能会跑到此处
							{
								//
							}
						}
					} while (! (o instanceof Map<?, ?>));
					mapWaitLoginResult = (Map<String, Object>) o;
					sUserID     = (String) mapWaitLoginResult.get ("UserID");
					sSessionID  = (String) mapWaitLoginResult.get ("SessionID");
					sSessionKey = (String) mapWaitLoginResult.get ("SessionKey");
					sPassTicket = (String) mapWaitLoginResult.get ("PassTicket");

					// 4. 确认登录后，初始化 Web 微信，返回初始信息
					JsonNode jsonInit = WebWeChatInit (sUserID, sSessionID, sSessionKey, sPassTicket);
					sMyAccountHashInThisSession = jsonInit.get ("User").get ("UserName").asText ();
					JsonNode jsonSyncCheckKeys = jsonInit.get ("SyncKey");

					JsonNode jsonStatusNotify = WebWeChatStatusNotify (sUserID, sSessionID, sSessionKey, sPassTicket, sMyAccountHashInThisSession);

					// 5. 获取联系人
					JsonNode jsonContacts = WebWeChatGetContacts (sUserID, sSessionID, sSessionKey, sPassTicket);
					JsonNode jsonRoomMemberContacts = WebWeChatGetRoomContacts (sUserID, sSessionID, sSessionKey, sPassTicket, jsonContacts);	// 补全各个群的联系人列表

					String sSayHiMessage = "机器人已通过微信网页版的通信协议登录，人机已合体。[奸笑]";
					WebWeChatSendTextMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sMyAccountHashInThisSession, sMyAccountHashInThisSession, sSayHiMessage + "\n\n" + new java.sql.Timestamp(System.currentTimeMillis ()));

					JsonNode jsonMessage = null;
					try
					{
						while (! Thread.interrupted ())
						{
							jsonMessage = WebWeChatGetMessages (sUserID, sSessionID, sSessionKey, sPassTicket, jsonSyncCheckKeys);
							if (jsonMessage == null)
							{
								TimeUnit.SECONDS.sleep (2);
								continue;
							}

							JsonNode jsonBaseResponse = jsonMessage.get ("BaseResponse");
							int nRet = jsonBaseResponse.get ("Ret").asInt ();
							String sErrMsg = jsonBaseResponse.get ("ErrMsg").asText ();
							if (nRet != 0)
							{
								System.err.print ("同步消息失败: 代码=" + nRet);
								if (StringUtils.isNotEmpty (sErrMsg))
								{
									System.err.print ("，消息=" + sErrMsg);
								}
								System.err.println ();
								TimeUnit.SECONDS.sleep (2);
								continue;
							}

							// 处理“接收”到的（实际是同步获取而来）消息
							jsonSyncCheckKeys = jsonMessage.get ("SyncCheckKey");	// 新的 SyncCheckKeys

							// 处理（实际上，应该交给 Bot 们处理）
							OnMessageReceived (jsonMessage);
						}
					}
					catch (IllegalStateException e)
					{
						continue _outer_loop;
					}
				}
				while (! Thread.interrupted ());
			}
			catch (InterruptedException e)
			{
				e.printStackTrace ();
			}
			catch (Exception e)
			{
				e.printStackTrace ();
			}

System.out.println ("bot 线程退出");
			try
			{
				WebWeChatSendTextMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sMyAccountHashInThisSession, sMyAccountHashInThisSession, "bot 线程退出" + "\n\n" + new java.sql.Timestamp(System.currentTimeMillis ()));
			}
			catch (Exception e)
			{

			}
		}

		void OnMessageReceived (JsonNode jsonMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
		{
			int i = 0;

			JsonNode jsonAddMsgCount = jsonMessage.get ("AddMsgCount");
			JsonNode jsonAddMsgList = jsonMessage.get ("AddMsgList");
			for (i=0; i<jsonAddMsgCount.asInt (); i++)
			{
				JsonNode jsonNode = jsonAddMsgList.get (i);
				int nMsgType = jsonNode.get ("MsgType").asInt ();
				String sContent = jsonNode.get ("Content").asText ();
				sContent = StringUtils.replaceEach (sContent, new String[]{"<br/>", "&lt;", "&gt;"}, new String[]{"\n", "<", ">"});
				String sRoom = null;
				String sFrom = jsonNode.get ("FromUserName").asText ();
				String sTo = jsonNode.get ("ToUserName").asText ();
				boolean isFromRoomOrChannel = StringUtils.startsWith (sFrom, "@@");	// 是否来自 聊天室/群/频道 的消息
				if (isFromRoomOrChannel)
				{
					sRoom = sFrom;
					// 找出发送人的 UserID
					String[] arrayContents = sContent.split ("\n", 2);
					sFrom = arrayContents[0];
					sContent = arrayContents[1];
				}
				if (StringUtils.equalsIgnoreCase (sMyAccountHashInThisSession, sFrom))	// 自己发送的消息，不再处理
					continue;
				switch (nMsgType)
				{
				case MSG_TYPE__TEXT:
					// 简单的复读机功能
					if (isFromRoomOrChannel)
					{
						WebWeChatSendTextMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sMyAccountHashInThisSession, sRoom, sContent + "\n\n" + new java.sql.Timestamp(System.currentTimeMillis ()));
					}
					else
					{
						WebWeChatSendTextMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sMyAccountHashInThisSession, sFrom, sContent + "\n\n" + new java.sql.Timestamp(System.currentTimeMillis ()));
					}
					break;
				case MSG_TYPE__IMAGE:
					break;
				case MSG_TYPE__VOICE:
					break;
				case MSG_TYPE__VERIFY_MSG:
					break;
				case MSG_TYPE__POSSIBLE_FRIND_MSG:
					break;
				case MSG_TYPE__VCARD:
					break;
				case MSG_TYPE__VIDEO_CALL:
					break;
				case MSG_TYPE__EMOTION:
					break;
				case MSG_TYPE__GPS_POSITION:
					break;
				case MSG_TYPE__URL:
					break;
				case MSG_TYPE__VOIP_MSG:
					break;
				case MSG_TYPE__INIT:
					break;
				case MSG_TYPE__VOIP_NOTIFY:
					break;
				case MSG_TYPE__VOIP_INVITE:
					break;
				case MSG_TYPE__SHORT_VIDEO:
					break;
				case MSG_TYPE__SYSTEM_NOTICE:
					break;
				case MSG_TYPE__SYSTEM:
					break;
				case MSG_TYPE__REVOKE:
					break;
				default:
					break;
				}
			}

			JsonNode jsonModContactCount = jsonMessage.get ("ModContactCount");
			JsonNode jsonModContactList = jsonMessage.get ("ModContactList");
			for (i=0; i<jsonModContactCount.asInt (); i++)
			{
				JsonNode jsonNode = jsonAddMsgList.get (i);
			}

			JsonNode jsonDelContactCount = jsonMessage.get ("DelContactCount");
			JsonNode jsonDelContactList = jsonMessage.get ("DelContactList");
			for (i=0; i<jsonDelContactCount.asInt (); i++)
			{
				JsonNode jsonNode = jsonAddMsgList.get (i);
			}

			JsonNode jsonModChatRoomMemerCount = jsonMessage.get ("ModChatRoomMemberCount");
			JsonNode jsonModChatRoomMemerList = jsonMessage.get ("ModChatRoomMemberList");
			for (i=0; i<jsonModChatRoomMemerCount.asInt (); i++)
			{
				JsonNode jsonNode = jsonAddMsgList.get (i);
			}
		}
	}

	public abstract class Bot
	{
		// 总入口
		public int OnMessageReceived (JsonNode jsonMessage)
		{
			return BotEngine.BOT_CHAIN_PROCESS_MODE__NOT_PROCESSED_BUT_CONTINUE;
		}

		////////////////////////////////
		// AddMsgList 节点处理
		////////////////////////////////
		/**
		 *
		 * @param sMessage
		 */
		public int OnTextMessageReceived (String sMessage)
		{
			return BotEngine.BOT_CHAIN_PROCESS_MODE__NOT_PROCESSED_BUT_CONTINUE;
		}

		////////////////////////////////
		// ModContactList 节点处理
		////////////////////////////////
		public int OnContactChanged (JsonNode jsonMessage)
		{
			return BotEngine.BOT_CHAIN_PROCESS_MODE__NOT_PROCESSED_BUT_CONTINUE;
		}

		////////////////////////////////
		// DelContactList 节点处理
		////////////////////////////////
		public int OnContactDeleted (JsonNode jsonMessage)
		{
			return BotEngine.BOT_CHAIN_PROCESS_MODE__NOT_PROCESSED_BUT_CONTINUE;
		}

		//
		//
		//
		public int OnChatRoomMemberChanged (JsonNode jsonMessage)
		{
			return BotEngine.BOT_CHAIN_PROCESS_MODE__NOT_PROCESSED_BUT_CONTINUE;
		}
	}

	/**
	 * App 线程： 接受命令行输入，进行简单的维护操作(含退出命令 /quit)。
	 */
	@Override
	public void run ()
	{
		String sTerminalInput = null;
		try
		{
			BufferedReader reader = new BufferedReader (new InputStreamReader (System.in));
			while ( (sTerminalInput=reader.readLine ()) != null)
			{
				if (StringUtils.isEmpty (sTerminalInput))
					continue;

				//try
				//{
					if (StringUtils.equalsIgnoreCase (sTerminalInput, "notifyAll"))
					{
						// 本微信号现在人机已合一，具体命令请用 @xxx help 获得帮助
					}
					else if (StringUtils.startsWithIgnoreCase (sTerminalInput, "enableFromUser "))
					{
						//
					}
					else if (StringUtils.startsWithIgnoreCase (sTerminalInput, "disableFromUser "))
					{
						//
					}
					else if (StringUtils.startsWithIgnoreCase (sTerminalInput, "/quit"))
					{
						System.err.println ("收到退出命令");
						//executor.st
						TimeUnit.MILLISECONDS.sleep (100);
						break;
						//System.exit (0);
					}
				//}
				//catch (Exception e)
				//{
				//	e.printStackTrace ();
				//}
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		catch (InterruptedException e)
		{
			// done
		}
System.out.println ("app 线程退出");
		executor.shutdownNow ();
	}

	public static void main (String[] args) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, ScriptException, ValidityException, ParsingException
	{
		net_maclife_wechat_http_WechatBot app = new net_maclife_wechat_http_WechatBot ();

		executor.submit (app);
		executor.submit (app.GetBotEngine ());
	}
}
