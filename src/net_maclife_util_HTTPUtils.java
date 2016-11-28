import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;

import javax.net.ssl.*;

import org.apache.commons.io.*;
import org.apache.commons.lang3.*;

public class net_maclife_util_HTTPUtils
{
	public static final Charset UTF8_CHARSET = Charset.forName ("utf-8");

	public static final int DEFAULT_CONNECT_TIMEOUT_SECOND = 30;
	public static final int DEFAULT_READ_TIMEOUT_SECOND = 30;

	// Create all-trusting host name verifier
	public static HostnameVerifier hvAllowAllHostnames =
		new HostnameVerifier()
		{
			@Override
			public boolean verify(String hostname, SSLSession session)
	    	{
	    		return true;
	    	}
		};


	// Create a trust manager that does not validate certificate chains
	static TrustManager[] tmTrustAllCertificates =
		new TrustManager[]
		{
			new X509TrustManager()
			{
				@Override
				public java.security.cert.X509Certificate[] getAcceptedIssuers()
				{
					return null;
				}
				@Override
				public void checkClientTrusted(X509Certificate[] certs, String authType)
		        {
		        }
		        @Override
				public void checkServerTrusted(X509Certificate[] certs, String authType)
		        {
		        }
		    }
		};

	// Install the all-trusting trust manager
	static SSLContext sslContext_TrustAllCertificates = null;
	static
	{
		try
		{
			sslContext_TrustAllCertificates = SSLContext.getInstance ("TLS");
			sslContext_TrustAllCertificates.init (null, tmTrustAllCertificates, new java.security.SecureRandom());
		}
		catch (java.security.NoSuchAlgorithmException e)
		{
			e.printStackTrace();
		}
		catch (java.security.KeyManagementException e)
		{
			e.printStackTrace();
		}
	}


