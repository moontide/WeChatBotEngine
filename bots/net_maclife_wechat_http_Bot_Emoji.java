import java.io.*;
import java.sql.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

import com.fasterxml.jackson.databind.*;

/**
 * 把所有 emoji 字符都显示出来的测试 Bot。
 * <p>
 * 注意：当在手机上发 emoji 表情时，比如：😞，web 端收到的是类似 <code>&lt;span class="emoji emoji1f612"&gt;&lt;/span&gt;</code> 这样的文字（难道微信担心 Web 版在不同浏览器下表现不一致？）。
 * 手机微信上的 emoji 字符的显示，应该是（猜测）手机操作系统自己显示的，比如 android ios 用系统内置的 emoji 字体来显示（再说一遍，是猜测）。
 * </p>
 * <p>
 * emoji 数据库是从 http://unicode.org/emoji/charts/full-emoji-list.html (这个 html 有 36M 大小！！！) 获取并用一个专门的程序写入的。
 * </p>
 * @author liuyan
 *
 */
public class net_maclife_wechat_http_Bot_Emoji extends net_maclife_wechat_http_Bot
{
	@Override
	public int OnTextMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, boolean isContentMentionedMe, boolean isContentMentionedMeFirst
		)
	{
		List<String> listCommands = net_maclife_wechat_http_BotApp.GetConfig ().getList (String.class, "bot.emoji-test.commands");
		if (listCommands==null || listCommands.isEmpty ())	// 如果未配置命令，则不处理
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

		try
		{
			String[] arrayMessages = sContent.split ("\\s+", 2);
			if (arrayMessages==null || arrayMessages.length<1)
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

			String sCommandInputed = arrayMessages[0];
			String sCommandParametersInputed = null;
			if (arrayMessages.length >= 2)
				sCommandParametersInputed = arrayMessages[1];

			String[] arrayCommandOptions = sCommandInputed.split ("\\.+", 2);
			sCommandInputed = arrayCommandOptions[0];
			String sCommandOptionsInputed = null;
			if (arrayCommandOptions.length >= 2)
				sCommandOptionsInputed = arrayCommandOptions[1];

			// 命令行命令格式没问题，现在开始查询数据库
			for (int i=0; i<listCommands.size (); i++)
			{
				String sCommand = listCommands.get (i);
				if (StringUtils.equalsIgnoreCase (sCommandInputed, sCommand))
				{
					if (StringUtils.isEmpty (sCommandParametersInputed))
					{
						SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, GetName() + " 在查询时需要指定关键字（关键字可指定多个，若为多个，则只匹配包含所有关键字的）。\n\n用法:\n" + sCommand + "[.detail]  <emoji 关键字>...\n\n比如：\n" + sCommand + "  cat face");
						return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					}

					// 解析命令“选项”： .detail .详细
					boolean bShowDetail = false;
					if (StringUtils.isNotEmpty (sCommandOptionsInputed))
					{
						arrayCommandOptions = sCommandOptionsInputed.split ("\\.+");
						for (String sCommandOption : arrayCommandOptions)
						{
							if (StringUtils.equalsIgnoreCase (sCommandOption, "detail") || StringUtils.equalsIgnoreCase (sCommandOption, "详细"))
							{
								bShowDetail = true;
							}
						}
					}

					try
					{
						String sResult = Query (sCommandParametersInputed, bShowDetail);
						if (StringUtils.isEmpty (sResult))
						{
							SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "找不到关键字为 " + sCommandParametersInputed + " 的 emoji 字符");
							return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
						}
						SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, sResult);
						break;
					}
					catch (Exception e)
					{
						SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "查询出错: " + e);
					}
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}

		return
			  net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
			| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	String Query (String sQuery, boolean bShowDetail) throws SQLException
	{
		String sTablePrefix = StringUtils.trimToEmpty (net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.emoji-test.jdbc.database-table.prefix"));
		net_maclife_wechat_http_BotApp.SetupDataSource ();
		java.sql.Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		StringBuilder sb = null;
		try
		{
			conn = net_maclife_wechat_http_BotApp.botDS.getConnection ();
			StringBuilder sbSQL = new StringBuilder ("SELECT * FROM " + sTablePrefix + "emoji e WHERE 1=1");
			if (! StringUtils.containsIgnoreCase (sQuery, "*"))
			{
				String[] arrayKeywords = sQuery.split (" +");
				for (String sKeyword : arrayKeywords)
				{	// 这里先用这种方式查询，以后考虑将 tag_name 挪到单独的表中，1对多的关系，查询时只按照 “=”的匹配方式进行匹配，不会造成现在这种模糊匹配的方式带来的不想要的结果：比如，查 eat 会匹配到 meat repeat 等
					sbSQL.append (" AND (tag_name=? OR tag_name LIKE ? OR tag_name LIKE ? OR 英文名称=? OR 英文名称 LIKE ? OR 英文名称 LIKE ?)");
				}
			}
			stmt = conn.prepareStatement (sbSQL.toString ());
			int nCol = 1;
			if (! StringUtils.containsIgnoreCase (sQuery, "*"))
			{
				String[] arrayKeywords = sQuery.split (" +");
				for (String sKeyword : arrayKeywords)
				{
					stmt.setString (nCol++, sKeyword);
					stmt.setString (nCol++, "%" + sKeyword + " %");
					stmt.setString (nCol++, "% " + sKeyword + "%");
					stmt.setString (nCol++, sKeyword);
					stmt.setString (nCol++, "%" + sKeyword + " %");
					stmt.setString (nCol++, "% " + sKeyword + "%");
				}
			}
			rs = stmt.executeQuery ();
			sb = new StringBuilder ();
			while (rs.next ())
			{
				if (bShowDetail)
				{
					sb.append ("--------------------\n");
					sb.append ("字符: ");
				}
				sb.append (rs.getString ("emoji_char"));
				sb.append (' ');
				if (bShowDetail)
				{
					sb.append ("\n");
					sb.append ("名称: ");
					sb.append (rs.getString ("英文名称"));
					sb.append ("\n");
					sb.append ("关键字: ");
					sb.append (rs.getString ("tag_name"));
					sb.append ("\n");
					//sb.append ("Unicode: ");
					//sb.append (rs.getString ("tag_name"));
					//sb.append ("\n");
					//sb.append ("UTF-8: ");
					//sb.append (rs.getString ("tag_name"));
					//sb.append ("\n");
				}
				//break;
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
			throw e;
		}
		finally
		{
			try
			{
				if (rs != null)
					rs.close ();
				if (stmt != null)
					stmt.close ();
				if (conn != null)
					conn.close ();
			}
			catch (SQLException e)
			{
				e.printStackTrace();
			}
		}
		return sb==null ? null : sb.toString ();
	}


	public static void Usage ()
	{
		System.out.println ("用法：");
		System.out.println ("从 full-emoji-list.html 生成 .sql 入库 SQL 脚本：");
		System.out.println ("	java net_maclife_wechat_http_Bot_Emoji gensql <.html 文件名> [输出到 .sql 文件名]");
		System.out.println ("	如果不指定 [输出到 .sql 文件名]，则输出到当前目录下的 emoji-data.mysql.sql 文件中 (如果文件存在的话，则直接覆盖)");
		System.out.println ("查询：");
		System.out.println ("	java net_maclife_wechat_http_Bot_Emoji <emoji 关键字>");
	}

	public static String ExtractImageData (String sImageSrc)
	{
		return StringUtils.substring (StringUtils.remove (sImageSrc, "—"), "data:image/png;base64,".length ());
	}
	public static void main (String[] args) throws IOException
	{
		if (args.length < 1)
		{
			Usage ();
			return;
		}
		String sAction = args[0];
		if (StringUtils.equalsIgnoreCase (sAction, "gensql"))
		{
			if (args.length < 2)
			{
				Usage ();
				return;
			}

			File fHTML = new File (args[1]);
			File fSQL = new File (args.length >=3 ? args[2] : "emoji-data.mysql.sql");
			FileWriter fwSQL = new FileWriter (fSQL);
			//fwSQL.write ("INSERT INTO emoji (sn, code, emoji_char, 浏览器显示图, Apple显示图, Google显示图, Twitter显示图, One显示图, Facebook显示图, FBM显示图, 三星显示图, Windows显示图, GMail显示图, SB显示图, DCM显示图, KDDI显示图, 英文名称, 日期, tag_name) VALUES\n");

			Document docHTML = Jsoup.parse (fHTML, net_maclife_wechat_http_BotApp.utf8);
			Elements eEmojiTable = docHTML.select ("table");
			Elements eRows = eEmojiTable.select ("tr");
			int nEmojiRow = 0;
			for (Element eRow : eRows)
			{
				Elements eCols = eRow.select ("td");
				if (eCols.isEmpty ())	// 这行是 标题行？ th?
					continue;

				if ((nEmojiRow % 100) == 0)	// 每 100 行数据出一个 INSERT，免得 MySQL 报错： ERROR 2006 (HY000) at line 1: MySQL server has gone away 。原因： MySQL 数据包有大小限制，虽然可以在配置文件中用 SET max_allowed_packet=nnnM 来解决，但还是成多个 INSERT 吧
				{
					if (nEmojiRow != 0)
						fwSQL.write (";");
					fwSQL.write ("\nINSERT INTO emoji (sn, code, emoji_char, 浏览器显示图, Apple显示图, Google显示图, Twitter显示图, EmojiOne显示图, Facebook显示图, FacebookMessenger显示图, Samsung显示图, Windows显示图, GMail显示图, SoftBank显示图, DoCoMo显示图, KDDI显示图, 英文名称, 日期, tag_name) VALUES\n");
				}

				if ((nEmojiRow % 100) == 0)	// 每个 INSERT 的第一条数据
					fwSQL.write ("\t  (");
				else
					fwSQL.write ("\n\t, (");

				nEmojiRow ++;
				int i=0;
				String s序号 = eCols.get (i++).text ();
				String sCode = eCols.get (i++).text ();
				String sChars = eCols.get (i++).text ();
				String s浏览器显示图 = ExtractImageData (eCols.get (i++).select ("img").attr ("src"));
				String sApple显示图 = ExtractImageData (eCols.get (i++).select ("img").attr ("src"));
				String sGoogle显示图 = ExtractImageData (eCols.get (i++).select ("img").attr ("src"));
				String sTwitter显示图 = ExtractImageData (eCols.get (i++).select ("img").attr ("src"));
				String sEmojiOne显示图 = ExtractImageData (eCols.get (i++).select ("img").attr ("src"));
				String sFacebook显示图 = ExtractImageData (eCols.get (i++).select ("img").attr ("src"));
				String sFacebookMessenger显示图 = ExtractImageData (eCols.get (i++).select ("img").attr ("src"));
				String sSamsung显示图 = ExtractImageData (eCols.get (i++).select ("img").attr ("src"));
				String sWindows显示图 = ExtractImageData (eCols.get (i++).select ("img").attr ("src"));
				String sGMail显示图 = ExtractImageData (eCols.get (i++).select ("img").attr ("src"));
				String sSoftBank显示图 = ExtractImageData (eCols.get (i++).select ("img").attr ("src"));
				String sDoCoMo显示图 = ExtractImageData (eCols.get (i++).select ("img").attr ("src"));
				String sKDDI显示图 = ExtractImageData (eCols.get (i++).select ("img").attr ("src"));
				String s英文名称 = eCols.get (i++).text ();
				String s日期时间 = eCols.get (i++).text ();
				String s关键字 = eCols.get (i++).text ();

				fwSQL.write (s序号);
				fwSQL.write (", '" + sCode + "'");
				fwSQL.write (", '" + sChars + "'");
				fwSQL.write (", '" + s浏览器显示图 + "'");
				fwSQL.write (", '" + sApple显示图 + "'");
				fwSQL.write (", '" + sGoogle显示图 + "'");
				fwSQL.write (", '" + sTwitter显示图 + "'");
				fwSQL.write (", '" + sEmojiOne显示图 + "'");
				fwSQL.write (", '" + sFacebook显示图 + "'");
				fwSQL.write (", '" + sFacebookMessenger显示图 + "'");
				fwSQL.write (", '" + sSamsung显示图 + "'");
				fwSQL.write (", '" + sWindows显示图 + "'");
				fwSQL.write (", '" + sGMail显示图 + "'");
				fwSQL.write (", '" + sSoftBank显示图 + "'");
				fwSQL.write (", '" + sDoCoMo显示图 + "'");
				fwSQL.write (", '" + sKDDI显示图 + "'");
				fwSQL.write (", '" + s英文名称 + "'");
				fwSQL.write (", '" + s日期时间 + "'");
				fwSQL.write (", '" + s关键字 + "'");
				fwSQL.write (')');

				System.out.println (sChars);
			}
			fwSQL.write (";");

			fwSQL.close ();
		}
	}
}
