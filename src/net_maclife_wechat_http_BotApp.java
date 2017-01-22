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
import org.apache.tomcat.jdbc.pool.*;

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
	public static final Logger logger = Logger.getLogger (net_maclife_wechat_http_BotApp.class.getName ());
	public static final ExecutorService executor = Executors.newCachedThreadPool ();	// .newFixedThreadPool (5);

	public static final String utf8 = "UTF-8";

	public static final Random random = new SecureRandom ();

	public static final int DEFAULT_NET_TRY_TIMES = 3;

	public static final int WECHAT_ACCOUNT_TYPE_MASK__Public = 0x08;	// 公众号
	public static final int WECHAT_ACCOUNT_TYPE_MASK__Subscriber = 0x10;	// 订阅号
	public static final int WECHAT_ACCOUNT_TYPE_MASK__WeChatTeam = 0x20;	// 微信团队自己的公众号

	private static final String configFileName = "src" + File.separator + "config.properties";
	private static Parameters configParameters = null;
	private static final ReloadingFileBasedConfigurationBuilder<PropertiesConfiguration> configBuilder;	// = null;
	//static Configurations configs = new Configurations();
	private static Configuration config = null;

	static
	{
		//try
		{
			configParameters = new Parameters ();
			BuilderParameters builderParameters = configParameters
				.fileBased ()
				.setEncoding (utf8)
				.setFileName (configFileName)
				;
			configBuilder = new ReloadingFileBasedConfigurationBuilder<PropertiesConfiguration> (PropertiesConfiguration.class).configure (builderParameters);
			configBuilder.setAutoSave (true);

			// 从配置文件读取并设置默认日志级别，省的每次启动后自己手工调节日志级别。
			String sDefaultLogLevel = GetConfig().getString ("app.log.default-level");
			if (StringUtils.isNotEmpty (sDefaultLogLevel))
			{
				logger.setLevel (Level.parse (sDefaultLogLevel));
			}
		}
		//catch (ConfigurationException e)
		{
		//	e.printStackTrace();
		}
	}

	public static Configuration GetConfig ()
	{
		try
		{
			// http://commons.apache.org/proper/commons-configuration/userguide/howto_reloading.html#Reloading_File-based_Configurations
			configBuilder.getReloadingController ().checkForReloading (null);
			config = configBuilder.getConfiguration ();
		}
		catch (ConfigurationException e)
		{
			e.printStackTrace();
		}
		return config;
	}

	public static String cacheDirectory = GetConfig ().getString ("app.running.cache-directory", "run");
	public static String qrcodeFilesDirectory = cacheDirectory + "/qrcodes";
	public static String mediaFilesDirectory = cacheDirectory + "/medias";
	static
	{
		File fQrcodeFilesDirectory = new File (qrcodeFilesDirectory);
		File fMediaFilesDirectory = new File (mediaFilesDirectory);
		fQrcodeFilesDirectory.mkdirs ();
		fMediaFilesDirectory.mkdirs ();
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

	public static final ObjectMapper jacksonObjectMapper_Strict = new ObjectMapper ();
	public static final ObjectMapper jacksonObjectMapper_Loose = new ObjectMapper ();	// 不那么严格的选项，但解析时也支持严格选项
	static
	{
		jacksonObjectMapper_Loose.configure (JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);	// 允许不对字段名加引号
		jacksonObjectMapper_Loose.configure (MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);	// 字段名不区分大小写

		jacksonObjectMapper_Loose.configure (JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);	// 允许用单引号把数值引起来
		jacksonObjectMapper_Loose.configure (JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);	// 数值前面补 0
	}

	static nu.xom.Builder xomBuilder = new nu.xom.Builder();

	static CookieManager cookieManager = new CookieManager ();
	static
	{
		cookieManager.setCookiePolicy (CookiePolicy.ACCEPT_ALL);
	}

	//static BasicDataSource botDS = null;
	static DataSource botDS = null;

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
logger.info ("获取新的登录 ID");
		String sURL = "https://login.weixin.qq.com/jslogin?appid=wx782c26e4c19acffb&redirect_uri=https%3A%2F%2Fwx.qq.com%2Fcgi-bin%2Fmmwebwx-bin%2Fwebwxnewloginpage&fun=new&lang=en_US&_=" + System.currentTimeMillis ();

		String sContent = null;
		int nTryTimes = GetConfig().getInt ("app.net.try-times", DEFAULT_NET_TRY_TIMES);
		for (int i=0; i<nTryTimes; i++)
		{
			try
			{
				sContent = net_maclife_util_HTTPUtils.CURL (sURL);	// window.QRLogin.code = 200; window.QRLogin.uuid = "QegF7Tukgw==";
logger.fine ("获取 LoginID 的 http 响应消息体:");
logger.fine ("	" + sContent);
				break;
			}
			//catch (UnknownHostException | SocketTimeoutException e)
			catch (IOException e)
			{
				e.printStackTrace ();
logger.info ("IO 异常: " + e + (i>=(nTryTimes-1) ? "，已是最后一次，不再重试" : "，准备重试 …"));
				continue;
			}
		}

		String sLoginID = public_jse.eval (StringUtils.replace (sContent, "window.QRLogin.", "var ") + " uuid;").toString ();
logger.info ("获取到的登录 ID:	" + sLoginID);

		return sLoginID;
	}

	public static File GetLoginQRCodeImageFile (String sLoginID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
logger.info ("根据登录 ID 获取二维码图片");
		String sURL = "https://login.weixin.qq.com/qrcode/" + sLoginID;
		//String sScanLoginURL = "https://login.weixin.qq.com/l/" + sLoginID;	// 二维码图片解码后的 URL
		String sFileName = qrcodeFilesDirectory + "/wechat-login-qrcode-image-" + sLoginID + ".jpg";
		String sFileName_PNG = qrcodeFilesDirectory + "/wechat-login-qrcode-image-" + sLoginID + "-10%.png";
		File fOutputFile = new File (sFileName);
		InputStream is = null;
		OutputStream os = null;

		int nTryTimes = GetConfig().getInt ("app.net.try-times", DEFAULT_NET_TRY_TIMES);
		for (int i=0; i<nTryTimes; i++)
		{
			try
			{
				is = net_maclife_util_HTTPUtils.CURL_Stream (sURL);
				os = new FileOutputStream (fOutputFile);
				IOUtils.copy (is, os);
				is.close ();
				os.close ();
logger.fine ("获取二维码图片的 http 响应消息体（保存到文件）:");
logger.info ("	" + fOutputFile);
				break;
			}
			//catch (UnknownHostException | SocketTimeoutException e)
			catch (IOException e)
			{
				e.printStackTrace ();
logger.info ("IO 异常: " + e + (i>=(nTryTimes-1) ? "，已是最后一次，不再重试" : "，准备重试 …"));
				continue;
			}
		}

		if (ParseBoolean(GetConfig ().getString ("app.text-QR-Code.display-in-terminal"), true))
		{
			boolean bBackgroundIsDarker = ParseBoolean(GetConfig ().getString ("app.text-QR-Code.terminal.background-is-darker-than-foreground"), true);
			boolean bUseANSIEscape = ParseBoolean(GetConfig ().getString ("app.text-QR-Code.use-ANSI-Escape"), true);
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
logger.info ("将二维码 jpg 文件【转换并缩小】适合文字输出大小的 png 文件: " + sPNGFileName);
		// convert wechat-login-qrcode-image-wb6kQwuV6A==.jpg -resize 10% -dither none -colors 2 -monochrome wechat-login-qrcode-image-wb6kQwuV6A==-10%.png
		ProcessBuilder pb = new ProcessBuilder
			(
				GetConfig ().getString ("app.external-utils.imagemagick.path") + File.separator + "convert"
				, sJPGFileName
				, "-resize"
				, "10%"
				, "-dither"
				, "none"
				, "-colors"
				, "2"
				, "-monochrome"
				, sPNGFileName
			);
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

		sbTextQRCode.append ("\n");	// 不跟 logger 在一行，新起一行
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

logger.info (sbTextQRCode.toString ());
	}

	public static Object 等待二维码被扫描以便登录 (String sLoginID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, ScriptException, ValidityException, ParsingException, URISyntaxException
	{
		String sLoginURL = "";
		String sLoginResultCode = "";
		int nLoginResultCode = 0;
		int nLoopCount = 0;

		String sURL = null;
		String sContent = null;
		int nStage = 1;	// 1: 等待扫描. 2:已扫描，等待登录确认
		int nTryTimes = GetConfig().getInt ("app.net.try-times", DEFAULT_NET_TRY_TIMES);
	while_loop:
		do
		{
			nLoginResultCode = 0;
			nLoopCount ++;
			long nTimestamp = System.currentTimeMillis ();
			long r = ~ nTimestamp;	// 0xFFFFFFFFFFFFFFFFL ^ nTimestamp;
			boolean bLoginIcon = false;	// true;
			sURL = "https://login.weixin.qq.com/cgi-bin/mmwebwx-bin/login?loginicon=" + bLoginIcon + "&uuid=" + sLoginID + "&tip=0&r=" + r + "&_=" + nTimestamp;

logger.info (String.format ("%3d", nLoopCount) + " 等待" + (nStage==1 ? "二维码被扫描" : nStage==2 ? "登录确认" : "") + " 的 http 响应消息体:");
			// window.code=408;	未扫描/超时。只要未扫描就不断循环，但 web 端好像重复 12 次（每次 25054 毫秒）左右后，就重新刷新 LoginID
			// window.code=201;	已扫描
			// window.code=200;	已确认登录
			// window.redirect_uri="https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxnewloginpage?ticket=A8qwapRV_lQ44viWM0mZmnpm@qrticket_0&uuid=gYiEFqEQdw==&lang=en_US&scan=1479893365";
			for (int i=0; i<nTryTimes; i++)
			{
				try
				{
					sContent = net_maclife_util_HTTPUtils.CURL (sURL);
logger.fine ("	" + sContent);
					break;
				}
				//catch (UnknownHostException | SocketTimeoutException e)
				catch (IOException e)
				{
					e.printStackTrace ();
logger.info ("IO 异常: " + e + (i>=(nTryTimes-1) ? "，已是最后一次，不再重试" : "，准备重试 …"));
					continue;
				}
			}
			if (StringUtils.isEmpty (sContent))
			{
				nLoginResultCode = 400;
logger.warning ("	响应消息体为空，二维码可能已经失效");
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
					nStage = 2;
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
			Map<String, Object> mapResult = new HashMap <String, Object> ();
			mapResult.put ("UserID", GetXMLValue (eXML, "wxuin"));
			mapResult.put ("SessionID", GetXMLValue (eXML, "wxsid"));
			mapResult.put ("SessionKey", GetXMLValue (eXML, "skey"));
			mapResult.put ("PassTicket", GetXMLValue (eXML, "pass_ticket"));
			mapResult.put ("LoginResultCode", nLoginResultCode);
			return mapResult;

		}
		return nLoginResultCode;
	}

	public static JsonNode MakeBaseRequestJsonNode (String sUserID, String sSessionID, String sSessionKey, String sDeviceID)
	{
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.put ("Uin", sUserID);
		on.put ("Sid", sSessionID);
		on.put ("Skey", sSessionKey);
		on.put ("DeviceID", sDeviceID);
		return on;
	}
	public static String MakeBaseRequestJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID)
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

	public static JsonNode MakeFullBaseRequestJsonNode (String sUserID, String sSessionID, String sSessionKey, String sDeviceID)
	{
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode(sUserID, sSessionID, sSessionKey, sDeviceID));
		return on;
	}

	public static String MakeFullBaseRequestJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID)
	{
		return
		"{\n" +
		"	\"BaseRequest\":\n" + MakeBaseRequestJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + "\n" +
		"}\n";
	}

	public static String MakeDeviceID ()
	{
		long nRand = random.nextLong () & 0x7FFFFFFFFFFFFFFFL;
		return "e" + String.format ("%015d", nRand).substring (0, 15);	// System.currentTimeMillis ();
	}

	public static void AppendContactInformation (StringBuilder sb, JsonNode jsonContact, boolean bIsRoomMember)
	{
		String sNickName = GetJSONText (jsonContact, "NickName");
		sb.append (sNickName);
		String sRemarkNameOrDisplayName = GetJSONText (jsonContact, bIsRoomMember ? "DisplayName" : "RemarkName");

		int nVerifyFlag = GetJSONInt (jsonContact, "VerifyFlag");
		boolean isPublicAccount = IsPublicAccount (nVerifyFlag);
		boolean isRoomAccount = IsRoomAccount (GetJSONText (jsonContact, "UserName"));
		if (isRoomAccount || isPublicAccount || (StringUtils.isNotBlank (sRemarkNameOrDisplayName) && ! StringUtils.equalsIgnoreCase (sNickName, sRemarkNameOrDisplayName)))
		{
			sb.append (" (");
			if (StringUtils.isNotBlank (sRemarkNameOrDisplayName) && ! StringUtils.equalsIgnoreCase (sNickName, sRemarkNameOrDisplayName))
			{
				sb.append (sRemarkNameOrDisplayName);
			}
			if (isRoomAccount)
				sb.append ("聊天室/群");
			if (isPublicAccount)
			{
				sb.append ("公众号");
				if (IsSubscriberAccount (nVerifyFlag))
				{
					sb.append (", 订阅号");
					if (IsWeChatTeamAccount (nVerifyFlag))
					{
						sb.append (", 微信团队号");
					}
				}
			}
			sb.append (")");
		}
	}
	public static void AppendContactInformation (StringBuilder sb, JsonNode jsonContact)
	{
		AppendContactInformation (sb, jsonContact, false);
	}

	public static JsonNode WebWeChatInit (String sUserID, String sSessionID, String sSessionKey, String sPassTicket) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("初始化 …");
		// https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxinit?r=1703974212&lang=zh_CN&pass_ticket=ZfvpI6wcO7N5PTkacmWK9zUTXpUOB3kqre%2BrkQ8IAtHDAIP2mc2psB5eDH8cwzsp
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxinit?r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + sPassTicket;
logger.fine ("WebWeChatInit 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
logger.finer ("发送 WebWeChatInit 的 http 请求消息头 (Content-Type):");
logger.finer ("	" + mapRequestHeaders);

		String sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullBaseRequestJsonNode (sUserID, sSessionID, sSessionKey, MakeDeviceID ()));
