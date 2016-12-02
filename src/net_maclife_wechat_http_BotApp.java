import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import javax.imageio.*;
import javax.script.*;

import org.apache.commons.configuration2.*;
import org.apache.commons.configuration2.builder.*;
import org.apache.commons.configuration2.builder.fluent.*;
import org.apache.commons.configuration2.ex.*;
import org.apache.commons.io.*;
import org.apache.commons.lang3.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import nu.xom.*;

/**
 * BotApp
 * 自身是微信 HTTP 协议的封装者，其他程序可以调用。
 * 也是 BotEngine 的调用者
 * @author liuyan
 *
 */
public class net_maclife_wechat_http_BotApp implements Runnable
{
	static final Logger logger = Logger.getLogger (net_maclife_wechat_http_BotApp.class.getName ());
	public static ExecutorService executor = Executors.newCachedThreadPool ();	// .newFixedThreadPool (5);

	public static final String utf8 = "UTF-8";

	public static final int WECHAT_ACCOUNT_TYPE_MASK__Public = 0x08;	// 公众号
	public static final int WECHAT_ACCOUNT_TYPE_MASK__Subscriber = 0x10;	// 订阅号
	public static final int WECHAT_ACCOUNT_TYPE_MASK__Tencent = 0x20;	// 腾讯自己的公众号

	static final String configFileName = "src" + File.separator + "config.properties";
	static Parameters configParameters = null;
	//static Configurations configs = new Configurations();
	static Configuration config = null;

	static
	{
		try
		{
			configParameters = new Parameters ();
			FileBasedConfigurationBuilder<PropertiesConfiguration> configBuilder =
				new FileBasedConfigurationBuilder<PropertiesConfiguration> (PropertiesConfiguration.class)
					.configure
					(
						configParameters.fileBased ()
							.setEncoding (utf8)
							.setFileName (configFileName)
					)
				;
			//config = configs.properties (new File(configFileName));
			config = configBuilder.getConfiguration ();
		}
		catch (ConfigurationException e)
		{
			e.printStackTrace();
		}
	}


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

	//static JsonFactory _JSON_FACTORY = new JsonFactory();

	public static String workingDirectory = config.getString ("app.working.directory", "run");
	public static String qrcodeFilesDirectory = workingDirectory + "/qrcodes";
	public static String mediaFilesDirectory = workingDirectory + "/medias";
	static
	{
		File fQrcodeFilesDirectory = new File (qrcodeFilesDirectory);
		File fMediaFilesDirectory = new File (mediaFilesDirectory);
		fQrcodeFilesDirectory.mkdirs ();
		fMediaFilesDirectory.mkdirs ();
	}

	Future<?> appTask = null;

	net_maclife_wechat_http_BotEngine engine;

	public net_maclife_wechat_http_BotApp ()
	{
		engine = new net_maclife_wechat_http_BotEngine ();
	}
	public net_maclife_wechat_http_BotEngine GetBotEngine ()
	{
		return engine;
	}

	public void Start ()
	{
		engine.Start ();	// 皇上，还记得《2012》电影里那个在机舱里准备开车下飞机的角色吗？“e.n.g.i.n.e  Start!”
		appTask = executor.submit (this);
	}

	public void Stop ()
	{
		engine.Stop ();

		if (appTask!=null && !appTask.isCancelled ())
		{
			appTask.cancel (true);
		}
	}

	public static String GetNewLoginID () throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, ScriptException
	{
logger.info ("获取新的登录ID GetNewLoginID");
		String sURL = "https://login.weixin.qq.com/jslogin?appid=wx782c26e4c19acffb&redirect_uri=https%3A%2F%2Fwx.qq.com%2Fcgi-bin%2Fmmwebwx-bin%2Fwebwxnewloginpage&fun=new&lang=en_US&_=" + System.currentTimeMillis ();

		String sContent = net_maclife_util_HTTPUtils.CURL (sURL);	// window.QRLogin.code = 200; window.QRLogin.uuid = "QegF7Tukgw==";
logger.fine ("获取 LoginID 的 http 响应消息体:");
logger.fine ("	" + sContent);

		String sLoginID = public_jse.eval (StringUtils.replace (sContent, "window.QRLogin.", "var ") + " uuid;").toString ();
logger.info ("获取到的 LoginID:	" + sLoginID);

		return sLoginID;
	}

	public static File GetLoginQRCodeImageFile (String sLoginID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
logger.info ("根据登录ID获取二维码 GetLoginQRCodeImageFile");
		String sURL = "https://login.weixin.qq.com/qrcode/" + sLoginID;
		//String sScanLoginURL = "https://login.weixin.qq.com/l/" + sLoginID;	// 二维码图片解码后的 URL
		String sFileName = qrcodeFilesDirectory + "/wechat-login-qrcode-image-" + sLoginID + ".jpg";
		String sFileName_PNG = qrcodeFilesDirectory + "/wechat-login-qrcode-image-" + sLoginID + "-10%.png";
		File fOutputFile = new File (sFileName);
		InputStream is = net_maclife_util_HTTPUtils.CURL_Stream (sURL);
		OutputStream os = new FileOutputStream (fOutputFile);
		IOUtils.copy (is, os);
logger.info ("获取 LoginQRCode 的 http 响应消息体（保存到文件）:");
logger.info ("	" + fOutputFile);

		if (ParseBoolean(config.getString ("app.textQRCode.displayInTerminal"), true))
		{
			boolean bBackgroundIsDarker = ParseBoolean(config.getString ("app.textQRCode.terminal.backgroundIsDarkerThanForeground"), true);
			boolean bUseANSIEscape = ParseBoolean(config.getString ("app.textQRCode.useANSIEscape"), true);
			ConvertQRCodeImage (sFileName, sFileName_PNG);
			DisplayQRCodeInConsole (sFileName_PNG, bBackgroundIsDarker, bUseANSIEscape);
		}
		return fOutputFile;
	}

