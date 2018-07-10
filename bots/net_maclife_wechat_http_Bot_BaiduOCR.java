import java.io.*;
import java.net.*;
import java.util.concurrent.*;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.*;
import org.apache.commons.lang3.*;
import org.jsoup.nodes.*;

import com.fasterxml.jackson.databind.*;

public class net_maclife_wechat_http_Bot_BaiduOCR extends net_maclife_wechat_http_Bot
{
	// 通用文字识别
	public static final String BAIDU_OCR_URL__GeneralBasic    = "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic";

	// 通用文字识别（高精度版）
	public static final String BAIDU_OCR_URL__AccurateBasic   = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic";

	// 通用文字识别（含位置信息版）
	public static final String BAIDU_OCR_URL__General         = "https://aip.baidubce.com/rest/2.0/ocr/v1/general";

	// 通用文字识别（含位置高精度版）
	public static final String BAIDU_OCR_URL__Accurate        = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate";

	// 通用文字识别（含生僻字版）
	public static final String BAIDU_OCR_URL__GeneralEnhanced = "https://aip.baidubce.com/rest/2.0/ocr/v1/general_enhanced";

	// 身份证识别
	public static final String BAIDU_OCR_URL__IDCard          = "https://aip.baidubce.com/rest/2.0/ocr/v1/idcard";

	// 银行卡识别
	public static final String BAIDU_OCR_URL__BankCard        = "https://aip.baidubce.com/rest/2.0/ocr/v1/bankcard";

	// 驾驶证识别
	public static final String BAIDU_OCR_URL__DriverLicense   = "https://aip.baidubce.com/rest/2.0/ocr/v1/driving_license";

	// 行驶证识别
	public static final String BAIDU_OCR_URL__VehicleLicense  = "https://aip.baidubce.com/rest/2.0/ocr/v1/vehicle_license";

	// 车牌识别
	public static final String BAIDU_OCR_URL__LicensePlate    = "https://aip.baidubce.com/rest/2.0/ocr/v1/license_plate";

	// 营业执照识别
	public static final String BAIDU_OCR_URL__BusinessLicense = "https://aip.baidubce.com/rest/2.0/ocr/v1/business_license";

	// 表格文字识别(异步接口) - 提交请求接口
	public static final String BAIDU_OCR_URL__Form_Async__Request   = "https://aip.baidubce.com/rest/2.0/solution/v1/form_ocr/request";
	// 表格文字识别(异步接口) - 获取结果接口
	public static final String BAIDU_OCR_URL__Form_Async__GetResult = "https://aip.baidubce.com/rest/2.0/solution/v1/form_ocr/get_request_result";
	//public static final String BAIDU_OCR_URL__Form_Async__Request   = "https://aip.baidubce.com/api/v1/solution/form_ocr/request";
	//public static final String BAIDU_OCR_URL__Form_Async__GetResult = "https://aip.baidubce.com/api/v1/solution/form_ocr/get_request_result";

	// 通用票据识别
	public static final String BAIDU_OCR_URL__Receipt         = "https://aip.baidubce.com/rest/2.0/ocr/v1/receipt";


