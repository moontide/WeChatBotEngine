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


	Bot bot;
	public net_maclife_wechat_http_WechatBot ()
	{
		bot = new Bot ();
	}
	public Bot GetBot ()
	{
		return bot;
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
		File fOutputFile = new File (sFileName);
		InputStream is = net_maclife_util_HTTPUtils.CURL_Stream (sURL);
		OutputStream os = new FileOutputStream (fOutputFile);
		IOUtils.copy (is, os);
System.out.println ("获取 LoginQRCode 的 http 响应消息体（保存到文件）:");
System.out.println ("	[" + fOutputFile + "]");
		//String sQRCode = "";
		return fOutputFile;
	}

	public static Object 等待二维码被扫描以便登录 (String sLoginID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, ScriptException, ValidityException, ParsingException
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

System.out.println ("登录页面设置的 Cookie:");
			Map<String, List<String>> mapHeaders = http.getHeaderFields ();
			for (String sHeaderName : mapHeaders.keySet ())
			{
				if (StringUtils.equalsIgnoreCase (sHeaderName, "Set-Cookie"))
				{
					List<String> listCookies = mapHeaders.get (sHeaderName);
System.out.println ("	[" + listCookies + "]");
					for (String sCookie : listCookies)
						net_maclife_util_HTTPUtils.ParseCookie (sCookie);
					//break;
				}
			}
			return mapResult;

		}
		return nLoginResultCode;
	}

	public static String MakeBaseRequestJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID)
	{
		return
		"{\n" +
		 "	\"BaseRequest\":\n" +
		"	{\n" +
		 "		\"Uin\": \"" + sUserID + "\",\n" +
		 "		\"Sid\": \"" + sSessionID + "\",\n" +
		 "		\"Skey\": \"" + sSessionKey + "\",\n" +
		 "		\"DeviceID\": \"" + sDeviceID + "\"\n" +
		 "	}\n" +
		 "}\n";
	}

	public static String MakeInitJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID)
	{
		return
		"{\n" +
		 "	\"BaseRequest\":\n" +
			MakeBaseRequestJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) +
		 "}";
	}

	public static JsonNode WebWeChatInit (String sUserID, String sSessionID, String sSessionKey, String sPassTicket) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
		// https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxinit?r=1703974212&lang=zh_CN&pass_ticket=ZfvpI6wcO7N5PTkacmWK9zUTXpUOB3kqre%2BrkQ8IAtHDAIP2mc2psB5eDH8cwzsp
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxinit?r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + sPassTicket;
System.out.println ("WebWeChatInit 的 URL:");
System.out.println (sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
		String sJSONStringRequestBody = MakeBaseRequestJSONString (sUserID, sSessionID, sSessionKey, "e" + System.currentTimeMillis ());
System.out.println (sJSONStringRequestBody);
		InputStream is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sJSONStringRequestBody.getBytes ());
		ObjectMapper om = new ObjectMapper ();
		JsonNode node = om.readTree (is);
System.out.println ("获取 WebWeChatInit 的 http 响应消息体:");
System.out.println ("	[" + node + "]");
		//
		return node;
	}

	public static JsonNode WebWeChatGetContacts (String sUserID, String sSessionID, String sSessionKey, String sPassTicket) throws JsonProcessingException, IOException, KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException
	{
		String sURL = "https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxgetcontact?r=" + System.currentTimeMillis () + "&lang=zh_CN&pass_ticket=" + sPassTicket;
System.out.println ("WebWeChatGetContacts 的 URL:");
System.out.println (sURL);

		Map<String, Object> mapRequestHeaders = new HashMap<String, Object> ();
		mapRequestHeaders.put ("Content-Type", "application/json; charset=utf-8");
		String sJSONStringRequestBody = MakeBaseRequestJSONString (sUserID, sSessionID, sSessionKey, "e" + System.currentTimeMillis ());
System.out.println (sJSONStringRequestBody);
		InputStream is = net_maclife_util_HTTPUtils.CURL_Post_Stream (sURL, mapRequestHeaders, sJSONStringRequestBody.getBytes ());
		ObjectMapper om = new ObjectMapper ();
		JsonNode node = om.readTree (is);
System.out.println ("获取 WebWeChatGetContacts 的 http 响应消息体:");
System.out.println ("	[" + node + "]");
		//
		return node;
	}

	public static void Logout ()
	{
		// https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxlogout?redirect=1&type=1&skey=@crypt_1df7c02d_9effb9a7d4292af4681c79dab30b6a57

		// 被踢出后重新登录
		// https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxnewloginpage?ticket=A-1wUN8dm6D-nIJH8m8g7yfh@qrticket_0&uuid=YZzRE6skKQ==&lang=zh_CN&scan=1479978616 对比最初的登录参数，后面是新加的： &fun=new&version=v2&lang=zh_CN
		//    <error><ret>0</ret><message></message><skey>@crypt_1df7c02d_131d1d0335be6fd38333592c098a5b16</skey><wxsid>GrS6IjctQkOxs0PP</wxsid><wxuin>2100343515</wxuin><pass_ticket>T%2FduUWTWjODelhztGXZAO1b3u7S5Ddy8ya8fP%2BYhZlRjxR1ERMDXHKbaCs6x2mQP</pass_ticket><isgrayscale>1</isgrayscale></error>
	}

	class Bot implements Runnable
	{
		/**
		 * Bot 线程： 不断循环尝试登录，直到登录成功。如果登录成功后被踢下线，依旧不断循环尝试登录……
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
				//do
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
								//continue _outer_loop;
							}
							else	// 大概只有 200 才能出来：当是 200 时，但访问登录页面失败时，可能会跑到此处
							{
								//
							}
						}
					} while (! (o instanceof Map<?, ?>));
					mapWaitLoginResult = (Map<String, Object>) o;
					String sUserID     = (String) mapWaitLoginResult.get ("UserID");
					String sSessionID  = (String) mapWaitLoginResult.get ("SessionID");
					String sSessionKey = (String) mapWaitLoginResult.get ("SessionKey");
					String sPassTicket = (String) mapWaitLoginResult.get ("PassTicket");

					// 4. 确认登录后，初始化 Web 微信，返回初始信息
					WebWeChatInit (sUserID, sSessionID, sSessionKey, sPassTicket);

					// 5. 获取联系人
					WebWeChatGetContacts (sUserID, sSessionID, sSessionKey, sPassTicket);
				}
				//while (! Thread.interrupted ());
			}
			catch (Exception e)
			{
				e.printStackTrace ();
			}
System.out.println ("bot 线程退出");
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
		executor.shutdown ();
	}

	public static void main (String[] args) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, ScriptException, ValidityException, ParsingException
	{
		net_maclife_wechat_http_WechatBot app = new net_maclife_wechat_http_WechatBot ();

		executor.submit (app);
		executor.submit (app.GetBot ());
	}
}
