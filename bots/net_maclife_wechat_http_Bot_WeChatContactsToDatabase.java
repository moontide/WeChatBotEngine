import java.io.*;
import java.sql.*;
import java.sql.Connection;
import java.util.*;

import org.apache.commons.lang3.*;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

import com.fasterxml.jackson.databind.*;

import nu.xom.Element;

/**
 * 将微信联系人写入到数据库中。除了添加到微信通讯录中联系人（包含群聊），还有未加入到通讯录的群、已经删除过的联系人。
 * <p>
 * </p>
 * <p>
 * </p>
 * @author liuyan
 *
 */
public class net_maclife_wechat_http_Bot_WeChatContactsToDatabase extends net_maclife_wechat_http_Bot
{
	public static final String TABLE_NAME__Contacts = "wechat_contacts";
	public static final String TABLE_NAME__RoomMembers = "wechat_contact_members";

	@Override
	public int OnInit (JsonNode jsonInitResult)
	{
		Connection conn = null;
		PreparedStatement stmt = null;
		try
		{
			String sSQL_Insert = "INSERT INTO " + TABLE_NAME__Contacts + " (微信号, 昵称, 备注名, 签名, 性别, 省, 市, 是否星标好友, 是否群, 群主UIN, 群成员数量, 是否公众号, 是否企业号, 是否微信团队号, 数据来源) VALUES (?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?) ON DUPLICATE KEY UPDATE 签名=?, 性别=?, 省=?, 市=?, 是否星标好友=?, 是否群=?, 群主UIN=?, 群成员数量=?, 是否公众号=?, 是否企业号=?, 是否微信团队号=?, 数据来源=?, 最后更新时间=CURRENT_TIMESTAMP";
			net_maclife_wechat_http_BotApp.SetupDataSource ();
			conn = net_maclife_wechat_http_BotApp.botDS.getConnection ();
			stmt = conn.prepareStatement (sSQL_Insert, new String[] {"contact_id"});

			int nCount = net_maclife_wechat_http_BotApp.GetJSONInt (jsonInitResult, "Count");
			JsonNode jsonRecentContactList = jsonInitResult.get ("ContactList");
			for (int i=0; i<nCount; i++)
			{
				JsonNode jsonRecentContact = jsonRecentContactList.get (i);

				int nVerifyFlag = net_maclife_wechat_http_BotApp.GetJSONInt (jsonRecentContact, "VerifyFlag");
				//sb.append ('/');
				//sb.append (nVerifyFlag);
				boolean isRoomAccount = net_maclife_wechat_http_BotApp.IsRoomAccount (net_maclife_wechat_http_BotApp.GetJSONText (jsonRecentContact, "UserName"));
				boolean isPublicAccount = net_maclife_wechat_http_BotApp.IsPublicAccount (nVerifyFlag);
				boolean isEnterprisePublicAccount = net_maclife_wechat_http_BotApp.IsEnterprisePublicAccount (nVerifyFlag);
				boolean isWeChatTeamAccount = net_maclife_wechat_http_BotApp.IsWeChatTeamAccount (nVerifyFlag);

				int nCol = 1;
				stmt.setString (nCol++, net_maclife_wechat_http_BotEngine.GetContactName (jsonRecentContact, "Alias"));
				stmt.setString (nCol++, net_maclife_wechat_http_BotEngine.GetContactName (jsonRecentContact, "NickName"));
				stmt.setString (nCol++, net_maclife_wechat_http_BotEngine.GetContactName (jsonRecentContact, "RemarkName"));
				stmt.setString (nCol++, net_maclife_wechat_http_BotApp.GetJSONText (jsonRecentContact, "Signature"));
				stmt.setInt (nCol++, net_maclife_wechat_http_BotApp.GetJSONInt (jsonRecentContact, "Sex"));
				stmt.setString (nCol++, net_maclife_wechat_http_BotApp.GetJSONText (jsonRecentContact, "Province"));
				stmt.setString (nCol++, net_maclife_wechat_http_BotApp.GetJSONText (jsonRecentContact, "City"));
				stmt.setInt (nCol++, net_maclife_wechat_http_BotApp.GetJSONInt (jsonRecentContact, "StarFriend"));
				stmt.setBoolean (nCol++, isRoomAccount);
				stmt.setLong (nCol++, net_maclife_wechat_http_BotApp.GetJSONLong (jsonRecentContact, "OwnerUin"));
				stmt.setInt (nCol++, net_maclife_wechat_http_BotApp.GetJSONInt (jsonRecentContact, "MemberCount"));
				stmt.setBoolean (nCol++, isPublicAccount);
				stmt.setBoolean (nCol++, isEnterprisePublicAccount);
				stmt.setBoolean (nCol++, isWeChatTeamAccount);
				stmt.setString (nCol++, "最近联系人");


				stmt.setString (nCol++, net_maclife_wechat_http_BotApp.GetJSONText (jsonRecentContact, "Signature"));
				stmt.setInt (nCol++, net_maclife_wechat_http_BotApp.GetJSONInt (jsonRecentContact, "Sex"));
				stmt.setString (nCol++, net_maclife_wechat_http_BotApp.GetJSONText (jsonRecentContact, "Province"));
				stmt.setString (nCol++, net_maclife_wechat_http_BotApp.GetJSONText (jsonRecentContact, "City"));
				stmt.setInt (nCol++, net_maclife_wechat_http_BotApp.GetJSONInt (jsonRecentContact, "StarFriend"));
				stmt.setBoolean (nCol++, isRoomAccount);
				stmt.setLong (nCol++, net_maclife_wechat_http_BotApp.GetJSONLong (jsonRecentContact, "OwnerUin"));
				stmt.setInt (nCol++, net_maclife_wechat_http_BotApp.GetJSONInt (jsonRecentContact, "MemberCount"));
				stmt.setBoolean (nCol++, isPublicAccount);
				stmt.setBoolean (nCol++, isEnterprisePublicAccount);
				stmt.setBoolean (nCol++, isWeChatTeamAccount);
				stmt.setString (nCol++, "最近联系人");

				stmt.executeUpdate ();
			}
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
		finally
		{
			try
			{
				if (stmt != null)
					stmt.close ();
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

		return
			  net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
			| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	@Override
	public int OnContactsReceived (JsonNode jsonContacts)
	{
		Connection conn = null;
		PreparedStatement stmt = null;
		try
		{
			String sSQL_Insert = "INSERT INTO " + TABLE_NAME__Contacts + " (微信号, 昵称, 备注名, 签名, /*电话号码, 描述, 标签, 照片或名片,*/ 省, 市, 是否星标好友, /*是否已删除, 删除时间,*/ 是否群, 群成员数量, 是否公众号, 是否企业号, 是否微信团队号, 数据来源) VALUES (?,?,?,?,?, ?,?,?,?,?, ?,?,?) ON DUPLICATE KEY UPDATE 签名=?, 省=?, 市=?, 是否星标好友=?, 是否群=?, 群成员数量=?, 是否公众号=?, 是否企业号=?, 是否微信团队号=?, 数据来源=?, 最后更新时间=CURRENT_TIMESTAMP";
			net_maclife_wechat_http_BotApp.SetupDataSource ();
			conn = net_maclife_wechat_http_BotApp.botDS.getConnection ();
			stmt = conn.prepareStatement (sSQL_Insert, new String[] {"contact_id"});

			int nCount = net_maclife_wechat_http_BotApp.GetJSONInt (jsonContacts, "MemberCount");
			JsonNode jsonMemberList = jsonContacts.get ("MemberList");
			for (int i=0; i<nCount; i++)
			{
				JsonNode jsonContact = jsonMemberList.get (i);
				int nVerifyFlag = net_maclife_wechat_http_BotApp.GetJSONInt (jsonContact, "VerifyFlag");
				//sb.append ('/');
				//sb.append (nVerifyFlag);
				boolean isRoomAccount = net_maclife_wechat_http_BotApp.IsRoomAccount (net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "UserName"));
				boolean isPublicAccount = net_maclife_wechat_http_BotApp.IsPublicAccount (nVerifyFlag);
				boolean isEnterprisePublicAccount = net_maclife_wechat_http_BotApp.IsEnterprisePublicAccount (nVerifyFlag);
				boolean isWeChatTeamAccount = net_maclife_wechat_http_BotApp.IsWeChatTeamAccount (nVerifyFlag);

				int nCol = 1;
				stmt.setString (nCol++, net_maclife_wechat_http_BotEngine.GetContactName (jsonContact, "Alias"));
				stmt.setString (nCol++, net_maclife_wechat_http_BotEngine.GetContactName (jsonContact, "NickName"));
				stmt.setString (nCol++, net_maclife_wechat_http_BotEngine.GetContactName (jsonContact, "RemarkName"));
				stmt.setString (nCol++, net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "Signature"));
				//stmt.setString (nCol++, "");	// Web 版并没有【电话号码】信息
				//stmt.setString (nCol++, "");	// Web 版并没有【描述】信息
				//stmt.setString (nCol++, "");	// Web 版并没有【标签】信息
				//stmt.setString (nCol++, "");	// Web 版并没有【照片或名片】信息
				stmt.setString (nCol++, net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "Province"));
				stmt.setString (nCol++, net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "City"));
				stmt.setInt (nCol++, net_maclife_wechat_http_BotApp.GetJSONInt (jsonContact, "StarFriend"));
				stmt.setBoolean (nCol++, isRoomAccount);
				stmt.setInt (nCol++, net_maclife_wechat_http_BotApp.GetJSONInt (jsonContact, "MemberCount"));
				stmt.setBoolean (nCol++, isPublicAccount);
				stmt.setBoolean (nCol++, isEnterprisePublicAccount);
				stmt.setBoolean (nCol++, isWeChatTeamAccount);
				stmt.setString (nCol++, "微信通讯录");


				stmt.setString (nCol++, net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "Signature"));
				stmt.setString (nCol++, net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "Province"));
				stmt.setString (nCol++, net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "City"));
				stmt.setInt (nCol++, net_maclife_wechat_http_BotApp.GetJSONInt (jsonContact, "StarFriend"));
				stmt.setBoolean (nCol++, isRoomAccount);
				stmt.setInt (nCol++, net_maclife_wechat_http_BotApp.GetJSONInt (jsonContact, "MemberCount"));
				stmt.setBoolean (nCol++, isPublicAccount);
				stmt.setBoolean (nCol++, isEnterprisePublicAccount);
				stmt.setBoolean (nCol++, isWeChatTeamAccount);
				stmt.setString (nCol++, "微信通讯录");

