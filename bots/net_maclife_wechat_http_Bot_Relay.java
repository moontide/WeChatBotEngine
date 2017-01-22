import java.io.*;
import java.net.*;
import java.util.*;

import org.apache.commons.io.*;
import org.apache.commons.lang3.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

/**
 * 消息转发/消息中继机器人。
 * 该机器人将从 Socket 接收 JSON 格式的数据，根据数据中指定的接收人、消息类型、消息内容发送到微信好友。
 *
 * <br/>
 * <b>JSON 消息格式</b>
 * <pre>
{
	From: "",	// 来自，可以是任意内容，但通常指定应用程序名称及版本。该字段可省略（等同于值为空的 From）
	To:	// 消息接收人，接收人可以是单个接收人、也可以多个（数组）。通过指定 Account、Alias、RemarkName、NickName 几个名称中的一个或多个的方式来定位该接收人。
		//     为了避免重名的情况，建议是用 Account 或 Alias 来定位接收人。 对于 Account，建议使用在后台日志中找到联系人的【明文 ID / 明文 Account】 -- 唯一、不变、相比加密帐号也短些且容易分辨些。
		// 如果是单个接收人，只需要采取下面数组内的格式即可，如：<code>{Account: "weixin"}</code>
		// 如果是多个接收人，只需要采取下面数组格式即可，如：<code>[{Account: "weixin"}, {Alias: "一个微信号"}, {RemarkName: "一个备注名"}, {NickName: "一个昵称"}]</code>
		[
			{	// 这几个字段，至少需要指定其中的一个。
				Account: "明文帐号，或加密帐号（前体是你得知道每次登录后该会话的加密帐号，通常是要手工在后台日志中获取），或通配符帐号"。
					// 明文帐号： 是在手机端打开一个联系人聊天窗口，然后在网页端获取到的明文，诸如： wxid_XXXX (比较常见的个人帐号)、 gh_XXX (公众号帐号)、 NNNNNN@chatroom (群聊帐号)、还有一些用户自定义的帐号（早期注册的？）
					// 通配符帐号： 通配符帐号的目的是为了群发用。目前支持的通配符帐号有：
						// <code>*</code> - 通讯录里的所有联系人；含群聊天室、公众号（含类似“微信团队”之类的）；
						// <code>*p</code> - 通讯录除去群聊天室、公众号后的联系人 (人/<font color='red'>P</font>erson，但含类似“文件传输助手”这类的非“人”的帐号 -- 暂时没法区分)；
						// <code>*w</code> 或 <code>*g</code> - 所有联系人 (女人/<font color='red'>W</font>oman/<font color='red'>G</font>irl)；
						// <code>*m</code> 或 <code>*b</code> - 所有联系人 (男人/<font color='red'>M</font>an/<font color='red'>B</font>oy)；
						// <code>*r</code> - 通讯录里的所有聊天室联系人 (<font color='red'>R</font>oom)；
						// <code>*gh</code> - 通讯录里的所有公众号联系人；(<font color='red'>gh</font> 是公众号的明文 ID/明文 Account 的帐号前缀，g公 h号？)
				Alias: "别名",
				RemarkName: "备注名",
				NickName: "昵称",	// 昵称可能会被好友自己改动，所以，不建议用这个
				//DisplayName: "聊天室/群内 人在此聊天室/群的“我在本群的昵称”",
			}
			, ...
		],
	MessageType: "消息类型",	// 取值 "text" | "voice" | "video"。 该字段也可以忽略，若为空或者忽略，则默认为 "text"
	Message: "消息"	// 对于 text 文本消息，直接传文字。 对于 voice 和 video，需要用 base64 编码后传递
}
 * </pre>
 * @author liuyan
 *
 */
public class net_maclife_wechat_http_Bot_Relay extends net_maclife_wechat_http_Bot implements Runnable
{
	ServerSocket ss = null;