	/**
	 * 将获取到的二维码 (.jpg 格式) 转换为 黑白单色、尺寸缩小到 1/10 的 .png 格式。
	 * 转换后的 .png 图片是最小的二维码图片，而且能够用来在字符终端界面用文字显示二维码。
	 * 转换工作是利用 ImageMagick 的 convert 工具来做的，所以，需要安装 ImageMagick，并在配置文件里配置其工作路径。
	 * @param sJPGFileName
	 * @param sPNGFileName
	 * @throws IOException
	 */
	public static void ConvertQRCodeImage (String sJPGFileName, String sPNGFileName) throws IOException
	{
logger.info ("ConvertQRCodeImage 将二维码 jpg 文件 " + sJPGFileName + " 【转换并缩小】到适合文字输出大小的 png 文件: " + sPNGFileName);
		List<String> listImageMagickConvertArgs = new ArrayList<String> ();
		// convert wechat-login-qrcode-image-wb6kQwuV6A==.jpg -resize 10% -dither none -colors 2 -monochrome wechat-login-qrcode-image-wb6kQwuV6A==-10%.png
		listImageMagickConvertArgs.add ("convert");
		listImageMagickConvertArgs.add (sJPGFileName);
		listImageMagickConvertArgs.add ("-resize");
		listImageMagickConvertArgs.add ("10%");
		listImageMagickConvertArgs.add ("-dither");
		listImageMagickConvertArgs.add ("none");
		listImageMagickConvertArgs.add ("-colors");
		listImageMagickConvertArgs.add ("2");
		listImageMagickConvertArgs.add ("-monochrome");
		listImageMagickConvertArgs.add (sPNGFileName);
		ProcessBuilder pb = new ProcessBuilder (listImageMagickConvertArgs);
		try
		{
			Process p = pb.start ();
			InputStream in = p.getInputStream ();
			InputStream err = p.getErrorStream ();
			while (-1 != in.read ());
			while (-1 != err.read ());
			int rc = p.waitFor ();
			assert (rc == 0);
			if (rc != 0)
			{
logger.severe ("转换失败");
			}
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
	}

	/**
	 * 在字符终端用文字显示二维码。
	 * @param sPNGFileName 必须是 2 色黑白的 PNG 图片，且，图片大小是原来微信返回的十分之一大小 （缩小后，每个像素 (pixel) 是原二维码中最小的点）
	 * @param bTerminalBackgroundColorIsDarkerThanForgroundColor 字符终端背景色不是黑色
	 * @param bUseANSIEscape 使用 ANSI 转义来输出颜色（保证二维码黑色是黑色，白色是白色，但需要 shell 的支持，比如 linux 下的 bash 或 Windows 安装 Cygwin 后的 bash）。
	 * 参考： https://en.wikipedia.org/wiki/ANSI_escape_code#Colors
	 * @throws IOException
	 */
	public static void DisplayQRCodeInConsole (String sPNGFileName, boolean bTerminalBackgroundColorIsDarkerThanForgroundColor, boolean bUseANSIEscape) throws IOException
	{
		StringBuilder sbTextQRCode = new StringBuilder ();
		BufferedImage img = ImageIO.read (new File(sPNGFileName));

		if (bTerminalBackgroundColorIsDarkerThanForgroundColor)
		{	// 如果背景色比前景色黑，则开启颜色反转
			sbTextQRCode.append ("\u001B[7m");
		}
		for (int y=0; y<img.getHeight (); y++)
		{
			for (int x=0; x<img.getWidth (); x++)
			{
				int nRGB = img.getRGB (x, y) & 0xFFFFFF;

				if (nRGB == 0)	// 黑色
				{
					if (!bTerminalBackgroundColorIsDarkerThanForgroundColor && bUseANSIEscape)
					{	// 当前字符转义开始
						sbTextQRCode.append ("\u001B[30m");
					}
					sbTextQRCode.append ("█");
					if (!bTerminalBackgroundColorIsDarkerThanForgroundColor && bUseANSIEscape)
					{	// 当前字符转义结束
						sbTextQRCode.append ("\u001B[m");
					}
				}
				else if (nRGB == 0xFFFFFF)	// 白色
				{
					if (!bTerminalBackgroundColorIsDarkerThanForgroundColor && bUseANSIEscape)
					{	// 当前字符转义开始
						sbTextQRCode.append ("\u001B[47;1m");
					}
					sbTextQRCode.append ("　");
					if (!bTerminalBackgroundColorIsDarkerThanForgroundColor && bUseANSIEscape)
					{	// 当前字符转义结束
						sbTextQRCode.append ("\u001B[m");
					}
				}
				else
				{
					//System.err.print ("未知的 RGB 颜色: " + nRGB);
				}
			}
			sbTextQRCode.append ("\n");
		}
		if (bTerminalBackgroundColorIsDarkerThanForgroundColor)
		{	// 如果背景色比前景色黑，则结束颜色反转
			sbTextQRCode.append ("\u001B[27m");
		}

		System.out.println (sbTextQRCode);
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

logger.info (String.format ("%3d", nLoopCount) + " 循环等待二维码被扫描以便登录 的 http 响应消息体:");
			// window.code=408;	未扫描/超时。只要未扫描就不断循环，但 web 端好像重复 12 次（每次 25054 毫秒）左右后，就重新刷新 LoginID
			// window.code=201;	已扫描
			// window.code=200;	已确认登录
			// window.redirect_uri="https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxnewloginpage?ticket=A8qwapRV_lQ44viWM0mZmnpm@qrticket_0&uuid=gYiEFqEQdw==&lang=en_US&scan=1479893365";
			sContent = net_maclife_util_HTTPUtils.CURL (sURL);
logger.fine ("	" + sContent);
			if (StringUtils.isEmpty (sContent))
			{
				nLoginResultCode = 400;
logger.warning ("	响应消息提为空，二维码可能已经失效");
				break;
			}

			String sJSCode = StringUtils.replace (sContent, "window.", "var ");
			sLoginResultCode = public_jse.eval (sJSCode + " code;").toString ();
			nLoginResultCode = Double.valueOf (sLoginResultCode).intValue ();
logger.fine ("	获取到的 LoginResultCode:	" + nLoginResultCode);

			switch (nLoginResultCode)
			{
			case 408:	// 假设等同于 http 响应码 408: Request Time-out
logger.info ("	" + nLoginResultCode + " 请求超时");
				break;
			case 201:	// 假设等同于 http 响应码 201: Created
logger.info ("	" + nLoginResultCode + " 已扫描");
				break;
			case 200:	// 假设等同于 http 响应码 200: OK
				sLoginURL = public_jse.eval (sJSCode + " redirect_uri;").toString ();
logger.info ("	" + nLoginResultCode + " 已确认登录，浏览器需要重定向到的登录页面网址为:");
logger.fine ("	" + sLoginURL);
				sLoginURL = sLoginURL + "&fun=new&version=v2";
logger.fine ("网址加上 &fun=new&version=v2:");
logger.info ("	" + sLoginURL);
				break;
			case 400:	// 假设等同于 http 响应码 400: Bad Request
logger.warning ("	" + nLoginResultCode + " 二维码已过期");
				//throw new RuntimeException ("二维码已过期");
				//break while_loop;
				return nLoginResultCode;
			default:
logger.warning ("	" + nLoginResultCode + " 未知的响应代码: " + nLoginResultCode);
				break while_loop;
			}
		} while (nLoginResultCode != 200);

		URLConnection http = net_maclife_util_HTTPUtils.CURL_Connection (sLoginURL);
		int iResponseCode = ((HttpURLConnection)http).getResponseCode();
		int iMainResponseCode = iResponseCode/100;
		if (iMainResponseCode==2)
		{
logger.finer ("登录页面设置的 Cookie:");
			Map<String, List<String>> mapHeaders = http.getHeaderFields ();
			cookieManager.put (new URI(sLoginURL), mapHeaders);
			for (String sHeaderName : mapHeaders.keySet ())
			{
				if (StringUtils.equalsIgnoreCase (sHeaderName, "Set-Cookie"))
				{
					List<String> listCookies = mapHeaders.get (sHeaderName);
logger.finer ("	" + listCookies);
				}
			}

			InputStream is = http.getInputStream ();
			//Document xml = xmlBuilder.parse (is);
			nu.xom.Document doc = xomBuilder.build (is);
			is.close ();
			nu.xom.Element eXML = doc.getRootElement ();
			//sContent = IOUtils.toString (is, net_maclife_util_HTTPUtils.UTF8_CHARSET);
logger.fine ("登录页面响应的消息体:");
//System.out.println ("	[" + sContent + "]");
logger.fine ("	[" + eXML.toXML() + "]");
logger.info ("	UIN=[" + eXML.getFirstChildElement ("wxuin").getValue () + "]");
logger.info ("	SID=[" + eXML.getFirstChildElement ("wxsid").getValue () + "]");
logger.info ("	SKEY=[" + eXML.getFirstChildElement ("skey").getValue () + "]");
logger.info ("	TICKET=[" + eXML.getFirstChildElement ("pass_ticket").getValue () + "]");
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
logger.info ("初始化 WebWeChatInit …");
		// https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxinit?r=1703974212&lang=zh_CN&pass_ticket=ZfvpI6wcO7N5PTkacmWK9zUTXpUOB3kqre%2BrkQ8IAtHDAIP2mc2psB5eDH8cwzsp
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxinit?r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + sPassTicket;
logger.fine ("WebWeChatInit 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
logger.finer ("发送 WebWeChatInit 的 http 请求消息头 (Content-Type):");
logger.finer ("	" + mapRequestHeaders);

		String sRequestBody_JSONString = MakeFullBaseRequestJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID ());
logger.finer ("发送 WebWeChatInit 的 http 请求消息体:");
logger.finer (sRequestBody_JSONString);

		InputStream is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
		ObjectMapper om = new ObjectMapper ();
		JsonNode node = om.readTree (is);
logger.fine ("获取 WebWeChatInit 的 http 响应消息体:");
logger.fine ("	" + node);

		JsonNode jsonUser = node.get ("User");
		StringBuilder sb = new StringBuilder ();
		sb.append ("\n");
		sb.append ("昵称: ");
		sb.append (GetJSONText (jsonUser, "NickName"));
		sb.append ("\n");
		sb.append ("本次会话帐号的 Hash: ");
		sb.append (GetJSONText (jsonUser, "UserName"));
		sb.append ("\n");
		sb.append ("最近联系人数量: ");
		sb.append (GetJSONText (node, "Count"));
		sb.append ("\n");
		sb.append ("最近联系人: ");
		sb.append ("\n");
		JsonNode jsonRecentContactList = node.get ("ContactList");
		for (int i=0; i<GetJSONInt (node, "Count"); i++)
		{
			JsonNode jsonContact = jsonRecentContactList.get (i);
			sb.append ("	");
			sb.append (GetJSONText (jsonContact, "NickName"));
			int nVerifyFlag = GetJSONInt (jsonContact, "VerifyFlag");
			boolean isPublicAccount = IsPublicAccount (nVerifyFlag);
			boolean isRoomAccount = IsRoomAccount (GetJSONText (jsonContact, "UserName"));
			if (isRoomAccount || isPublicAccount)
			{
				sb.append (" (");
				if (isRoomAccount)
					sb.append ("聊天室/群");
				if (isPublicAccount)
				{
					sb.append ("公众号");
					if (IsPublicAccount (nVerifyFlag))
					{
						sb.append (", 订阅号");
						if (IsTencentAccount (nVerifyFlag))
						{
							sb.append (", 腾讯号");
						}
					}
				}
				sb.append (")");
			}
			sb.append ("\n");
		}
logger.info (sb.toString ());
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
logger.info ("开启状态通知 WebWeChatStatusNotify …");
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxstatusnotify?lang=zh_CN&pass_ticket=" + sPassTicket;
logger.fine ("WebWeChatStatusNotify 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
logger.finer ("发送 WebWeChatStatusNotify 的 http 请求消息头 (Content-Type):");
logger.finer ("	" + mapRequestHeaders);

		String sRequestBody_JSONString = MakeFullStatusNotifyRequestJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID (), sMyAccountHashInThisSession);
logger.finer ("发送 WebWeChatStatusNotify 的 http 请求消息体:");
logger.finer (sRequestBody_JSONString);
		InputStream is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
		ObjectMapper om = new ObjectMapper ();
		JsonNode node = om.readTree (is);
logger.fine ("获取 WebWeChatStatusNotify 的 http 响应消息体:");
logger.fine ("	" + node);
		//
		return node;
	}

	public static JsonNode WebWeChatGetContacts (String sUserID, String sSessionID, String sSessionKey, String sPassTicket) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("获取联系人 WebWeChatGetContacts …");
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxgetcontact?r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + sPassTicket;
logger.fine ("WebWeChatGetContacts 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
logger.finer  ("发送 WebWeChatGetContacts 的 http 请求消息头 (Content-Type):");
logger.finer  ("	" + mapRequestHeaders);

String sRequestBody_JSONString = MakeFullBaseRequestJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID ());
logger.finer  ("发送 WebWeChatGetContacts 的 http 请求消息体:");
logger.finer  ("	" + sRequestBody_JSONString);

		InputStream is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
		ObjectMapper om = new ObjectMapper ();
		JsonNode node = om.readTree (is);
logger.fine  ("获取 WebWeChatGetContacts 的 http 响应消息体:");
logger.fine  ("	" + node);

//logger.info ("获取到 " + GetJSONInt (node, "MemberCount") + " 个联系人");

		StringBuilder sb = new StringBuilder ();
		sb.append ("\n");
		int nCount = GetJSONInt (node, "MemberCount");
		sb.append ("共 " + nCount + " 个联系人");
		JsonNode jsonMemberList = node.get ("MemberList");
		for (int i=0; i<nCount; i++)
		{
			if (i%10 == 0)
			{	// 每隔 10 人另起一行
				sb.append ("\n");
				sb.append ("	");
			}
			sb.append (i+1);
			sb.append (". ");

			JsonNode jsonMember = jsonMemberList.get (i);
			String sNickName = GetJSONText (jsonMember, "NickName");
			sb.append (sNickName);

			String sRemarkName = GetJSONText (jsonMember, "RemarkName");
			if (! StringUtils.equalsIgnoreCase (sNickName, sRemarkName) && StringUtils.isNotBlank (sRemarkName))
			{
				sb.append (" (");
				sb.append (sRemarkName);
				sb.append (")");
			}
			sb.append ("    ");
		}
logger.info (sb.toString ());
		return node;
	}

