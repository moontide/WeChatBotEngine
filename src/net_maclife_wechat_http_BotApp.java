import java.awt.image.*;
import java.io.*;
import java.math.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.regex.*;

import javax.imageio.*;
import javax.script.*;
import javax.sound.sampled.*;

import org.apache.commons.codec.*;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.*;
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
	public static final Charset UTF_32BE = Charset.forName ("UTF-32BE");

	public static final Random random = new SecureRandom ();

	public static final int DEFAULT_NET_TRY_TIMES = 3;

	/**
	 * 执行 Bot 命令时，Bot 命令和选项之间的分隔符。如： <code>cmd.10.stderr</code>，<code>cmd</code> 就是命令，后面的小数点就是选项分隔符，<code>10</code> 和 <code>stderr</code> 是选项
	 */
	public static final String COMMAND_OPTION_SEPARATOR = ".";

	public static final int WECHAT_ACCOUNT_TYPE_MASK__Public = 0x08;	// 公众号
	public static final int WECHAT_ACCOUNT_TYPE_MASK__Subscriber = 0x10;	// 订阅号
	public static final int WECHAT_ACCOUNT_TYPE_MASK__WeChatTeam = 0x20;	// 微信团队自己的公众号

	static final String sMultipartBoundary = "JsoupDoesNotSupportFormDataWell, and, ApacheHCDoesNotSupportSOCKSProxy";

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

	public static String sSessionCacheFileName = cacheDirectory + File.separator + "wechat-session-cache.json";
	public static String sCookiesCacheFileName = cacheDirectory + File.separator + "wechat-cookie-cache.json";

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
		jacksonObjectMapper_Loose.configure (JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);	// 允许数值前面带 0
		jacksonObjectMapper_Loose.configure (JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);	// 允许不引起来的控制字符
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
			if (eXML.getFirstChildElement ("ret") != null)
			{
				if (Long.parseLong(GetXMLValue(eXML, "ret")) != 0)
				{
// <error><ret>1203</ret><message>当前登录环境异常。为了你的帐号安全，暂时不能登录web微信。你可以通过手机客户端或者windows微信登录。</message></error>
logger.severe (net_maclife_util_ANSIEscapeTool.Red (GetXMLValue(eXML, "message")));
					return null;
				}
			}
			mapResult.put ("UserID", Long.parseLong (GetXMLValue (eXML, "wxuin")));
			mapResult.put ("SessionID", GetXMLValue (eXML, "wxsid"));
			mapResult.put ("SessionKey", GetXMLValue (eXML, "skey"));
			mapResult.put ("PassTicket", GetXMLValue (eXML, "pass_ticket"));
			mapResult.put ("LoginResultCode", nLoginResultCode);
			return mapResult;

		}
		return nLoginResultCode;
	}

	//public static JsonNode MakeBaseRequestJsonNode (String sUserID, String sSessionID, String sSessionKey, String sDeviceID)
	//{
	//	ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
	//	on.put ("Uin", sUserID);
	//	on.put ("Sid", sSessionID);
	//	on.put ("Skey", sSessionKey);
	//	on.put ("DeviceID", sDeviceID);
	//	return on;
	//}

	public static JsonNode MakeBaseRequestJsonNode (long nUserID, String sSessionID, String sSessionKey, String sDeviceID)
	{
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.put ("Uin", nUserID);
		on.put ("Sid", sSessionID);
		on.put ("Skey", sSessionKey);
		on.put ("DeviceID", sDeviceID);
		return on;
	}

	//public static JsonNode MakeFullBaseRequestJsonNode (String sUserID, String sSessionID, String sSessionKey, String sDeviceID)
	//{
	//	ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
	//	on.set ("BaseRequest", MakeBaseRequestJsonNode (sUserID, sSessionID, sSessionKey, sDeviceID));
	//	return on;
	//}

	public static JsonNode MakeFullBaseRequestJsonNode (long nUserID, String sSessionID, String sSessionKey, String sDeviceID)
	{
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (nUserID, sSessionID, sSessionKey, sDeviceID));
		return on;
	}


	public static String MakeDeviceID ()
	{
		long nRand = random.nextLong () & 0x7FFFFFFFFFFFFFFFL;
		return "e" + String.format ("%015d", nRand).substring (0, 15);	// System.currentTimeMillis ();
	}

	public static void AppendContactInformation (StringBuilder sb, JsonNode jsonContact, boolean bIsRoomMember)
	{
		String sNickName = GetJSONText (jsonContact, "NickName");
		if (ParseBoolean (GetConfig ().getString ("engine.message.name.restore-emoji-character"), false))
			sNickName = RestoreEmojiCharacters (sNickName);
		sb.append (sNickName);
		String sRemarkNameOrDisplayName = GetJSONText (jsonContact, bIsRoomMember ? "DisplayName" : "RemarkName");
		if (ParseBoolean (GetConfig ().getString ("engine.message.name.restore-emoji-character"), false))
			sRemarkNameOrDisplayName = RestoreEmojiCharacters (sRemarkNameOrDisplayName);

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

	public static JsonNode WebWeChatInit (long nUserID, String sSessionID, String sSessionKey, String sPassTicket) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("初始化 …");
		// https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxinit?r=1703974212&lang=zh_CN&pass_ticket=ZfvpI6wcO7N5PTkacmWK9zUTXpUOB3kqre%2BrkQ8IAtHDAIP2mc2psB5eDH8cwzsp
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxinit?r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + URLEncoder.encode (sPassTicket, utf8);
logger.fine ("WebWeChatInit 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
logger.finer ("发送 WebWeChatInit 的 http 请求消息头 (Content-Type):");
logger.finer ("	" + mapRequestHeaders);

		String sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullBaseRequestJsonNode (nUserID, sSessionID, sSessionKey, MakeDeviceID ()));
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

	public static JsonNode MakeFullStatisticsReportRequestJsonNode (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sMyAccount, JsonNode jsonData)
	{
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (nUserID, sSessionID, sSessionKey, sDeviceID));
		if (jsonData==null || !(jsonData instanceof ArrayNode))
		{
			on.put ("Count", 0);
			on.set ("List", jacksonObjectMapper_Strict.createArrayNode ());
		}
		else
		{
			ArrayNode arraynodeData = (ArrayNode) jsonData;
			on.put ("Count", arraynodeData.size ());
			on.set ("List", arraynodeData);
		}
		return on;
	}
	/**
	 *
	 * @param jsonData 可以为 null
	 * @return
	 */
	public static JsonNode WebWeChatStatisticsReport (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sMyAccount, JsonNode jsonData) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("发送统计报告到腾讯 = = …");
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxstatreport?fun=new";
logger.fine ("WebWeChatStatisticsReport 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
logger.finer ("发送 WebWeChatStatisticsReport 的 http 请求消息头 (Content-Type):");
logger.finer ("	" + mapRequestHeaders);

		String sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullStatisticsReportRequestJsonNode (nUserID, sSessionID, sSessionKey, MakeDeviceID (), sMyAccount, jsonData));
