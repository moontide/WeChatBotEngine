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

	// 网络图片文字识别
	public static final String BAIDU_OCR_URL__WebImage        = "https://aip.baidubce.com/rest/2.0/ocr/v1/webimage";

	// 手写文字识别 【此接口需要您在页面中提交合作咨询开通权限】
	// 对手写中文汉字进行识别
	public static final String BAIDU_OCR_URL__HandWriting     = "https://aip.baidubce.com/rest/2.0/ocr/v1/handwriting";


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

	// 护照识别 【此接口需要您在页面中提交合作咨询开通权限】
	// 支持对中国大陆居民护照的资料页进行结构化识别，包含国家码、姓名、性别、护照号、出生日期、签发日期、有效期至、签发地点。
	public static final String BAIDU_OCR_URL__Passport        = "https://aip.baidubce.com/rest/2.0/ocr/v1/passport";

	// 名片识别 【此接口需要您在页面中提交合作咨询开通权限】
	// 提供对各类名片的结构化识别功能，提取姓名、邮编、邮箱、电话、网址、地址、手机号字段
	public static final String BAIDU_OCR_URL__BusinessCard    = "https://aip.baidubce.com/rest/2.0/ocr/v1/business_card";

	// 表格文字识别(异步接口) - 提交请求接口
	public static final String BAIDU_OCR_URL__Form_Async__Request   = "https://aip.baidubce.com/rest/2.0/solution/v1/form_ocr/request";
	// 表格文字识别(异步接口) - 获取结果接口
	public static final String BAIDU_OCR_URL__Form_Async__GetResult = "https://aip.baidubce.com/rest/2.0/solution/v1/form_ocr/get_request_result";
	//public static final String BAIDU_OCR_URL__Form_Async__Request   = "https://aip.baidubce.com/api/v1/solution/form_ocr/request";
	//public static final String BAIDU_OCR_URL__Form_Async__GetResult = "https://aip.baidubce.com/api/v1/solution/form_ocr/get_request_result";

	// 表格文字识别(同步接口)
	// 【此接口需要您在页面中提交合作咨询开通权限】
	// 自动识别表格线及表格内容，结构化输出表头、表尾及每个单元格的文字内容。
	// 本接口为同步接口，相比于异步接口，本接口在请求后会实时返回请求结果，建议针对复杂的表格使用异步接口，以减少识别时间过长导致的超时报错。
	public static final String BAIDU_OCR_URL__Form            = "https://aip.baidubce.com/rest/2.0/ocr/v1/form";

	// 通用票据识别
	public static final String BAIDU_OCR_URL__Receipt         = "https://aip.baidubce.com/rest/2.0/ocr/v1/receipt";

	// 增值税发票识别 【此接口需要您在页面中提交合作咨询开通权限】
	// 识别并结构化返回增值税发票的各个字段及其对应值，包含了发票基础信息9项，货物相关信息12项，购买方/销售方的名称、识别号、地址电话、开户行及账号，共29项结构化字段。
	public static final String BAIDU_OCR_URL__VatInvoice      = "https://aip.baidubce.com/rest/2.0/ocr/v1/vat_invoice";

	// 二维码识别 【此接口需要您在页面中提交合作咨询开通权限】
	// 识别条形码、二维码中包含的URL或其他信息内容
	public static final String BAIDU_OCR_URL__QRCode          = "https://aip.baidubce.com/rest/2.0/ocr/v1/qrcode";

	// 数字识别 【此接口需要您在页面中提交合作咨询开通权限】
	// 对图像中的阿拉伯数字进行识别提取，适用于快递单号、手机号、充值码提取等场景
	public static final String BAIDU_OCR_URL__DigitNumber     = "https://aip.baidubce.com/rest/2.0/ocr/v1/numbers";

	// 彩票识别 【此接口需要您在页面中提交合作咨询开通权限】
	// 对大乐透、双色球彩票进行识别，并返回识别结果
	public static final String BAIDU_OCR_URL__Lottery         = "https://aip.baidubce.com/rest/2.0/ocr/v1/lottery";

	// 自定义模版文字识别
	public static final String BAIDU_OCR_URL__Customized      = "https://aip.baidubce.com/rest/2.0/solution/v1/iocr/recognise";


	static String sBaiduCloudAppID           = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.baidu.ocr.app.id");
	static String sBaiduCloudAppKey          = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.baidu.ocr.app.key");
	static String sBaiduCloudAppPassword     = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.baidu.ocr.app.password");
	static String sBaiduOAuthAccessTokenFileInJSONFormat = net_maclife_wechat_http_BotApp.cacheDirectory + "/" + net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.baidu.ocr.accessTokenFile");

	/**
	 * 几个文字接口公用的（几个“通用文字识别”、网络图片文字识别）
	 * @param fMedia
	 * @return
	 * @throws Exception
	 */
	public static JsonNode ProcessOCR_Common (File fMedia, String sOCR_URL, String sAPIName) throws IOException, InterruptedException, ExecutionException
	{
		String sAccessToken = net_maclife_util_BaiduCloud.GetBaiduAccessToken (sBaiduCloudAppKey, sBaiduCloudAppPassword, sBaiduOAuthAccessTokenFileInJSONFormat);
		if (StringUtils.isEmpty (sAccessToken))
			return null;

		byte[] arrayImageData = IOUtils.toByteArray (new FileInputStream (fMedia));
		String sImageBase64 = Base64.encodeBase64String (arrayImageData);	//Base64.encodeBase64URLSafeString (arrayImageData);
//System.out.println (sImageBase64);
		//String sImageBase64_URLEncoded = URLEncoder.encode (sImageBase64, net_maclife_wechat_http_BotApp.utf8);
//System.out.println (sImageBase64_URLEncoded);
		String sURL = sOCR_URL + "?access_token=" + sAccessToken + "";
		org.jsoup.Connection jsoup_conn = null;
		jsoup_conn = org.jsoup.Jsoup.connect (sURL)
				.header ("Content-Type", "application/x-www-form-urlencoded")
				.ignoreContentType (true)
				.data ("image", sImageBase64)	// 和url二选一
				// .data ("url", sImageURL) // 和image二选一
				//.data ("language_type", "CHN_ENG")	// 识别语言类型，默认为CHN_ENG。可选值包括：
								// - CHN_ENG：中英文混合；
								// - ENG：英文；
								// - POR：葡萄牙语；
								// - FRE：法语；
								// - GER：德语；
								// - ITA：意大利语；
								// - SPA：西班牙语；
								// - RUS：俄语；
								// - JAP：日语；
								// - KOR：韩语

				//.data ("detect_direction", "false")	// boolean，true、false，是否检测图像朝向，默认不检测，即：false。朝向是指输入图像是正常方向、逆时针旋转90/180/270度。可选值包括:
								// - true：检测朝向；
								// - false：不检测朝向。
				//.data ("detect_language", "false")	// string，true、false，是否检测语言，默认不检测。当前支持（中文、英语、日语、韩语）
				//.data ("probability", "false")	// string，true、false，是否返回识别结果中每一行的置信度
				;
		Document doc = jsoup_conn.post ();
		JsonNode jsonResponse = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (doc.text ());
System.out.println (jsonResponse);
		if (jsonResponse.get ("error_code") != null)
		{
			net_maclife_wechat_http_BotApp.logger.warning ("百度 OCR " + sAPIName + " 返回失败：" + net_maclife_wechat_http_BotApp.GetJSONText (jsonResponse, "error_code") + " " + net_maclife_wechat_http_BotApp.GetJSONText (jsonResponse, "error_msg"));
			return null;
		}
		assert (jsonResponse.get ("words_result") != null && jsonResponse.get ("words_result").isArray ());
		JsonNode jsonWordsResult = jsonResponse.get ("words_result");
		assert (jsonWordsResult.size () > 0);
		for (int i=0; i<jsonWordsResult.size (); i++)
		{
			JsonNode jsonWord = jsonWordsResult.get (i);
System.out.println (net_maclife_wechat_http_BotApp.GetJSONText (jsonWord, "words"));
		}
		return jsonWordsResult;
	}

	/**
	 * 通用文字识别
	 * @param fMedia
	 * @return
	 */
	public static JsonNode ProcessOCR_GeneralBasic (File fMedia) throws IOException, InterruptedException, ExecutionException
	{
		return ProcessOCR_Common (fMedia, BAIDU_OCR_URL__GeneralBasic, "通用文字识别");
	}

	/**
	 * 通用文字识别（高精度版）
	 * @param fMedia
	 * @return
	 */
	public static JsonNode ProcessOCR_AccurateBasic (File fMedia) throws IOException, InterruptedException, ExecutionException
	{
		return ProcessOCR_Common (fMedia, BAIDU_OCR_URL__AccurateBasic, "通用文字识别（高精度版）");
	}

	/**
	 * 通用文字识别（含位置信息版）
	 * @param fMedia
	 * @return
	 */
	public static JsonNode ProcessOCR_General (File fMedia) throws IOException, InterruptedException, ExecutionException
	{
		return ProcessOCR_Common (fMedia, BAIDU_OCR_URL__General, "通用文字识别（含字符位置信息版）");
	}

	/**
	 * 通用文字识别（含位置高精度版）
	 * @param fMedia
	 * @return
	 */
	public static JsonNode ProcessOCR_Accurate (File fMedia) throws IOException, InterruptedException, ExecutionException
	{
		return ProcessOCR_Common (fMedia, BAIDU_OCR_URL__Accurate, "通用文字识别（含字符位置高精度版）");
	}

	/**
	 * 通用文字识别（含生僻字版）。
	 * <em>注意：这个接口并不提供免费使用。调用时会报错： 百度 OCR 通用文字识别（含生僻字版） 返回失败：18 Open api qps request limit reached</em>
	 * @param fMedia
	 * @return
	 */
	public static JsonNode ProcessOCR_GeneralEnhanced (File fMedia) throws IOException, InterruptedException, ExecutionException
	{
		return ProcessOCR_Common (fMedia, BAIDU_OCR_URL__GeneralEnhanced, "通用文字识别（含生僻字版）");
	}

	/**
	 * 网络图片文字识别
	 * @param fMedia
	 * @return
	 */
	public static JsonNode ProcessOCR_WebImage (File fMedia) throws IOException, InterruptedException, ExecutionException
	{
		return ProcessOCR_Common (fMedia, BAIDU_OCR_URL__WebImage, "网络图片文字识别");
	}

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
		//String sImageBase64_URLEncoded = URLEncoder.encode (sImageBase64, net_maclife_wechat_http_BotApp.utf8);
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

		Callable<JsonNode> taskAsyncGetResult = new FormOCR_AsyncGetResultTask (sRequestID);
		JsonNode jsonOCRResult = net_maclife_wechat_http_BotApp.executor.submit (taskAsyncGetResult).get ();
		return jsonOCRResult;
	}

	static class FormOCR_AsyncGetResultTask implements Callable<JsonNode>
	{
		String sRequestID;
		public FormOCR_AsyncGetResultTask (String sRequestID)
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

		//ProcessOCR_GeneralBasic (fMedia);
		//ProcessOCR_AccurateBasic (fMedia);
		//ProcessOCR_General (fMedia);
		ProcessOCR_Accurate (fMedia);
		//ProcessOCR_GeneralEnhanced (fMedia);
		//ProcessOCR_WebImage (fMedia);

		//ProcessOCR_Form_Async (fMedia);
	}
}