logger.finer ("发送 WebWeChatInit 的 http 请求消息体:");
logger.finer (sRequestBody_JSONString);

		InputStream is = null;
		JsonNode node = null;
		int nTryTimes = GetConfig().getInt ("app.net.try-times", DEFAULT_NET_TRY_TIMES);
		for (int i=0; i<nTryTimes; i++)
		{
			try
			{
				is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
				node = jacksonObjectMapper_Loose.readTree (is);
				is.close ();
logger.fine ("获取 WebWeChatInit 的 http 响应消息体:");
logger.fine ("	" + node);
				break;
			}
			//catch (UnknownHostException | SocketTimeoutException e)
			catch (IOException e)
			{
				e.printStackTrace ();
logger.info ("IO 异常: " + e + (i>=(nTryTimes-1) ? "，已是最后一次，不再重试" : "，准备重试 …"));
				continue;
			}
		}

		ProcessBaseResponse (node, "WebWeChatInit");

		JsonNode jsonUser = node.get ("User");
		StringBuilder sb = new StringBuilder ();
		sb.append ("\n");
		sb.append ("昵称: ");
		sb.append (GetJSONText (jsonUser, "NickName"));
		sb.append ("\n");
		sb.append ("本次会话的加密帐号: ");
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
			AppendContactInformation (sb, jsonContact);
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

	public static JsonNode MakeFullStatusNotifyRequestJsonNode (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sMyAccount)
	{
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode(sUserID, sSessionID, sSessionKey, sDeviceID));
		on.put ("Code", 3);
		on.put ("FromUserName", sMyAccount);
		on.put ("ToUserName", sMyAccount);
		on.put ("ClientMsgId", GenerateLocalMessageID ());
		return on;
	}
	public static String MakeFullStatusNotifyRequestJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sMyAccount)
	{
		return
		"{\n" +
		"	\"BaseRequest\":\n" + MakeBaseRequestJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + ",\n" +
		"	\"Code\": 3,\n" +
		"	\"FromUserName\": \"" + sMyAccount + "\",\n" +
		"	\"ToUserName\": \"" + sMyAccount + "\",\n" +
		"	\"ClientMsgId\": " + GenerateLocalMessageID () + "\n" +
		"}\n";
	}
	public static JsonNode WebWeChatStatusNotify (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, String sMyAccount) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("开启状态通知 …");
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxstatusnotify?lang=zh_CN&pass_ticket=" + sPassTicket;
logger.fine ("WebWeChatStatusNotify 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
logger.finer ("发送 WebWeChatStatusNotify 的 http 请求消息头 (Content-Type):");
logger.finer ("	" + mapRequestHeaders);

		String sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullStatusNotifyRequestJsonNode (sUserID, sSessionID, sSessionKey, MakeDeviceID (), sMyAccount));