logger.finer ("发送 WebWeChatStatisticsReport 的 http 请求消息体:");
logger.finer (sRequestBody_JSONString);
		InputStream is = null;
		JsonNode node = null;
		int nTryTimes = GetConfig().getInt ("app.net.try-times", DEFAULT_NET_TRY_TIMES);
		for (int i=0; i<nTryTimes; i++)
		{
			try
			{
				String sContent = net_maclife_util_HTTPUtils.CURL_Post (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
				//is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
				//node = jacksonObjectMapper_Loose.readTree (is);
				//is.close ();
logger.fine ("获取 WebWeChatStatisticsReport 的 http 响应消息体:");	// 空内容
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

		//ProcessBaseResponse (node, "WebWeChatStatisticsReport");	// 空内容

		return node;
	}
	public static JsonNode WebWeChatStatisticsReport (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sMyAccount) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		return WebWeChatStatisticsReport (nUserID, sSessionID, sSessionKey, sPassTicket, sMyAccount, null);
	}

	public static JsonNode MakeFullStatusNotifyRequestJsonNode (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sMyAccount)
	{
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (nUserID, sSessionID, sSessionKey, sDeviceID));
		on.put ("Code", 3);
		on.put ("FromUserName", sMyAccount);
		on.put ("ToUserName", sMyAccount);
		on.put ("ClientMsgId", GenerateLocalMessageID ());
		return on;
	}
	public static JsonNode WebWeChatStatusNotify (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sMyAccount) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("开启状态通知 …");
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxstatusnotify?lang=zh_CN&pass_ticket="+ URLEncoder.encode (sPassTicket, utf8);
logger.fine ("WebWeChatStatusNotify 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
logger.finer ("发送 WebWeChatStatusNotify 的 http 请求消息头 (Content-Type):");
logger.finer ("	" + mapRequestHeaders);

		String sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullStatusNotifyRequestJsonNode (nUserID, sSessionID, sSessionKey, MakeDeviceID (), sMyAccount));
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

	public static JsonNode WebWeChatGetContacts (long nUserID, String sSessionID, String sSessionKey, String sPassTicket) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("获取联系人 …");
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxgetcontact?r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + URLEncoder.encode (sPassTicket, utf8);
logger.fine ("WebWeChatGetContacts 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
logger.finer  ("发送 WebWeChatGetContacts 的 http 请求消息头 (Content-Type):");
logger.finer  ("	" + mapRequestHeaders);

String sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullBaseRequestJsonNode (nUserID, sSessionID, sSessionKey, MakeDeviceID ()));
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
	public static JsonNode MakeFullGetRoomContactRequestJsonNode (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, List<String> listRoomAccounts)
	{
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (nUserID, sSessionID, sSessionKey, sDeviceID));
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

	public static JsonNode WebWeChatGetRoomsContacts (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, List<String> listRoomAccounts) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("获取 " + listRoomAccounts.size () + " 个聊天室的联系人 …");
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxbatchgetcontact?type=ex&r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + URLEncoder.encode (sPassTicket, utf8);
logger.fine ("WebWeChatGetRoomContacts 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
		String sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullGetRoomContactRequestJsonNode (nUserID, sSessionID, sSessionKey, MakeDeviceID (), listRoomAccounts));
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

		DumpGroupsContacts (node);
		//
		return node;
	}

	public static void DumpGroupsContacts (JsonNode node)
	{
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
			if (ParseBoolean (GetConfig ().getString ("engine.message.name.restore-emoji-character"), false))
				sb.append (RestoreEmojiCharacters (GetJSONText (jsonContact, "NickName")));
			else
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
	}

	/**
	 * 从 WebWeChatGetContacts 返回的 JsonNode 中的 MemberList 中找出符合条件的联系人。
	 * 只要指定了 sEncryptedAccountInASession，就会搜到唯一一个联系人（除非给出的 ID 不正确），
	 * 如果没指定 sEncryptedAccountInASession (null 或空字符串)，则尽可能全部指定 sAlias、sRemarkName、sNickName，以便更精确的匹配（即：匹配到的人数量尽可能只有 1 个）联系人。
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
		if (jsonMemberList == null)
			return null;
		List<JsonNode> listContactsMatched = new ArrayList <JsonNode> ();
		List<Integer> listNonUniqueContactsWeights = new ArrayList <Integer> ();

		if (jsonMemberList.size () > 0xFFFFFF)
		{
logger.severe ("联系人数量超过 0xFFFFFF" + 0xFFFFFF + " 个，这样可能会导致搜索联系人出问题 -- 无法获取到正确的联系人、排序权重也不正确。");
		}

		for (int i=0; i<jsonMemberList.size (); i++)
		{
			JsonNode node = jsonMemberList.get (i);

			int nMatchWeight = 0;

			if (StringUtils.isNotEmpty (sEncryptedAccountInASession))
			{
				String sTemp = GetJSONText (node, "UserName");
				if (StringUtils.equalsIgnoreCase (sEncryptedAccountInASession, sTemp))
				{
					nMatchWeight |= 0x10;
					listContactsMatched.add (node);
					break;	// 加密帐号是唯一的，找到了就不再继续找了
				}
			}

			else if (StringUtils.isNotEmpty (sAliasAccount))
			{
				String sTemp = GetJSONText (node, "Alias");
				if (StringUtils.equalsIgnoreCase (sAliasAccount, sTemp))
				{
					nMatchWeight |= 0x08;
					listContactsMatched.add (node);
					break;	// 微信号也应该是唯一的，找到了就不再继续找了
				}
			}

			if (StringUtils.isNotEmpty (sRemarkName))
			{
				String sTemp = GetJSONText (node, "RemarkName");
				if (StringUtils.equalsIgnoreCase (sRemarkName, sTemp))
				{
					nMatchWeight |= 0x04;
				}
			}

			if (StringUtils.isNotEmpty (sDisplayName))
			{
				String sTemp = GetJSONText (node, "DisplayName");
				if (StringUtils.equalsIgnoreCase (sDisplayName, sTemp))
				{
					nMatchWeight |= 0x02;
				}
			}

			if (StringUtils.isNotEmpty (sNickName))
			{
				String sTemp = GetJSONText (node, "NickName");
				if (StringUtils.equalsIgnoreCase (sNickName, sTemp))
				{
					nMatchWeight |= 0x01;
				}
			}
			if (nMatchWeight > 0)
			{
				listContactsMatched.add (node);

				// 利用 int 的 3 个低字节存储索引号、高字节存储【匹配/排序】的权重，所以，要确保：
				//   联系人数量不超过 3 字节代表的数值：16777215
				// 权重在高字节可以按整数类型数值自然排序
				listNonUniqueContactsWeights.add ((nMatchWeight << 24) | (listContactsMatched.size ()-1));
			}

		}

		// 如果匹配到多个，则再根据匹配的权重排序 备注名、群昵称、昵称
		if (listContactsMatched.size () > 1)
		{
			Collections.sort (listNonUniqueContactsWeights, Collections.reverseOrder ());
			List<JsonNode> listNonUniqueContactsMatched_Sorted = new ArrayList <JsonNode> ();
			for (int n : listNonUniqueContactsWeights)
			{
				listNonUniqueContactsMatched_Sorted.add (listContactsMatched.get (n & 0xFFFFFF));
			}
			listContactsMatched = listNonUniqueContactsMatched_Sorted;
		}
		return listContactsMatched;
	}


	/**
	 * 搜索并返回符合条件的第一个联系人，如果没有搜到符合条件的联系人，则返回 <code>null</code>。
	 * @param jsonMemberList
	 * @param sEncryptedAccountInThisSession
	 * @param sAlias
	 * @param sRemarkName
	 * @param sDisplayName
	 * @param sNickName
	 * @return 返回符合条件的第一个联系人，如果没有搜到符合条件的联系人，则返回 <code>null</code>
	 */
	public static JsonNode SearchForSingleContact (JsonNode jsonMemberList, String sEncryptedAccountInThisSession, String sAlias, String sRemarkName, String sDisplayName, String sNickName)
	{
		List<JsonNode> listUsers = SearchForContacts (jsonMemberList, sEncryptedAccountInThisSession, sAlias, sRemarkName, sDisplayName, sNickName);
		return (listUsers==null || listUsers.size ()==0) ? null : listUsers.get (0);
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
	public static JsonNode MakeFullWeChatSyncRequestJsonNode (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, JsonNode jsonSyncKey)
	{
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (nUserID, sSessionID, sSessionKey, sDeviceID));
		on.set ("SyncKey", jsonSyncKey);
		on.put ("rr", System.currentTimeMillis ()/1000);
		return on;
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
	public static JsonNode WebWeChatGetMessagePackage (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, JsonNode jsonSyncCheckKeys) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, ScriptException, URISyntaxException, InterruptedException
	{
logger.finest ("等待并获取新消息 WebWeChatGetMessagePackage (synccheck & webwxsync) …");	// 这里的日志级别改为了 fine，因这个在死循环中，产生太多日志
		String sSyncCheckKeys = MakeSyncCheckKeysQueryString (jsonSyncCheckKeys);
		String sSyncCheckURL = "https://webpush.wx2.qq.com/cgi-bin/mmwebwx-bin/synccheck?r=" + System.currentTimeMillis () + "&skey=" + URLEncoder.encode (sSessionKey, utf8) + "&sid=" + URLEncoder.encode (sSessionID, utf8) + "&uin=" + nUserID + "&deviceid=" + MakeDeviceID () + "&synckey=" +  sSyncCheckKeys + "&_=" + System.currentTimeMillis ();
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

		JsonNode jsonResult = null;
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
				TimeUnit.SECONDS.sleep (5);
				continue;
			}
			catch (IllegalStateException e)
			{
				if (StringUtils.containsIgnoreCase (e.toString (), "HTTP/1.1 0"))
				{
logger.info ("对方正在输入…");
					// 对方正在输入：
					return jsonResult;
				}
			}
		}
logger.finest ("获取 WebWeChatGetMessagePackage 中 synccheck 的 http 响应消息体:");
logger.finest ("	" + sContent);

		String sJSCode = StringUtils.replace (sContent, "window.", "var ");
		String sSyncCheckReturnCode = public_jse.eval (sJSCode + "; synccheck.retcode;").toString ();
		String sSyncCheckSelector = public_jse.eval (sJSCode + "; synccheck.selector;").toString ();

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
					String sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullWeChatSyncRequestJsonNode (nUserID, sSessionID, sSessionKey, MakeDeviceID (), jsonSyncCheckKeys));