	/**
	 * 从 URL 获取信息，并返回。
	 * 本函数参数较多，建议使用参数简化版的函数，如:
	 *  {@link #CURL(String)}
	 *  {@link #CURL_Post(String)}
	 *  {@link #CURL_ViaProxy(String, String, String, String)}
	 *  {@link #CURL_Post_ViaProxy(String, String, String, String)}

	 *  {@link #CURL_Stream(String)}
	 *  {@link #CURL_Stream_Post(String)}
	 *  {@link #CURL_Stream_ViaProxy(String, String, String, String)}
	 *  {@link #CURL_Stream_Post_ViaProxy(String, String, String, String)}
	 * <p>
	 * 返回的数据可以是 {@link String}，也可以是 {@link InputStream}，取决于 {@code isReturnContentOrStream} 的取值是 true 还是 false
	 * </p>
	 * @param sRequestMethod 请求方法，{@code GET} 或 {@code POST}，当为 {@code POST} 时，本函数会自动将 URL 中 ? 前的 URL 以及 ? 后的 QueryString 截取出来（如果有的话），并用 {@code POST} 方法请求到新的 URL (截取后的 URL)。
	 * @param sURL 网址，必须是以 http:// 开头或者以 https:// 开头的网址 (ftp:// 不行的)
	 * @param mapRequestHeaders Map&lt;String, Object&gt; 请求头。其中 Object 可以是 String 类型 （单个值），或者 List&lt;String&gt; （多个值） 类型
	 * @param arrayPostData POST 方法所要 POST 的数据。如果为 null，则从 sURL 中的 QueryString 中提取出来当作 PostData 处理
	 * @param bReturnURLConnection 是否返回 URLConnection。通常，如果需要自己读取响应头（比如 Cookie）时需要用到。
	 * @param isReturnContentOrStream 是返回 {@link String} 数据，还是 {@link InputStream}　数据。当 true　时，返回 {@link String}， false　时返回 {@link InputStream}
	 * @param sContentCharset 当 isReturnContentOrStream 为 true 时，指定网页的字符集编码。如果不指定 (null 或 空白)，则默认为 UTF-8 字符集
	 * @param isFollowRedirects 设置是否跟随重定向 (HTTP 3XX)。 true - 跟随. false - 不跟随
	 * @param nTimeoutSeconds_Connect 连接操作的超时时长，单位：秒。 如果小于等于 0，则改用默认值 DEFAULT_CONNECT_TIMEOUT_SECOND
	 * @param nTimeoutSeconds_Read 读取操作的超时时长，单位：秒。 如果小于等于 0，则改用默认值 DEFAULT_READ_TIMEOUT_SECOND
	 * @param sProxyType 代理服务器类型(不区分大小写)。 "http" - HTTP 代理， "socks" - SOCKS 代理， 其他值 - 不使用代理
	 * @param sProxyHost 代理服务器主机地址
	 * @param sProxyPort 代理服务器端口
	 * @param isIgnoreTLSCertificateValidation 是否忽略 TLS 服务器证书验证
	 * @param isIgnoreTLSHostnameValidation 是否忽略 TLS 主机名验证
	 * @param sTLSTrustStoreType (以下所有带 TLS 名称的参数都仅仅用于访问 https:// 的设置)。 TLS [信任证书/服务器证书]仓库类型。 "jks" 或 "pkcs12"，null 或 "" 被当做 "jks"
	 * @param sTLSTrustStoreFileName TLS [信任证书/服务器证书]仓库文件名 (不是证书自身的文件名)，此仓库中应当包含 信任证书/服务器证书
	 * @param sTLSTrustStorePassword TLS [信任证书/服务器证书]仓库的密码。当为 null 或 "" 时，被当做 java 默认的 "changeit" 密码来用
	 * @param sTLSClientKeyStoreType TLS [客户端证书] 仓库类型，取值与 sTLSTrustStoreType 相同。
	 * @param isTLSClientCertificate TLS [客户端证书] 仓库文件名 (不是证书自身的文件名)，此仓库中应当包含 客户端证书
	 * @param sTLSClientCertificatePassword TLS [客户端证书] 仓库的密码。当为 null 或 "" 时，被当做 java 默认的 "changeit" 密码来用
	 * @return 取决于 bReturnURLConnection isReturnContentOrStream。要么是 URLConnection (true, *)，要么是消息体 (String 类型) (false, true)，要么是消息流 (InputStream 类型) (false, false)，
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 * @throws KeyManagementException
	 * @throws KeyStoreException
	 * @throws CertificateException
	 * @throws UnrecoverableKeyException
	 */
	public static Object CURL (
			String sRequestMethod,
			String sURL,
			Map<String, Object> mapRequestHeaders,
			byte[] arrayPostData,
			boolean bReturnURLConnection,
			boolean isReturnContentOrStream,
			String sContentCharset,
			boolean isFollowRedirects,
			int nTimeoutSeconds_Connect,
			int nTimeoutSeconds_Read,

			String sProxyType,
			String sProxyHost,
			String sProxyPort,

			boolean isIgnoreTLSCertificateValidation,
			boolean isIgnoreTLSHostnameValidation,
			String sTLSTrustStoreType,
			String sTLSTrustStoreFileName,
			String sTLSTrustStorePassword,
			String sTLSClientKeyStoreType,
			InputStream isTLSClientCertificate,
			String sTLSClientCertificatePassword
			) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		// 将请求方法名规范化
		if (StringUtils.equalsIgnoreCase("POST", sRequestMethod))
		{
			sRequestMethod = "POST";

			if (arrayPostData == null)
			{
				int i = sURL.indexOf('?');
				if (i == -1)
					throw new IllegalArgumentException ("使用 POST 方法时，即没有传递 PostData，URL 中也没有查询字符串 (Query String)");

				String sQueryString = sURL.substring (i+1);
				sURL = sURL.substring (0, i);
				arrayPostData = sQueryString.getBytes ();	// sQueryString.getBytes (sContentCharset);
			}
		}
		else
			sRequestMethod = "GET";

