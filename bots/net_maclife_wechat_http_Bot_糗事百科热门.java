import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

/**
 * 获取糗事百科热门信息。目前，不打算用 Bot 形式来处理糗事百科，而仅仅当做 Relay Bot 的一个外部消息源。
 * @author liuyan
 * @deprecated 除了个别网站（比如需要先获取一个网页、取得 Cookie 或者某数值，再用该数值访问第二个网页），其他所有抓取网页的 Bot，都建议改为 net_maclife_wechat_http_Bot_WebSoup 的模板来处理。
 *
 */
@Deprecated
public class net_maclife_wechat_http_Bot_糗事百科热门 extends net_maclife_wechat_http_Bot
{
	/**
	 * Map 中的 key 名称：
	 * - mode 模式，取值：
	 *   '='       等于（不区分大小写）;
	 *   'regexp'  规则表达式
	 * - expression : 表达式； 当 mode 为 '=' 时，这里就应该给出具体发帖人姓名; 当 mode 为 'regexp' 时，这里应该给出规则表达式
	 * - reason : 加入黑名单的原因
	 */
	static List<Map<String, String>> listBlackList = new ArrayList<Map<String, String>>();

	public static String 获取糗事百科热门 (int nMax) throws IOException
	{
		String sURL = "http://www.qiushibaike.com/8hr/page/";	// http://www.qiushibaike.com/8hr/page/${p=1}
		Document doc = null;
		//org.jsoup.Connection jsoup_conn = null;
		doc = Jsoup.connect (sURL)
			.userAgent ("IE")
			.timeout (net_maclife_util_HTTPUtils.DEFAULT_READ_TIMEOUT_SECOND * 1000)
			.get ();

		StringBuilder sb = new StringBuilder ();
		Elements 糗事列表 = doc.select ("div[id^=qiushi_tag_]");
		if (糗事列表.size () == 0)
			System.exit (5);	// 5 - 无内容

		int nNotBanned = 0;
		for (int i=0; nNotBanned<nMax; i++)
		{
			Element e = 糗事列表.get (i);

			Elements 好评量列表 = e.select ("a[id^=up-] > span");
			Elements 差评量列表 = e.select ("a[id^=dn-] > span");
			Elements 发帖人列表 = e.select ("div.author h2");
			Element 热评 = e.select ("div.cmtMain").first ();
			String s发帖人 = 发帖人列表.text ();
			String sBlackListReason = GetBanReason (s发帖人);
			if (sBlackListReason != null)
			{
System.err.println ("【" + s发帖人 + "】 为黑名单用户，原因： " + sBlackListReason);
				continue;
			}

			nNotBanned ++;
			sb.append (i+1);
			sb.append (" ");

			sb.append (e.select ("li.comments a").first ().absUrl ("href"));
			sb.append ("\n");
			sb.append (e.select ("div.content").text());

			Elements 图片列表 = e.select ("div.thumb img");
			if (图片列表.size () > 0)
			{
				sb.append ("\n");
				String sImageURL = 图片列表.attr ("src");
				if (StringUtils.startsWithIgnoreCase (sImageURL, "http:"))
					sb.append (sImageURL);
				else
					sb.append ("http:" + sImageURL);
			}

			// 视频目前已取不到，需要通过单独的 http 请求获取 json 数据获取
			//Elements 视频列表 = e.select ("div.thumb img");
			//if (视频列表.size () > 0)
			//{
			//	sb.append ("\n");
			//	sb.append (视频列表.attr ("src"));
			//}

			int i好笑量 = 0;
			int i不好笑量 = 0;
			double 好笑率 = 0;
			try
			{
				i好笑量 = Integer.parseInt (StringUtils.trimToEmpty (好评量列表.text ()));
				i不好笑量 = Integer.parseInt (StringUtils.trimToEmpty (差评量列表.text ()));
				好笑率 = ((double)i好笑量) / (i好笑量 + Math.abs (i不好笑量));
			}
			catch (Exception ex)
			{
				ex.printStackTrace ();
			}
			sb.append ("\n");
			//sb.append ("[呲牙]");
			sb.append ("😁");
			sb.append ("+");
			sb.append (好评量列表.text ());
			sb.append (" (");
			sb.append ((int)(好笑率 * 100));
			sb.append ("%)");
			sb.append ("  ");
			//sb.append ("<span class=\\\"emoji emoji1f612\\\"></span>");	// 虽然收到的表情的文字是这样的，貌似相同的文字发出去就不行
			//sb.append ("[撇嘴]");
			sb.append ("😞");
			sb.append (差评量列表.text ());
			sb.append ("  作者: ");
			sb.append (发帖人列表.text ());
			if (热评 != null)
			{
				sb.append ("\n\n");
				//sb.append ("热评：");
				sb.append ("  💬 ");	// 💬💭
				sb.append (热评.text ());
				//sb.append (StringUtils.remove (热评.text (), '\u0001'));	// 剔除掉 0x01 字符，否则 jackson 报错： com.fasterxml.jackson.core.JsonParseException: Illegal unquoted character ((CTRL-CHAR, code 1)): has to be escaped using backslash to be included in string value 。貌似表情代码 (如 '[doge]') 的前面就是 0x01 字符
			}
			sb.append ("\n\n");
		}

		return sb.toString ();
	}