logger.finer ("发送 WebWeChatStatusNotify 的 http 请求消息体:");
logger.finer (sRequestBody_JSONString);
		InputStream is = null;
		JsonNode node = null;
		int nTryTimes = GetConfig().getInt ("app.net.try-times", DEFAULT_NET_TRY_TIMES);
		for (int i=0; i<nTryTimes; i++)
		{
			try
			{
				is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
				node = jacksonObjectMapper_Loose.readTree (is);
				is.close ();
logger.fine ("获取 WebWeChatStatusNotify 的 http 响应消息体:");
logger.fine ("	" + node);
				break;
			}
			//catch (UnknownHostException | SocketTimeoutException e)
			catch (IOException e)
			{
				e.printStackTrace ();
logger.info ("IO 异常: " + e + (i>=(nTryTimes-1) ? "，已是最后一次，不再重试" : "，准备重试 …"));
				continue;
			}
		}

		ProcessBaseResponse (node, "WebWeChatStatusNotify");

		return node;
	}

	public static JsonNode WebWeChatGetContacts (String sUserID, String sSessionID, String sSessionKey, String sPassTicket) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("获取联系人 …");
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxgetcontact?r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + sPassTicket;
logger.fine ("WebWeChatGetContacts 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
logger.finer  ("发送 WebWeChatGetContacts 的 http 请求消息头 (Content-Type):");
logger.finer  ("	" + mapRequestHeaders);

String sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullBaseRequestJsonNode (sUserID, sSessionID, sSessionKey, MakeDeviceID ()));
logger.finer  ("发送 WebWeChatGetContacts 的 http 请求消息体:");
logger.finer  ("	" + sRequestBody_JSONString);

		InputStream is = null;
		JsonNode node = null;
		int nTryTimes = GetConfig().getInt ("app.net.try-times", DEFAULT_NET_TRY_TIMES);
		for (int i=0; i<nTryTimes; i++)
		{
			try
			{
				is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
				node = jacksonObjectMapper_Loose.readTree (is);
				is.close ();
logger.fine  ("获取 WebWeChatGetContacts 的 http 响应消息体:");
logger.fine  ("	" + node);
				break;
			}
			//catch (UnknownHostException | SocketTimeoutException e)
			catch (IOException e)
			{
				e.printStackTrace ();
logger.info ("IO 异常: " + e + (i>=(nTryTimes-1) ? "，已是最后一次，不再重试" : "，准备重试 …"));
				continue;
			}
		}

		ProcessBaseResponse (node, "WebWeChatGetContacts");

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
			AppendContactInformation (sb, jsonMember);
			sb.append ("    ");
		}
logger.info (sb.toString ());
		return node;
	}

	public static List<String> GetRoomAccountsFromContacts (JsonNode jsonContacts)
	{
		List<String> listRoomAccounts = new ArrayList<String> ();
		JsonNode jsonMemberList = jsonContacts.get ("MemberList");
		for (int i=0; i<jsonMemberList.size (); i++)
		{
			JsonNode jsonContact = jsonMemberList.get (i);
			String sUserEncryptedAccount = GetJSONText (jsonContact, "UserName");
			if (IsRoomAccount (sUserEncryptedAccount))
				listRoomAccounts.add (sUserEncryptedAccount);
		}
		return listRoomAccounts;
	}
	public static JsonNode MakeFullGetRoomContactRequestJsonNode (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, List<String> listRoomAccounts)
	{
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode(sUserID, sSessionID, sSessionKey, sDeviceID));
		on.put ("Count", listRoomAccounts.size ());
		ArrayNode an = jacksonObjectMapper_Strict.createArrayNode ();
		for (int i=0; i<listRoomAccounts.size (); i++)
		{
			ObjectNode temp = jacksonObjectMapper_Strict.createObjectNode ();
			temp.put ("UserName", listRoomAccounts.get (i));
			temp.put ("EncryChatRoomAccount", "");
			an.add (temp);
		}
		on.set ("List", an);
		return on;
	}
	public static String MakeFullGetRoomContactRequestJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, List<String> listRoomAccounts)
	{
		StringBuilder sbBody = new StringBuilder ();
		sbBody.append ("{\n");
		sbBody.append ("	\"BaseRequest\":\n" + MakeBaseRequestJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + ",\n");
		sbBody.append ("	\"Count\": " + listRoomAccounts.size () + ",\n");
		sbBody.append ("	\"List\":\n");
		sbBody.append ("	[\n");
		for (int i=0; i<listRoomAccounts.size (); i++)
		{
			sbBody.append ("		{\n");
			sbBody.append ("			\"UserName\": \"" + listRoomAccounts.get (i) + "\",\n");
			sbBody.append ("			\"EncryChatRoomAccount\": \"\"\n");
			sbBody.append ("		}");
			if (i != listRoomAccounts.size ()-1)
			{
				sbBody.append (",");
			}
			sbBody.append ("\n");
		}
		sbBody.append ("	]\n");
		sbBody.append ("}\n");
		return sbBody.toString ();
	}

	public static JsonNode WebWeChatGetRoomContacts (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, List<String> listRoomAccounts) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("获取 " + listRoomAccounts.size () + " 个聊天室的联系人 …");
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxbatchgetcontact?type=ex&r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + sPassTicket;
logger.fine ("WebWeChatGetRoomContacts 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
		String sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullGetRoomContactRequestJsonNode (sUserID, sSessionID, sSessionKey, MakeDeviceID (), listRoomAccounts));