		URL url = new URL (sURL);

		InputStream is = null;
		URLConnection http = null;

		Proxy.Type proxyType = null;
		if (StringUtils.endsWithIgnoreCase (sProxyType, "http"))
			proxyType = Proxy.Type.HTTP;
		else if (StringUtils.endsWithIgnoreCase (sProxyType, "socks"))
			proxyType = Proxy.Type.SOCKS;

		if (proxyType == null)
			http = url.openConnection ();
		else
		{
			Proxy proxy = new Proxy (proxyType, new InetSocketAddress(sProxyHost, Integer.parseInt (sProxyPort)));
			System.out.println (proxy);
			http = url.openConnection (proxy);
		}
		http.setConnectTimeout ((nTimeoutSeconds_Connect <= 0 ? DEFAULT_CONNECT_TIMEOUT_SECOND : nTimeoutSeconds_Connect) * 1000);
		http.setReadTimeout ((nTimeoutSeconds_Read <= 0 ? DEFAULT_READ_TIMEOUT_SECOND : nTimeoutSeconds_Read) * 1000);
		if (mapRequestHeaders!=null && mapRequestHeaders.size() > 0)
		{
			for (String key : mapRequestHeaders.keySet ())
			{
				Object valueOrValues = mapRequestHeaders.get (key);
				if (valueOrValues instanceof String)
					http.addRequestProperty (key, (String)valueOrValues);
				else if (valueOrValues instanceof List<?>)
				{
					List<String> listValues = (List<String>)valueOrValues;
					for (String value : listValues)
						http.addRequestProperty (key, value);
				}
			}
		}

		((HttpURLConnection)http).setInstanceFollowRedirects (isFollowRedirects);

		// 设置 https 参数
		if (url.getProtocol ().equalsIgnoreCase ("https"))
		{
			HttpsURLConnection https = (HttpsURLConnection)http;

			if (isIgnoreTLSHostnameValidation)
				https.setHostnameVerifier (hvAllowAllHostnames);
			if (isIgnoreTLSCertificateValidation)
			{
				/*
				SSLContext ctx = SSLContext.getInstance("TLS");

				// 服务器证书，如果有的话
				TrustManagerFactory tmf = null;
				if (StringUtils.isNotEmpty (sTLSTrustStoreFileName))
				{
					tmf = TrustManagerFactory.getInstance ("SunX509");
					KeyStore ksServerCertificate = KeyStore.getInstance (StringUtils.isEmpty(sTLSTrustStoreType) ? "JKS" : sTLSTrustStoreType);
					FileInputStream fisServerCertificate = new FileInputStream (sTLSTrustStoreFileName);
					ksServerCertificate.load (fisServerCertificate, (StringUtils.isEmpty (sTLSTrustStorePassword) ? "changeit" : sTLSTrustStorePassword).toCharArray());
						fisServerCertificate.close ();
					tmf.init (ksServerCertificate);
				}

				// 客户端证书，如果有的话
				KeyManagerFactory kmf = null;
				if (isTLSClientCertificate != null)
				{
					kmf = KeyManagerFactory.getInstance ("SunX509");
					KeyStore ksClientCertificate = KeyStore.getInstance ("PKCS12");
					ksClientCertificate.load (isTLSClientCertificate, sTLSClientCertificatePassword.toCharArray());
						isTLSClientCertificate.close();
					kmf.init (ksClientCertificate, sTLSClientCertificatePassword.toCharArray());
					//kmf
				}

				//ctx.init (kmf!=null ? kmf.getKeyManagers () : null, tmf!=null ? tmf.getTrustManagers () : null, new java.security.SecureRandom());
				ctx.init (null, tmTrustAllCertificates, new java.security.SecureRandom());
				https.setSSLSocketFactory (ctx.getSocketFactory());
				//*/
				https.setSSLSocketFactory (sslContext_TrustAllCertificates.getSocketFactory());
			}
		}