	public static List<String> GetRoomIDsFromContacts (JsonNode jsonContacts)
	{
		List<String> listRoomIDs = new ArrayList<String> ();
		JsonNode jsonMemberList = jsonContacts.get ("MemberList");
		for (int i=0; i<jsonMemberList.size (); i++)
		{
			JsonNode jsonContact = jsonMemberList.get (i);
			String sUserHashID = GetJSONText (jsonContact, "UserName");
			if (IsRoomAccount (sUserHashID))
				listRoomIDs.add (sUserHashID);
		}
		return listRoomIDs;
	}
	public static String MakeFullGetRoomContactRequestJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, List<String> listRoomIDs)
	{
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

	public static JsonNode WebWeChatGetRoomContacts (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, List<String> listRoomIDs) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("获取 " + listRoomIDs.size () + " 个聊天室的联系人 WebWeChatGetRoomContacts …");
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxbatchgetcontact?type=ex&r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + sPassTicket;
logger.fine ("WebWeChatGetRoomContacts 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
		String sRequestBody_JSONString = MakeFullGetRoomContactRequestJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID (), listRoomIDs);
logger.finer ("发送 WebWeChatGetRoomContacts 的 http 请求消息体:");
logger.finer ("	" + sRequestBody_JSONString);
		InputStream is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
		ObjectMapper om = new ObjectMapper ();
		JsonNode node = om.readTree (is);
logger.fine ("获取 WebWeChatGetRoomContacts 的 http 响应消息体:");
logger.fine ("	" + node);

		StringBuilder sb = new StringBuilder ();
		sb.append ("\n");
		int nCount = GetJSONInt (node, "Count");
		sb.append ("共 " + nCount + " 个聊天室\n");
		JsonNode jsonContactList = node.get ("ContactList");
		for (int i=0; i<nCount; i++)
		{
			JsonNode jsonContact = jsonContactList.get (i);
			sb.append (String.format ("%" + String.valueOf (nCount).length () + "d", (i+1)));
			sb.append ("  ");
			sb.append (GetJSONText (jsonContact, "NickName"));

			JsonNode jsonMemberList = jsonContact.get ("MemberList");
			for (int j=0; j<jsonMemberList.size (); j++)
			{
				if (j%10 == 0)
				{	// 每隔 10 人另起一行
					sb.append ("\n");
					sb.append ("	");
				}
				sb.append (j+1);
				sb.append (". ");

				JsonNode jsonMember = jsonMemberList.get (j);
				String sNickName = GetJSONText (jsonMember, "NickName");
				sb.append (sNickName);

				String sDisplayName = GetJSONText (jsonMember, "DisplayName");
				if (! StringUtils.equalsIgnoreCase (sNickName, sDisplayName) && StringUtils.isNotBlank (sDisplayName))
				{
					sb.append (" (");
					sb.append (sDisplayName);
					sb.append (")");
				}
				sb.append ("    ");
			}
			sb.append ("\n");
		}
logger.info (sb.toString ());
		//
		return node;
	}

	/**
	 * 从 WebWeChatGetContacts 返回的 JsonNode 中的 MemberList 中找出符合条件的联系人。
	 * 只要指定了 sAccountHashInASession，就会搜到唯一一个联系人（除非给出的 ID 不正确），
	 * 如果没指定 sAccountHashInASession (null 或空字符串)，则尽可能全部指定 sAlias、sRemarkName、sNickName，以便更精确的匹配联系人。
	 * @param jsonMemberList
	 * @param sAccountHashInASession 类似  @********** filehelper weixin 等 ID，可以唯一对应一个联系人。最高优先级。
	 * @param sAliasAccount 自定义帐号。如果 UserIDInThisSession 为空，则尝试根据 sAlias 获取。次优先级。
	 * @param sRemarkName 备注名。如果 Alias 也为空，则根据备注名称获取。再次优先级。
	 * @param sNickName 昵称。如果 Alias 也为空，则根据昵称获取。这个优先级在最后，因为，用户自己更改昵称的情况应该比前面的更常见，导致不确定性更容易出现。
	 * @return 搜索到的联系人的 JsonNode 列表。正常情况下应该为 1 个，但也可能为空，也可能为多个。
	 */
	public static List<JsonNode> SearchForContacts (JsonNode jsonMemberList, String sAccountHashInASession, String sAliasAccount, String sRemarkName, String sNickName)
	{
		List<JsonNode> listUsersMatched = new ArrayList <JsonNode> ();
		JsonNode jsonUser = null;
		for (int i=0; i<jsonMemberList.size (); i++)
		{
			JsonNode node = jsonMemberList.get (i);

			if (StringUtils.isNotEmpty (sAccountHashInASession))
			{
				String sTemp = GetJSONText (node, "UserName");
				if (StringUtils.equalsIgnoreCase (sAccountHashInASession, sTemp))
				{
					//jsonUser = node;
					listUsersMatched.add (node);
					break;
				}
			}

			else if (StringUtils.isNotEmpty (sAliasAccount))
			{
				String sTemp = GetJSONText (node, "Alias");
				if (StringUtils.equalsIgnoreCase (sAliasAccount, sTemp))
				{
					//jsonUser = node;
					//break;
					listUsersMatched.add (node);
				}
			}

			else if (StringUtils.isNotEmpty (sRemarkName))
			{
				String sTemp = GetJSONText (node, "RemarkName");
				if (StringUtils.equalsIgnoreCase (sRemarkName, sTemp))
				{
					//jsonUser = node;
					//break;
					listUsersMatched.add (node);
				}
			}

			else if (StringUtils.isNotEmpty (sNickName))
			{
				String sTemp = GetJSONText (node, "NickName");
				if (StringUtils.equalsIgnoreCase (sNickName, sTemp))
				{
					//jsonUser = node;
					//break;
					listUsersMatched.add (node);
				}
			}
		}

		//// 如果匹配到多个（通常来说，是在未指定 ），则再根据 自定义帐号、备注名、昵称 共同筛选出全部匹配的
		//if (listUsersMatched.size () > 1)
		//{
		//	for (int i=listUsersMatched.size ()-1; i>=0; i--)
		//	{
		//		jsonUser = listUsersMatched.get (i);
		//	}
		//}
		return listUsersMatched;
	}

	/**
	 * 查找
	 * @param jsonMemberList
	 * @param sAccountHashInThisSession
	 * @param sAlias
	 * @param sRemarkName
	 * @param sNickName
	 * @return
	 */
	public static JsonNode SearchForSingleContact (JsonNode jsonMemberList, String sAccountHashInThisSession, String sAlias, String sRemarkName, String sNickName)
	{
		List<JsonNode> listUsers = SearchForContacts (jsonMemberList, sAccountHashInThisSession, sAlias, sRemarkName, sNickName);
		return listUsers.size ()==0 ? null : listUsers.get (0);
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
			sbSyncCheckKeys.append (GetJSONText (jsonKey, "Key"));
			sbSyncCheckKeys.append ("_");
			sbSyncCheckKeys.append (GetJSONText (jsonKey, "Val"));
		}
		return sbSyncCheckKeys.toString ();
	}
	public static String MakeFullWeChatSyncJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, JsonNode jsonSyncKey)
	{
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
logger.fine ("等待并获取新消息 WebWeChatGetMessages (synccheck & webwxsync) …");	// 这里的日志级别改为了 fine，因这个在死循环中，产生太多日志
		String sSyncCheckKeys = MakeSyncCheckKeys (jsonSyncCheckKeys);
		String sSyncCheckURL = "https://webpush.wx2.qq.com/cgi-bin/mmwebwx-bin/synccheck?r=" + System.currentTimeMillis () + "&skey=" + URLEncoder.encode (sSessionKey, utf8) + "&sid=" + URLEncoder.encode (sSessionID, utf8) + "&uin=" + sUserID + "&deviceid=" + MakeDeviceID () + "&synckey=" +  sSyncCheckKeys + "&_=" + System.currentTimeMillis ();
logger.fine ("WebWeChatGetMessages 中 synccheck 的 URL:");
logger.fine ("	" + sSyncCheckURL);

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
logger.finer ("发送 WebWeChatGetMessages 中 synccheck 的 http 请求消息头 (Cookie):");
logger.finer ("	" + mapRequestHeaders);

		String sContent = null;
	_适应临时网络错误_TolerateTemporarilyNetworkIssue:
		while (true)
		{
			try
			{
				sContent = net_maclife_util_HTTPUtils.CURL (sSyncCheckURL, mapRequestHeaders);	// window.synccheck={retcode:"0",selector:"2"}
				break;
			}
			catch (UnknownHostException e)
			{
				continue _适应临时网络错误_TolerateTemporarilyNetworkIssue;
			}
		}
logger.fine ("获取 WebWeChatGetMessages 中 synccheck 的 http 响应消息体:");
logger.fine ("	" + sContent);

		String sJSCode = StringUtils.replace (sContent, "window.", "var ");
		String sSyncCheckReturnCode = public_jse.eval (sJSCode + "; synccheck.retcode;").toString ();
		String sSyncCheckSelector = public_jse.eval (sJSCode + "; synccheck.selector;").toString ();

		JsonNode jsonResult = null;
		if (StringUtils.equalsIgnoreCase (sSyncCheckReturnCode, "0"))
		{
			switch (sSyncCheckSelector)
			{
			case "2":	// 有新消息
				String sSyncURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsync?sid=" + URLEncoder.encode (sSessionID, utf8) + "&skey" + URLEncoder.encode (sSessionKey, utf8) + "&lang=zh_CN&pass_ticket=" +  sPassTicket;
logger.fine ("WebWeChatGetMessages 中 webwxsync 的 URL:");
logger.fine ("	" + sSyncURL);

				//mapRequestHeaders = new HashMap<String, Object> ();
				mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
				//mapRequestHeaders.put ("Cookie", sCookieValue);	// 避免服务器返回 "Ret": 1 代码
				String sRequestBody_JSONString = MakeFullWeChatSyncJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID (), jsonSyncCheckKeys);
logger.finer ("发送 WebWeChatGetMessages 中 webwxsync 的 http 请求消息头 (Cookie & Content-Type):");
logger.finer ("	" + mapRequestHeaders);
logger.finer ("发送 WebWeChatGetMessages 中 webwxsync 的 http 请求消息体:");
logger.finer ("	\n" + sRequestBody_JSONString);
				InputStream is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sSyncURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
				ObjectMapper om = new ObjectMapper ();
				JsonNode node = om.readTree (is);
logger.fine ("获取 WebWeChatGetMessages 中 webwxsync 的 http 响应消息体:");
logger.fine ("\n" + node);
				jsonResult = node;
				break;
			case "0":	// nothing
logger.fine ("WebWeChatGetMessages 中 synccheck 返回 0 -- 无消息");
				break;
			case "7":	// 进入离开聊天页面
logger.fine ("WebWeChatGetMessages 中 synccheck 返回 7 -- 进入/离开聊天页面");
				break;
			default:
				break;
			}
		}
		else if (StringUtils.equalsIgnoreCase (sSyncCheckReturnCode, "1100") || StringUtils.equalsIgnoreCase (sSyncCheckReturnCode, "1101") || StringUtils.equalsIgnoreCase (sSyncCheckReturnCode, "1102"))
		{
logger.warning ("WebWeChatGetMessages 中 synccheck 返回 " + sSyncCheckReturnCode + " -- 可能微信网页版（含 Windows 版）在其他地方登录了");
			throw new IllegalStateException ("微信被退出 / 被踢出了");
		}
		//else if (StringUtils.equalsIgnoreCase (sSyncCheckReturnCode, "1102"))	// 当 skey=*** 不小心输错变成 skey*** 时返回了 1102 错误
		{
			//throw new IllegalArgumentException ("参数错误");
		}
		//
		return jsonResult;
	}

	public static void WebWeChatLogout (String sUserID, String sSessionID, String sSessionKey, String sPassTicket) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
