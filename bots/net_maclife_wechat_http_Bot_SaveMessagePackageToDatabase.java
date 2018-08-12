import java.sql.*;

import org.apache.commons.lang3.*;

import com.fasterxml.jackson.databind.*;

/**
 * <ul>
 * 	<li>记录【韬乐园快餐店】润迅订餐群里的员工点餐记录（记录所点的每一份快餐的每一个具体餐品）</li>
 * </ul>
 * <p>
 * </p>
 * <p>
 * </p>
 * @depends
 * @author liuyan
 *
 */
public class net_maclife_wechat_http_Bot_SaveMessagePackageToDatabase extends net_maclife_wechat_http_Bot
{
	@Override
	public int OnMessagePackageReceived (JsonNode jsonMessagePackage)
	{
		保存消息包 (jsonMessagePackage);
		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	void 保存消息包 (JsonNode jsonMessagePackage)
	{
		Connection conn = null;
		PreparedStatement stmt_保存消息包 = null;
		try
		{
			int nAddMsgCount = net_maclife_wechat_http_BotApp.GetJSONInt (jsonMessagePackage, "AddMsgCount", 0);
			int nModContactCount = net_maclife_wechat_http_BotApp.GetJSONInt (jsonMessagePackage, "ModContactCount", 0);
			int nDelContactCount = net_maclife_wechat_http_BotApp.GetJSONInt (jsonMessagePackage, "DelContactCount", 0);
			int nModChatRoomMemerCount = net_maclife_wechat_http_BotApp.GetJSONInt (jsonMessagePackage, "ModChatRoomMemberCount", 0);

			net_maclife_wechat_http_BotApp.SetupDataSource ();
			conn = net_maclife_wechat_http_BotApp.botDS.getConnection ();
			stmt_保存消息包 = conn.prepareStatement ("INSERT INTO wechat_message_packages (package_json, AddMsgCount, ModContactCount, DelContactCount, ModChatRoomMemberCount) VALUES (?,?,?,?,?)", new String[] {"package_id"});
			int nCol = 1;
			stmt_保存消息包.setString (nCol++, jsonMessagePackage.toString ());
			stmt_保存消息包.setInt (nCol++, nAddMsgCount);
			stmt_保存消息包.setInt (nCol++, nModContactCount);
			stmt_保存消息包.setInt (nCol++, nDelContactCount);
			stmt_保存消息包.setInt (nCol++, nModChatRoomMemerCount);
			stmt_保存消息包.executeUpdate ();
			stmt_保存消息包.close ();

			conn.close ();
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
		finally
		{
			try
			{
				if (stmt_保存消息包 != null)
					stmt_保存消息包.close ();
			}
			catch (Exception e)
			{
				e.printStackTrace ();
			}
			try
			{
				if (conn != null)
					conn.close ();
			}
			catch (Exception e)
			{
				e.printStackTrace ();
			}
		}
	}


	public static void main (String[] args)
	{
	}
}