				stmt.executeUpdate ();
			}
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
		finally
		{
			try
			{
				if (stmt != null)
					stmt.close ();
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

		return
			  net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
			| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	@Override
	public int OnRoomsAndTheirMembersReceived (JsonNode jsonRoomsAndTheirMembers)
	{
		Connection conn = null;
		PreparedStatement stmt = null;
		PreparedStatement stmt_QueryContactID = null;
		try
		{
			String sSQL_Insert = "INSERT INTO " + TABLE_NAME__RoomMembers + " (contact_id, 序号, 成员昵称, 成员群昵称) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE 成员昵称=?, 成员群昵称=?";
			String sSQL_QueryContactID = "SELECT contact_id FROM " + TABLE_NAME__Contacts + " WHERE 昵称=? AND 备注名=?";
			net_maclife_wechat_http_BotApp.SetupDataSource ();
			conn = net_maclife_wechat_http_BotApp.botDS.getConnection ();
			stmt_QueryContactID = conn.prepareStatement (sSQL_QueryContactID);

			stmt = conn.prepareStatement (sSQL_Insert);
			int nCount = net_maclife_wechat_http_BotApp.GetJSONInt (jsonRoomsAndTheirMembers, "Count");
			JsonNode jsonContactList = jsonRoomsAndTheirMembers.get ("ContactList");
			for (int i=0; i<nCount; i++)
			{
				JsonNode jsonContact = jsonContactList.get (i);
				int nContactID = 0;
				String s群昵称 = net_maclife_wechat_http_BotEngine.GetContactName (jsonContact, "NickName");
				String s群备注名 = net_maclife_wechat_http_BotEngine.GetContactName (jsonContact, "RemarkName");
				try
				{
					stmt_QueryContactID.setString (1, s群昵称);
					stmt_QueryContactID.setString (2, s群备注名);
					ResultSet rs = stmt_QueryContactID.executeQuery ();
					while (rs.next ())
					{
						nContactID = rs.getInt (1);
						break;
					}
					rs.close ();
				}
				catch (Exception e)
				{
					e.printStackTrace ();
					continue;	// 针对当前群取这个群在数据库中的 contact_id 时出错，则跳过这个群，继续下一个群
				}
				if (nContactID == 0)
				{
					net_maclife_wechat_http_BotApp.logger.warning ("无法根据群的昵称【" + s群昵称 + "】和备注名【" + s群备注名 + "】从表中取到其 contact_id，也许群尚未写到表中，也有可能群已经改名");
					continue;
				}

				int nMemberCount = net_maclife_wechat_http_BotApp.GetJSONInt (jsonContact, "MemberCount");
				JsonNode jsonMemberList = jsonContact.get ("MemberList");
				for (int j=0; j<nMemberCount; j++)
				{
					JsonNode jsonMember = jsonMemberList.get (j);
					int nCol = 1;
					stmt.setInt (nCol++, nContactID);
					stmt.setInt (nCol++, (j+1));
					stmt.setString (nCol++, net_maclife_wechat_http_BotEngine.GetContactName (jsonMember, "NickName"));
					stmt.setString (nCol++, net_maclife_wechat_http_BotEngine.GetContactName (jsonMember, "DisplayName"));

					stmt.setString (nCol++, net_maclife_wechat_http_BotEngine.GetContactName (jsonMember, "NickName"));
					stmt.setString (nCol++, net_maclife_wechat_http_BotEngine.GetContactName (jsonMember, "DisplayName"));

					stmt.executeUpdate ();
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
		finally
		{
			try
			{
				if (stmt != null)
					stmt.close ();
			}
			catch (Exception e)
			{
				e.printStackTrace ();
			}
			try
			{
				if (stmt_QueryContactID != null)
					stmt_QueryContactID.close ();
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

		return
			  net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
			| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
		联系人变化。

	 */
	@Override
	public int OnContactChanged (JsonNode jsonMessage)
	{
		return
			  net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
			| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	@Override
	public int OnContactDeleted (JsonNode jsonMessage)
	{
		return
			  net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
			| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	@Override
	public int OnRoomMemberChanged (JsonNode jsonMessage)
	{
		return
			  net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
			| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	@Override
	public int OnMessageIsRevokedMessageReceived
		(
			JsonNode jsonMessage,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, String sRevokedMsgID, String sReplacedByMsg
		)
	{
		// 该消息中包含了联系人/聊天群的明文 ID 信息，可以将其写入到数据库中，并作为非空明文 ID 的唯一标识

		//
		String sSQL_Count = "SELECT COUNT(昵称) FROM " + TABLE_NAME__Contacts + " WHERE 昵称=? AND 备注名=?";
		String sSQL_Update = "UPDATE " + TABLE_NAME__Contacts + " SET 明文ID=? WHERE 昵称=? AND 备注名=?";
		int nCount = 0;
		Connection conn = null;
		PreparedStatement stmt_Count = null;
		PreparedStatement stmt_Update = null;
		try
		{

			net_maclife_wechat_http_BotApp.SetupDataSource ();
			conn = net_maclife_wechat_http_BotApp.botDS.getConnection ();
			stmt_Count = conn.prepareStatement (sSQL_Count);
			stmt_Count.setString (1, net_maclife_wechat_http_BotEngine.GetContactName (jsonReplyTo, "NickName"));
			stmt_Count.setString (2, net_maclife_wechat_http_BotEngine.GetContactName (jsonReplyTo, "RemarkName"));
			ResultSet rs = stmt_Count.executeQuery ();
			while (rs.next ())
			{
				nCount = rs.getInt (1);
				break;
			}
			rs.close ();


			if (nCount != 1)
			{
				net_maclife_wechat_http_BotApp.logger.warning ("昵称 " + sReplyToName + " 在 wechat_contacts 表中" + (nCount > 1 ? "不唯一" : "不存在") + "，这将导致不能将明文 ID 对应到单个联系人上面。");
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
			}

			nu.xom.Document doc = net_maclife_wechat_http_BotApp.xomBuilder.build (sContent, null);
			Element sysmsg = doc.getRootElement ();
			Element revokemsg = sysmsg.getFirstChildElement ("revokemsg");
			String sPeerAccount = net_maclife_wechat_http_BotApp.GetXMLValue (revokemsg, "session");

			stmt_Update = conn.prepareStatement (sSQL_Update);
			stmt_Update.setString (1, sPeerAccount);
			stmt_Update.setString (2, net_maclife_wechat_http_BotEngine.GetContactName (jsonReplyTo, "NickName"));
			stmt_Update.setString (3, net_maclife_wechat_http_BotEngine.GetContactName (jsonReplyTo, "RemarkName"));
			stmt_Update.executeUpdate ();
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
		finally
		{
			try
			{
				if (stmt_Count != null)
					stmt_Count.close ();
			}
			catch (Exception e)
			{
				e.printStackTrace ();
			}
			try
			{
				if (stmt_Update != null)
					stmt_Update.close ();
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

		return
			  net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED
			| net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}
}