logger.finer ("发送 WebWeChatGetRoomContacts 的 http 请求消息体:");
logger.finer ("	" + sRequestBody_JSONString);
		InputStream is = null;
		JsonNode node = null;
		int nTryTimes = GetConfig().getInt ("app.net.try-times", DEFAULT_NET_TRY_TIMES);
		for (int i=0; i<nTryTimes; i++)
		{
			try
			{
				is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
				node = jacksonObjectMapper_Loose.readTree (is);
				is.close ();
logger.fine ("获取 WebWeChatGetRoomContacts 的 http 响应消息体:");
logger.fine ("	" + node);
				break;
			}
			//catch (UnknownHostException | SocketTimeoutException e)
			catch (IOException e)
			{
				e.printStackTrace ();
logger.info ("IO 异常: " + e + (i>=(nTryTimes-1) ? "，已是最后一次，不再重试" : "，准备重试 …"));
				continue;
			}
		}

		ProcessBaseResponse (node, "WebWeChatGetRoomContacts");

		StringBuilder sb = new StringBuilder ();
		//sb.append ("\n");
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
				AppendContactInformation (sb, jsonMember, true);
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
	 * 只要指定了 sEncryptedAccountInASession，就会搜到唯一一个联系人（除非给出的 ID 不正确），
	 * 如果没指定 sEncryptedAccountInASession (null 或空字符串)，则尽可能全部指定 sAlias、sRemarkName、sNickName，以便更精确的匹配联系人。
	 * @param jsonMemberList
	 * @param sEncryptedAccountInASession 类似  @********** filehelper weixin 等 ID，可以唯一对应一个联系人。最高优先级。
	 * @param sAliasAccount 自定义帐号。如果 UserIDInThisSession 为空，则尝试根据 sAlias 获取。次优先级。
	 * @param sRemarkName 备注名。如果 Alias 也为空，则根据备注名称获取。再次优先级。
	 * @param sDisplayName 显示名/群昵称。如果 Alias 也为空，则根据显示名/群昵称获取。再次优先级。 注意：DisplayName 通常只在群联系人中才有可能有值。
	 * @param sNickName 昵称。如果 Alias 也为空，则根据昵称获取。这个优先级在最后，因为，用户自己更改昵称的情况应该比前面的更常见，导致不确定性更容易出现。
	 * @return 搜索到的联系人的 JsonNode 列表。正常情况下应该为 1 个，但也可能为空，也可能为多个。
	 */
	public static List<JsonNode> SearchForContacts (JsonNode jsonMemberList, String sEncryptedAccountInASession, String sAliasAccount, String sRemarkName, String sDisplayName, String sNickName)
	{
		List<JsonNode> listUsersMatched = new ArrayList <JsonNode> ();
		//JsonNode jsonUser = null;
		for (int i=0; i<jsonMemberList.size (); i++)
		{
			JsonNode node = jsonMemberList.get (i);

			if (StringUtils.isNotEmpty (sEncryptedAccountInASession))
			{
				String sTemp = GetJSONText (node, "UserName");
				if (StringUtils.equalsIgnoreCase (sEncryptedAccountInASession, sTemp))
				{
					//jsonUser = node;
					listUsersMatched.add (node);
					break;	// 加密帐号是唯一的，找到了就不再继续找了
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
					break;	// 微信号也应该是唯一的，找到了就不再继续找了
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

			else if (StringUtils.isNotEmpty (sDisplayName))
			{
				String sTemp = GetJSONText (node, "DisplayName");
				if (StringUtils.equalsIgnoreCase (sDisplayName, sTemp))
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
	 * @param sEncryptedAccountInThisSession
	 * @param sAlias
	 * @param sRemarkName
	 * @param sDisplayName
	 * @param sNickName
	 * @return
	 */
	public static JsonNode SearchForSingleContact (JsonNode jsonMemberList, String sEncryptedAccountInThisSession, String sAlias, String sRemarkName, String sDisplayName, String sNickName)
	{
		List<JsonNode> listUsers = SearchForContacts (jsonMemberList, sEncryptedAccountInThisSession, sAlias, sRemarkName, sDisplayName, sNickName);
		return listUsers.size ()==0 ? null : listUsers.get (0);
	}

	public static String MakeSyncCheckKeysQueryString (JsonNode jsonSyncCheckKeys)
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
	public static JsonNode MakeFullWeChatSyncRequestJsonNode (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, JsonNode jsonSyncKey)
	{
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode(sUserID, sSessionID, sSessionKey, sDeviceID));
		on.set ("SyncKey", jsonSyncKey);
		on.put ("rr", System.currentTimeMillis ()/1000);
		return on;
	}
	public static String MakeFullWeChatSyncRequestJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, JsonNode jsonSyncKey)
	{
		return
		"{\n" +
		"	\"BaseRequest\":\n" + MakeBaseRequestJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + ",\n" +
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
	public static JsonNode WebWeChatGetMessagePackage (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, JsonNode jsonSyncCheckKeys) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, ScriptException, URISyntaxException, InterruptedException
	{
logger.finest ("等待并获取新消息 WebWeChatGetMessagePackage (synccheck & webwxsync) …");	// 这里的日志级别改为了 fine，因这个在死循环中，产生太多日志
		String sSyncCheckKeys = MakeSyncCheckKeysQueryString (jsonSyncCheckKeys);
		String sSyncCheckURL = "https://webpush.wx2.qq.com/cgi-bin/mmwebwx-bin/synccheck?r=" + System.currentTimeMillis () + "&skey=" + URLEncoder.encode (sSessionKey, utf8) + "&sid=" + URLEncoder.encode (sSessionID, utf8) + "&uin=" + sUserID + "&deviceid=" + MakeDeviceID () + "&synckey=" +  sSyncCheckKeys + "&_=" + System.currentTimeMillis ();
logger.finest ("WebWeChatGetMessagePackage 中 synccheck 的 URL:");
logger.finest ("	" + sSyncCheckURL);

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
logger.finest ("发送 WebWeChatGetMessagePackage 中 synccheck 的 http 请求消息头 (Cookie):");
logger.finest ("	" + mapRequestHeaders);

		String sContent = null;
		int nTryTimes = GetConfig().getInt ("app.net.try-times", DEFAULT_NET_TRY_TIMES);
		for (int i=0; i<nTryTimes; i++)
		{
			try
			{
				sContent = net_maclife_util_HTTPUtils.CURL (sSyncCheckURL, mapRequestHeaders);	// window.synccheck={retcode:"0",selector:"2"}
				break;
			}
			//catch (UnknownHostException | SocketTimeoutException e)
			catch (IOException e)
			{
				e.printStackTrace ();
logger.info ("IO 异常: " + e + (i>=(nTryTimes-1) ? "，已是最后一次，不再重试" : "，准备重试 …"));
				continue;
			}
		}
logger.finest ("获取 WebWeChatGetMessagePackage 中 synccheck 的 http 响应消息体:");
logger.finest ("	" + sContent);

		String sJSCode = StringUtils.replace (sContent, "window.", "var ");
		String sSyncCheckReturnCode = public_jse.eval (sJSCode + "; synccheck.retcode;").toString ();
		String sSyncCheckSelector = public_jse.eval (sJSCode + "; synccheck.selector;").toString ();

		JsonNode jsonResult = null;
		if (StringUtils.equalsIgnoreCase (sSyncCheckReturnCode, "0"))
		{
//logger.finest ("WebWeChatGetMessagePackage 中 synccheck 返回 selector " + sSyncCheckSelector);
			switch (sSyncCheckSelector)
			{
				case "0":	// nothing
//logger.finest ("WebWeChatGetMessagePackage 中 synccheck 返回 selector 0 -- 无消息");
					break;
				//case "2":	// 有新消息
//logger.finest ("WebWeChatGetMessagePackage 中 synccheck 返回 selector 2 -- 有新消息");
				//case "6":
				//case "7":
				default:
					String sSyncURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsync?sid=" + URLEncoder.encode (sSessionID, utf8) + "&skey" + URLEncoder.encode (sSessionKey, utf8) + "&lang=zh_CN&pass_ticket=" +  sPassTicket;
logger.finest ("WebWeChatGetMessagePackage 中 webwxsync 的 URL:");
logger.finest ("	" + sSyncURL);

					//mapRequestHeaders = new HashMap<String, Object> ();
					mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
					//mapRequestHeaders.put ("Cookie", sCookieValue);	// 避免服务器返回 "Ret": 1 代码
					String sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullWeChatSyncRequestJsonNode (sUserID, sSessionID, sSessionKey, MakeDeviceID (), jsonSyncCheckKeys));
logger.finest ("发送 WebWeChatGetMessagePackage 中 webwxsync 的 http 请求消息头 (Cookie & Content-Type):");
logger.finest ("	" + mapRequestHeaders);
logger.finest ("发送 WebWeChatGetMessagePackage 中 webwxsync 的 http 请求消息体:");
logger.finest ("	\n" + sRequestBody_JSONString);
					InputStream is = null;
					JsonNode node = null;
					for (int i=0; i<nTryTimes; i++)
					{
						try
						{
							is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sSyncURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
							node = jacksonObjectMapper_Loose.readTree (is);
							jsonResult = node;
logger.info ("\n--------------------------------------------------");
logger.finer ("获取 WebWeChatGetMessagePackage 中 webwxsync 的 http 响应消息体:");
logger.finer ("\n" + node);
							is.close ();
							break;
						}
						//catch (UnknownHostException | SocketTimeoutException e)
						catch (IOException e)
						{
							e.printStackTrace ();
logger.info ("IO 异常: " + e + (i>=(nTryTimes-1) ? "，已是最后一次，不再重试" : "，准备重试 …"));
							TimeUnit.SECONDS.sleep (5);
							continue;
						}
					}

					ProcessBaseResponse (node, "WebWeChatGetMessagePackage 中 webwxsync");

					break;
				//case "6":	// 这个是啥？昨天晚上遇到过了，貌似是别人请求添加好友时遇到的，然后就一直返回 6，死循环出不来了
//logger.fine ("WebWeChatGetMessagePackage 中 synccheck 返回 selector 6 -- 别人请求添加好友？");
				//	break;
				//case "7":	// 进入离开聊天页面？
//logger.fine ("WebWeChatGetMessagePackage 中 synccheck 返回 selector 7 -- 进入/离开聊天页面？");
				//	break;
				//default:
//logger.fine ("WebWeChatGetMessagePackage 中 synccheck 返回未知的 selector: " + sSyncCheckSelector);
					//break;
			}
		}
		else if (StringUtils.equalsIgnoreCase (sSyncCheckReturnCode, "1100") || StringUtils.equalsIgnoreCase (sSyncCheckReturnCode, "1101") || StringUtils.equalsIgnoreCase (sSyncCheckReturnCode, "1102"))
		{
logger.warning (net_maclife_util_ANSIEscapeTool.Yellow ("WebWeChatGetMessagePackage 中 synccheck 返回 " + sSyncCheckReturnCode + " -- 可能在其他地方登录了微信网页版（含 Windows 版）、或者 SyncCheckKey 参数不正确、或者手机微信退出"));
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
logger.info ("退出微信 …");
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

		String sContent = null;
		int nTryTimes = GetConfig().getInt ("app.net.try-times", DEFAULT_NET_TRY_TIMES);
		for (int i=0; i<nTryTimes; i++)
		{
			try
			{
				sContent = net_maclife_util_HTTPUtils.CURL_Post (sURL, mapRequestHeaders, sRequestBody.getBytes ());
logger.fine ("获取 WebWeChatLogout 的 http 响应消息体:");
logger.fine ("\n" + sContent);
				break;
			}
			//catch (UnknownHostException | SocketTimeoutException e)
			catch (IOException e)
			{
				e.printStackTrace ();
logger.info ("IO 异常: " + e + (i>=(nTryTimes-1) ? "，已是最后一次，不再重试" : "，准备重试 …"));
				continue;
			}
		}
	}

	/**
	 * 发送消息
	 *
	 * @param sFrom_Account 来自帐号（大概，Web 版微信 HTTP 协议中，发件人只有可能是自己了，然而也不确定）
	 * @param sTo_Account 发往帐号。帐号可以是本次会话的加密帐号，也可以是类似 wxid_***  gh_***  filehelper 之类的明帐号
	 */
	public static JsonNode WebWeChatSendMessage (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_Account, String sTo_Account, int nMessageType, Object oMessage) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
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
				sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullSendTextMessageRequestJsonNode (sUserID, sSessionID, sSessionKey, MakeDeviceID (), sFrom_Account, sTo_Account, (String)oMessage));
				break;
			case net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__IMAGE:
				sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendmsgimg?fun=async&f=json&lang=zh_CN&pass_ticket=" + sPassTicket;
				sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullSendImageMessageRequestJsonNode (sUserID, sSessionID, sSessionKey, MakeDeviceID (), sFrom_Account, sTo_Account, (String)oMessage));
				break;
			case net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__VIDEO_MSG:
				sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendvideomsg?fun=async&f=json&lang=zh_CN&pass_ticket=" + sPassTicket;
				sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullSendImageMessageRequestJsonNode (sUserID, sSessionID, sSessionKey, MakeDeviceID (), sFrom_Account, sTo_Account, (String)oMessage));
				break;
			case net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__APP:
				sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendappmsg?fun=async&f=json&lang=zh_CN&pass_ticket=" + sPassTicket;
				sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullSendApplicationMessageRequestJsonNode (sUserID, sSessionID, sSessionKey, MakeDeviceID (), sFrom_Account, sTo_Account, (Element)oMessage));
				break;
			case net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__EMOTION:
				sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendemoticon?fun=sys&f=json&lang=zh_CN&pass_ticket=" + sPassTicket;
				sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullSendEmotionMessageRequestJSONString (sUserID, sSessionID, sSessionKey, MakeDeviceID (), sFrom_Account, sTo_Account, (String)oMessage));
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
		InputStream is = null;
		JsonNode node = null;
		int nTryTimes = GetConfig().getInt ("app.net.try-times", DEFAULT_NET_TRY_TIMES);
		for (int i=0; i<nTryTimes; i++)
		{
			try
			{
				is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
				node = jacksonObjectMapper_Loose.readTree (is);
				is.close ();
				break;
			}
			//catch (UnknownHostException | SocketTimeoutException e)
			catch (IOException e)
			{
				e.printStackTrace ();
logger.info ("IO 异常: " + e + (i>=(nTryTimes-1) ? "，已是最后一次，不再重试" : "，准备重试 …"));
				continue;
			}
		}