logger.info ("退出微信 WebWeChatLogout …");
		// https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxlogout?redirect=1&type=1&skey=@crypt_1df7c02d_9effb9a7d4292af4681c79dab30b6a57	// 加上表单数据 uin=****&sid=**** ，POST

		// 被踢出后重新登录
		// https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxnewloginpage?ticket=A-1wUN8dm6D-nIJH8m8g7yfh@qrticket_0&uuid=YZzRE6skKQ==&lang=zh_CN&scan=1479978616 对比最初的登录参数，后面是新加的： &fun=new&version=v2&lang=zh_CN
		//    <error><ret>0</ret><message></message><skey>@crypt_1df7c02d_131d1d0335be6fd38333592c098a5b16</skey><wxsid>GrS6IjctQkOxs0PP</wxsid><wxuin>2100343515</wxuin><pass_ticket>T%2FduUWTWjODelhztGXZAO1b3u7S5Ddy8ya8fP%2BYhZlRjxR1ERMDXHKbaCs6x2mQP</pass_ticket><isgrayscale>1</isgrayscale></error>
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxlogout?redirect=0&type=1&skey=" + URLEncoder.encode (sSessionKey, utf8);
logger.fine ("WebWeChatLogout 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/x-www-form-urlencoded");
		CookieStore cookieStore = cookieManager.getCookieStore ();
		List<HttpCookie> listCookies = cookieStore.get (new URI(sURL));
		String sCookieValue = "";
		sCookieValue = MakeCookieValue (listCookies);
		mapRequestHeaders.put ("Cookie", sCookieValue);	// 避免服务器返回 1100 1102 代码？