logger.finest ("发送 WebWeChatGetMessagePackage 中 webwxsync 的 http 请求消息头 (Cookie & Content-Type):");
logger.finest ("	" + mapRequestHeaders);
logger.finest ("发送 WebWeChatGetMessagePackage 中 webwxsync 的 http 请求消息体:");
logger.finest ("	\n" + sRequestBody_JSONString);
					InputStream is = null;
					JsonNode node = null;
					URLConnection http = null;
					for (int i=0; i<nTryTimes; i++)
					{
						try
						{
							//is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sSyncURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
							//node = jacksonObjectMapper_Loose.readTree (is);
							//jsonResult = node;
							http = net_maclife_util_HTTPUtils.CURL_Post_Connection (sSyncURL, mapRequestHeaders, sRequestBody_JSONString.getBytes ());
							int iResponseCode = ((HttpURLConnection)http).getResponseCode();
							int iMainResponseCode = iResponseCode/100;
							if (iMainResponseCode==2)
							{
logger.info ("\n--------------------------------------------------");
								Map<String, List<String>> mapHeaders = http.getHeaderFields ();
								cookieManager.put (new URI(sSyncURL), mapHeaders);
								for (String sHeaderName : mapHeaders.keySet ())
								{
									if (StringUtils.equalsIgnoreCase (sHeaderName, "Set-Cookie"))
									{
logger.finer ("获取 WebWeChatGetMessagePackage 中 webwxsync 设置的新 Cookie （保持会话不过期就指望它了）:");
										List<String> listCookieStrings = mapHeaders.get (sHeaderName);
logger.finer ("	" + listCookieStrings);
									}
								}

logger.finer ("获取 WebWeChatGetMessagePackage 中 webwxsync 的 http 响应消息体:");
								is = http.getInputStream ();
								node = jacksonObjectMapper_Loose.readTree (is);
logger.finer ("\n" + node);
								jsonResult = node;
								is.close ();
								break;
							}
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
				//case "6":	// 这个是啥？昨天晚上遇到过了，貌似是别人请求添加联系人时遇到的，然后就一直返回 6，死循环出不来了
//logger.fine ("WebWeChatGetMessagePackage 中 synccheck 返回 selector 6 -- 别人请求添加联系人？");
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

	public static void WebWeChatLogout (long nUserID, String sSessionID, String sSessionKey, String sPassTicket) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
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

		String sRequestBody = "wxsid=" + URLEncoder.encode (sSessionID, utf8) + "&uin=" + nUserID;
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
	public static JsonNode WebWeChatSendMessage (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_Account, String sTo_Account, int nMessageType, Object oMessage) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.fine ("发消息 WebWeChatSendMessage …");
		String sURL = null;

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
		String sRequestBody_JSONString = "";
		switch (nMessageType)
		{
			case net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__TEXT:
				sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendmsg?r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + URLEncoder.encode (sPassTicket, utf8);
				sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullSendTextMessageRequestJsonNode (nUserID, sSessionID, sSessionKey, MakeDeviceID (), sFrom_Account, sTo_Account, (String)oMessage));
				break;
			case net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__IMAGE:
				sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendmsgimg?fun=async&f=json&lang=zh_CN&pass_ticket="+ URLEncoder.encode (sPassTicket, utf8);
				sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullSendImageMessageRequestJsonNode (nUserID, sSessionID, sSessionKey, MakeDeviceID (), sFrom_Account, sTo_Account, (String)oMessage));
				break;
			case net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__VOICE:
				sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendvoicemsg?fun=async&f=json&lang=zh_CN&pass_ticket="+ URLEncoder.encode (sPassTicket, utf8);
				sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullSendVoiceMessageRequestJsonNode (nUserID, sSessionID, sSessionKey, MakeDeviceID (), sFrom_Account, sTo_Account, (String)oMessage));
				break;
			case net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__VIDEO_MSG:
				sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendvideomsg?fun=async&f=json&lang=zh_CN&pass_ticket="+ URLEncoder.encode (sPassTicket, utf8);
				sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullSendVideoMessageRequestJsonNode (nUserID, sSessionID, sSessionKey, MakeDeviceID (), sFrom_Account, sTo_Account, (String)oMessage));
				break;
			case net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__APP:
				sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendappmsg?fun=async&f=json&lang=zh_CN&pass_ticket="+ URLEncoder.encode (sPassTicket, utf8);
				sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullSendApplicationMessageRequestJsonNode (nUserID, sSessionID, sSessionKey, MakeDeviceID (), sFrom_Account, sTo_Account, (Element)oMessage));
				break;
			case net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__EMOTION:
				sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxsendemoticon?fun=sys&f=json&lang=zh_CN&pass_ticket="+ URLEncoder.encode (sPassTicket, utf8);
				sRequestBody_JSONString = jacksonObjectMapper_Strict.writeValueAsString (MakeFullSendEmotionMessageRequestJsonNode (nUserID, sSessionID, sSessionKey, MakeDeviceID (), sFrom_Account, sTo_Account, (String)oMessage));
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

	public static JsonNode MakeFullSendTextMessageRequestJsonNode (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sContent)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (nUserID, sSessionID, sSessionKey, sDeviceID));
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
	public static JsonNode WebWeChatSendTextMessage (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_Account, String sTo_Account, String sMessage) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("发文本消息:\n" + net_maclife_util_ANSIEscapeTool.Cyan (sMessage));
		return WebWeChatSendMessage (nUserID, sSessionID, sSessionKey, sPassTicket, sFrom_Account, sTo_Account, net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__TEXT, sMessage);
	}

	public static JsonNode MakeFullSendImageMessageRequestJsonNode (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sMediaID)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (nUserID, sSessionID, sSessionKey, sDeviceID));
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
	public static JsonNode WebWeChatSendImageMessage (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_Account, String sTo_Account, String sMediaID) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("发图片消息: " + sMediaID);
		return WebWeChatSendMessage (nUserID, sSessionID, sSessionKey, sPassTicket, sFrom_Account, sTo_Account, net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__IMAGE, sMediaID);
	}

	public static JsonNode MakeFullSendEmotionMessageRequestJsonNode (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sMediaID)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (nUserID, sSessionID, sSessionKey, sDeviceID));
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
	public static JsonNode WebWeChatSendEmotionMessage (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_Account, String sTo_Account, String sMediaID) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("发表情图消息: " + sMediaID);
		return WebWeChatSendMessage (nUserID, sSessionID, sSessionKey, sPassTicket, sFrom_Account, sTo_Account, net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__EMOTION, sMediaID);
	}

	public static Element MakeFullSendVoiceMessageRequestElement (String sMediaID, File f) throws UnsupportedAudioFileException, IOException
	{
		// 	<msg><voicemsg endflag="1" cancelflag="0" forwardflag="0" voiceformat="4" voicelength="3646" length="5552" bufid="434305989364023680" clientmsgid="41623332313162653763633237366500381057072517d00d3382b0e100" fromusername="wxid_ghgw5py0s3g922" /></msg>
		AudioInputStream audioInputStream = AudioSystem.getAudioInputStream (f);
		AudioFormat format = audioInputStream.getFormat ();
		long nFrames = audioInputStream.getFrameLength ();
		float fFrameRate = format.getFrameRate();
		long nDurationInMilliSeconds = (long)(nFrames * 1000 / fFrameRate);

		Element eVoiceMsg = new Element ("voicemsg");
		eVoiceMsg.addAttribute (new Attribute ("endflag", "1"));
		eVoiceMsg.addAttribute (new Attribute ("cancelflag", "0"));
		eVoiceMsg.addAttribute (new Attribute ("forwardflag", "0"));
		eVoiceMsg.addAttribute (new Attribute ("voiceformat", "4"));
		eVoiceMsg.addAttribute (new Attribute ("voicelength", String.valueOf (nDurationInMilliSeconds)));
		eVoiceMsg.addAttribute (new Attribute ("length", String.valueOf (f.length ())));
		//eVoiceMsg.addAttribute (new Attribute ("bufid", ？？？));
		//eVoiceMsg.addAttribute (new Attribute ("clientmsgid", ？？？));
		//eVoiceMsg.addAttribute (new Attribute ("fromusername", ？？？));	// wxid_ghgw5py0s3g922

		return eVoiceMsg;
	}
	public static JsonNode MakeFullSendVoiceMessageRequestJsonNode (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sMediaID)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (nUserID, sSessionID, sSessionKey, sDeviceID));
			ObjectNode msg = jacksonObjectMapper_Strict.createObjectNode ();
			msg.put ("Type", net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__VOICE);
			msg.put ("MediaId", sMediaID);
			msg.put ("FromUserName", sFrom);
			msg.put ("ToUserName", sTo);
			msg.put ("LocalID", nLocalMessageID);
			msg.put ("ClientMsgId", nLocalMessageID);
		on.set ("Msg", msg);
		on.put ("Scene", 0);
		return on;
	}
	public static JsonNode WebWeChatSendVoiceMessage (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_Account, String sTo_Account, String sMediaID) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("发语音消息: " + sMediaID);
		return WebWeChatSendMessage (nUserID, sSessionID, sSessionKey, sPassTicket, sFrom_Account, sTo_Account, net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__VOICE, sMediaID);
	}

	public static JsonNode MakeFullSendVideoMessageRequestJsonNode (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, String sMediaID)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (nUserID, sSessionID, sSessionKey, sDeviceID));
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
	public static JsonNode WebWeChatSendVideoMessage (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_Account, String sTo_Account, String sMediaID) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("发视频消息: " + sMediaID);
		return WebWeChatSendMessage (nUserID, sSessionID, sSessionKey, sPassTicket, sFrom_Account, sTo_Account, net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__VIDEO_MSG, sMediaID);
	}

	public static Element MakeFullSendApplicationMessageRequestElement (String sMediaID, File f)
	{
		// 	"Content":"<appmsg appid='wxeb7ec651dd0aefa9' sdkver=''><title>乘法表.html</title><des></des><action></action><type>6</type><content></content><url></url><lowurl></lowurl><appattach><totallen>2450</totallen><attachid>@crypt_31cbb6a9_6b3d4a70ef9dfb160caaa0248f4b35555af79d7cde554bc49cfae602ba2e405bfc7c1b3b2ba8374747f769ef74d90512d4c2e8bcdf41628ebfa9fab04f9a1758018c4efe47195287bf738797da768e7c</attachid><fileext>html</fileext></appattach><extinfo></extinfo></appmsg>",

		Element eAppMsg = new Element ("appmsg");
		eAppMsg.addAttribute (new Attribute ("appid", "wxeb7ec651dd0aefa9"));
		eAppMsg.addAttribute (new Attribute ("sdkver", ""));
			Element eTitle = new Element ("title");
				eTitle.appendChild (f.getName ());
			Element eDescription = new Element ("des");
			Element eAction = new Element ("action");
			Element eType = new Element ("type");
				eType.appendChild (String.valueOf (net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__APP));
			Element eContent = new Element ("content");
			Element eURL = new Element ("url");
			Element eLowURL = new Element ("lowurl");
			Element eAppAttach = new Element ("appattach");
				Element eFileSize = new Element ("totallen");
				eFileSize.appendChild ("" + f.length ());
				Element eMediaID = new Element ("attachid");
				eMediaID.appendChild (sMediaID);
				Element eFileExtensionName = new Element ("fileext");
				eFileExtensionName.appendChild (FilenameUtils.getExtension (f.getName ()));
				eAppAttach.appendChild (eFileSize);
				eAppAttach.appendChild (eMediaID);
				eAppAttach.appendChild (eFileExtensionName);
			Element eExtInfo = new Element ("extinfo");

		eAppMsg.appendChild (eTitle);
		eAppMsg.appendChild (eDescription);
		eAppMsg.appendChild (eAction);
		eAppMsg.appendChild (eType);
		eAppMsg.appendChild (eContent);
		eAppMsg.appendChild (eURL);
		eAppMsg.appendChild (eLowURL);
		eAppMsg.appendChild (eAppAttach);
		eAppMsg.appendChild (eExtInfo);

		return eAppMsg;
	}
	public static JsonNode MakeFullSendApplicationMessageRequestJsonNode (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom, String sTo, Element eXML)
	{
		long nLocalMessageID = GenerateLocalMessageID ();
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (nUserID, sSessionID, sSessionKey, sDeviceID));
			ObjectNode msg = jacksonObjectMapper_Strict.createObjectNode ();
			msg.put ("Type", net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__APP);
			msg.put ("Content", eXML.toXML ());
			msg.put ("FromUserName", sFrom);
			msg.put ("ToUserName", sTo);
			msg.put ("LocalID", nLocalMessageID);
			msg.put ("ClientMsgId", nLocalMessageID);
		on.set ("Msg", msg);
		on.put ("Scene", 0);
		return on;
	}
	public static JsonNode WebWeChatSendApplicationMessage (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_Account, String sTo_Account, Element eXML) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