logger.fine ("获取 WebWeChatSendMessage 的 http 响应消息体:");
logger.fine ("\n" + node);

		ProcessBaseResponse (node, "WebWeChatSendMessage");

		return node;
	}

	public static JsonNode MakeFullSendTextMessageRequestJsonNode (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sContent)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (sUserID, sSessionID, sSessionKey, sDeviceID));
			ObjectNode msg = jacksonObjectMapper_Strict.createObjectNode ();
			msg.put ("Type", net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__TEXT);
			msg.put ("Content", sContent);
			msg.put ("FromUserName", sFrom);
			msg.put ("ToUserName", sTo);
			msg.put ("LocalID", nLocalMessageID);
			msg.put ("ClientMsgId", nLocalMessageID);
		on.set ("Msg", msg);
		return on;
	}
	public static String MakeFullSendTextMessageRequestJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sMessage)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		return
		"{\n" +
		"	\"BaseRequest\":\n" + MakeBaseRequestJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + ",\n" +
		"	\"Msg\":\n" +
		"	{\n" +
		"		\"Type\": " + net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__TEXT + ",\n" +
		"		\"Content\": \"" + StringUtils.replace (sMessage, "\"", "\\\"") + "\",\n" +
		"		\"FromUserName\": \"" + sFrom + "\",\n" +
		"		\"ToUserName\": \"" + sTo + "\",\n" +
		"		\"LocalID\": \"" + nLocalMessageID + "\",\n" +
		"		\"ClientMsgId\": \"" + nLocalMessageID + "\"\n" +
		"	}\n" +
		"}\n";
	}
	public static JsonNode WebWeChatSendTextMessage (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_Account, String sTo_Account, String sMessage) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("发文本消息:\n" + net_maclife_util_ANSIEscapeTool.Cyan (sMessage));
		return WebWeChatSendMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sFrom_Account, sTo_Account, net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__TEXT, sMessage);
	}

	public static JsonNode MakeFullSendImageMessageRequestJsonNode (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sMediaID)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (sUserID, sSessionID, sSessionKey, sDeviceID));
			ObjectNode msg = jacksonObjectMapper_Strict.createObjectNode ();
			msg.put ("Type", net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__IMAGE);
			msg.put ("MediaId", sMediaID);
			msg.put ("FromUserName", sFrom);
			msg.put ("ToUserName", sTo);
			msg.put ("LocalID", nLocalMessageID);
			msg.put ("ClientMsgId", nLocalMessageID);
		on.set ("Msg", msg);
		on.put ("Scene", 0);
		return on;
	}
	public static String MakeFullSendImageMessageRequestJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sMediaID)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		return
		"{\n" +
		"	\"BaseRequest\":\n" + MakeBaseRequestJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + ",\n" +
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
	public static JsonNode WebWeChatSendImageMessage (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_Account, String sTo_Account, String sMediaID) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("发图片消息: " + sMediaID);
		return WebWeChatSendMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sFrom_Account, sTo_Account, net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__IMAGE, sMediaID);
	}

	public static JsonNode MakeFullSendEmotionMessageRequestJsonNode (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sMediaID)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (sUserID, sSessionID, sSessionKey, sDeviceID));
			ObjectNode msg = jacksonObjectMapper_Strict.createObjectNode ();
			msg.put ("Type", net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__EMOTION);
			msg.put ("EmojiFlag", 2);
			msg.put ("EMotionMd5", sMediaID);
			msg.put ("FromUserName", sFrom);
			msg.put ("ToUserName", sTo);
			msg.put ("LocalID", nLocalMessageID);
			msg.put ("ClientMsgId", nLocalMessageID);
		on.set ("Msg", msg);
		on.put ("Scene", 0);
		return on;
	}
	public static String MakeFullSendEmotionMessageRequestJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sMediaID)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		return
		"{\n" +
		"	\"BaseRequest\":\n" + MakeBaseRequestJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + ",\n" +
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
	public static JsonNode WebWeChatSendEmotionMessage (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_Account, String sTo_Account, String sMediaID) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("发表情图消息: " + sMediaID);
		return WebWeChatSendMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sFrom_Account, sTo_Account, net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__EMOTION, sMediaID);
	}

	public static JsonNode MakeFullSendVideoMessageRequestJsonNode (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sMediaID)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (sUserID, sSessionID, sSessionKey, sDeviceID));
			ObjectNode msg = jacksonObjectMapper_Strict.createObjectNode ();
			msg.put ("Type", net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__VIDEO_MSG);
			msg.put ("MediaId", sMediaID);
			msg.put ("FromUserName", sFrom);
			msg.put ("ToUserName", sTo);
			msg.put ("LocalID", nLocalMessageID);
			msg.put ("ClientMsgId", nLocalMessageID);
		on.set ("Msg", msg);
		on.put ("Scene", 0);
		return on;
	}
	public static String MakeFullSendVideoMessageRequestJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sMediaID)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		return
		"{\n" +
		"	\"BaseRequest\":\n" + MakeBaseRequestJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + ",\n" +
		"	\"Msg\":\n" +
		"	{\n" +
		"		\"Type\": " + net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__VIDEO_MSG + ",\n" +
		"		\"MediaId\": \"" + sMediaID + "\",\n" +
		"		\"FromUserName\": \"" + sFrom + "\",\n" +
		"		\"ToUserName\": \"" + sTo + "\",\n" +
		"		\"LocalID\": \"" + nLocalMessageID + "\",\n" +
		"		\"ClientMsgId\": \"" + nLocalMessageID + "\"\n" +
		"	},\n" +
		"	\"Scene\": 0\n" +
		"}\n";
	}
	public static JsonNode WebWeChatSendVideoMessage (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_Account, String sTo_Account, String sMediaID) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("发视频消息: " + sMediaID);
		return WebWeChatSendMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sFrom_Account, sTo_Account, net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__VIDEO_MSG, sMediaID);
	}

	public static JsonNode MakeFullSendApplicationMessageRequestJsonNode (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, Element eXML)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (sUserID, sSessionID, sSessionKey, sDeviceID));
			ObjectNode msg = jacksonObjectMapper_Strict.createObjectNode ();
			msg.put ("Type", net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__APP);
			msg.put ("Content", eXML.toXML ());
			msg.put ("FromUserName", sFrom);
			msg.put ("ToUserName", sTo);
			msg.put ("LocalID", nLocalMessageID);
			msg.put ("ClientMsgId", nLocalMessageID);
		on.set ("Msg", msg);
		return on;
	}
	public static String MakeFullSendApplicationMessageRequestJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sXMLMessage)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		return
		"{\n" +
		"	\"BaseRequest\":\n" + MakeBaseRequestJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) + ",\n" +
		"	\"Msg\":\n" +
		"	{\n" +
		"		\"Type\": " + net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__APP + ",\n" +
		"		\"Content\": \"" + sXMLMessage + "\",\n" +
		"		\"FromUserName\": \"" + sFrom + "\",\n" +
		"		\"ToUserName\": \"" + sTo + "\",\n" +
		"		\"LocalID\": \"" + nLocalMessageID + "\",\n" +
		"		\"ClientMsgId\": \"" + nLocalMessageID + "\"\n" +
		"	}\n" +
		"}\n";
	}
	public static JsonNode WebWeChatSendApplicationMessage (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_Account, String sTo_Account, String sMediaID) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("发应用程序 (如：上传文件) 消息，媒体 ID: " + sMediaID);
		return WebWeChatSendMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sFrom_Account, sTo_Account, net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__APP, sMediaID);
	}

	public static JsonNode WebWeChatUploadMedia (File f)
	{
logger.info ("上传媒体/上传文件: " + f);
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
logger.fine ("获取媒体/获取文件 …");
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/" + sAPI + "?" + (StringUtils.equalsIgnoreCase (sAPI, "webwxgetmsgimg") ? "MsgId" : "msgid") + "=" + sMsgID + "&skey=" + URLEncoder.encode (sSessionKey, utf8);
		String sMediaFileName = mediaFilesDirectory + "/" + sMsgID;
		File fMediaFile = null;

logger.fine ("WebWeChatGetMedia 的 URL (api = " + sAPI + ")");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		CookieStore cookieStore = cookieManager.getCookieStore ();
		List<HttpCookie> listCookies = cookieStore.get (new URI(sURL));
		String sCookieValue = "";
		sCookieValue = MakeCookieValue (listCookies);
		mapRequestHeaders.put ("Cookie", sCookieValue);
		mapRequestHeaders.put ("Range", "bytes=0-");	// 该请求头是必需的，否则下载视频文件时会返回 0 字节的数据
		//mapRequestHeaders.put ("Accept", "*/*");
		//mapRequestHeaders.put ("User-Agent", "bot");
logger.fine ("发送 WebWeChatGetMedia 的 http 请求消息头 (Cookie、Range):");
logger.fine ("	" + mapRequestHeaders);

		int nTryTimes = GetConfig().getInt ("app.net.try-times", DEFAULT_NET_TRY_TIMES);
		for (int i=0; i<nTryTimes; i++)
		{
			try
			{
				URLConnection http = net_maclife_util_HTTPUtils.CURL_Connection (sURL, mapRequestHeaders);
				int iResponseCode = ((HttpURLConnection)http).getResponseCode();
				int iMainResponseCode = iResponseCode/100;
				if (iMainResponseCode==2)
				{
					String sExtensionName = net_maclife_util_HTTPUtils.ContentTypeToFileExtensionName (http.getHeaderField ("Content-Type"));
					if (StringUtils.isNotEmpty (sExtensionName))
						sMediaFileName = sMediaFileName + "." + sExtensionName;

					fMediaFile = new File (sMediaFileName);
logger.fine ("获取 WebWeChatGetMedia 的 http 响应消息体 (保存到文件)");
logger.fine ("	" + fMediaFile);

					InputStream is = http.getInputStream ();
					OutputStream os = new FileOutputStream (fMediaFile);
					int nBytes = IOUtils.copy (is, os);
logger.info ("获取了 " + nBytes + " 字节的数据");
					is.close ();
					os.close ();
				}
				break;
			}
			//catch (UnknownHostException | SocketTimeoutException e)
			catch (IOException e)
			{
				e.printStackTrace ();
logger.info ("IO 异常: " + e + (i>=(nTryTimes-1) ? "，已是最后一次，不再重试" : "，准备重试 …"));
				continue;
			}
		}
		return fMediaFile;
	}
	public static File WebWeChatGetImage (String sSessionKey, String sMsgID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
logger.info ("获取图片，MsgID: " + sMsgID);
		return WebWeChatGetMedia (sSessionKey, "webwxgetmsgimg", sMsgID);
	}
	public static File WebWeChatGetVoice (String sSessionKey, String sMsgID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
logger.info ("获取音频，msgid: " + sMsgID);
		return WebWeChatGetMedia (sSessionKey, "webwxgetvoice", sMsgID);
	}
	public static File WebWeChatGetVideo (String sSessionKey, String sMsgID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
logger.info ("获取视频, msgid: " + sMsgID);
		return WebWeChatGetMedia (sSessionKey, "webwxgetvideo", sMsgID);
	}

	public static File WebWeChatGetMedia2 (String sUserID, String sSessionID, String sSessionKey, String sPassTicket, String sAccount, String sMediaID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
logger.info ("获取媒体/获取文件，媒体 ID: " + sMediaID);
		//             https://file.wx2.qq.com/cgi-bin/mmwebwx-bin/webwxgetmedia?sender=********************&mediaid=*********&filename=*******&fromuser=2100343515&pass_ticket=********&webwx_data_ticket=*****
		String sURL = "https://file.wx2.qq.com/cgi-bin/mmwebwx-bin/webwxgetmedia?sender=" + sAccount + "&mediaid=" + sMediaID + "&skey=" + URLEncoder.encode (sSessionKey, utf8);
		String sMediaFileName = mediaFilesDirectory + "/" + sMediaID;
		File fMediaFile = null;

logger.fine ("WebWeChatGetMedia2 的 URL");
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
			is.close ();
			os.close ();
		}