logger.finer ("发送 WebWeChatLogout 的 http 请求消息头:");
logger.finer ("	" + mapRequestHeaders);

		String sRequestBody = "wxsid=" + URLEncoder.encode (sSessionID, utf8) + "&uin=" + sUserID;
logger.finer ("发送 WebWeChatLogout 的 http 请求消息体:");
logger.finer ("	" + sRequestBody);

		String sContent = net_maclife_util_HTTPUtils.CURL_Post (sURL, mapRequestHeaders, sRequestBody.getBytes ());
logger.fine ("获取 WebWeChatLogout 的 http 响应消息体:");
logger.fine ("\n" + sContent);
		//
	}

	public static JsonNode WebWeChatSendMessage (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_AccountHash, String sTo_AccountHash, int nMessageType, Object oMessage) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.fine ("发消息 WebWeChatSendMessage …");
		String sURL = null;

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
		String sRequestBody_JSONString = "";
		switch (nMessageType)
		{
		case net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__TEXT:
			sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendmsg?r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + sPassTicket;
			sRequestBody_JSONString = MakeFullTextMessageJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID (), sFrom_AccountHash, sTo_AccountHash, (String)oMessage);
			break;
		case net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__IMAGE:
			sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendmsgimg?fun=async&f=json&lang=zh_CN&pass_ticket=" + sPassTicket;
			sRequestBody_JSONString = MakeFullImageMessageJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID (), sFrom_AccountHash, sTo_AccountHash, (String)oMessage);
			break;
		case net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__APP:
			sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendappmsg?fun=async&f=json&lang=zh_CN&pass_ticket=" + sPassTicket;
			sRequestBody_JSONString = MakeFullApplicationMessageJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID (), sFrom_AccountHash, sTo_AccountHash, (String)oMessage);
			break;
		case net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__EMOTION:
			sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendemoticon?fun=sys&f=json&lang=zh_CN&pass_ticket=" + sPassTicket;
			sRequestBody_JSONString = MakeFullEmotionMessageJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID (), sFrom_AccountHash, sTo_AccountHash, (String)oMessage);
			break;
		default:
			break;
		}
