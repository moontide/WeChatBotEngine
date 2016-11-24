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
	public ExecutorService executor = Executors.newFixedThreadPool (5);

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

	public static String GetLoginID () throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, ScriptException
	{
		String sURL = "https://login.weixin.qq.com/jslogin?appid=wx782c26e4c19acffb&redirect_uri=https%3A%2F%2Fwx.qq.com%2Fcgi-bin%2Fmmwebwx-bin%2Fwebwxnewloginpage&fun=new&lang=en_US&_=" + System.currentTimeMillis ();

		String sContent = net_maclife_util_HTTPUtils.CURL (sURL);	// window.QRLogin.code = 200; window.QRLogin.uuid = "QegF7Tukgw==";
System.out.println ("获取 LoginID 的 http 响应消息体:");
System.out.println ("[" + sContent + "]");

		String sLoginID = public_jse.eval (StringUtils.replace (sContent, "window.QRLogin.", "var ") + " uuid;").toString ();
System.out.println ("获取到的 LoginID:");
System.out.println ("[" + sLoginID + "]");

		return sLoginID;
	}

	public static File GetLoginQRCodeImageFile (String sLoginID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		String sURL = "https://login.weixin.qq.com/qrcode/" + sLoginID;
		String sFileName = "/tmp/wechat-login-qrcode-image-" + sLoginID + ".jpg";
		File fOutputFile = new File (sFileName);
		InputStream is = net_maclife_util_HTTPUtils.CURL_Stream (sURL);
		OutputStream os = new FileOutputStream (fOutputFile);
		IOUtils.copy (is, os);
System.out.println ("获取 LoginQRCode 的 http 响应消息体（保存到文件）:");
System.out.println ("[" + fOutputFile + "]");
		//String sQRCode = "";
		return fOutputFile;
	}

	public static void 等待二维码被扫描以便登录 (String sLoginID) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, ScriptException, ValidityException, ParsingException
	{
		String sLoginURL = "";
		String sLoginResultCode = "";
		int nLoginResultCode = 0;

		String sURL = null;
		String sContent = null;
		do
		{
		long nTimestamp = System.currentTimeMillis ();
		long r = ~ nTimestamp;	// 0xFFFFFFFFFFFFFFFFL ^ nTimestamp;
		boolean bLoginIcon = false;	// true;
		sURL = "https://login.weixin.qq.com/cgi-bin/mmwebwx-bin/login?loginicon=" + bLoginIcon + "&uuid=" + sLoginID + "&tip=0&r=" + r + "&_=" + nTimestamp;

		// window.code=408;	未扫描/超时。只要未扫描就不断循环，但 web 端好像重复 12 次（每次 25054 毫秒）左右后，就重新刷新 LoginID
		// window.code=201;	已扫描
		// window.code=200;	已确认登录
		// window.redirect_uri="https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxnewloginpage?ticket=A8qwapRV_lQ44viWM0mZmnpm@qrticket_0&uuid=gYiEFqEQdw==&lang=en_US&scan=1479893365";
		sContent = net_maclife_util_HTTPUtils.CURL (sURL);

System.out.println ("等待二维码被扫描以便登录 的 http 响应消息体:");
System.out.println ("[" + sContent + "]");

		String sJSCode = StringUtils.replace (sContent, "window.", "var ");
		sLoginResultCode = public_jse.eval (sJSCode + " code;").toString ();
		nLoginResultCode = Double.valueOf (sLoginResultCode).intValue ();
System.out.println ("	获取到的 LoginResultCode:");
System.out.println ("	[" + nLoginResultCode + "]");

			if (nLoginResultCode == 200)
			{
				sLoginURL = public_jse.eval (sJSCode + " redirect_uri;").toString ();
System.out.println ("已确认登录，浏览器需要重定向到的登录页面网址为:");
System.out.println ("	[" + sLoginURL + "]");
				sLoginURL = sLoginURL + "&fun=new&version=v2";
System.out.println ("网址加上 &fun=new&version=v2:");
System.out.println ("	[" + sLoginURL + "]");
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
			nu.xom.Element eXML = doc.getRootElement ();
			//sContent = IOUtils.toString (is, net_maclife_util_HTTPUtils.UTF8_CHARSET);
System.out.println ("登录页面消息体:");
//System.out.println ("	[" + sContent + "]");
System.out.println ("	[" + eXML.toXML() + "]");
System.out.println ("	UIN=[" + eXML.getFirstChildElement ("wxuin").getValue () + "]");
System.out.println ("	SID=[" + eXML.getFirstChildElement ("wxsid").getValue () + "]");
System.out.println ("	SKEY=[" + eXML.getFirstChildElement ("skey").getValue () + "]");
System.out.println ("	TICKET=[" + eXML.getFirstChildElement ("pass_ticket").getValue () + "]");

System.out.println ("登录页面设置的 Cookie:");
			Map<String, List<String>> mapHeaders = http.getHeaderFields ();
			for (String sHeaderName : mapHeaders.keySet ())
			{
				if (StringUtils.equalsIgnoreCase (sHeaderName, "Set-Cookie"))
				{
System.out.println (mapHeaders.get (sHeaderName).size ());
					String sCookies = mapHeaders.get (sHeaderName).get (0);
System.out.println ("	[" + sCookies + "]");
System.out.println ("	[" + mapHeaders.get (sHeaderName) + "]");
					net_maclife_util_HTTPUtils.ParseCookie (sCookies);
					//break;
				}
			}
		}
	}

	public static String MakeBaseRequestJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID)
	{
		return
		"{\n" +
		 "	Uin: \"" + sUserID + "\"," +
		 "	Sid: \"" + sSessionID + "\"," +
		 "	Skey: \"" + sSessionKey + "\"," +
		 "	DeviceID: \"" + sDeviceID + "\"," +
		 "}";
	}

	public static String MakeInitJSONString (String sUserID, String sSessionID, String sSessionKey, String sDeviceID)
	{
		return
		"{\n" +
		 "	BaseicRequest:\n" +
			MakeBaseRequestJSONString (sUserID, sSessionID, sSessionKey, sDeviceID) +
		 "}";
	}

	public static void WebWeChatInit (String sUserID, String sSessionID, String sSessionKey, String sDeviceID)
	{
	}

	public static void Logout ()
	{
		// https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxlogout?redirect=1&type=1&skey=@crypt_1df7c02d_9effb9a7d4292af4681c79dab30b6a57

		// 被踢出后重新登录
		// https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxnewloginpage?ticket=A-1wUN8dm6D-nIJH8m8g7yfh@qrticket_0&uuid=YZzRE6skKQ==&lang=zh_CN&scan=1479978616 对比最初的登录参数，后面是新加的： &fun=new&version=v2&lang=zh_CN
		//    <error><ret>0</ret><message></message><skey>@crypt_1df7c02d_131d1d0335be6fd38333592c098a5b16</skey><wxsid>GrS6IjctQkOxs0PP</wxsid><wxuin>2100343515</wxuin><pass_ticket>T%2FduUWTWjODelhztGXZAO1b3u7S5Ddy8ya8fP%2BYhZlRjxR1ERMDXHKbaCs6x2mQP</pass_ticket><isgrayscale>1</isgrayscale></error>
	}

	class MessagePoll implements Runnable
	{

		@Override
		public void run ()
		{
		}

	}

	@Override
	public void run ()
	{
	}

	public static void main (String[] args) throws KeyManagementException, UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, ScriptException, ValidityException, ParsingException
	{
		String sLoginID = GetLoginID ();
		File f = GetLoginQRCodeImageFile (sLoginID);
		等待二维码被扫描以便登录 (sLoginID);
	}
}