logger.fine ("获取 WebWeChatGetMedia 的 http 响应消息体 (保存到文件)");
logger.fine ("	" + fMediaFile);
		return fMediaFile;
	}

	public static void ProcessBaseResponse (JsonNode node, String sAPIName)
	{
		if (node == null)
			return;
		JsonNode jsonBaseResponse = node.get ("BaseResponse");
		if (jsonBaseResponse == null)
			return;

		int nRet = GetJSONInt (jsonBaseResponse, "Ret");
		if (nRet == 0)	// 0 = 成功
			return;

		String sErrorMsg = GetJSONText (jsonBaseResponse, "ErrMsg");
logger.warning (net_maclife_util_ANSIEscapeTool.Red (sAPIName + " 失败，代码: " + nRet + " , 错误信息: " + sErrorMsg));
	}

	/**
	 * 根据加密的帐号来判断是否是聊天室帐号
	 * @param sEncryptedAccount 加密的帐号
	 * @return 如果帐号以 <code>@@</code> 开头，则返回 <code>true</code>，否则返回 <code>false</code>
	 */
	public static boolean IsRoomAccount (String sEncryptedAccount)
	{
		return StringUtils.startsWith (sEncryptedAccount, "@@");
	}

	/**
	 * 根据帐号的 VerifyFlag 来判断是否是公众号。
	 * 参考自： https://github.com/Urinx/WeixinBot/blob/master/README.md
	 * @param nVerifyFlag
	 * @return
	 */
	public static boolean IsPublicAccount (int nVerifyFlag)
	{
		return nVerifyFlag!=-1 && ((nVerifyFlag & WECHAT_ACCOUNT_TYPE_MASK__Public) == WECHAT_ACCOUNT_TYPE_MASK__Public);
	}
	public static boolean IsSubscriberAccount (int nVerifyFlag)
	{
		return nVerifyFlag!=-1 && ((nVerifyFlag & WECHAT_ACCOUNT_TYPE_MASK__Subscriber) == WECHAT_ACCOUNT_TYPE_MASK__Subscriber);
	}
	public static boolean IsWeChatTeamAccount (int nVerifyFlag)
	{
		return nVerifyFlag!=-1 && ((nVerifyFlag & WECHAT_ACCOUNT_TYPE_MASK__WeChatTeam) == WECHAT_ACCOUNT_TYPE_MASK__WeChatTeam);
	}

	/**
	 * 群聊文本消息中是否提到了某人。
	 * @param sRoomTextMessage 群文本消息
	 * @param sNickName 某人的昵称
	 * @param sDisplayName 某人的群昵称（显示名）
	 * @return
	 */
	public static boolean IsRoomTextMessageMentionedThisOne (String sRoomTextMessage, String sNickName, String sDisplayName)
	{
		boolean bMentioned = false;
		if (StringUtils.isNotEmpty (sDisplayName))
			bMentioned = StringUtils.containsIgnoreCase (sRoomTextMessage, "@" + sDisplayName + " ");
		if (! bMentioned)
			bMentioned = StringUtils.containsIgnoreCase (sRoomTextMessage, "@" + sNickName + " ");
		return bMentioned;
	}
	public static boolean IsRoomTextMessageMentionedThisOne (String sRoomTextMessage, JsonNode jsonContactInRoom)
	{
		String sNickName = GetJSONText (jsonContactInRoom, "NickName");
		String sDisplayName = GetJSONText (jsonContactInRoom, "DisplayName");
		return IsRoomTextMessageMentionedThisOne (sRoomTextMessage, sNickName, sDisplayName);
	}

	/**
	 * 群文本消息是否指名道姓的 @ 某人，即：群文本消息以 @某人 为开头。
	 * （很奇怪，微信消息包中并没有 “消息是否 @自己” 的信息）
	 * @param sRoomTextMessage 群文本消息
	 * @param sNickName 某人的昵称
	 * @param sDisplayName 某人的群昵称（显示名）
	 * @return
	 */
	public static boolean IsRoomTextMessageMentionedThisOneFirst (String sRoomTextMessage, String sNickName, String sDisplayName)
	{
		boolean bMentionedFirst = false;
		if (StringUtils.isNotEmpty (sDisplayName))
			bMentionedFirst = StringUtils.startsWithIgnoreCase (sRoomTextMessage, "@" + sDisplayName + " ");
		if (! bMentionedFirst)
			bMentionedFirst = StringUtils.startsWithIgnoreCase (sRoomTextMessage, "@" + sNickName + " ");
		return bMentionedFirst;
	}
	public static boolean IsRoomTextMessageMentionedThisOneFirst (String sRoomTextMessage, JsonNode jsonContactInRoom)
	{
		String sNickName = GetJSONText (jsonContactInRoom, "NickName");
		String sDisplayName = GetJSONText (jsonContactInRoom, "DisplayName");
		return IsRoomTextMessageMentionedThisOneFirst (sRoomTextMessage, sNickName, sDisplayName);
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
			while (true)
			{
//System.err.println ("等待控制台命令输入…");
				sTerminalInput = reader.readLine ();
//System.err.println ("收到控制台输入: [" + sTerminalInput + "]");
				if (StringUtils.isEmpty (sTerminalInput))
					continue;

//System.err.println ("分割……");
				String[] arrayParams = sTerminalInput.split (" +", 2);
//System.err.println (arrayParams.length + " 个数组元素");
				String sCommand = arrayParams[0];
				String sParam = null;
				if (arrayParams.length >=2)
					sParam = arrayParams[1];

				String sConsoleCommandPrefix = GetConfig ().getString ("app.console.command-prefix");
				if (StringUtils.isNotEmpty (sConsoleCommandPrefix))
				{
					if (! StringUtils.startsWithIgnoreCase (sCommand, sConsoleCommandPrefix))
					{
logger.warning ("控制台命令必须以 " + sConsoleCommandPrefix + " 开头");
						continue;
					}

					sCommand = StringUtils.substring (sCommand, StringUtils.length (sConsoleCommandPrefix));
				}
//System.err.println ("Command=[" + sCommand + "], Param=[" + sParam + "]");
				try
				{
					if (StringUtils.equalsIgnoreCase (sCommand, "notifyAll"))
					{
						// 本微信号现在人机已合一，具体命令请用 @xxx help 获得帮助
					}
					else if (StringUtils.equalsIgnoreCase (sCommand, "EnableEngineFor"))
					{	// 针对某个群聊或某个联系人）启用引擎
						// sParam
					}
					else if (StringUtils.equalsIgnoreCase (sCommand, "DisableEngineFor"))
					{
						//
					}
					else if (StringUtils.equalsIgnoreCase (sCommand, "EnableBotFor"))
					{	// 针对某个群聊或某个联系人）启用某个 Bot
						if (StringUtils.isEmpty (sParam))
						{
//logger.warning ();
							continue;
						}
						String[] arrayTemp = sParam.split (" +", 2);
						String sBotClassName = null;
						String sTarget = null;
						if (arrayTemp.length > 0)
							sBotClassName = arrayTemp[0];
						if (arrayTemp.length > 1)
							sTarget = arrayTemp[1];

						//config.setProperty (key, value);
					}
					else if (StringUtils.equalsIgnoreCase (sCommand, "DisableBotFor"))
					{
						//
					}
					else if (StringUtils.equalsIgnoreCase (sCommand, "LogLevel"))
					{
						if (StringUtils.isEmpty (sParam))
						{
System.out.println ("当前日志级别: " + logger.getLevel ());
							continue;
						}

						try
						{
							String sNewLogLevel = StringUtils.upperCase (sParam);
							logger.setLevel (Level.parse (sNewLogLevel));
System.out.println ("日志级别已改为: " + logger.getLevel ());
						}
						catch (IllegalArgumentException e)
						{
System.out.println ("非法日志级别: " + sParam + ", 请换有效的日志级别名称，比如 all finest finer fine info warning severe 1000 0 1 ...");
							e.printStackTrace ();
						}
					}
					else if (StringUtils.equalsIgnoreCase (sCommand, "LoadBot"))
					{
						engine.LoadBot (sParam);
					}
					else if (StringUtils.equalsIgnoreCase (sCommand, "UnLoadBot"))
					{
						engine.UnloadBot (sParam);
					}
					else if (StringUtils.equalsIgnoreCase (sCommand, "ListBots"))
					{
						engine.ListBots ();
					}
					else if (StringUtils.equalsIgnoreCase (sCommand, "login"))	// 二维码扫描自动登录，无需在这里处理。反正 Engine 线程会一直循环尝试登录
					{
					}
					else if (StringUtils.equalsIgnoreCase (sCommand, "logout"))
					{
						engine.Logout ();
					}
					else if (StringUtils.equalsIgnoreCase (sCommand, "start"))	// 二维码扫描自动登录，无需在这里处理。反正 Engine 线程会一直循环尝试登录
					{
						engine.Start ();
					}
					else if (StringUtils.equalsIgnoreCase (sCommand, "stop"))
					{
						engine.Stop ();
					}
					else if (StringUtils.equalsIgnoreCase (sCommand, "quit"))
					{	// 单纯退出程序，不注销登录（需要让 session 的缓存保持有效）
System.err.println ("收到退出命令");
						engine.Stop ();
						TimeUnit.MILLISECONDS.sleep (100);
						break;
						//System.exit (0);
					}
					else if (StringUtils.equalsIgnoreCase (sCommand, "reply"))
					{
						if (StringUtils.isEmpty (sParam))
						{
logger.warning ("必须输入回复的消息内容");
							continue;
						}
						String sMessage = StringEscapeUtils.unescapeJava (sParam);	// 目的：将 \n 转成回车符号，用单行文字书写多行文字。虽然，测试时发现，也不需要 unescape，微信接收到后会自动解转义（大概是 json 的原因吧）。为了日志好看一些，还是自己取消转义……
						engine.ReplyTextMessage (sMessage);
					}
					else if (StringUtils.equalsIgnoreCase (sCommand, "msg") || StringUtils.equalsIgnoreCase (sCommand, "send"))	// msg 命令 - 仿 IRC 频道的 msg 命令
					{
						if (StringUtils.isEmpty (sParam))
						{
logger.warning (sCommand + " <接收人帐号> <消息内容>");
							continue;
						}
						String[] arraySendMessage = sParam.split (" +", 2);
						String sToAccount = null;
						String sMessage = null;
						if (arraySendMessage.length > 0)
							sToAccount = arraySendMessage[0];
						if (arraySendMessage.length > 1)
							sMessage = arraySendMessage[1];

						if (StringUtils.isEmpty (sToAccount))
						{
logger.warning ("必须输入接收人的帐号。接收人帐号可以是加密过的形式，如： @XXXX @@XXXX 或未加密过的形式，如：wxid_XXXX filehelper gh_XXXX");
							continue;
						}
						if (StringUtils.isEmpty (sMessage))
						{
logger.warning ("必须输入消息内容");
							continue;
						}
						sMessage = StringEscapeUtils.unescapeJava (sMessage);	// 目的：将 \n 转成回车符号，用单行文字书写多行文字。虽然，测试时发现，也不需要 unescape，微信接收到后会自动解转义（大概是 json 的原因吧）。为了日志好看一些，还是自己取消转义……
						engine.SendTextMessage (sToAccount, sMessage);
					}
					else
					{
logger.warning ("未知控制台命令: " + sCommand);
					}
				}
				catch (Exception e)
				{
					e.printStackTrace ();
				}
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		//catch (InterruptedException e)
		{
			// done
		}
logger.warning ("app 线程退出");
		executor.shutdownNow ();
	}

	static void SetupDataSource ()
	{
		if (botDS != null)
			return;

		String sDriverClassName = net_maclife_wechat_http_BotApp.GetConfig ().getString ("app.jdbc.driver");	// , "com.mysql.jdbc.Driver"
		String sURL = net_maclife_wechat_http_BotApp.GetConfig ().getString ("app.jdbc.url");	// , "jdbc:mysql://localhost/WeChatBotEngine?autoReconnect=true&amp;characterEncoding=UTF-8&amp;zeroDateTimeBehavior=convertToNull"
		String sUserName = net_maclife_wechat_http_BotApp.GetConfig ().getString ("app.jdbc.username");	// , "root"
		String sPassword = net_maclife_wechat_http_BotApp.GetConfig ().getString ("app.jdbc.password");
		String sValidationQuery = net_maclife_wechat_http_BotApp.GetConfig ().getString ("app.jdbc.keep-alive-sql");
		int nValidationInterval = net_maclife_wechat_http_BotApp.GetConfig ().getInt ("app.jdbc.keep-alive-interval", 0);
		int nValidationTimeout = net_maclife_wechat_http_BotApp.GetConfig ().getInt ("app.jdbc.keep-alive-timeout", 0);
		if (StringUtils.isEmpty (sDriverClassName) || StringUtils.isEmpty (sURL) || StringUtils.isEmpty (sUserName))
		{
net_maclife_wechat_http_BotApp.logger.warning ("jdbc 需要将 driver、username、userpassword 信息配置完整");
			return;
		}
net_maclife_wechat_http_BotApp.logger.config ("app.jdbc.driver = " + sDriverClassName);
net_maclife_wechat_http_BotApp.logger.config ("app.jdbc.url = " + sURL);
net_maclife_wechat_http_BotApp.logger.config ("app.jdbc.username = " + sUserName);
net_maclife_wechat_http_BotApp.logger.config ("app.jdbc.url = " + sPassword);

		//botDS = new BasicDataSource();
		botDS = new DataSource ();

		//botDS.setDriverClassName("org.mariadb.jdbc.Driver");
		botDS.setDriverClassName (sDriverClassName);
		// 要赋给 mysql 用户对 mysql.proc SELECT 的权限，否则执行存储过程报错
		// GRANT SELECT ON mysql.proc TO bot@'192.168.2.%'
		// 参见: http://stackoverflow.com/questions/986628/cant-execute-a-mysql-stored-procedure-from-java
		botDS.setUrl (sURL);
		// 在 prepareCall 时报错:
		// User does not have access to metadata required to determine stored procedure parameter types. If rights can not be granted, configure connection with "noAccessToProcedureBodies=true" to have driver generate parameters that represent INOUT strings irregardless of actual parameter types.
		//botDS.setUrl ("jdbc:mysql://192.168.2.1/bot?autoReconnect=true&amp;characterEncoding=utf8mb4&amp;zeroDateTimeBehavior=convertToNull&amp;noAccessToProcedureBodies=true&amp;useInformationSchema=true"); // 没有作用

		// http://thenullhandler.blogspot.com/2012/06/user-does-not-have-access-error-with.html // 没有作用
		// http://bugs.mysql.com/bug.php?id=61203
		//botDS.setUrl ("jdbc:mysql://192.168.2.1/bot?autoReconnect=true&amp;characterEncoding=utf8mb4&amp;zeroDateTimeBehavior=convertToNull&amp;useInformationSchema=true");

		botDS.setUsername (sUserName);
		if (StringUtils.isNotEmpty (sPassword))
			botDS.setPassword (sPassword);

		if (StringUtils.isNotEmpty (sValidationQuery))
			botDS.setValidationQuery (sValidationQuery);
		if (nValidationInterval > 0)
			botDS.setValidationInterval (nValidationInterval);
		if (nValidationTimeout > 0)
			botDS.setValidationQueryTimeout (nValidationTimeout);

		botDS.setRemoveAbandoned (true);
		//botDS.setMaxTotal (5);
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


	public static String GetXMLValue (Element element, String sFirstChildElementName, String sDefault)
	{
		if (element==null || element.getFirstChildElement (sFirstChildElementName)==null)
			return sDefault;
		return element.getFirstChildElement (sFirstChildElementName).getValue ();
	}
	public static String GetXMLValue (Element element, String sFirstChildElementName)
	{
		return GetXMLValue (element, sFirstChildElementName, "");
	}


	public static String GetXMLAttributeValue (Element element, String sAttributeName, String sDefault)
	{
		if (element==null)
			return sDefault;
		return element.getAttributeValue (sAttributeName);
	}
	public static String GetXMLAttributeValue (Element element, String sAttributeName)
	{
		return GetXMLAttributeValue (element, sAttributeName, "");
	}





	public static boolean isQuoteChar (char ch)
	{
		return ch=='"' || ch=='\'';
	}
	public static boolean isQuoteSeparator (char ch, char previous)
	{
		return isQuoteChar(ch) && previous!='\\';
	}
	public static boolean isQuoteEnd (char ch, char previous, char quoteChar)
	{
		return ch==quoteChar && previous!='\\';
	}
	public static boolean isWhitespace(char ch)
	{
		return ch==' ' || ch=='	';
	}
	public static boolean isEscapeChar(char ch)
	{
		return ch=='\\';
	}
	public static List<String> SplitCommandLine (String cmdline)
	{
		return SplitCommandLine (cmdline, true, false);
	}
	/**
	 *
	 * @param cmdline
	 * @param unquoted 分割项是否不包含引号 true - 不把引号包含进去; false - 把引号包含进去
	 * @param unescape 是否处理转义字符 '\'， true - 处理转义字符; false - 不处理转义字符
	 * @return
	 */
	public static List<String> SplitCommandLine (String cmdline, boolean unquoted, boolean unescape)
	{
		if (StringUtils.isEmpty (cmdline))
			return null;

		boolean token_state_in_token = false;
		boolean quote_state_in_quote = false;

		char quoteChar = 0;
		char[] arrayCmdLine = cmdline.toCharArray ();
		int iTokenStart = 0, iTokenEnd = 0;
		int iQuoteStart = 0, iQuoteEnd = 0;
		StringBuilder token = new StringBuilder ();
		String subToken = null;
		List<String> listTokens = new ArrayList<String> ();
		for (int i=0; i<arrayCmdLine.length; i++)
		{
			char thisChar = arrayCmdLine[i];
			char previousChar = (i==0 ? 0 : arrayCmdLine[i-1]);
//System.out.print ("字符"+ (i+1)+ "[" + thisChar + "]:");
			if (!token_state_in_token && !quote_state_in_quote)
			{
				if (!isWhitespace(thisChar))
				{
//System.out.print ("进入token,");
					token_state_in_token = true;
					iTokenStart = i;
				}
				if (isQuoteSeparator(thisChar, previousChar))
				{
//System.out.print ("进入quote,进入子token,");
					quote_state_in_quote = true;
					iQuoteStart = i;
					quoteChar = thisChar;
				}
			}
			else if (!token_state_in_token && quote_state_in_quote)
			{
				// 不可能发生：在引号内必定在 token 内
//System.err.println ("不在 token 内，却在引号中，不可能");
			}

			else if (token_state_in_token && !quote_state_in_quote)
			{
				if (isWhitespace(thisChar))
				{
//System.out.print ("结束token,");
					token_state_in_token = !token_state_in_token;
					if (!isQuoteChar(previousChar))	// 如果前面不是引号结束的，就需要自己处理剩余的
					{
						iTokenEnd = i;
						subToken = cmdline.substring (iTokenStart, iTokenEnd);
						token.append (subToken);
					}
//System.out.print (token);
					listTokens.add (token.toString());
					token = new StringBuilder ();

				}
				if (isQuoteSeparator(thisChar, previousChar))	// aa"(此处)bb"cc
				{
//System.out.print ("结束子token,");
					iTokenEnd = i;
					subToken = cmdline.substring (iTokenStart, iTokenEnd);
					token.append (subToken);
					iTokenStart = i + 1;
//System.out.print (subToken);
//System.out.print (",开始quote,开始子token,");
					quote_state_in_quote = !quote_state_in_quote;
					iQuoteStart = i;
					quoteChar = thisChar;
				}
			}
			else if (token_state_in_token && quote_state_in_quote)
			{
				if (isQuoteEnd (thisChar, previousChar, quoteChar))
				{
//System.out.print ("结束子token 结束quote,");
					quote_state_in_quote = !quote_state_in_quote;
					iQuoteEnd = i;
					if (unquoted)	// 不把引号包含进去
						subToken = cmdline.substring (iQuoteStart+1, iQuoteEnd);
					else	// 把引号也包含进去
						subToken = cmdline.substring (iQuoteStart, iQuoteEnd+1);

//System.out.print (subToken);
					iTokenStart = i + 1;
					token.append (subToken);
				}
			}
//System.out.println ();
		}

		if (token_state_in_token)
		{	// 结束
			if (quote_state_in_quote)
			{	// 给出警告，或错误
//System.out.println ("警告：引号未关闭");
				token_state_in_token = !token_state_in_token;
				quote_state_in_quote = !quote_state_in_quote;
				iQuoteEnd = arrayCmdLine.length;
				if (unquoted)
					token.append (cmdline.substring (iQuoteStart+1, iQuoteEnd));	// 不把引号包含进去
				else
				{
					token.append (cmdline.substring (iQuoteStart, iQuoteEnd+1));	// 把引号也包含进去
					token.append (quoteChar);	// 把缺失的引号补充进去
				}
			}
			else
			{
				token_state_in_token = !token_state_in_token;
				iTokenEnd = arrayCmdLine.length;

				token.append (cmdline.substring (iTokenStart, iTokenEnd));
			}
//System.out.println ("全部结束");

			listTokens.add (token.toString());
		}
//System.out.println (listTokens);

		assert !token_state_in_token;
		assert !quote_state_in_quote;

		return listTokens;
	}

	public static void main (String[] args) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, ScriptException, ValidityException, ParsingException
	{
		net_maclife_wechat_http_BotApp app = new net_maclife_wechat_http_BotApp ();
		app.Start ();
	}
}