logger.info ("发应用程序 (如：上传文件) 消息，XML: " + eXML.toXML ());
		return WebWeChatSendMessage (nUserID, sSessionID, sSessionKey, sPassTicket, sFrom_Account, sTo_Account, net_maclife_wechat_http_BotEngine.WECHAT_MSG_TYPE__APP, eXML);
	}

	public static JsonNode MakeFullUploadMediaRequestJsonNode (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sFrom_Account, String sTo_Account, File f)
	{
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.put ("UploadType", 2);
		on.set ("BaseRequest", MakeBaseRequestJsonNode(nUserID, sSessionID, sSessionKey, sDeviceID));
		on.put ("ClientMediaId", GenerateLocalMessageID ());
		on.put ("TotalLen", f.length ());
		on.put ("StartPos", 0);
		on.put ("DataLen", f.length ());
		on.put ("MediaType", 4);
		on.put ("FromUserName", sFrom_Account);
		on.put ("ToUserName", sTo_Account);
		try
		{
			FileInputStream fis = new FileInputStream(f);
			String sFileMD5Sum = DigestUtils.md5Hex (fis);
			fis.close ();
			on.put ("FileMd5", sFileMD5Sum);
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
		return on;
	}

	public static String GetCookieValue (List<HttpCookie> listCookies, String sCookieName)
	{
		for (HttpCookie cookie : listCookies)
		{
			if (StringUtils.equals (cookie.getName (), sCookieName))
				return cookie.getValue ();
		}
		return null;
	}

	public static JsonNode WebWeChatUploadMedia (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sFrom_Account, String sTo_Account, File f) throws IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, URISyntaxException
	{
		if (f!=null && !f.exists ())
			return null;
logger.info ("上传媒体/上传文件: " + f);
		String sURL = "https://file.wx2.qq.com/cgi-bin/mmwebwx-bin/webwxuploadmedia?f=json";

		// OPTIONS sURL :
		// Response (JSON): BaseResponse: xxx , MediaId: "", StartPos: 0, CDNThumbImgHeight: 0, CDNThumbImgWidth: 0

		// POST sURL :
		// Content-Type: "multipart/form-data; boundary=---------------------------18419982551043833290966102030"
		// 消息体： 包含
		//

		// 自己构造 multipart/form-data 消息体
		//*
		OutputStream os = null;
		byte[] arrayPostData = null;
		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		//mapRequestHeaders.put ("Content-Type", "");	// Java HttpURLConnection 你妈的能不能彻底删除 Content-Type 消息头啊
		//mapRequestHeaders.put ("Content-Length", "");	// Java HttpURLConnection 你妈的能不能彻底删除 Content-Length 消息头啊
		mapRequestHeaders.put ("User-Agent", "Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:50.0) Gecko/20100101 Firefox/1234 Firefox is versioning emperor #2, Chrome is versioning emperor #1!!!");	// 经过多次测试，User-Agent 和/或 Accept-Language 头是必须要的，否则返回不了正确响应
		mapRequestHeaders.put ("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3");
		// POST 方法访问
		ByteArrayOutputStream baos = new ByteArrayOutputStream ();
		//FillMultipartSimplely (baos, sMultipartBoundary, "image_url", "");

		String sImageContentType = Files.probeContentType (f.toPath ());
		String sMediaTypeForWeChat = StringUtils.startsWithIgnoreCase (sImageContentType, "video/") ? "video" : (StringUtils.startsWithIgnoreCase (sImageContentType, "image/") ? "pic" : "doc");
		net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "id", "WU_FILE_1");	// 每次只上传一个，所以，固定 id 即可
		net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "name", f.getName ());
		net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "type", sImageContentType);
		net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "lastModifiedDate", new Date (f.lastModified ()).toGMTString ());
		net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "size", String.valueOf (f.length ()));
		//net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "chunks", "1");	// 一次性发送完，不分段处理（web 端分段可能只是为了显示上传进度？）
		//net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "chunk", "0");	// chunk 是索引（从 0 开始），不是序号
		net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "mediatype", sMediaTypeForWeChat);

		// {"UploadType":2,"BaseRequest":{"Uin":2100343515,"Sid":"4jLhehGMAlOrWmf3","Skey":"@crypt_1df7c02d_37e815cc9f64301caac3bb34bfa582a5","DeviceID":"e946852220005919"},"ClientMediaId":1483416567527,"TotalLen":2703813,"StartPos":0,"DataLen":2703813,"MediaType":4,"FromUserName":"@310be1aaf2953c82ea2a4482fd6be8c1ce3d123d6e37a3382cbfd5e629e01d84","ToUserName":"@5c9e12b6d0f2e6eafa6f9d061df8e53cf6922ed853b55642ba9b0989c6ecccdb","FileMd5":"8adb013061367e26b17730d06b77b3fb"}
		JsonNode onUploadMediaRequest = MakeFullUploadMediaRequestJsonNode (nUserID, sSessionID, sSessionKey, MakeDeviceID(), sFrom_Account, sTo_Account, f);
		net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "uploadmediarequest", jacksonObjectMapper_Strict.writeValueAsString (onUploadMediaRequest));
		CookieStore cookieStore = cookieManager.getCookieStore ();
		List<HttpCookie> listCookies = cookieStore.get (new URI(sURL));
		net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "webwx_data_ticket", GetCookieValue (listCookies, "webwx_data_ticket"));
		net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "pass_ticket", sPassTicket);
		net_maclife_util_HTTPUtils.FillMultipartSimplely (baos, sMultipartBoundary, "filename", f, "application/octet-stream");
		net_maclife_util_HTTPUtils.FillMultipartSimplelyEnd (baos, sMultipartBoundary);
		baos.flush ();

		ByteArrayOutputStream baos_multipart = null;
		mapRequestHeaders.put ("Content-Type", "multipart/form-data; boundary=" + sMultipartBoundary);
		mapRequestHeaders.put ("Content-Length", String.valueOf (baos.size ()));
		baos_multipart = baos;

		arrayPostData = baos_multipart.toByteArray ();

		URLConnection http = null;
		//sResponseBody = net_maclife_util_HTTPUtils.CURL_Post (sURL, mapRequestHeaders, arrayPostData);
		http = (URLConnection) net_maclife_util_HTTPUtils.CURL
				("POST", sURL, mapRequestHeaders, arrayPostData, true, true, null, false /* 不跟随重定向 */, 0, 0,
					null, null, 0,
					true, true, null, null, null, null, null, null
				);

		if (http != null)
		{
			int iResponseCode = ((HttpURLConnection)http).getResponseCode();
			String sStatusLine = http.getHeaderField (0);	// HTTP/1.1 200 OK、HTTP/1.1 404 Not Found

			int iMainResponseCode = iResponseCode/100;
			if (iMainResponseCode == 2)
			{
				InputStream is = http.getInputStream ();
				JsonNode node = jacksonObjectMapper_Loose.readTree (is);
				is.close ();
System.out.println (jacksonObjectMapper_Loose.writerWithDefaultPrettyPrinter ().writeValueAsString (node));
				ProcessBaseResponse (node, "WebWeChatUploadMedia");

				// 把 MediaType 附加上
				((ObjectNode)node).put ("MediaType", sMediaTypeForWeChat);
				return node;
			}
		}

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

	public static JsonNode MakeFullJsonNode_SendOrAcceptRequestToMakeFriend (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, boolean bRequestOrResponse, String sMakeFriendRequestTicketFromPeer, int nScene, String sTo_Account, String sContent)
	{
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (nUserID, sSessionID, sSessionKey, sDeviceID));
		on.put ("Opcode", bRequestOrResponse ? 2 : 3);
		on.put ("VerifyUserListSize", 1);	// 暂时，固定每次只处理一个
		ArrayNode anUserList = jacksonObjectMapper_Strict.createArrayNode ();
			ObjectNode onUser = jacksonObjectMapper_Strict.createObjectNode ();
			onUser.put ("Value", sTo_Account);
			onUser.put ("VerifyUserTicket", bRequestOrResponse ? "" : sMakeFriendRequestTicketFromPeer);
			anUserList.add (onUser);
		on.set ("VerifyUserList", anUserList);
		on.put ("VerifyContent", sContent);
		on.put ("SceneListCount", 1);
		ArrayNode anSceneList = jacksonObjectMapper_Strict.createArrayNode ();
			anSceneList.add (nScene);
		on.set ("SceneList", anSceneList);
		on.put ("skey", sSessionKey);
		return on;
	}

	public static JsonNode MakeFullJsonNode_SendRequestToMakeFriend (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, int nScene, String sTo_Account, String sContent)
	{
		return MakeFullJsonNode_SendOrAcceptRequestToMakeFriend (nUserID, sSessionID, sSessionKey, sDeviceID, true, null, nScene, sTo_Account, sContent);
	}
	public static JsonNode MakeFullJsonNode_SendRequestToMakeFriend (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sTo_Account, String sContent)
	{
		return MakeFullJsonNode_SendOrAcceptRequestToMakeFriend (nUserID, sSessionID, sSessionKey, sDeviceID, true, null, net_maclife_wechat_http_BotEngine.WECHAT_SCENE_RoomMemberList2, sTo_Account, sContent);
	}

	public static JsonNode MakeFullJsonNode_AcceptRequestToMakeFriend (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sMakeFriendRequestTicketFromPeer, int nScene, String sTo_Account, String sContent)
	{
		return MakeFullJsonNode_SendOrAcceptRequestToMakeFriend (nUserID, sSessionID, sSessionKey, sDeviceID, false, sMakeFriendRequestTicketFromPeer, nScene, sTo_Account, sContent);
	}
	public static JsonNode MakeFullJsonNode_AcceptRequestToMakeFriend (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sMakeFriendRequestTicketFromPeer, String sTo_Account, String sContent)
	{
		return MakeFullJsonNode_SendOrAcceptRequestToMakeFriend (nUserID, sSessionID, sSessionKey, sDeviceID, false, sMakeFriendRequestTicketFromPeer, net_maclife_wechat_http_BotEngine.WECHAT_SCENE_RoomMemberList2, sTo_Account, sContent);
	}

	private static JsonNode WebWeChatSendOrAcceptRequestToMakeFriend (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, boolean bRequestOrResponse, String sMakeFriendRequestTicketFromPeer, int nScene, String sTo_Account, String sIdentityContent) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