	@Override
	public void Start ()
	{
		try
		{
			String sListenddress = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.relay.listen.address");
			int nListenPort = net_maclife_wechat_http_BotApp.GetConfig ().getInt ("bot.relay.listen.port");

			ss = new ServerSocket ();
			ss.bind (new InetSocketAddress (InetAddress.getByName (sListenddress), nListenPort));
		}
		catch (IOException e)
		{
net_maclife_wechat_http_BotApp.logger.severe (GetName () + " 启动失败: " + e);
			e.printStackTrace();
			return;
		}

		if (botTask == null)
		{
			botTask = net_maclife_wechat_http_BotApp.executor.submit (this);
		}
	}

	@Override
	public void Stop ()
	{
		try
		{
			super.Stop ();
			if (ss != null && !ss.isClosed ())
			{
				ss.close ();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void run ()
	{
		Socket s = null;
		while (!Thread.interrupted ())
		{
			try
			{
				s = ss.accept ();
				InputStream is = s.getInputStream ();
				PrintWriter out = new PrintWriter (s.getOutputStream (), true);
				String sInput = IOUtils.toString (is, net_maclife_wechat_http_BotApp.utf8);
net_maclife_wechat_http_BotApp.logger.fine (GetName() + " 收到数据:\n" + sInput);
				JsonNode jsonNode = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (sInput);
				ProcessMessage (jsonNode, out);
				s.close ();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	void ProcessMessage (JsonNode jsonMessageToRelay, PrintWriter out)
	{
		try
		{
			String sFrom = net_maclife_wechat_http_BotApp.GetJSONText (jsonMessageToRelay, "From");
			String sMessageType = net_maclife_wechat_http_BotApp.GetJSONText (jsonMessageToRelay, "MessageType");
			String sMessageType_LowerCase = StringUtils.lowerCase (sMessageType);
			String sMessage = net_maclife_wechat_http_BotApp.GetJSONText (jsonMessageToRelay, "Message");
			if (StringUtils.isEmpty (sMessage))
			{
				out.println ("必须指定消息内容");
				return;
			}
			if (StringUtils.isNotEmpty (sFrom))
			{
				sMessage = sMessage + "\n-- 消息来源: " + sFrom;
			}

			JsonNode jsonTOs = jsonMessageToRelay.get ("To");
			List<String> listTOs = new ArrayList<String> ();
			if (jsonTOs.isArray ())
			{
				for (int i=0; i<jsonTOs.size (); i++)
				{
					JsonNode jsonTo = jsonTOs.get (i);
					BuildRecipients (jsonTo, listTOs);
				}
			}
			else
			{
				JsonNode jsonTo = jsonTOs;
				BuildRecipients (jsonTo, listTOs);
			}

			if (listTOs.isEmpty ())
			{
				out.println ("必须指定至少一个接收人");
				return;
			}
			for (String sTo : listTOs)
			{
				switch (sMessageType_LowerCase)
				{
					case "":
					case "text":
						SendTextMessage (sTo, sMessage);
						break;
					case "voice":
						out.println ("暂时不处理音频消息");
						break;
					case "video":
						out.println ("暂时不处理视频消息");
						break;
					default:
						out.println ("未知的消息类型: " + sMessageType);
						return;
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
	}

	void BuildRecipients (JsonNode jsonTo, List<String> listTOs)
	{
			String sAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonTo, "Account");
			String sAlias = net_maclife_wechat_http_BotApp.GetJSONText (jsonTo, "Alias");
			String sRemarkName = net_maclife_wechat_http_BotApp.GetJSONText (jsonTo, "RemarkName");
			String sNickName = net_maclife_wechat_http_BotApp.GetJSONText (jsonTo, "NickName");
			//String sDisplayName = net_maclife_wechat_http_BotApp.GetJSONText (jsonTo, "DisplayName");

			if (StringUtils.isEmpty (sAccount))
			{
				JsonNode jsonContact = engine.SearchForSingleContact (null, sAlias, sRemarkName, sNickName);
				if (jsonContact != null)
					listTOs.add (net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "UserName"));
			}
			else
			{
				if (StringUtils.equalsAnyIgnoreCase (sAccount, "*"))	// 所有联系人
				{
					if (engine.jsonContacts == null)
						return;

					JsonNode jsonContactList = engine.jsonContacts.get ("MemberList");
					for (JsonNode jsonContact : jsonContactList)
						listTOs.add (net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "UserName"));
				}
				else if (StringUtils.equalsAnyIgnoreCase (sAccount, "*p"))	// 所有“人”（排除群聊、公众号），但其实，也可能包含类似 filehelper 之类的
				{
					if (engine.jsonContacts == null)
						return;

					JsonNode jsonContactList = engine.jsonContacts.get ("MemberList");
					for (JsonNode jsonContact : jsonContactList)
					{
						String sEncryptedAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "UserName");
						int nVerifyFlag = net_maclife_wechat_http_BotApp.GetJSONInt (jsonContact, "VerifyFlag");
						boolean isPublicAccount = net_maclife_wechat_http_BotApp.IsPublicAccount (nVerifyFlag);
						boolean isRoomAccount = net_maclife_wechat_http_BotApp.IsRoomAccount (sEncryptedAccount);
						if (isPublicAccount || isRoomAccount)
							continue;

						listTOs.add (sEncryptedAccount);
					}
				}
				else if (StringUtils.equalsAnyIgnoreCase (sAccount, "*m") || StringUtils.equalsAnyIgnoreCase (sAccount, "*b")	// 所有“男人/Man/Boy”（排除群聊、公众号）
						|| StringUtils.equalsAnyIgnoreCase (sAccount, "*w") || StringUtils.equalsAnyIgnoreCase (sAccount, "*g"))	// 所有“女人/Woman/Girl”（排除群聊、公众号）
				{
					if (engine.jsonContacts == null)
						return;

					JsonNode jsonContactList = engine.jsonContacts.get ("MemberList");
					for (JsonNode jsonContact : jsonContactList)
					{
						String sEncryptedAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "UserName");
						int nVerifyFlag = net_maclife_wechat_http_BotApp.GetJSONInt (jsonContact, "VerifyFlag");
						boolean isPublicAccount = net_maclife_wechat_http_BotApp.IsPublicAccount (nVerifyFlag);
						boolean isRoomAccount = net_maclife_wechat_http_BotApp.IsRoomAccount (sEncryptedAccount);
						if (isPublicAccount || isRoomAccount)
							continue;

						int nGender = net_maclife_wechat_http_BotApp.GetJSONInt (jsonContact, "Sex");
						if (nGender == 1 && (StringUtils.equalsAnyIgnoreCase (sAccount, "*m") || StringUtils.equalsAnyIgnoreCase (sAccount, "*b"))
							||
							nGender == 2 && (StringUtils.equalsAnyIgnoreCase (sAccount, "*w") || StringUtils.equalsAnyIgnoreCase (sAccount, "*g"))
							)
							listTOs.add (sEncryptedAccount);
					}
				}
				else if (StringUtils.equalsAnyIgnoreCase (sAccount, "*r"))	// 所有群聊
				{
					if (engine.jsonContacts == null)
						return;

					JsonNode jsonContactList = engine.jsonContacts.get ("MemberList");
					for (JsonNode jsonContact : jsonContactList)
					{
						String sEncryptedAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "UserName");
						boolean isRoomAccount = net_maclife_wechat_http_BotApp.IsRoomAccount (sEncryptedAccount);
						if (isRoomAccount)
							listTOs.add (sEncryptedAccount);
					}
				}
				else if (StringUtils.equalsAnyIgnoreCase (sAccount, "*gh"))	// 所有公众号，也不知道有没有人会专门发到所有公众号里…
				{
					if (engine.jsonContacts == null)
						return;

					JsonNode jsonContactList = engine.jsonContacts.get ("MemberList");
					for (JsonNode jsonContact : jsonContactList)
					{
						String sEncryptedAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "UserName");
						int nVerifyFlag = net_maclife_wechat_http_BotApp.GetJSONInt (jsonContact, "VerifyFlag");
						boolean isPublicAccount = net_maclife_wechat_http_BotApp.IsPublicAccount (nVerifyFlag);
						if (isPublicAccount)
							listTOs.add (sEncryptedAccount);
					}
				}
				else
				{	// 非特殊形式的帐号字符串，则直接当成帐号
					listTOs.add (sAccount);
				}
			}
	}
}
