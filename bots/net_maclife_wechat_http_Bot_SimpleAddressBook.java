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
	public int OnTextMessageReceived (String sFrom_EncryptedRoomAccount, String sFrom_RoomNickName, String sFrom_EncryptedAccount, String sFrom_Name, String sTo_EncryptedAccount, String sTo_Name, JsonNode jsonMessage, String sMessage, boolean bMentionedMeInRoomChat, boolean bMentionedMeFirstInRoomChat)
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
					if (StringUtils.isEmpty (sFrom_RoomNickName))
					{
						SendTextMessage (sFrom_EncryptedRoomAccount, sFrom_EncryptedAccount, sFrom_Name, GetName() + " 需要在群聊中执行。如果你确实是在群聊中执行的，请务必设置好群名称，并告知管理员（我）。");
						return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					}
					if (StringUtils.isEmpty (sCommandParametersInputed))
					{
						SendTextMessage (sFrom_EncryptedRoomAccount, sFrom_EncryptedAccount, sFrom_Name, GetName() + " 在查询时需要指定要姓名。\n\n用法:\n" + sCommand + "  <通讯录中的姓名(通常是真实姓名)>");
						return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					}

					String sResult = Query (sFrom_RoomNickName, sCommandParametersInputed);
					if (StringUtils.isEmpty (sResult))
					{
						SendTextMessage (sFrom_EncryptedRoomAccount, sFrom_EncryptedAccount, sFrom_Name, "在通讯簿【" + sFrom_RoomNickName + "】（与群名相同）中没找到姓名为【" + sCommandParametersInputed + "】的联系信息");
						return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					}
					SendTextMessage (sFrom_EncryptedRoomAccount, sFrom_EncryptedAccount, sFrom_Name, sResult);
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


	String Query (String sRoomName, String sQuery)
	{
		String sTablePrefix = StringUtils.trimToEmpty (net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.simple-address-book.jdbc.database-table.prefix"));
		SetupDataSource ();
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		StringBuilder sb = null;
		try
		{
			conn = botDS.getConnection ();
			stmt = conn.prepareStatement ("SELECT * FROM " + sTablePrefix + "simple_wechat_address_book WHERE 微信群昵称=? AND 群联系人姓名=?");
			int nCol = 1;
			stmt.setString (nCol++, sRoomName);
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


	BasicDataSource botDS = null;
	void SetupDataSource ()
	{
		if (botDS != null)
			return;

		String sDriverClassName = net_maclife_wechat_http_BotApp.GetConfig ().getString ("app.jdbc.driver");	// , "com.mysql.jdbc.Driver"
		String sURL = net_maclife_wechat_http_BotApp.GetConfig ().getString ("app.jdbc.url");	// , "jdbc:mysql://localhost/WeChatBotEngine?autoReconnect=true&amp;characterEncoding=UTF-8&amp;zeroDateTimeBehavior=convertToNull"
		String sUserName = net_maclife_wechat_http_BotApp.GetConfig ().getString ("app.jdbc.username");	// , "root"
		String sUserPassword = net_maclife_wechat_http_BotApp.GetConfig ().getString ("app.jdbc.userpassword");
		if (StringUtils.isEmpty (sDriverClassName) || StringUtils.isEmpty (sURL) || StringUtils.isEmpty (sUserName))
		{
net_maclife_wechat_http_BotApp.logger.warning ("jdbc 需要将 driver、username、userpassword 信息配置完整");
			return;
		}

		botDS = new BasicDataSource();
		//botDS.setDriverClassName("org.mariadb.jdbc.Driver");
		botDS.setDriverClassName (sDriverClassName);
		// 要赋给 mysql 用户对 mysql.proc SELECT 的权限，否则执行存储过程报错
		// GRANT SELECT ON mysql.proc TO bot@'192.168.2.%'
		// 参见: http://stackoverflow.com/questions/986628/cant-execute-a-mysql-stored-procedure-from-java
		botDS.setUrl (sURL);
		// 在 prepareCall 时报错:
		// User does not have access to metadata required to determine stored procedure parameter types. If rights can not be granted, configure connection with "noAccessToProcedureBodies=true" to have driver generate parameters that represent INOUT strings irregardless of actual parameter types.
		//botDS.setUrl ("jdbc:mysql://192.168.2.1/bot?autoReconnect=true&amp;characterEncoding=UTF-8&amp;zeroDateTimeBehavior=convertToNull&amp;noAccessToProcedureBodies=true&amp;useInformationSchema=true"); // 没有作用

		// http://thenullhandler.blogspot.com/2012/06/user-does-not-have-access-error-with.html // 没有作用
		// http://bugs.mysql.com/bug.php?id=61203
		//botDS.setUrl ("jdbc:mysql://192.168.2.1/bot?autoReconnect=true&amp;characterEncoding=UTF-8&amp;zeroDateTimeBehavior=convertToNull&amp;useInformationSchema=true");

		botDS.setUsername (sUserName);
		if (StringUtils.isNotEmpty (sUserPassword))
			botDS.setPassword (sUserPassword);

		//botDS.setMaxTotal (5);
	}

}
