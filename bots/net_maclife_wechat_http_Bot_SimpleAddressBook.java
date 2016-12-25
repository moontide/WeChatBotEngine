import java.sql.*;
import java.util.*;

import org.apache.commons.dbcp2.*;
import org.apache.commons.lang3.*;

import com.fasterxml.jackson.databind.*;

/**
 * 简易微信群通讯录。
 * 简易通讯录的数据存放在数据库的单张表中。简易通讯录提供简单的通讯录的 查、修改功能。
 *
 * 查：
 *    通讯录 姓名 -- 查指定姓名的通讯录
 *    通讯录      -- 查自己的通讯录、同时提供帮助信息？
 * 修改：
 *
 * 增加： 暂时不提供增加的功能，考虑的原因是：
 * @author liuyan
 *
 */
public class net_maclife_wechat_http_Bot_SimpleAddressBook extends net_maclife_wechat_http_Bot
{

	@Override
	public int OnTextMessageReceived
		(
			JsonNode jsonFrom, String sFromAccount, String sFromName,
			JsonNode jsonFrom_RoomMember, String sFromAccount_RoomMember, String sFromName_RoomMember,
			JsonNode jsonFrom_Person, String sFromAccount_Person, String sFromName_Person,
			JsonNode jsonTo, String sToAccount, String sToName,
			JsonNode jsonMessage, String sMessage,
			boolean bMentionedMeInRoomChat, boolean bMentionedMeFirstInRoomChat
		)
	{
		List<String> listCommands = net_maclife_wechat_http_BotApp.GetConfig ().getList (String.class, "bot.simple-address-book.commands");
		if (listCommands==null || listCommands.isEmpty ())	// 如果未配置命令，则不处理
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

		try
		{
			String[] arrayMessages = sMessage.split (" +", 2);
			if (arrayMessages==null || arrayMessages.length<1)
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

			String sCommandInputed = arrayMessages[0];
			String sCommandParametersInputed = null;
			if (arrayMessages.length >= 2)
				sCommandParametersInputed = arrayMessages[1];

			// 命令行命令格式没问题，现在开始查询数据库
			for (int i=0; i<listCommands.size (); i++)
			{
				String sCommand = listCommands.get (i);
				if (StringUtils.equalsIgnoreCase (sCommandInputed, sCommand))
				{
					if (StringUtils.isEmpty (sFromAccount_RoomMember))
					{
						SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, GetName() + " 需要在群聊中执行。如果你确实是在群聊中执行的，请务必设置好群名称，并告知管理员（我）。");
						return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					}
					if (StringUtils.isEmpty (sCommandParametersInputed))
					{
						SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, GetName() + " 在查询时需要指定要姓名。\n\n用法:\n" + sCommand + "  <通讯录中的姓名(通常是真实姓名)>");
						return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					}

					String sResult = Query (sFromName, sCommandParametersInputed);
					if (StringUtils.isEmpty (sResult))
					{
						SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, "在通讯簿【" + sFromName + "】（与群名相同）中没找到姓名为【" + sCommandParametersInputed + "】的联系信息");
						return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					}
					SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, sResult);
					break;
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


	String Query (String sFromName, String sQuery)
	{
		String sTablePrefix = StringUtils.trimToEmpty (net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.simple-address-book.jdbc.database-table.prefix"));
		net_maclife_wechat_http_BotApp.SetupDataSource ();
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		StringBuilder sb = null;
		try
		{
			conn = net_maclife_wechat_http_BotApp.botDS.getConnection ();
			stmt = conn.prepareStatement ("SELECT * FROM " + sTablePrefix + "simple_wechat_address_book WHERE 微信群昵称=? AND 群联系人姓名=?");
			int nCol = 1;
			stmt.setString (nCol++, sFromName);
			stmt.setString (nCol++, sQuery);
			rs = stmt.executeQuery ();
			sb = new StringBuilder ();
			while (rs.next ())
			{
				sb.append (rs.getString ("群联系人姓名"));
				sb.append ("\n");
				sb.append ("--------------------\n");
				sb.append ("电话: ");
				sb.append (rs.getString ("电话号码"));
				sb.append ("\n");
				sb.append ("单位: ");
				sb.append (rs.getString ("工作单位"));
				sb.append ("\n");
				sb.append ("住址: ");
				sb.append (rs.getString ("住址"));
				sb.append ("\n");
				sb.append ("昵称: ");
				sb.append (rs.getString ("群联系人昵称"));
				sb.append ("\n");
				break;
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
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
}