	public static void ReadBanListFromFile_ByName (String sBlackListFileName)
	{
		File f = new File (sBlackListFileName);
		if (! f.exists ())
			return;

		try
		{
			BufferedReader br = new BufferedReader (new FileReader (f));
			int n = 0;
			while (true)
			{
				String sLine = br.readLine ();
				n++;
				if (sLine == null)
					break;

				if (StringUtils.startsWithAny (sLine, "#", "//"))	// 备注行，不做处理
					continue;

				String[] arrayColumns = sLine.split ("	", 3);	// <TAB> 分隔符
				if (arrayColumns==null || arrayColumns.length < 2)
				{
System.err.println ("第 " + n + " 行不存在，或者列数小于 2");
					continue;
				}

				String sMode = arrayColumns[0];
				String sExpression = arrayColumns[1];
				String sReason = "";
				if (arrayColumns.length > 2)
				{
					sReason = arrayColumns[2];
				}

				// 检查数据有效性
				if (StringUtils.equalsAnyIgnoreCase (sMode, "=", "%", " %", "%%", "*", "regexp"))
				{
					Map<String, String> mapBlack = new HashMap <String, String> ();
					mapBlack.put ("mode", sMode);
					mapBlack.put ("expression", sExpression);
					mapBlack.put ("reason", sReason);
					listBlackList.add (mapBlack);
				}
				else
				{
System.err.println ("第 " + n + " 行第一列是非法的“模式”：" + sMode);
					continue;
				}
			}
			br.close ();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public static void ReadBanListFromFile_GroupByReason (String sBlackListFileName)
	{
		File f = new File (sBlackListFileName);
		if (! f.exists ())
			return;

		try
		{
			BufferedReader br = new BufferedReader (new FileReader (f));
			int n = 0;
			while (true)
			{
				String sLine = br.readLine ();
				n++;
				if (sLine == null)
					break;

				sLine = StringUtils.trimToEmpty (sLine);
				if (StringUtils.isEmpty (sLine))	// 空行、空白字符行，不做处理
					continue;

				if (StringUtils.startsWithAny (sLine, "#", "//"))	// 备注行，不做处理
					continue;

				String[] arrayColumns = sLine.split ("	", 3);	// <TAB> 分隔符
				if (arrayColumns==null || arrayColumns.length < 2)
				{
System.err.println ("第 " + n + " 行不存在，或者列数小于 2");
					continue;
				}

				String sMode = arrayColumns[0];
				String sExpression = arrayColumns[1];
				String sReason = "";
				if (arrayColumns.length > 2)
				{
					sReason = arrayColumns[2];
				}

				// 检查数据有效性
				if (StringUtils.equalsAnyIgnoreCase (sMode, "=", "%", " %", "%%", "*", "regexp"))
				{
					Map<String, String> mapBlack = new HashMap <String, String> ();
					mapBlack.put ("mode", sMode);
					mapBlack.put ("expression", sExpression);
					mapBlack.put ("reason", sReason);
					listBlackList.add (mapBlack);
				}
				else
				{
System.err.println ("第 " + n + " 行第一列是非法的“模式”：" + sMode);
					continue;
				}
			}
			br.close ();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/**
	 *
	 * @param sOP_OriginalPoster
	 * @return
	 * null - 没在黑名单内;
	 * 非 null (含空字符串) - 加入黑名单的原因
	 */
	public static String GetBanReason (String sOP_OriginalPoster)
	{
		for (Map<String, String> mapBan : listBlackList)
		{
			String sMode = mapBan.get ("mode");
			String sExpression = mapBan.get ("expression");
			String sReason = mapBan.get ("reason");
			if (sReason==null)
				sReason = "";

			if (
				(StringUtils.equalsIgnoreCase (sMode, "=") && StringUtils.equalsIgnoreCase (sExpression, sOP_OriginalPoster))
				|| (StringUtils.equalsIgnoreCase (sMode, " %") && StringUtils.startsWithIgnoreCase (sOP_OriginalPoster, sExpression))
				|| ((StringUtils.equalsIgnoreCase (sMode, "%") || StringUtils.equalsIgnoreCase (sMode, "% ")) && StringUtils.endsWithIgnoreCase (sOP_OriginalPoster, sExpression))
				|| ((StringUtils.equalsIgnoreCase (sMode, "%%") || StringUtils.equalsIgnoreCase (sMode, "*")) && StringUtils.containsIgnoreCase (sOP_OriginalPoster, sExpression))
				|| (StringUtils.equalsIgnoreCase (sMode, "regexp") && sOP_OriginalPoster.matches (sExpression))
			)
			{
				return sReason;
			}
		}
		return null;
	}

	public static void main (String[] args) throws IOException
	{
		ReadBanListFromFile_ByName ("糗事百科黑名单.txt");
		int n = 3;
		if (args.length >= 1)
		{
			try
			{
				n = Integer.parseInt (args[0]);
			}
			catch (Exception e)
			{
				e.printStackTrace ();
			}
		}
		System.out.println (获取糗事百科热门 (n));
	}
}