logger.info ("添加朋友 或 接收添加朋友的请求 …");
		// https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxverifyuser?r=***&pass_ticket=***	// 加上 JSON 格式的消息体，POST
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxverifyuser?r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + URLEncoder.encode (sPassTicket, utf8);
logger.fine ("WebWeChatSendOrAcceptRequestToMakeFriend 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
		CookieStore cookieStore = cookieManager.getCookieStore ();
		List<HttpCookie> listCookies = cookieStore.get (new URI(sURL));
		String sCookieValue = "";
		sCookieValue = MakeCookieValue (listCookies);
		mapRequestHeaders.put ("Cookie", sCookieValue);	// 避免服务器返回 1100 1102 代码？
logger.finer ("发送 WebWeChatSendOrAcceptRequestToMakeFriend 的 http 请求消息头:");
logger.finer ("	" + mapRequestHeaders);

		JsonNode jsonRequestBody = null;
		if (bRequestOrResponse)
			jsonRequestBody = MakeFullJsonNode_SendRequestToMakeFriend (nUserID, sSessionID, sSessionKey, MakeDeviceID (), nScene, sTo_Account, sIdentityContent);
		else
			jsonRequestBody = MakeFullJsonNode_AcceptRequestToMakeFriend (nUserID, sSessionID, sSessionKey, MakeDeviceID (), sMakeFriendRequestTicketFromPeer, nScene, sTo_Account, sIdentityContent);
		String sRequestBody = jacksonObjectMapper_Strict.writeValueAsString (jsonRequestBody);
logger.finer ("发送 WebWeChatSendOrAcceptRequestToMakeFriend 的 http 请求消息体:");
logger.finer ("	" + sRequestBody);

		String sContent = null;
		int nTryTimes = GetConfig().getInt ("app.net.try-times", DEFAULT_NET_TRY_TIMES);
		for (int i=0; i<nTryTimes; i++)
		{
			try
			{
				sContent = net_maclife_util_HTTPUtils.CURL_Post (sURL, mapRequestHeaders, sRequestBody.getBytes ());
logger.fine ("获取 WebWeChatSendOrAcceptRequestToMakeFriend 的 http 响应消息体:");
logger.fine ("\n" + sContent);

				JsonNode node = jacksonObjectMapper_Loose.readTree (sContent);
				ProcessBaseResponse (node, "WebWeChatSendOrAcceptRequestToMakeFriend (webwxverifyuser)");
				return node;
				//break;
			}
			catch (IOException e)
			{
				e.printStackTrace ();
logger.info ("IO 异常: " + e + (i>=(nTryTimes-1) ? "，已是最后一次，不再重试" : "，准备重试 …"));
				continue;
			}
		}
		return null;
	}
	public static JsonNode WebWeChatSendRequestToMakeFriend (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, int nScene, String sTo_Account, String sIdentityContent) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
		return WebWeChatSendOrAcceptRequestToMakeFriend (nUserID, sSessionID, sSessionKey, sPassTicket, true, null, nScene, sTo_Account, sIdentityContent);
	}
	public static JsonNode WebWeChatSendRequestToMakeFriend (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sTo_Account, String sIdentityContent) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
		return WebWeChatSendOrAcceptRequestToMakeFriend (nUserID, sSessionID, sSessionKey, sPassTicket, true, null, net_maclife_wechat_http_BotEngine.WECHAT_SCENE_RoomMemberList2, sTo_Account, sIdentityContent);
	}
	public static JsonNode WebWeChatAcceptRequestToMakeFriend (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sMakeFriendRequestTicketFromPeer, int nScene, String sTo_Account, String sIdentityContent) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
		return WebWeChatSendOrAcceptRequestToMakeFriend (nUserID, sSessionID, sSessionKey, sPassTicket, false, sMakeFriendRequestTicketFromPeer, nScene, sTo_Account, sIdentityContent);
	}
	public static JsonNode WebWeChatAcceptRequestToMakeFriend (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sMakeFriendRequestTicketFromPeer, String sTo_Account, String sIdentityContent) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
		return WebWeChatSendOrAcceptRequestToMakeFriend (nUserID, sSessionID, sSessionKey, sPassTicket, false, sMakeFriendRequestTicketFromPeer, net_maclife_wechat_http_BotEngine.WECHAT_SCENE_RoomMemberList2, sTo_Account, sIdentityContent);
	}




	public static JsonNode MakeFullJsonNode_UpdateChatRoom (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sTo_RoomAccount, String sFun, String sParam)
	{
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (nUserID, sSessionID, sSessionKey, sDeviceID));
		on.put ("ChatRoomName", sTo_RoomAccount);
		if (StringUtils.equalsIgnoreCase (sFun, "addmember"))
			on.put ("AddMemberList", sParam);
		else if (StringUtils.equalsIgnoreCase (sFun, "delmember"))
			on.put ("DelMemberList", sParam);
		else if (StringUtils.equalsIgnoreCase (sFun, "modtopic"))
			on.put ("NewTopic", sParam);
		return on;
	}
	public static JsonNode MakeFullJsonNode_InviteOrKick (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sTo_RoomAccount, String sFriendsAccounts_CommaSeparated, boolean bInviteOrKick)
	{
		return MakeFullJsonNode_UpdateChatRoom (nUserID, sSessionID, sSessionKey, sDeviceID, sTo_RoomAccount, bInviteOrKick ? "addmember" : "delmember", sFriendsAccounts_CommaSeparated);
	}
	public static JsonNode MakeFullJsonNode_InviteOrKick (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sTo_RoomAccount, List<String> listFriendsAccounts, boolean bInviteOrKick)
	{
		StringBuilder sb = new StringBuilder ();
		for (int i=0; i<listFriendsAccounts.size (); i++)
		{
			if (i!=0)
				sb.append (',');
			String sAccount = listFriendsAccounts.get (i);
			sb.append (sAccount);
		}
		return MakeFullJsonNode_InviteOrKick (nUserID, sSessionID, sSessionKey, sDeviceID, sTo_RoomAccount, sb.toString (), bInviteOrKick);
	}
	public static JsonNode MakeFullJsonNode_InviteFriendsToRoom (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sTo_RoomAccount, String sFriendsAccounts_CommaSeparated)
	{
		return MakeFullJsonNode_InviteOrKick (nUserID, sSessionID, sSessionKey, sDeviceID, sTo_RoomAccount, sFriendsAccounts_CommaSeparated, true);
	}
	public static JsonNode MakeFullJsonNode_InviteFriendsToRoom (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sTo_RoomAccount, List<String> listFriendsAccounts)
	{
		return MakeFullJsonNode_InviteOrKick (nUserID, sSessionID, sSessionKey, sDeviceID, sTo_RoomAccount, listFriendsAccounts, true);
	}
	public static JsonNode MakeFullJsonNode_KickMemberFromRoom (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sTo_RoomAccount, String sFriendsAccounts_CommaSeparated)
	{
		return MakeFullJsonNode_InviteOrKick (nUserID, sSessionID, sSessionKey, sDeviceID, sTo_RoomAccount, sFriendsAccounts_CommaSeparated, false);
	}
	public static JsonNode MakeFullJsonNode_KickMemberFromRoom (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sTo_RoomAccount, List<String> listFriendsAccounts)
	{
		return MakeFullJsonNode_InviteOrKick (nUserID, sSessionID, sSessionKey, sDeviceID, sTo_RoomAccount, listFriendsAccounts, false);
	}
	public static JsonNode MakeFullJsonNode_ModifyChatRoomName (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sTo_RoomAccount, String sNewName)
	{
		return MakeFullJsonNode_UpdateChatRoom (nUserID, sSessionID, sSessionKey, sDeviceID, sTo_RoomAccount, "modtopic", sNewName);
	}
	private static JsonNode WebWeChatUpdateChatRoom (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sTo_RoomAccount, String sFun, String sParam) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
		switch (sFun)
		{
			case "addmember":
logger.info ("邀请联系人到群聊…");
				break;
			case  "delmember":
logger.info ("从群中踢出联系人…");
				break;
			case "modtopic":
logger.info ("修改群名…");
				break;
			default:
logger.info ("未知的更新群聊操作: " + sFun);
				return null;
		}
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxupdatechatroom?fun=" + sFun + "&pass_ticket=" + URLEncoder.encode (sPassTicket, utf8);
logger.fine ("WebWeChatUpdateChatRoom 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
		CookieStore cookieStore = cookieManager.getCookieStore ();
		List<HttpCookie> listCookies = cookieStore.get (new URI(sURL));
		String sCookieValue = "";
		sCookieValue = MakeCookieValue (listCookies);
		mapRequestHeaders.put ("Cookie", sCookieValue);	// 避免服务器返回 1100 1102 代码？
logger.finer ("发送 WebWeChatUpdateChatRoom 的 http 请求消息头:");
logger.finer ("	" + mapRequestHeaders);

		JsonNode jsonRequestBody = null;
		switch (sFun)
		{
			case "addmember":
				jsonRequestBody = MakeFullJsonNode_InviteFriendsToRoom (nUserID, sSessionID, sSessionKey, MakeDeviceID (), sTo_RoomAccount, sParam);
				break;
			case  "delmember":
				jsonRequestBody = MakeFullJsonNode_KickMemberFromRoom (nUserID, sSessionID, sSessionKey, MakeDeviceID (), sTo_RoomAccount, sParam);
				break;
			case "modtopic":
				jsonRequestBody = MakeFullJsonNode_ModifyChatRoomName (nUserID, sSessionID, sSessionKey, MakeDeviceID (), sTo_RoomAccount, sParam);
				break;
		}
		String sRequestBody = jacksonObjectMapper_Strict.writeValueAsString (jsonRequestBody);
logger.finer ("发送 WebWeChatUpdateChatRoom 的 http 请求消息体:");
logger.finer ("	" + sRequestBody);

		String sContent = null;
		int nTryTimes = GetConfig().getInt ("app.net.try-times", DEFAULT_NET_TRY_TIMES);
		for (int i=0; i<nTryTimes; i++)
		{
			try
			{
				sContent = net_maclife_util_HTTPUtils.CURL_Post (sURL, mapRequestHeaders, sRequestBody.getBytes ());
logger.fine ("获取 WebWeChatUpdateChatRoom 的 http 响应消息体:");
logger.fine ("\n" + sContent);

				JsonNode node = jacksonObjectMapper_Loose.readTree (sContent);
				ProcessBaseResponse (node, "WebWeChatUpdateChatRoom (webwxupdatechatroom?fun=" + sFun + ")");
				return node;
				//break;
			}
			catch (IOException e)
			{
				e.printStackTrace ();
logger.info ("IO 异常: " + e + (i>=(nTryTimes-1) ? "，已是最后一次，不再重试" : "，准备重试 …"));
				continue;
			}
		}
		return null;
	}
	public static JsonNode WebWeChatInviteFriendsToRoom (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sTo_RoomAccount, String sFriendsAccounts_CommaSeparated) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
		return WebWeChatUpdateChatRoom (nUserID, sSessionID, sSessionKey, sPassTicket, sTo_RoomAccount, "addmember", sFriendsAccounts_CommaSeparated);
	}
	public static JsonNode WebWeChatKickMemberFromRoom (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sTo_RoomAccount, String sFriendsAccounts_CommaSeparated) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
		return WebWeChatUpdateChatRoom (nUserID, sSessionID, sSessionKey, sPassTicket, sTo_RoomAccount, "delmember", sFriendsAccounts_CommaSeparated);
	}
	public static JsonNode WebWeChatModifyRoomName (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sTo_RoomAccount, String sNewName) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
		return WebWeChatUpdateChatRoom (nUserID, sSessionID, sSessionKey, sPassTicket, sTo_RoomAccount, "modtopic", sNewName);
	}




	public static JsonNode MakeFullJsonNode_CreateChatRoom (long nUserID, String sSessionID, String sSessionKey, String sDeviceID, String sTopic, List<String> listMemberAccounts)
	{
		ObjectNode on = jacksonObjectMapper_Strict.createObjectNode ();
		on.set ("BaseRequest", MakeBaseRequestJsonNode (nUserID, sSessionID, sSessionKey, sDeviceID));
		on.put ("Topic", sTopic);
		on.put ("MemberCount", listMemberAccounts.size ());
		ArrayNode anMemberList = jacksonObjectMapper_Strict.createArrayNode ();
		for (String sAccount : listMemberAccounts)
		{
			ObjectNode onMember = jacksonObjectMapper_Strict.createObjectNode ();
			onMember.put ("UserName", sAccount);
			anMemberList.add (onMember);
		}
		on.set ("MemberList", anMemberList);
		return on;
	}
	public static JsonNode WebWeChatCreateChatRoom (long nUserID, String sSessionID, String sSessionKey, String sPassTicket, String sTopic, List<String> listMemberAccounts) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