		if (StringUtils.equalsIgnoreCase("POST", sRequestMethod))
		{
			((HttpURLConnection)http).setRequestMethod (sRequestMethod);
			http.setDoOutput (true);
			//http.setRequestProperty ("Content-Type", "application/x-www-form-urlencoded");
			http.setRequestProperty ("Content-Length", String.valueOf (arrayPostData.length));

			DataOutputStream dos = new DataOutputStream (http.getOutputStream());
			dos.write (arrayPostData, 0, arrayPostData.length);
			dos.flush ();
			dos.close ();
		}

		if (bReturnURLConnection)
			return http;

		int iResponseCode = ((HttpURLConnection)http).getResponseCode();
		String sStatusLine = http.getHeaderField(0);	// HTTP/1.1 200 OK、HTTP/1.1 404 Not Found

		int iMainResponseCode = iResponseCode/100;
		if (iMainResponseCode==2)
		{
			is = http.getInputStream ();
			if (isReturnContentOrStream)
			{
				Charset cs =  UTF8_CHARSET;
				if (StringUtils.isNotEmpty (sContentCharset))
					cs = Charset.forName (sContentCharset);
				return IOUtils.toString (is, cs);
			}
			else
				return is;
		}
		else
		{
			String s = "";
			try
			{
				if (iMainResponseCode >= 4)
					is = ((HttpURLConnection)http).getErrorStream();
				else
					is = http.getInputStream();

				if (StringUtils.isEmpty (sContentCharset))
					s = IOUtils.toString (is, UTF8_CHARSET);
				else
					s = IOUtils.toString (is, sContentCharset);
			}
			catch (Exception e)
			{
				//e.printStackTrace ();
				System.err.println (e);
			}

			throw new IllegalStateException ("HTTP 响应不是 2XX: " + sStatusLine + "\n" + s);
		}
	}

	/**
	 * 直接返回 URLConnection，而不是读取其消息体。通常，如果需要自己读取响应头（比如 Cookie），则可能需要用到这个函数
	 * @param sURL
	 * @return
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 * @throws KeyManagementException
	 * @throws KeyStoreException
	 * @throws CertificateException
	 * @throws UnrecoverableKeyException
	 */
	public static URLConnection CURL_Connection (String sURL) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return (URLConnection)CURL (null, sURL, null, null, true, true, null, true, 0, 0,
				null, null, null,
				true, true, null, null, null, null, null, null
			);
	}

	/**
	 * 最简化版的 CURL - GET。
	 * <ul>
	 * 	<li>GET 方法</li>
	 * 	<li>返回 Content 而不是 InputStream</li>
	 * 	<li>默认字符集(UTF-8)</li>
	 * 	<li>跟随重定向</li>
	 * 	<li>默认超时时长 DEFAULT_CONNECT_TIMEOUT_SECOND / DEFAULT_READ_TIMEOUT_SECOND 秒</li>
	 * 	<li>不用代理</li>
	 * 	<li>不设置 https 证书(服务器端 以及 客户端)</li>
	 * 	<li>不验证 https 服务器证书有效性</li>
	 * 	<li>不验证 https 主机名有效性</li>
	 * </ul>
	 * @param sURL 网址
	 * @return String Content
	 */
	public static String CURL (String sURL, Map<String, Object> mapRequestHeaders, int nTimeoutSeconds) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return (String)CURL (null, sURL, mapRequestHeaders, null, false, true, null, true, nTimeoutSeconds, nTimeoutSeconds,
				null, null, null,
				true, true, null, null, null, null, null, null
			);
	}
	public static String CURL (String sURL, Map<String, Object> mapRequestHeaders) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return CURL (sURL, mapRequestHeaders, 0);
	}
	public static String CURL (String sURL, int nTimeoutSeconds) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return CURL (sURL, (Map<String, Object>)null, nTimeoutSeconds);
	}
	public static String CURL (String sURL) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return CURL (sURL, 0);
	}

	/**
	 * 简化版的 CURL - GET content decoded by specific charset。
	 * 除了增加了字符集编码设置以外，其他与 {@link #CURL(String)} 相同
	 * @param sURL 网址
	 * @param sCharSet 返回的字符串内容的字符集编码
	 * @param nTimeoutSeconds 超时时长（秒）
	 * @return String Content
	 */
	public static String CURL (String sURL, String sCharSet, int nTimeoutSeconds) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return (String)CURL (null, sURL, null, null, false, true, sCharSet, true, nTimeoutSeconds, nTimeoutSeconds,
				null, null, null,
				true, true, null, null, null, null, null, null
			);
	}
	public static String CURL (String sURL, String sCharSet) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return CURL (sURL, sCharSet, 0);
	}

	/**
	 * 最简化版的 CURL - Post。
	 * 除了请求方法换为 POST 以外，其他与 {@link #CURL(String)} 相同
	 * @param sURL 网址
	 * @return String Content
	 */
	public static String CURL_Post (String sURL, byte[] arrayPostData, int nTimeoutSeconds) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return (String)CURL ("POST", sURL, null, arrayPostData, false, true, null, true, nTimeoutSeconds, nTimeoutSeconds,
				null, null, null,
				true, true, null, null, null, null, null, null
			);
	}
	public static String CURL_Post (String sURL, byte[] arrayPostData) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return CURL_Post (sURL, arrayPostData, 0);
	}
	public static String CURL_Post (String sURL) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return CURL_Post (sURL, null, 0);
	}

	/**
	 * 参数简化版的 CURL - GET content decoded by specific charset via Proxy。
	 * 除了增加了字符集编码设置、增加了代理服务器以外，其他与 {@link #CURL(String)} 相同
	 * @param sURL 网址
	 * @param sCharSet 返回的字符串内容的字符集编码
	 * @return String Content
	 */
	public static String CURL_ViaProxy (String sURL, String sCharSet, int nTimeoutSeconds, String sProxyType, String sProxyHost, String sProxyPort) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return (String)CURL (null, sURL, null, null, false, true, sCharSet, true, nTimeoutSeconds, nTimeoutSeconds,
				sProxyType, sProxyHost, sProxyPort,
				true, true, null, null, null, null, null, null
			);
	}
	public static String CURL_ViaProxy (String sURL, String sProxyType, String sProxyHost, String sProxyPort) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return CURL_ViaProxy (sURL, null, 0, sProxyType, sProxyHost, sProxyPort);
	}

	/**
	 * 参数简化版的 CURL - POST via Proxy。
	 * 除了请求方法改为 POST、增加了代理服务器以外，其他与 {@link #CURL(String)} 相同
	 * @param sURL 网址
	 * @return String Content
	 */
	public static String CURL_Post_ViaProxy (String sURL, byte[] arrayPostData, int nTimeoutSeconds, String sProxyType, String sProxyHost, String sProxyPort) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return (String)CURL ("POST", sURL, null, arrayPostData, false, true, null, true, nTimeoutSeconds, nTimeoutSeconds,
				sProxyType, sProxyHost, sProxyPort,
				true, true, null, null, null, null, null, null
			);
	}
	public static String CURL_Post_ViaProxy (String sURL,  byte[] arrayPostData, String sProxyType, String sProxyHost, String sProxyPort) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return CURL_Post_ViaProxy (sURL, arrayPostData, 0, sProxyType, sProxyHost, sProxyPort);
	}
	public static String CURL_Post_ViaProxy (String sURL, String sProxyType, String sProxyHost, String sProxyPort) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return CURL_Post_ViaProxy (sURL, null, 0, sProxyType, sProxyHost, sProxyPort);
	}

	/**
	 * 参数简化版的 CURL - GET InputStream。
	 * 除了返回的是 InputStream 而不是 Content，其他与 {@link #CURL(String)} 相同
	 * @param sURL 网址
	 * @return Input Stream
	 */
	public static InputStream CURL_Stream (String sURL, int nTimeoutSeconds) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return (InputStream)CURL (null, sURL, null, null, false, false, null, true, nTimeoutSeconds, nTimeoutSeconds,
				null, null, null,
				true, true, null, null, null, null, null, null
			);
	}
	public static InputStream CURL_Stream (String sURL) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return CURL_Stream (sURL, 0);
	}

	/**
	 * 参数简化版的 CURL - POST InputStream。
	 * 除了请求方法改为 POST、返回的是 InputStream 而不是 Content，其他与 {@link #CURL(String)} 相同
	 * @param sURL 网址
	 * @return Input Stream
	 */
	public static InputStream CURL_Post_Stream (String sURL, Map<String, Object> mapRequestHeaders, byte[] arrayPostData, int nTimeoutSeconds) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return (InputStream)CURL ("POST", sURL, mapRequestHeaders, arrayPostData, false, false, null, true, nTimeoutSeconds, nTimeoutSeconds,
				null, null, null,
				true, true, null, null, null, null, null, null
			);
	}
	public static InputStream CURL_Post_Stream (String sURL, Map<String, Object> mapRequestHeaders, byte[] arrayPostData) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return CURL_Post_Stream (sURL, mapRequestHeaders, arrayPostData, 0);
	}
	public static InputStream CURL_Post_Stream (String sURL, byte[] arrayPostData) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return CURL_Post_Stream (sURL, null, arrayPostData, 0);
	}
	public static InputStream CURL_Post_Stream (String sURL) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return CURL_Post_Stream (sURL, null, null, 0);
	}

	/**
	 * 参数简化版的 CURL - GET InputStream via Proxy。
	 * 除了增加了代理服务器、返回的是 InputStream 而不是 Content，其他与 {@link #CURL(String)} 相同
	 * @param sURL 网址
	 * @return Input Stream
	 */
	public static InputStream CURL_Stream_ViaProxy (String sURL, int nTimeoutSeconds, String sProxyType, String sProxyHost, String sProxyPort) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return (InputStream)CURL (null, sURL, null, null, false, false, null, true, nTimeoutSeconds, nTimeoutSeconds,
				sProxyType, sProxyHost, sProxyPort,
				true, true, null, null, null, null, null, null
			);
	}
	public static InputStream CURL_Stream_ViaProxy (String sURL, String sProxyType, String sProxyHost, String sProxyPort) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return CURL_Stream_ViaProxy (sURL, 0, sProxyType, sProxyHost, sProxyPort);
	}

	/**
	 * 参数简化版的 CURL - POST InputStream via Proxy。
	 * 除了请求方法改为 POST、增加了代理服务器、返回的是 InputStream 而不是 Content，其他与 {@link #CURL(String)} 相同
	 * @param sURL 网址
	 * @return Input Stream
	 */
	public static InputStream CURL_Post_Stream_ViaProxy (String sURL, byte[] arrayPostData, int nTimeoutSeconds, String sProxyType, String sProxyHost, String sProxyPort) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return (InputStream)CURL ("POST", sURL, null, arrayPostData, false, false, null, true, nTimeoutSeconds, nTimeoutSeconds,
				sProxyType, sProxyHost, sProxyPort,
				true, true, null, null, null, null, null, null
			);
	}
	public static InputStream CURL_Post_Stream_ViaProxy (String sURL, byte[] arrayPostData, String sProxyType, String sProxyHost, String sProxyPort) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return CURL_Post_Stream_ViaProxy (sURL, arrayPostData, 0, sProxyType, sProxyHost, sProxyPort);
	}
	public static InputStream CURL_Post_Stream_ViaProxy (String sURL, String sProxyType, String sProxyHost, String sProxyPort) throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException, UnrecoverableKeyException
	{
		return CURL_Post_Stream_ViaProxy (sURL, null, 0, sProxyType, sProxyHost, sProxyPort);
	}
}