logger.fine ("WebWeChatSendMessage 的 URL:");
logger.fine ("	" + sURL);
logger.finer ("发送 WebWeChatSendMessage 的 http 请求消息头:");
logger.finer ("	" + mapRequestHeaders);
logger.finer ("发送 WebWeChatSendMessage 的 http 请求消息体:");
logger.finer ("	" + sRequestBody_JSONString);
		InputStream is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
		ObjectMapper om = new ObjectMapper ();
		JsonNode node = om.readTree (is);
logger.fine ("获取 WebWeChatSendMessage 的 http 响应消息体:");
logger.fine ("\n" + node);
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
		"		\"Type\": " + net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__TEXT + ",\n" +
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
logger.info ("发文本消息 WebWeChatSendTextMessage: " + sMessage);
		return WebWeChatSendMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sFrom_AccountHash, sTo_AccountHash, net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__TEXT, sMessage);
	}

	public static String MakeFullImageMessageJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sMediaID)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		return
		"{\n" +
		"	\"BaseRequest\":\n" + MakeBaseRequestValueJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + ",\n" +
		"	\"Msg\":\n" +
		"	{\n" +
		"		\"Type\": " + net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__IMAGE + ",\n" +
		"		\"MediaId\": \"" + sMediaID + "\",\n" +
		"		\"FromUserName\": \"" + sFrom + "\",\n" +
		"		\"ToUserName\": \"" + sTo + "\",\n" +
		"		\"LocalID\": \"" + nLocalMessageID + "\",\n" +
		"		\"ClientMsgId\": \"" + nLocalMessageID + "\"\n" +
		"	},\n" +
		"	\"Scene\": 0\n" +
		"}\n";
	}
	public static JsonNode WebWeChatSendImageMessage (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_AccountHash, String sTo_AccountHash, String sMediaID) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("发图片消息 WebWeChatSendImageMessage: " + sMediaID);
		return WebWeChatSendMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sFrom_AccountHash, sTo_AccountHash, net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__IMAGE, sMediaID);
	}

	public static String MakeFullEmotionMessageJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sMediaID)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		return
		"{\n" +
		"	\"BaseRequest\":\n" + MakeBaseRequestValueJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + ",\n" +
		"	\"Msg\":\n" +
		"	{\n" +
		"		\"Type\": " + net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__EMOTION + ",\n" +
		"		\"EmojiFlag\": 2,\n" +
		"		\"EMotionMd5\": \"" + sMediaID + "\",\n" +
		"		\"FromUserName\": \"" + sFrom + "\",\n" +
		"		\"ToUserName\": \"" + sTo + "\",\n" +
		"		\"LocalID\": \"" + nLocalMessageID + "\",\n" +
		"		\"ClientMsgId\": \"" + nLocalMessageID + "\"\n" +
		"	},\n" +
		"	\"Scene\": 0\n" +
		"}\n";
	}
	public static JsonNode WebWeChatSendEmotionMessage (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_AccountHash, String sTo_AccountHash, String sMediaID) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("发表情图消息 WebWeChatSendEmotionMessage: " + sMediaID);
		return WebWeChatSendMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sFrom_AccountHash, sTo_AccountHash, net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__EMOTION, sMediaID);
	}

	public static String MakeFullApplicationMessageJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sMessage)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		return
		"{\n" +
		"	\"BaseRequest\":\n" + MakeBaseRequestValueJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + ",\n" +
		"	\"Msg\":\n" +
		"	{\n" +
		"		\"Type\": " + net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__TEXT + ",\n" +
		"		\"Content\": \"" + sMessage + "\",\n" +
		"		\"FromUserName\": \"" + sFrom + "\",\n" +
		"		\"ToUserName\": \"" + sTo + "\",\n" +
		"		\"LocalID\": \"" + nLocalMessageID + "\",\n" +
		"		\"ClientMsgId\": \"" + nLocalMessageID + "\"\n" +
		"	}\n" +
		"}\n";
	}
	public static JsonNode WebWeChatSendApplicationMessage (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_AccountHash, String sTo_AccountHash, String sMediaID) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("发应用程序 (如：上传文件) 消息 WebWeChatSendApplicationMessage: " + sMediaID);
		return WebWeChatSendMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sFrom_AccountHash, sTo_AccountHash, net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__APP, sMediaID);
	}

	public static JsonNode WebWeChatUploadMedia (File f)
	{
logger.info ("上传媒体/上传文件 WebWeChatUploadMedia: " + f);
		String sURL = "https://file.wx2.qq.com/cgi-bin/mmwebwx-bin/webwxuploadmedia?f=json";

		// OPTIONS sURL :
		// Response (JSON): BaseResponse: xxx , MediaId: "", StartPos: 0, CDNThumbImgHeight: 0, CDNThumbImgWidth: 0

		// POST sURL :
		// Content-Type: "multipart/form-data; boundary=---------------------------18419982551043833290966102030"
		// 消息体： 包含
		//
		// Response (JSON): BaseResponse: xxx , MediaId: "@crypt_169个英文字符", StartPos: 文件大小, CDNThumbImgHeight: 0, CDNThumbImgWidth: 0
		return null;
	}

	public static File WebWeChatGetMedia (String sSessionKey, String sAPI, String sMsgID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
logger.fine ("获取媒体/获取文件 WebWeChatGetMedia …");
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/" + sAPI + "?" + (StringUtils.equalsIgnoreCase (sAPI, "webwxgetmsgimg") ? "MsgId" : "msgid") + "=" + sMsgID + "&skey=" + URLEncoder.encode (sSessionKey, utf8);
		String sMediaFileName = mediaFilesDirectory + "/" + sMsgID;
		File fMediaFile = null;

logger.fine ("获取 WebWeChatGetMedia 的 URL (api = " + sAPI + ")");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		CookieStore cookieStore = cookieManager.getCookieStore ();
		List<HttpCookie> listCookies = cookieStore.get (new URI(sURL));
		String sCookieValue = "";
		sCookieValue = MakeCookieValue (listCookies);
		mapRequestHeaders.put ("Cookie", sCookieValue);
		//mapRequestHeaders.put ("Range", "bytes=0-");
		//mapRequestHeaders.put ("Accept", "*/*");
		//mapRequestHeaders.put ("User-Agent", "bot");
logger.fine ("发送 WebWeChatGetMedia 的 http 请求消息头 (Cookie、Range):");
logger.fine ("	" + mapRequestHeaders);

		URLConnection http = net_maclife_util_HTTPUtils.CURL_Connection (sURL, mapRequestHeaders);
		int iResponseCode = ((HttpURLConnection)http).getResponseCode();
		int iMainResponseCode = iResponseCode/100;
		if (iMainResponseCode==2)
		{
			String sExtensionName = net_maclife_util_HTTPUtils.ContentTypeToFileExtensionName (http.getHeaderField ("Content-Type"));
			if (StringUtils.isNotEmpty (sExtensionName))
				sMediaFileName = sMediaFileName + "." + sExtensionName;

			fMediaFile = new File (sMediaFileName);
			InputStream is = http.getInputStream ();
			OutputStream os = new FileOutputStream (fMediaFile);
			IOUtils.copy (is, os);
		}
logger.fine ("获取 WebWeChatGetMedia 的 http 响应消息体 (保存到文件)");
logger.fine ("	" + fMediaFile);
		return fMediaFile;
	}
	public static File WebWeChatGetImage (String sSessionKey, String sMsgID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
logger.info ("获取图片 WebWeChatGetImage：" + sMsgID);
		return WebWeChatGetMedia (sSessionKey, "webwxgetmsgimg", sMsgID);
	}
	public static File WebWeChatGetVoice (String sSessionKey, String sMsgID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
logger.info ("获取音频 WebWeChatGetVoice：" + sMsgID);
		return WebWeChatGetMedia (sSessionKey, "webwxgetvoice", sMsgID);
	}
	public static File WebWeChatGetVideo (String sSessionKey, String sMsgID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
logger.info ("获取视频 WebWeChatGetVideo：" + sMsgID);
		return WebWeChatGetMedia (sSessionKey, "webwxgetvideo", sMsgID);
	}

	public static File WebWeChatGetMedia2 (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, String sAccountHash, String sAPI, String sMediaID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
logger.info ("获取媒体/获取文件 WebWeChatGetMedia2：" + sMediaID);
		//             https://file.wx2.qq.com/cgi-bin/mmwebwx-bin/webwxgetmedia?sender=********************&mediaid=*********&filename=*******&fromuser=2100343515&pass_ticket=********&webwx_data_ticket=*****
		String sURL = "https://file.wx2.qq.com/cgi-bin/mmwebwx-bin/webwxgetmedia?sender=" + sAccountHash + "&mediaid=" + sMediaID + "&skey=" + URLEncoder.encode (sSessionKey, utf8);
		String sMediaFileName = mediaFilesDirectory + "/" + sMediaID;
		File fMediaFile = null;

logger.fine ("获取 WebWeChatGetMedia2 的 URL");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		CookieStore cookieStore = cookieManager.getCookieStore ();
		List<HttpCookie> listCookies = cookieStore.get (new URI(sURL));
		String sCookieValue = "";
		sCookieValue = MakeCookieValue (listCookies);
		mapRequestHeaders.put ("Cookie", sCookieValue);
		//mapRequestHeaders.put ("Range", "bytes=0-");
		//mapRequestHeaders.put ("Accept", "*/*");
		//mapRequestHeaders.put ("User-Agent", "bot");
logger.finer ("发送 WebWeChatGetMedia2 的 http 请求消息头 (Cookie、Range):");
logger.finer ("	" + mapRequestHeaders);

		URLConnection http = net_maclife_util_HTTPUtils.CURL_Connection (sURL, mapRequestHeaders);
		int iResponseCode = ((HttpURLConnection)http).getResponseCode();
		int iMainResponseCode = iResponseCode/100;
		if (iMainResponseCode==2)
		{
			String sExtensionName = net_maclife_util_HTTPUtils.ContentTypeToFileExtensionName (http.getHeaderField ("Content-Type"));
			if (StringUtils.isNotEmpty (sExtensionName))
				sMediaFileName = sMediaFileName + "." + sExtensionName;

			fMediaFile = new File (sMediaFileName);
			InputStream is = http.getInputStream ();
			OutputStream os = new FileOutputStream (fMediaFile);
			IOUtils.copy (is, os);
		}
logger.fine ("获取 WebWeChatGetMedia 的 http 响应消息体 (保存到文件)");
logger.fine ("	" + fMediaFile);
		return fMediaFile;
	}

	/**
	 * 根据帐号 Hash 来判断是否是聊天室帐号
	 * @param sAccountHash 帐号 Hash
	 * @return 如果帐号以 <code>@@</code> 开头，则返回 <code>true</code>，否则返回 <code>false</code>
	 */
	public static boolean IsRoomAccount (String sAccountHash)
	{
		return StringUtils.startsWith (sAccountHash, "@@");
	}

	/**
	 * 根据帐号的 VerifyFlag 来判断是否是公众号。
	 * 参考自： https://github.com/Urinx/WeixinBot/blob/master/README.md
	 * @param nVerifyFlag
	 * @return
	 */
	public static boolean IsPublicAccount (int nVerifyFlag)
	{
		return (nVerifyFlag & WECHAT_ACCOUNT_TYPE_MASK__Public) == WECHAT_ACCOUNT_TYPE_MASK__Public;
	}
	public static boolean IsSubscriberAccount (int nVerifyFlag)
	{
		return (nVerifyFlag & WECHAT_ACCOUNT_TYPE_MASK__Subscriber) == WECHAT_ACCOUNT_TYPE_MASK__Subscriber;
	}
	public static boolean IsTencentAccount (int nVerifyFlag)
	{
		return (nVerifyFlag & WECHAT_ACCOUNT_TYPE_MASK__Tencent) == WECHAT_ACCOUNT_TYPE_MASK__Tencent;
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
					else if (StringUtils.equalsIgnoreCase (sTerminalInput, "/login"))	// 二维码扫描自动登录，无需在这里处理。反正 Engine 线程会一直循环尝试登录
					{
					}
					else if (StringUtils.equalsIgnoreCase (sTerminalInput, "/logout"))
					{
						engine.Logout ();
					}
					else if (StringUtils.equalsIgnoreCase (sTerminalInput, "/start"))	// 二维码扫描自动登录，无需在这里处理。反正 Engine 线程会一直循环尝试登录
					{
						engine.Start ();
					}
					else if (StringUtils.startsWithIgnoreCase (sTerminalInput, "/stop"))
					{
						engine.Stop ();
					}
					else if (StringUtils.startsWithIgnoreCase (sTerminalInput, "/quit"))
					{
System.err.println ("收到退出命令");
						engine.Stop ();
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
logger.warning ("app 线程退出");
		executor.shutdownNow ();
	}

	public static boolean ParseBoolean (String sBoolean, boolean bDefault)
	{
		boolean r = bDefault;
		if (StringUtils.isEmpty (sBoolean))
			return r;

		if (StringUtils.equalsIgnoreCase (sBoolean, ("true"))
			|| StringUtils.equalsIgnoreCase (sBoolean, ("yes"))
			|| StringUtils.equalsIgnoreCase (sBoolean, ("on"))
			|| StringUtils.equalsIgnoreCase (sBoolean, ("t"))
			|| StringUtils.equalsIgnoreCase (sBoolean, ("y"))
			|| StringUtils.equalsIgnoreCase (sBoolean, ("1"))
			|| StringUtils.equalsIgnoreCase (sBoolean, ("是"))
			)
			r = true;
		else if (StringUtils.equalsIgnoreCase (sBoolean, ("false"))
			|| StringUtils.equalsIgnoreCase (sBoolean, ("no"))
			|| StringUtils.equalsIgnoreCase (sBoolean, ("off"))
			|| StringUtils.equalsIgnoreCase (sBoolean, ("f"))
			|| StringUtils.equalsIgnoreCase (sBoolean, ("n"))
			|| StringUtils.equalsIgnoreCase (sBoolean, ("0"))
			|| StringUtils.equalsIgnoreCase (sBoolean, ("否"))
			)
			r = false;

		return r;
	}
	public static boolean ParseBoolean (String sValue)
	{
		return ParseBoolean (sValue, false);
	}

	public static String GetJSONText (JsonNode node, String sFieldName, String sDefault)
	{
		if (node==null || node.get (sFieldName)==null)
			return sDefault;
		return node.get (sFieldName).asText ();
	}
	public static String GetJSONText (JsonNode node, String sFieldName)
	{
		return GetJSONText (node, sFieldName, "");
	}

	public static int GetJSONInt (JsonNode node, String sFieldName, int nDefault)
	{
		if (node==null || node.get (sFieldName)==null)
			return nDefault;
		return node.get (sFieldName).asInt ();
	}
	public static int GetJSONInt (JsonNode node, String sFieldName)
	{
		return GetJSONInt (node, sFieldName, -1);
	}

	public static void main (String[] args) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, ScriptException, ValidityException, ParsingException
	{
		net_maclife_wechat_http_BotApp app = new net_maclife_wechat_http_BotApp ();
		app.Start ();
	}
}