logger.info ("开房…………………………");
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxcreatechatroom?r=" + System.currentTimeMillis () + "&pass_ticket=" + URLEncoder.encode (sPassTicket, utf8);
logger.fine ("WebWeChatCreateChatRoom 的 URL:");
logger.fine ("	" + sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
		CookieStore cookieStore = cookieManager.getCookieStore ();
		List<HttpCookie> listCookies = cookieStore.get (new URI(sURL));
		String sCookieValue = "";
		sCookieValue = MakeCookieValue (listCookies);
		mapRequestHeaders.put ("Cookie", sCookieValue);	// 避免服务器返回 1100 1102 代码？
logger.finer ("发送 WebWeChatCreateChatRoom 的 http 请求消息头:");
logger.finer ("	" + mapRequestHeaders);

		JsonNode jsonRequestBody = null;
		jsonRequestBody = MakeFullJsonNode_CreateChatRoom (nUserID, sSessionID, sSessionKey, MakeDeviceID (), sTopic, listMemberAccounts);
		String sRequestBody = jacksonObjectMapper_Strict.writeValueAsString (jsonRequestBody);
logger.finer ("发送 WebWeChatCreateChatRoom 的 http 请求消息体:");
logger.finer ("	" + sRequestBody);

		String sContent = null;
		int nTryTimes = GetConfig().getInt ("app.net.try-times", DEFAULT_NET_TRY_TIMES);
		for (int i=0; i<nTryTimes; i++)
		{
			try
			{
				sContent = net_maclife_util_HTTPUtils.CURL_Post (sURL, mapRequestHeaders, sRequestBody.getBytes ());
logger.fine ("获取 WebWeChatCreateChatRoom 的 http 响应消息体:");
logger.fine ("\n" + sContent);

				JsonNode node = jacksonObjectMapper_Loose.readTree (sContent);
				ProcessBaseResponse (node, "WebWeChatCreateChatRoom (webwxcreatechatroom)");
				return node;
				//break;
			}
			catch (IOException e)
			{
				e.printStackTrace ();
logger.info ("IO 异常: " + e + (i>=(nTryTimes-1) ? "，已是最后一次，不再重试" : "，准备重试 …"));
				continue;
			}
		}
		return null;
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
logger.warning (net_maclife_util_ANSIEscapeTool.Red (sAPIName + " 失败，代码: " + nRet + (StringUtils.isNotEmpty (sErrorMsg) ? ", 错误信息: " + sErrorMsg : "")));
	}

	/**
	 * 根据帐号来判断是否是聊天室帐号
	 * @param sAccount 帐号，可以是加密过的帐号（以 "@@" 开头）、或者未加密过的帐号（\d+@chatroom）
	 * @return 如果帐号以 <code>@@</code> 开头，则返回 <code>true</code>，否则返回 <code>false</code>
	 */
	public static boolean IsRoomAccount (String sAccount)
	{
		return StringUtils.startsWith (sAccount, "@@") || (StringUtils.isNotEmpty (sAccount) && sAccount.matches ("^\\d+@chatroom$"));
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

	public static final String REGEXP_FindTransformedEmojiHexString = "<span class=\"emoji emoji(\\p{XDigit}{4,})\"></span>";
	public static final Pattern PATTERN_FindTransformedEmojiHexString = Pattern.compile (REGEXP_FindTransformedEmojiHexString, Pattern.CASE_INSENSITIVE);
	public static String RestoreEmojiCharacters (String sContent)
	{
logger.finest ("原内容: " + sContent);
		Matcher matcher = PATTERN_FindTransformedEmojiHexString.matcher (sContent);
		boolean bMatched = false;
		StringBuffer sbReplace = new StringBuffer ();
		while (matcher.find ())
		{
			bMatched = true;
			String sEmojiHexString = matcher.group(1);

			Hex hex = new Hex (StandardCharsets.ISO_8859_1);
			try
			{
				String sEmoji = "";
				for (int i=0; i<sEmojiHexString.length ();)
				{
					Charset charset = null;
					String sSingleEmojiGlyphHexString = null;
					String sStartString = StringUtils.substring (sEmojiHexString, i, i+2);
					if (StringUtils.startsWithIgnoreCase (sStartString, "1f"))
					{
						sSingleEmojiGlyphHexString = "000" + StringUtils.substring (sEmojiHexString, i, i+5);
						i += 5;
						charset = UTF_32BE;
					}
					else
					{
						sSingleEmojiGlyphHexString = StringUtils.substring (sEmojiHexString, i, i+4);
						i += 4;
						charset = StandardCharsets.UTF_16BE;
					}
//System.out.println (sSingleEmojiGlyphHexString);
					//BigInteger bi = new BigInteger (sEmojiHexString, 16);
					byte[] arraySingleEmoji = null;
					//arrayEmoji = bi.toByteArray ();
					arraySingleEmoji = (byte[])hex.decode (sSingleEmojiGlyphHexString);
//System.out.println (Arrays.toString (arrayEmoji));
					sEmoji = sEmoji + new String (arraySingleEmoji, charset);
					//sbReplace.append (b)Character (nEmojiCode);
				}
				matcher.appendReplacement (sbReplace, sEmoji);	// 直接剔除掉，然后再补上 emoji 字符。<del>（不直接替换的原因：appendReplacement 只接受 String 参数，而不接受 char[] 参数）</del>
			}
			catch (DecoderException e)
			{
logger.warning (sEmojiHexString + " " + e.toString ());
				e.printStackTrace();
			}
		}
		matcher.appendTail (sbReplace);
//System.out.println (sbReplace);

		if (bMatched)
			sContent = sbReplace.toString ();

logger.finest ("替换后: " + sContent);

		return sContent;
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
							e.printStackTrace ();
System.out.println ("非法日志级别: " + sParam + ", 请换有效的日志级别名称，比如 all finest finer fine info warning severe 1000 0 1 ...");
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
					else if (StringUtils.equalsAnyIgnoreCase (sCommand, "msg", "send", "text"))	// msg 命令 - 仿 IRC 频道的 msg 命令
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
					else if (StringUtils.equalsAnyIgnoreCase (sCommand, "msgToAlias", "sendToAlias", "textToAlias",	// 根据用户的微信号来发文字消息
							"msgToRemarkName", "sendToRemarkName", "textToRemarkName",	// 根据自己给用户做的备注名来发文字消息
							"msgToNickName", "sendToNickName", "textToNickName",	// 根据用户的昵称来发文字消息
							"msgToMe", "sendToMe", "textToMe", "msgToSelf", "sendToSelf", "textToSelf", "msgToMyself", "sendToMyself", "textToMyself"	// 发送给自己（到手机端）
							)
					)
					{
						String sSearchBy = "";
						String sNameOfSearchBy = "";
						if (StringUtils.startsWithIgnoreCase (sCommand, "msgTo"))
						{
							sSearchBy = StringUtils.substring (sCommand, 5);
						}
						else if (StringUtils.startsWithIgnoreCase (sCommand, "sendTo") || StringUtils.startsWithIgnoreCase (sCommand, "textTo"))
						{
							sSearchBy = StringUtils.substring (sCommand, 6);
						}
						else
						{
							continue;
						}

						if (StringUtils.equalsAnyIgnoreCase (sSearchBy, "me", "myself", "self"))
							sNameOfSearchBy = "自己";
						else if (StringUtils.equalsIgnoreCase (sSearchBy, "Alias"))
							sNameOfSearchBy = "微信号";
						else if (StringUtils.equalsIgnoreCase (sSearchBy, "RemarkName"))
							sNameOfSearchBy = "备注名";
						else if (StringUtils.equalsIgnoreCase (sSearchBy, "NickName"))
							sNameOfSearchBy = "昵称";
						else
						{
logger.warning ("不知道你要根据什么发消息… sSearchByName = " + sNameOfSearchBy);
							continue;
						}

						if (StringUtils.isEmpty (sParam))
						{
							if (StringUtils.equalsIgnoreCase (sNameOfSearchBy, "自己"))
logger.warning (sCommand + " <发给自己的消息内容>");
							else
logger.warning (sCommand + " <接收人的" + sNameOfSearchBy + "> <消息内容>");
							continue;
						}

						String sToTarget = null;
						String sMessage = null;
						if (StringUtils.equalsIgnoreCase (sNameOfSearchBy, "自己"))
						{
							sToTarget = engine.sMyEncryptedAccountInThisSession;
							sMessage = sParam;
						}
						else
						{
							String[] arraySendMessage = sParam.split (" +", 2);
							if (arraySendMessage.length > 0)
								sToTarget = arraySendMessage[0];
							if (arraySendMessage.length > 1)
								sMessage = arraySendMessage[1];
						}

						if (StringUtils.isEmpty (sToTarget))
						{
logger.warning ("必须输入接收人的" + sNameOfSearchBy + "。");
							continue;
						}
						if (StringUtils.isEmpty (sMessage))
						{
logger.warning ("必须输入消息内容");
							continue;
						}

						List<JsonNode> listContacts = null;
						if (StringUtils.equalsIgnoreCase (sNameOfSearchBy, "自己"))
							listContacts = engine.SearchForContacts (sToTarget, null, null, null);
						else if (StringUtils.equalsIgnoreCase (sNameOfSearchBy, "微信号"))
							listContacts = engine.SearchForContacts (null, sToTarget, null, null);
						else if (StringUtils.equalsIgnoreCase (sNameOfSearchBy, "备注名"))
							listContacts = engine.SearchForContacts (null, null, sToTarget, null);
						else if (StringUtils.equalsIgnoreCase (sNameOfSearchBy, "昵称"))
							listContacts = engine.SearchForContacts (null, null, null, sToTarget);
						if (listContacts.size () != 1)
						{
							if (listContacts.size () == 0)
							{
logger.warning (net_maclife_util_ANSIEscapeTool.Yellow ("根据" + sNameOfSearchBy + "【" + sToTarget + "】未搜到联系人。注意：只从微信通信录中搜索，群聊如果没有加到微信通信录，是搜不到的（联系人里没有）。"));
							}
							else
							{
logger.warning (net_maclife_util_ANSIEscapeTool.Yellow ("根据" + sNameOfSearchBy + "【" + sToTarget + "】搜索到的联系人不是 1 个，而是 " + listContacts.size () + " 个。"));
							}
							continue;
						}
						JsonNode jsonContact = listContacts.get (0);
						sMessage = StringEscapeUtils.unescapeJava (sMessage);	// 目的：将 \n 转成回车符号，用单行文字书写多行文字。虽然，测试时发现，也不需要 unescape，微信接收到后会自动解转义（大概是 json 的原因吧）。为了日志好看一些，还是自己取消转义……
						engine.SendTextMessage (GetJSONText (jsonContact, "UserName"), sMessage);
					}
					else if (StringUtils.equalsAnyIgnoreCase (sCommand, "SendFile", "SendImage", "SendAudio", "SendVideo"))	// 发送图片、视频、其他文件
					{
						if (StringUtils.isEmpty (sParam))
						{
logger.warning (sCommand + " <接收人帐号> <本地文件名>");
							continue;
						}
						String[] arraySendFile = sParam.split (" +", 2);
						String sToAccount = null;
						String sFileName = null;
						if (arraySendFile.length > 0)
							sToAccount = arraySendFile[0];
						if (arraySendFile.length > 1)
							sFileName = arraySendFile[1];

						if (StringUtils.isEmpty (sToAccount))
						{
logger.warning ("必须输入接收人的帐号。接收人帐号可以是加密过的形式，如： @XXXX @@XXXX 或未加密过的形式，如：wxid_XXXX filehelper gh_XXXX");
							continue;
						}
						if (StringUtils.isEmpty (sFileName))
						{
logger.warning ("必须输入文件名");
							continue;
						}
						File f = new File (sFileName);
						if (! f.exists ())
						{
logger.warning ("文件 " + sFileName + " 不存在！");
							continue;
						}
						engine.SendMediaFile (sToAccount, f);
					}
					else if (StringUtils.equalsAnyIgnoreCase (sCommand, "fileToAlias", "imageToAlias", "audioToAlias", "voiceToAlias", "videoToAlias",	// 根据用户的微信号来发文件
							"fileToRemarkName", "imageToRemarkName", "audioToRemarkName", "voiceToRemarkName", "videoToRemarkName",	// 根据自己给用户做的备注名来发文件
							"fileToNickName", "imageToNickName", "audioToNickName", "voiceToNickName", "videoToNickName",	// 根据用户的昵称来发文件
							"fileToMe", "imageToMe", "audioToMe", "voiceToMe", "videoToMe",
							"fileToSelf", "imageToSelf", "audioToSelf", "voiceToSelf", "videoToSelf",
							"fileToMyself", "imageToMyself", "audioToMyself", "voiceToMyself", "videoToMyself"	// 发送给自己（到手机端）
							)
					)
					{
						String sSearchBy = "";
						String sNameOfSearchBy = "";
						if (StringUtils.startsWithIgnoreCase (sCommand, "fileTo"))
						{
							sSearchBy = StringUtils.substring (sCommand, 6);
						}
						else if (StringUtils.startsWithIgnoreCase (sCommand, "imageTo") || StringUtils.startsWithIgnoreCase (sCommand, "audioTo") || StringUtils.startsWithIgnoreCase (sCommand, "voiceTo") || StringUtils.startsWithIgnoreCase (sCommand, "videoTo"))
						{
							sSearchBy = StringUtils.substring (sCommand, 7);
						}
						else
						{
							continue;
						}

						if (StringUtils.equalsAnyIgnoreCase (sSearchBy, "me", "myself", "self"))
							sNameOfSearchBy = "自己";
						else if (StringUtils.equalsIgnoreCase (sSearchBy, "Alias"))
							sNameOfSearchBy = "微信号";
						else if (StringUtils.equalsIgnoreCase (sSearchBy, "RemarkName"))
							sNameOfSearchBy = "备注名";
						else if (StringUtils.equalsIgnoreCase (sSearchBy, "NickName"))
							sNameOfSearchBy = "昵称";
						else
						{
logger.warning ("不知道你要根据什么发消息… sSearchByName = " + sNameOfSearchBy);
							continue;
						}

						if (StringUtils.isEmpty (sParam))
						{
							if (StringUtils.equalsIgnoreCase (sNameOfSearchBy, "自己"))
logger.warning (sCommand + " <发给自己的本地文件名>");
							else
logger.warning (sCommand + " <接收人的" + sNameOfSearchBy + "> <本地文件名>");
							continue;
						}
						String sToTarget = null;
						String sFileName = null;
						if (StringUtils.equalsIgnoreCase (sNameOfSearchBy, "自己"))
						{
							sToTarget = engine.sMyEncryptedAccountInThisSession;
							sFileName = sParam;
						}
						else
						{
							String[] arraySendFile = sParam.split (" +", 2);
							if (arraySendFile.length > 0)
								sToTarget = arraySendFile[0];
							if (arraySendFile.length > 1)
								sFileName = arraySendFile[1];
						}

						if (StringUtils.isEmpty (sToTarget))
						{
logger.warning ("必须输入接收人的" + sNameOfSearchBy + "。");
							continue;
						}
						if (StringUtils.isEmpty (sFileName))
						{
logger.warning ("必须输入文件名");
							continue;
						}
						File f = new File (sFileName);
						if (! f.exists ())
						{
logger.warning ("文件 " + sFileName + " 不存在！");
							continue;
						}

						List<JsonNode> listContacts = null;
						if (StringUtils.equalsIgnoreCase (sNameOfSearchBy, "自己"))
							listContacts = engine.SearchForContacts (sToTarget, null, null, null);
						else if (StringUtils.equalsIgnoreCase (sNameOfSearchBy, "微信号"))
							listContacts = engine.SearchForContacts (null, sToTarget, null, null);
						else if (StringUtils.equalsIgnoreCase (sNameOfSearchBy, "备注名"))
							listContacts = engine.SearchForContacts (null, null, sToTarget, null);
						else if (StringUtils.equalsIgnoreCase (sNameOfSearchBy, "昵称"))
							listContacts = engine.SearchForContacts (null, null, null, sToTarget);
						if (listContacts.size () != 1)
						{
							if (listContacts.size () == 0)
							{
logger.warning (net_maclife_util_ANSIEscapeTool.Yellow ("根据" + sNameOfSearchBy + "【" + sToTarget + "】未搜到联系人。注意：只从微信通信录中搜索，群聊如果没有加到微信通信录，是搜不到的（联系人里没有）。"));
							}
							else
							{
logger.warning (net_maclife_util_ANSIEscapeTool.Yellow ("根据" + sNameOfSearchBy + "【" + sToTarget + "】搜索到的联系人不是 1 个，而是 " + listContacts.size () + " 个。"));
							}
							continue;
						}
						JsonNode jsonContact = listContacts.get (0);
						engine.SendMediaFile (GetJSONText (jsonContact, "UserName"), f);
					}
					else if (StringUtils.equalsAnyIgnoreCase (sCommand, "AddFriend", "AddContact", "MakeFriend"))
					{
						if (StringUtils.isEmpty (sParam))
						{
logger.warning (sCommand + " <对方帐号（明文或密文）、微信号、手机号码、QQ 号码> <附加消息内容>");
							continue;
						}

						String[] arrayMakeFriend = sParam.split (" +", 2);
						String sTo = null;
						String sIdentityMessage = null;
						if (arrayMakeFriend.length > 0)
							sTo = arrayMakeFriend[0];
						if (arrayMakeFriend.length > 1)
							sIdentityMessage = arrayMakeFriend[1];

						if (StringUtils.isEmpty (sTo))
						{
logger.warning ("必须输入对方帐号。对方帐号可以是加密过的形式，如： @XXXX @@XXXX 或未加密过的形式，如：wxid_XXXX filehelper gh_XXXX");
							continue;
						}
						if (StringUtils.isEmpty (sIdentityMessage))
						{
logger.warning ("必须输入附加消息内容");
							continue;
						}
						engine.SendRequestToMakeFriend (sTo, sIdentityMessage);
					}

					else if (StringUtils.equalsAnyIgnoreCase (sCommand, "Invite", "邀请", "InviteByAlias", "按微信号邀请", "InviteByRemarkName", "按备注名邀请", "InviteByNickName", "按昵称邀请"))
					{	// 一般，建议使用 Manager Bot 提供的 invite 功能，因为 bot 的 invite 命令必须在群中执行，所以那样可以省略“群帐号”参数
						if (StringUtils.isEmpty (sParam))
						{
logger.warning (sCommand + " <群帐号> <联系人帐号/微信号/备注名/昵称>");
							continue;
						}

						String sSearchBy = "";
						String sNameOfSearchBy = "";
						if (StringUtils.startsWithIgnoreCase (sCommand, "Invite") || StringUtils.startsWithIgnoreCase (sCommand, "邀请"))
						{
							sSearchBy = "Account";
						}
						else if (StringUtils.startsWithIgnoreCase (sCommand, "InviteBy"))
						{
							sSearchBy = StringUtils.substring (sCommand, "InviteBy".length ());
						}
						else if (StringUtils.startsWithIgnoreCase (sCommand, "按") && StringUtils.endsWithIgnoreCase (sCommand, "邀请"))
						{
							sSearchBy = StringUtils.substring (sCommand, 1, sCommand.length () - 2);
						}
						else
						{
							continue;
						}

						if (StringUtils.equalsAnyIgnoreCase (sSearchBy, "Account", "帐号"))
							sNameOfSearchBy = "帐号";
						else if (StringUtils.equalsAnyIgnoreCase (sSearchBy, "Alias", "微信号"))
							sNameOfSearchBy = "微信号";
						else if (StringUtils.equalsAnyIgnoreCase (sSearchBy, "RemarkName", "备注名"))
							sNameOfSearchBy = "备注名";
						else if (StringUtils.equalsAnyIgnoreCase (sSearchBy, "NickName", "昵称"))
							sNameOfSearchBy = "昵称";
						else
						{
logger.warning ("不知道你要根据什么发消息… sSearchByName = " + sNameOfSearchBy);
							continue;
						}

						String[] arrayInviteFriendsToRoom = sParam.split (" +", 2);
						String sToRoomAccount = null;
						String sFriends = null;
						if (arrayInviteFriendsToRoom.length > 0)
							sToRoomAccount = arrayInviteFriendsToRoom[0];
						if (arrayInviteFriendsToRoom.length > 1)
							sFriends = arrayInviteFriendsToRoom[1];

						if (StringUtils.isEmpty (sToRoomAccount))
						{
logger.warning ("必须输入群的帐号。群帐号可以是加密过的形式（如：@@XXXX）或者未加密过的形式（如：100000@chatroom）");
							continue;
						}
						else if (! IsRoomAccount(sToRoomAccount))
						{
logger.warning ("输入的群帐号不是有效的群帐号。群帐号可以是加密过的形式（如：@@XXXX）或者未加密过的形式（如：100000@chatroom）");
						}
						if (StringUtils.isEmpty (sFriends))
						{
logger.warning ("必须输入一个联系人帐号/微信号/备注名/昵称");
							continue;
						}

						List<String> listFriends = SplitCommandLine (sFriends);	// 鉴于用户昵称可能包含空格或特殊字符的情况，这里必须用 SplitCommandLine 函数处理，不能简单的 .split (" ")
						StringBuilder sbFriendsAccounts = new StringBuilder ();
						for (int i=0; i<listFriends.size (); i++)
						{
							String sFriend = listFriends.get (i);
							JsonNode jsonContact = null;
							if (StringUtils.equalsIgnoreCase (sNameOfSearchBy, "帐号"))
								jsonContact = engine.SearchForSingleContact (sFriend, null, null, null);
							else if (StringUtils.equalsIgnoreCase (sNameOfSearchBy, "微信号"))
								jsonContact = engine.SearchForSingleContact (null, sFriend, null, null);
							else if (StringUtils.equalsIgnoreCase (sNameOfSearchBy, "备注名"))
								jsonContact = engine.SearchForSingleContact (null, null, sFriend, null);
							else if (StringUtils.equalsIgnoreCase (sNameOfSearchBy, "昵称"))
								jsonContact = engine.SearchForSingleContact (null, null, null, sFriend);

							if (jsonContact==null)
							{
logger.warning ("根据【" + sNameOfSearchBy + "】搜索【" + sFriend + "】，未搜索到联系人");
								continue;
							}

							if (sbFriendsAccounts.length () != 0)
								sbFriendsAccounts.append (',');

							sbFriendsAccounts.append (GetJSONText (jsonContact, "UserName"));
						}

						if (sbFriendsAccounts.length () == 0)
						{
logger.warning ("根据【" + sNameOfSearchBy + "】搜索 " + sFriends + "，未搜索到任何联系人");
							continue;
						}

						engine.InviteFriendsToRoom (sToRoomAccount, sbFriendsAccounts.toString ());
					}
					else if (StringUtils.equalsAnyIgnoreCase (sCommand, "StatReport", "EmptyStatReport"))
					{
						engine.EmptyStatisticsReport ();
					}
					else if (StringUtils.equalsIgnoreCase (sCommand, "FakeStatReport"))
					{
						engine.FakeStatisticsReport ();
					}
					else
					{
logger.warning (net_maclife_util_ANSIEscapeTool.Red ("未知控制台命令: " + sCommand));
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
		String sURL = net_maclife_wechat_http_BotApp.GetConfig ().getString ("app.jdbc.url");	// , "jdbc:mysql://localhost/WeChatBotEngine?autoReconnect=true&amp;zeroDateTimeBehavior=convertToNull"
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
		//botDS.setUrl ("jdbc:mysql://192.168.2.1/bot?autoReconnect=true&amp;zeroDateTimeBehavior=convertToNull&amp;noAccessToProcedureBodies=true&amp;useInformationSchema=true"); // 没有作用

		// http://thenullhandler.blogspot.com/2012/06/user-does-not-have-access-error-with.html // 没有作用
		// http://bugs.mysql.com/bug.php?id=61203
		//botDS.setUrl ("jdbc:mysql://192.168.2.1/bot?autoReconnect=true&amp;zeroDateTimeBehavior=convertToNull&amp;useInformationSchema=true");

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
		return node.get (sFieldName).asText (sDefault);
	}
	public static String GetJSONText (JsonNode node, String sFieldName)
	{
		return GetJSONText (node, sFieldName, "");
	}

	public static int GetJSONInt (JsonNode node, String sFieldName, int nDefault)
	{
		if (node==null || node.get (sFieldName)==null)
			return nDefault;
		return node.get (sFieldName).asInt (nDefault);
	}
	public static int GetJSONInt (JsonNode node, String sFieldName)
	{
		return GetJSONInt (node, sFieldName, -1);
	}

	public static long GetJSONLong (JsonNode node, String sFieldName, long nDefault)
	{
		if (node==null || node.get (sFieldName)==null)
			return nDefault;
		return node.get (sFieldName).asLong (nDefault);
	}
	public static long GetJSONLong (JsonNode node, String sFieldName)
	{
		return GetJSONLong (node, sFieldName, -1L);
	}

	public static boolean GetJSONBoolean (JsonNode node, String sFieldName, boolean bDefault)
	{
		if (node==null || node.get (sFieldName)==null)
			return bDefault;
		return node.get (sFieldName).asBoolean (bDefault);
	}
	public static boolean GetJSONBoolean (JsonNode node, String sFieldName)
	{
		return GetJSONBoolean (node, sFieldName, false);
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

	public static boolean hasCommandPrefix (String sContent)
	{
		String sPrefix = GetConfig ().getString ("app.text-message.command-prefix");
		return StringUtils.isEmpty (sPrefix) || StringUtils.startsWithIgnoreCase (sContent, sPrefix);
	}

	public static String StripOutCommandPrefix (String sContent, String sPrefix)
	{
		if (StringUtils.isEmpty (sPrefix))
			return sContent;
		return StringUtils.substring (sContent, StringUtils.length (sPrefix));
	}
	public static String StripOutCommandPrefix (String sContent)
	{
		return StripOutCommandPrefix (sContent, GetConfig ().getString ("app.text-message.command-prefix"));
	}

	public static void main (String[] args) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, ScriptException, ValidityException, ParsingException
	{
		net_maclife_wechat_http_BotApp app = new net_maclife_wechat_http_BotApp ();
		app.Start ();
	}
}