	static String sBaiduCloudAppID           = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.baidu.ocr.app.id");
	static String sBaiduCloudAppKey          = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.baidu.ocr.app.key");
	static String sBaiduCloudAppPassword     = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.baidu.ocr.app.password");
	static String sBaiduOAuthAccessTokenFileInJSONFormat = net_maclife_wechat_http_BotApp.cacheDirectory + "/" + net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.baidu.ocr.accessTokenFile");
	/**
	 * 表格文字识别(异步接口)。
	 * 注意，调用方直接读取返回值即可，不需要做异步处理，本函数自己做了异步处理 -- 调用者如同使用同步接口一样，无需关心异步返回结果问题的处理
	 * @param fMedia
	 * @return
	 * @throws ExecutionException
	 * @throws InterruptedException
	 */
	public static JsonNode ProcessOCR_Form_Async (File fMedia) throws IOException, InterruptedException, ExecutionException
	{
		String sAccessToken = net_maclife_util_BaiduCloud.GetBaiduAccessToken (sBaiduCloudAppKey, sBaiduCloudAppPassword, sBaiduOAuthAccessTokenFileInJSONFormat);
		if (StringUtils.isEmpty (sAccessToken))
			return null;

		byte[] arrayImageData = IOUtils.toByteArray (new FileInputStream (fMedia));
		String sImageBase64 = Base64.encodeBase64String (arrayImageData);	//Base64.encodeBase64URLSafeString (arrayImageData);
//System.out.println (sImageBase64);
		String sImageBase64_URLEncoded = URLEncoder.encode (sImageBase64, net_maclife_wechat_http_BotApp.utf8);
//System.out.println (sImageBase64_URLEncoded);
		String sURL = BAIDU_OCR_URL__Form_Async__Request + "?access_token=" + sAccessToken + "";
		org.jsoup.Connection jsoup_conn = null;
		jsoup_conn = org.jsoup.Jsoup.connect (sURL)
				.header ("Content-Type", "application/x-www-form-urlencoded")
				.ignoreContentType (true)
				.data ("image", sImageBase64)
				;
		Document doc = jsoup_conn.post ();
		JsonNode jsonResponse = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (doc.text ());
System.out.println (jsonResponse);
		if (jsonResponse.get ("error_code") != null)
		{
			net_maclife_wechat_http_BotApp.logger.warning ("百度 OCR 表格文字识别(异步接口) 发出请求后返回失败：" + net_maclife_wechat_http_BotApp.GetJSONText (jsonResponse, "error_code") + " " + net_maclife_wechat_http_BotApp.GetJSONText (jsonResponse, "error_msg"));
			return null;
		}
		assert (jsonResponse.get ("request") != null && jsonResponse.get ("request").isArray ());
		JsonNode jsonResult = jsonResponse.get ("result");
		assert (jsonResult.size () > 0);
		String sRequestID = net_maclife_wechat_http_BotApp.GetJSONText (jsonResult.get (0), "request_id");

		Callable<JsonNode> processor = new Form_Async_Processor (sRequestID);
		JsonNode jsonOCRResult = net_maclife_wechat_http_BotApp.executor.submit (processor).get ();
		return jsonOCRResult;
	}

	static class Form_Async_Processor implements Callable<JsonNode>
	{
		String sRequestID;
		public Form_Async_Processor (String sRequestID)
		{
			this.sRequestID = sRequestID;
		}

		@Override
		public JsonNode call () throws Exception
		{
			try
			{
				int nProgressPercent = 0;
				do
				{
					String sAccessToken = net_maclife_util_BaiduCloud.GetBaiduAccessToken (sBaiduCloudAppKey, sBaiduCloudAppPassword, sBaiduOAuthAccessTokenFileInJSONFormat);
					if (StringUtils.isEmpty (sAccessToken))
						return null;
					String sURL = BAIDU_OCR_URL__Form_Async__GetResult + "?access_token=" + sAccessToken + "";
					org.jsoup.Connection jsoup_conn = null;
					jsoup_conn = org.jsoup.Jsoup.connect (sURL)
							.header ("Content-Type", "application/x-www-form-urlencoded")
							.ignoreContentType (true)
							.data ("request_id", sRequestID)
							.data ("result_type", "json")	// request_type 若不设置，则默认为 "excel"，但这里我们需要的是 "json"
							;
					Document doc = jsoup_conn.post ();
					JsonNode jsonResponse = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (doc.text ());
System.out.println (jsonResponse);
					if (jsonResponse.get ("error_code") != null)
					{
						net_maclife_wechat_http_BotApp.logger.warning ("百度 OCR 表格文字识别(异步接口) 获取请求结果返回失败：" + net_maclife_wechat_http_BotApp.GetJSONText (jsonResponse, "error_code") + " " + net_maclife_wechat_http_BotApp.GetJSONText (jsonResponse, "error_msg"));
						return null;
					}
					assert (jsonResponse.get ("result") != null);
					JsonNode jsonRequest = jsonResponse.get ("result");
					nProgressPercent = net_maclife_wechat_http_BotApp.GetJSONInt (jsonRequest, "percent");
					if (nProgressPercent == 100)
					{
						return jsonRequest.get ("result_data");
					}

					TimeUnit.SECONDS.sleep (2);
				} while (nProgressPercent != 100);
			}
			catch (Exception e)
			{
				e.printStackTrace ();
			}
			return null;
		}
	}

	public static void main (String[] args) throws Exception
	{
		if (args.length < 1)
		{
			System.err.println ("需要指定一个图片文件 (.jpg 或 .png 或 .bmp 格式的图片文件)");
			return;
		}
		String sFileName = args[0];
		File fMedia = new File (sFileName);
		ProcessOCR_Form_Async (fMedia);
	}
}
