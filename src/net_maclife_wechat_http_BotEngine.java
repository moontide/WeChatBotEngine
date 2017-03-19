import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import javax.script.*;

import org.apache.commons.io.*;
import org.apache.commons.lang3.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

import nu.xom.*;

/**
 * 机器人引擎：封装好针对此帐号的处理、挂载多个机器人小程序、解析微信消息、分发消息到机器人小程序。
 * @author liuyan
 *
 */
class net_maclife_wechat_http_BotEngine implements Runnable
{
	public static String sSessionCacheFileName = net_maclife_wechat_http_BotApp.cacheDirectory + File.separator + "wechat-session-cache.json";
	// 几种 Bot 链处理方式标志（Bot 链处理方式仅仅在 ${engine.message.dispatch.thread-mode} 配置为【单线程/共享线程】时才有用）。组合值列表：
	// 0: 本 Bot 没处理，后面的 Bot 也别处理了
	// 1: 本 Bot 已处理，后面的 Bot 别处理了
	// 2: 本 Bot 没处理，但后面的 Bot 请继续处理，别管我……
	// 3: 本 Bot 已处理，但后面的 Bot 也请继续处理
	public static final int BOT_CHAIN_PROCESS_MODE_MASK__PROCESSED = 1;	// 标志位： 消息是否已经处理过。如果此位为 0，则表示未处理过。
	public static final int BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE  = 2;	// 标志位： 消息是否让后面的 Bot 继续处理。如果此位为 0，则表示不让后面的 Bot 继续处理。


	public static final String REGEXP_GROUP_CHAT_ACTUAL_MEMBER = "^(@\\p{XDigit}+):\\n(.*)$";
	public static final Pattern PATTERN_GROUP_CHAT_ACTUAL_MEMBER = Pattern.compile (REGEXP_GROUP_CHAT_ACTUAL_MEMBER, Pattern.DOTALL);	// 必须启用 DOTALL，因为是多行的
	// 消息类型列表
	// 参考自: https://github.com/Urinx/WeixinBot/blob/master/README.md ，但做了一些改动
	//
	public static final int WECHAT_MSG_TYPE__TEXT                  = 1;
	public static final int WECHAT_MSG_TYPE__IMAGE                 = 3;
	public static final int WECHAT_MSG_TYPE__APP                   = 6;	// 上面的参考中没有的
	public static final int WECHAT_MSG_TYPE__VOICE                 = 34;
	public static final int WECHAT_MSG_TYPE__REQUEST_TO_ADD_FRIEND = 37;
	//public static final int WECHAT_MSG_TYPE__POSSIBLE_FRIEND_MSG   = 40;
	public static final int WECHAT_MSG_TYPE__WECHAT_VCARD          = 42;
	public static final int WECHAT_MSG_TYPE__VIDEO_MSG             = 43;
	public static final int WECHAT_MSG_TYPE__EMOTION               = 47;
	//public static final int WECHAT_MSG_TYPE__GPS_POSITION          = 48;
	public static final int WECHAT_MSG_TYPE__URL                   = 49;
	//public static final int WECHAT_MSG_TYPE__VOIP_MSG              = 50;
	public static final int WECHAT_MSG_TYPE__OPERATION             = 51;	// 上面的参考文档认为是初始化消息，我这里看起来更像是一个“操作”消息
	//public static final int WECHAT_MSG_TYPE__VOIP_NOTIFY           = 52;
	//public static final int WECHAT_MSG_TYPE__VOIP_INVITE           = 53;
	public static final int WECHAT_MSG_TYPE__SHORT_VIDEO           = 62;
	//public static final int WECHAT_MSG_TYPE__SYSTEM_NOTICE         = 9999;
	public static final int WECHAT_MSG_TYPE__SYSTEM                = 10000;
	public static final int WECHAT_MSG_TYPE__MSG_REVOKED           = 10002;

	/*
	public enum WeChatMsgType
	{
		文本 (WECHAT_MSG_TYPE__TEXT),
		文本消息中的位置信息 (WECHAT_MSG_TYPE__TEXT),
		图片 (WECHAT_MSG_TYPE__IMAGE),
		应用 (WECHAT_MSG_TYPE__APP),
		语音 (WECHAT_MSG_TYPE__VOICE),
		RequestToAddFriend (WECHAT_MSG_TYPE__REQUEST_TO_ADD_FRIEND),
		PossibleFriendMessage (WECHAT_MSG_TYPE__POSSIBLE_FRIEND_MSG),
		名片WeChatVCard (WECHAT_MSG_TYPE__WECHAT_VCARD),
		视频通话 (WECHAT_MSG_TYPE__VIDEO_CALL),
		表情图 (WECHAT_MSG_TYPE__EMOTION),
		位置 (WECHAT_MSG_TYPE__GPS_POSITION),
		链接 (WECHAT_MSG_TYPE__URL),
		VOIP (WECHAT_MSG_TYPE__VOIP_MSG),
		初始化或操作 (WECHAT_MSG_TYPE__OPERATION),
		VOIP通话通知 (WECHAT_MSG_TYPE__VOIP_NOTIFY),
		VOIP通话发起 (WECHAT_MSG_TYPE__VOIP_INVITE),
		小视频 (WECHAT_MSG_TYPE__SHORT_VIDEO),
		系统通知 (WECHAT_MSG_TYPE__SYSTEM_NOTICE),
		系统 (WECHAT_MSG_TYPE__SYSTEM),
		消息撤回 (WECHAT_MSG_TYPE__MSG_REVOKED),
		;

		int value = 0;
		WeChatMsgType (int nType)
		{
			value = nType;
		}
		public int GetValue ()
		{
			return value;
		}
		public int ValueOf (int nType)
		{
			switch (nType)
			{
			}
		}
	}

	class KeepSessionAliveTask extends TimerTask
	{

		@Override
		public void run ()
		{
			if (loggedIn)
			{
				try
				{
					// 在微信网页版中观察到【可能是】因为调用了这个，微信网页版在登录 12 小时后不用重新登录。尝试一下… （或许是 pingd 接口？）
					//StatisticsReport ();
					FakeStatisticsReport ();
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}

		}

	}
	*/

	Future<?> engineTask = null;
	//TimerTask timertaskKeepSessionAlive = new KeepSessionAliveTask ();
	//Timer timerKeepSessionAlive = new Timer ();

	List<net_maclife_wechat_http_Bot> listBots = new ArrayList<net_maclife_wechat_http_Bot> ();

	boolean loggedIn  = false;

	String sUserID     = null;
	String sSessionID  = null;
	String sSessionKey = null;
	String sPassTicket = null;

	JsonNode jsonMe       = null;
	String sMyEncryptedAccountInThisSession = null;
	String sMyCustomAccount   = null;	// 微信号
	String sMyNickName    = null;	// 昵称
	//String sMyRemarkName  = null;
	JsonNode jsonContacts = null;	// 联系人 JsonNode，注意，这个 JsonNode 是获取联系人是获取到的完整 JSON 消息体，因此包含了 BaseResponse 等额外信息，需要用 MemberList 获取真正的联系人
	JsonNode jsonRoomsContacts = null;

	String sLastFromAccount = null;	// 收到的最后一条消息的发送人帐号
	String sLastFromName = null;	// 收到的最后一条消息的发送人名称

	volatile boolean bStopFlag = false;

	public net_maclife_wechat_http_BotEngine ()
	{
	}

	public void Start ()
	{
		bStopFlag = false;
		LoadBots ();
		engineTask = net_maclife_wechat_http_BotApp.executor.submit (this);
		//timerKeepSessionAlive.schedule (timertaskKeepSessionAlive, net_maclife_wechat_http_BotApp.GetConfig ().getInt ("engine.keep-session-alive-timer.delay-minutes")*60*1000, net_maclife_wechat_http_BotApp.GetConfig ().getInt ("engine.keep-session-alive-timer.interval-minutes")*60*1000);
	}

	public void Stop ()
	{
		bStopFlag = true;
		UnloadAllBots ();

		if (engineTask!=null && !engineTask.isCancelled ())
		{
			engineTask.cancel (true);
		}
		//timertaskKeepSessionAlive.cancel ();
		//timerKeepSessionAlive.cancel ();
	}

	public void LoadBot (String sBotClassName)
	{
		try
		{
			Class<?> botClass = Class.forName (sBotClassName);
			Object obj = botClass.newInstance ();
			if (obj instanceof net_maclife_wechat_http_Bot)
			{
				net_maclife_wechat_http_Bot newBot = (net_maclife_wechat_http_Bot) obj;
				boolean bAlreadyLoaded = false;
				// 检查有没有该类的实例存在，有的话，则不再重复添加
				for (int i=0; i<listBots.size (); i++)
				{
					net_maclife_wechat_http_Bot bot = listBots.get (i);
					//if (bot.getClass ().isInstance (obj))
						if (botClass.isInstance (bot))
						{
							bAlreadyLoaded = true;
net_maclife_wechat_http_BotApp.logger.warning (net_maclife_util_ANSIEscapeTool.Yellow ("已经加载过 " + bot.GetName() + " 机器人，不重复加载"));
							break;
						}
					}

					if (! bAlreadyLoaded)
					{
						newBot.SetEngine (this);
						newBot.Start ();
						listBots.add (newBot);
net_maclife_wechat_http_BotApp.logger.info (net_maclife_util_ANSIEscapeTool.Green (newBot.GetName () + " 机器人已创建并加载"));
				}
			}
			//
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
	}

	public void LoadBots ()
	{
		List<String> listBotClassNames = net_maclife_wechat_http_BotApp.GetConfig ().getList (String.class, "engine.bots.load.classNames");
		if (listBotClassNames != null)
			for (String sBotFullClassName : listBotClassNames)
			{
				if (StringUtils.isEmpty (sBotFullClassName))
					continue;

				LoadBot (sBotFullClassName);
			}
	}

	public void UnloadBot (net_maclife_wechat_http_Bot bot)
	{
		try
		{
			listBots.remove (bot);
			bot.Stop ();
net_maclife_wechat_http_BotApp.logger.info (net_maclife_util_ANSIEscapeTool.Brown (bot.GetName () + " 机器人已被卸载"));
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
	}

	public void UnloadBot (String sBotFullClassName)
	{
		try
		{
			boolean bFound = false;
			for (int i=0; i<listBots.size (); i++)
			{
				net_maclife_wechat_http_Bot bot = listBots.get (i);
				if (StringUtils.equalsIgnoreCase (bot.getClass ().getCanonicalName (), sBotFullClassName))
				{
					bFound = true;
					UnloadBot (bot);
					break;
				}
			}
			if (! bFound)
			{
net_maclife_wechat_http_BotApp.logger.warning (net_maclife_util_ANSIEscapeTool.Yellow ("在已加载的机器人列表中找不到 " + sBotFullClassName + " 机器人"));
			}
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
	}
	public void UnloadAllBots ()
	{
		try
		{
			for (int i=listBots.size ()-1; i>=0; i--)
			{
				net_maclife_wechat_http_Bot bot = listBots.remove (i);
				UnloadBot (bot);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
	}

	public void ListBots ()
	{
		for (int i=listBots.size ()-1; i>=0; i--)
		{
			net_maclife_wechat_http_Bot bot = listBots.get (i);
			//UnloadBot (bot);
net_maclife_wechat_http_BotApp.logger.info (bot.GetName () + " (" + net_maclife_util_ANSIEscapeTool.White (bot.getClass ().getCanonicalName ()) + ")");
		}
	}

	public boolean IsMe (String  sEncryptedAccount)
	{
		return StringUtils.equalsIgnoreCase (sMyEncryptedAccountInThisSession, sEncryptedAccount);
	}

	JsonNode Init () throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		return net_maclife_wechat_http_BotApp.WebWeChatInit (sUserID, sSessionID, sSessionKey, sPassTicket);
	}

	JsonNode EmptyStatisticsReport () throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		return net_maclife_wechat_http_BotApp.WebWeChatStatisticsReport (sUserID, sSessionID, sSessionKey, sPassTicket, sMyEncryptedAccountInThisSession);
	}

	JsonNode FakeStatisticsReport () throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		ArrayNode jsonFakeData = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Strict.createArrayNode ();
		ObjectNode jsonFakeData_data1 = jsonFakeData.insertObject (0);
		jsonFakeData_data1.put ("Type", 1);
		ObjectNode jsonFakeData_data1_text = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Strict.createObjectNode ();
		jsonFakeData_data1_text.put ("type", "[action-record]");
		ObjectNode jsonFakeData_data1_text_data = jsonFakeData_data1_text.putObject ("data");
		ArrayNode jsonFakeData_data1_text_data_actions = jsonFakeData_data1_text_data.putArray ("actions");
		ObjectNode jsonFakeData_data1_text_data_actions_action1 = jsonFakeData_data1_text_data_actions.insertObject (0);
			jsonFakeData_data1_text_data_actions_action1.put ("type", "resize");
			jsonFakeData_data1_text_data_actions_action1.put ("time", System.currentTimeMillis ());
			String sRandomFakeResizeAction = (net_maclife_wechat_http_BotApp.random.nextBoolean () ? "width" : "height") + "-" + (net_maclife_wechat_http_BotApp.random.nextBoolean () ? "bigger" : "smaller");
			jsonFakeData_data1_text_data_actions_action1.put ("action", sRandomFakeResizeAction);
		jsonFakeData_data1.put ("Text", net_maclife_wechat_http_BotApp.jacksonObjectMapper_Strict.writeValueAsString (jsonFakeData_data1_text));

		return net_maclife_wechat_http_BotApp.WebWeChatStatisticsReport (sUserID, sSessionID, sSessionKey, sPassTicket, sMyEncryptedAccountInThisSession, jsonFakeData);
	}

	JsonNode StatusNotify () throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		return net_maclife_wechat_http_BotApp.WebWeChatStatusNotify (sUserID, sSessionID, sSessionKey, sPassTicket, sMyEncryptedAccountInThisSession);
	}

	JsonNode GetContacts () throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		return net_maclife_wechat_http_BotApp.WebWeChatGetContacts (sUserID, sSessionID, sSessionKey, sPassTicket);
	}

	JsonNode GetRoomContacts (List<String> listRoomAccounts) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		return net_maclife_wechat_http_BotApp.WebWeChatGetRoomContacts (sUserID, sSessionID, sSessionKey, sPassTicket, listRoomAccounts);
	}

	JsonNode GetRoomContacts (String sRoomAccount) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		List<String> listRoomAccounts = new ArrayList<String> ();
		listRoomAccounts.add (sRoomAccount);
		return GetRoomContacts (listRoomAccounts);
	}

	JsonNode GetMessagePackage (JsonNode jsonSyncCheckKeys) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, ScriptException, URISyntaxException, InterruptedException
	{
		return net_maclife_wechat_http_BotApp.WebWeChatGetMessagePackage (sUserID, sSessionID, sSessionKey, sPassTicket, jsonSyncCheckKeys);
	}

	public void Logout ()
	{
		try
		{
			net_maclife_wechat_http_BotApp.WebWeChatLogout (sUserID, sSessionID, sSessionKey, sPassTicket);
			loggedIn = false;
			OnLoggedOut ();
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
	}

	/**
	 * 发送文本消息。发送文本消息时，会根据传入的参数做以下处理：
	 *  1.在附加时间信息时插入一个空行（排版目的）；
	 *  2. 如果接收人昵称不为空，并且他/她以 @我 的方式发送的消息，则也加上一个 @对方 的信息行。
	 * @param sToAccount
	 * @param sToName
	 * @param sToAccount_RoomMember
	 * @param sToName_RoomMember
	 * @param sMessage
	 * @param bInsertExtraNewLineBeforeTimestamp
	 * @param bMentionedMeInIncomingRoomMessage
	 * @param bMentionedMeFirstnIncomingRoomMessage
	 */
	public void SendTextMessage (String sToAccount, String sToName, String sToAccount_RoomMember, String sToName_RoomMember, String sMessage, boolean bInsertExtraNewLineBeforeTimestamp, boolean bMentionedMeInIncomingRoomMessage, boolean bMentionedMeFirstnIncomingRoomMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		if (net_maclife_wechat_http_BotApp.ParseBoolean (net_maclife_wechat_http_BotApp.GetConfig ().getString ("engine.message.text.append-timestamp", "yes")))
		{
			sMessage = sMessage + (bInsertExtraNewLineBeforeTimestamp ? "\n" : "") + "\n" + new java.sql.Timestamp (System.currentTimeMillis ());
		}
		if (StringUtils.isEmpty (sToAccount_RoomMember))
		{	// 私信，直接发送
			net_maclife_wechat_http_BotApp.WebWeChatSendTextMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sMyEncryptedAccountInThisSession, sToAccount, sMessage);
		}
		else
		{	// 聊天室，需要做一下处理： @一下发送人，然后是消息
			net_maclife_wechat_http_BotApp.WebWeChatSendTextMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sMyEncryptedAccountInThisSession, sToAccount, (bMentionedMeFirstnIncomingRoomMessage && StringUtils.isNotEmpty (sToName_RoomMember) ? "@" + sToName_RoomMember + "\n" : "") + sMessage);
		}
	}

	public void SendTextMessage (String sToAccount, String sToName, String sTo_RoomMemberAccount, String sToName_RoomMember, String sMessage, boolean bInsertExtraNewLineBeforeTimestamp) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		SendTextMessage (sToAccount, sToName, sTo_RoomMemberAccount, sToName_RoomMember, sMessage, bInsertExtraNewLineBeforeTimestamp, false, false);
	}
	public void SendTextMessage (String sToAccount, String sToName, String sTo_RoomMemberAccount, String sToName_RoomMember, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		SendTextMessage (sToAccount, sToName, sTo_RoomMemberAccount, sToName_RoomMember, sMessage, true, false, false);
	}

	/**
	 * 发文本消息到群。只有明确知道消息是发往群的时候，才用这个函数
	 * @param sToAccount
	 * @param sTo_RoomMemberAccount
	 * @param sToName_RoomMember
	 * @param sMessage
	 * @param bInsertExtraNewLineBeforeTimestamp
	 */
	public void SendTextMessage (String sToAccount, String sTo_RoomMemberAccount, String sToName_RoomMember, String sMessage, boolean bInsertExtraNewLineBeforeTimestamp) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		SendTextMessage (sToAccount, null, sTo_RoomMemberAccount, sToName_RoomMember, sMessage, bInsertExtraNewLineBeforeTimestamp);
	}

	/**
	 * 发文本消息到群。只有明确知道消息是发往群的时候，才用这个函数
	 * @param sToAccount
	 * @param sTo_RoomMemberAccount
	 * @param sToName_RoomMember
	 * @param sMessage
	 */
	public void SendTextMessage (String sToAccount, String sTo_RoomMemberAccount, String sToName_RoomMember, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		SendTextMessage (sToAccount, null, sTo_RoomMemberAccount, sToName_RoomMember, sMessage);
	}


	/**
	 * 简化的发文本消息。
	 * @param sToAccount 接收人帐号
	 * @param sToName 接收人名称
	 * @param sMessage 消息内容
	 * @param bInsertExtraNewLineBeforeTimestamp
	 */
	public void SendTextMessage (String sToAccount, String sToName, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		SendTextMessage (sToAccount, sToName, null, null, sMessage);
	}

	/**
	 * 最简化的发文本消息。
	 * @param sToAccount 接收人帐号
	 * @param sMessage 消息内容
	 * @param bInsertExtraNewLineBeforeTimestamp
	 */
	public void SendTextMessage (String sToAccount, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		SendTextMessage (sToAccount, null, null, null, sMessage);
	}

	/**
	 * 最简化的发图片消息。
	 * @param sToAccount 接收人帐号
	 * @param sMediaID 消息内容
	 * @param bInsertExtraNewLineBeforeTimestamp
	 */
	public void SendImageMessage (String sToAccount, String sMediaID) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		File f = null;
		// 先上传文件
		net_maclife_wechat_http_BotApp.WebWeChatUploadMedia (f);

		// 再用上传返回的 MediaID 把图片消息（已不是图片本身）发出
		net_maclife_wechat_http_BotApp.WebWeChatSendImageMessage (sUserID, sSessionID, sSessionKey, sPassTicket, this.sMyEncryptedAccountInThisSession, sToAccount, sMediaID);
	}

	/**
	 * Bot 发送文本消息。Bot 发送文本消息时，会把消息前后的空白剔除，然后根据配置，可能附加上 Bot 名称。
	 * @param bot
	 * @param sToAccount
	 * @param sToAccount_RoomMember
	 * @param sToName
	 * @param sMessage
	 * @throws KeyManagementException
	 * @throws UnrecoverableKeyException
	 * @throws JsonProcessingException
	 * @throws NoSuchAlgorithmException
	 * @throws KeyStoreException
	 * @throws CertificateException
	 * @throws IOException
	 */
	public void BotSendTextMessage (net_maclife_wechat_http_Bot bot, String sToAccount, String sToName, String sToAccount_RoomMember, String sToName_RoomMember, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		sMessage = StringUtils.trimToEmpty (sMessage);
		if (net_maclife_wechat_http_BotApp.ParseBoolean (net_maclife_wechat_http_BotApp.GetConfig ().getString ("engine.message.text.append-bot-name", "yes")))
		{
			sMessage = sMessage + "\n\n-- 【" + bot.GetName () + "】机器人";
			SendTextMessage (sToAccount, sToName, sToAccount_RoomMember, sToName_RoomMember, sMessage, false);
		}
		else
			SendTextMessage (sToAccount, sToName, sToAccount_RoomMember, sToName_RoomMember, sMessage, true);
	}
	public void BotSendTextMessage (net_maclife_wechat_http_Bot bot, String sToAccount, String sToName, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		BotSendTextMessage (bot, sToAccount, sToName, null, null, sMessage);
	}
	public void BotSendTextMessage (net_maclife_wechat_http_Bot bot, String sToAccount, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		BotSendTextMessage (bot, sToAccount, null, null, null, sMessage);
	}

	/**
	 * 回复文字消息，消息接收人是最后一条消息的发件人。
	 * @param sMessage
	 * @return
	 */
	public void ReplyTextMessage (String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		if (StringUtils.isEmpty (sLastFromAccount))
		{
net_maclife_wechat_http_BotApp.logger.warning (net_maclife_util_ANSIEscapeTool.Yellow ("尚未接收到任何消息，不知道该回复给谁"));
			return;
		}

		SendTextMessage (sLastFromAccount, sMessage);
	}

	// -------------------------------------------------------------------------
	// 联系人（包括群联系人）、群成员 搜索、维护（增删改）
	// -------------------------------------------------------------------------

	public List<JsonNode> SearchForContacts (String sEncryptedAccountInThisSession, String sAliasAccount, String sRemarkName, String sNickName)
	{
		return net_maclife_wechat_http_BotApp.SearchForContacts (jsonContacts.get ("MemberList"), sEncryptedAccountInThisSession, sAliasAccount, sRemarkName, null, sNickName);
	}
	public JsonNode SearchForSingleContact (String sEncryptedAccountInThisSession, String sAliasAccount, String sRemarkName, String sNickName)
	{
		return net_maclife_wechat_http_BotApp.SearchForSingleContact (jsonContacts.get ("MemberList"), sEncryptedAccountInThisSession, sAliasAccount, sRemarkName, null, sNickName);
	}
	public JsonNode SearchForSingleContact (String sEncryptedAccountInThisSession)
	{
		return SearchForSingleContact (sEncryptedAccountInThisSession, null, null, null);
	}

	/**
	 * 用 jsonChangedContact 取代当前联系人列表中相同 "UserName" 的原联系人，如果找不到原来的联系人（比如：未添加到通讯录的群联系人），则直接加入到联系人列表中。
	 * 主要用于 ModContact 事件更新联系人
	 * @param jsonChangedContact
	 * @return 如果找不到原来的联系人，则将该联系人加入到通讯录中，并返回该新联系人。 如果找到并替换，则原样返回被替换的原来的联系人信息
	 */
	public JsonNode ReplaceOrAddContact (JsonNode jsonChangedContact)
	{
		if (jsonContacts == null)
			return null;
		JsonNode jsonContactsMemberList = jsonContacts.get ("MemberList");
		if (jsonContactsMemberList == null)
			return null;
		String sAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonChangedContact, "UserName");
		for (int i=0; i<jsonContactsMemberList.size (); i++)
		{
			JsonNode jsonOldContact = jsonContactsMemberList.get (i);
			if (StringUtils.equalsIgnoreCase (sAccount, net_maclife_wechat_http_BotApp.GetJSONText (jsonOldContact, "UserName")))
			{
				((ArrayNode)jsonContactsMemberList).set (i, jsonChangedContact);
				return jsonOldContact;
				//break;
			}
		}
		((ArrayNode)jsonContactsMemberList).add (jsonChangedContact);
		return jsonChangedContact;
	}

	/**
	 * 根据 jsonDeletedContact ，删除当前联系人列表中相同 "UserName" 的联系人。
	 * 主要用于 DelContact 事件联系人更新
	 * @param jsonDeletedContact
	 * @return 如果找不到原来的联系人，则返回 null。 如果找到并删除，则返回被删除的原来的联系人信息
	 */
	public JsonNode DeleteContact (JsonNode jsonDeletedContact)
	{
		if (jsonContacts == null)
			return null;
		String sAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonDeletedContact, "UserName");
		for (int i=0; i<jsonContacts.size (); i++)
		{
			JsonNode jsonOldContact = jsonContacts.get (i);
			if (StringUtils.equalsIgnoreCase (sAccount, net_maclife_wechat_http_BotApp.GetJSONText (jsonOldContact, "UserName")))
			{
				((ArrayNode)jsonContacts).remove (i);
				return jsonOldContact;
				//break;
			}
		}
		return null;
	}

	public JsonNode GetRoomByRoomAccount (String sRoomAccount)
	{
		JsonNode jsonRooms = jsonRoomsContacts.get ("ContactList");
		JsonNode jsonRoom = null;
		for (int i=0; i<jsonRooms.size (); i++)
		{
			jsonRoom = jsonRooms.get (i);
			if (StringUtils.equalsIgnoreCase (sRoomAccount, net_maclife_wechat_http_BotApp.GetJSONText (jsonRoom, "UserName")))
				return jsonRoom;
		}

		// 如果找不到聊天室，则尝试重新获取一次
		try
		{
			JsonNode jsonThisRoomContact = GetRoomContacts (sRoomAccount);
			JsonNode jsonThisRooms = jsonThisRoomContact.get ("ContactList");
			if (jsonThisRooms.size () == 1)
			{
				jsonRoom = jsonThisRooms.get (0);
				((ArrayNode)jsonRooms).add (jsonRoom);
				return jsonRoom;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}
	public List<JsonNode> SearchForContactsInRoom (String sRoomAccountInThisSession, String sRoomMemberAccountInThisSession, String sAliasAccount, String sDisplayName, String sNickName)
	{
		JsonNode jsonRoom = GetRoomByRoomAccount (sRoomAccountInThisSession);
		return net_maclife_wechat_http_BotApp.SearchForContacts (jsonRoom.get ("MemberList"), sRoomMemberAccountInThisSession, sAliasAccount, null, sDisplayName, sNickName);
	}
	public JsonNode SearchForSingleMemberContactInRoom (JsonNode jsonRoom, String sRoomMemberAccountInThisSession, String sAliasAccount, String sDisplayName, String sNickName)
	{
		return net_maclife_wechat_http_BotApp.SearchForSingleContact (jsonRoom.get ("MemberList"), sRoomMemberAccountInThisSession, sAliasAccount, null, sDisplayName, sNickName);
	}
	public JsonNode SearchForSingleMemberContactInRoom (String sRoomAccountInThisSession, String sRoomMemberAccountInThisSession, String sAliasAccount, String sDisplayName, String sNickName)
	{
		JsonNode jsonRoom = GetRoomByRoomAccount (sRoomAccountInThisSession);
		return SearchForSingleMemberContactInRoom (jsonRoom, sRoomMemberAccountInThisSession, sAliasAccount, sDisplayName, sNickName);
	}
	public JsonNode SearchForSingleMemberContactInRoom (JsonNode jsonRoom, String sRoomMemberAccountInThisSession)
	{
		return SearchForSingleMemberContactInRoom (jsonRoom, sRoomMemberAccountInThisSession, null, null, null);
	}
	public JsonNode SearchForSingleMemberContactInRoom (String sRoomAccountInThisSession, String sRoomMemberAccountInThisSession)
	{
		return SearchForSingleMemberContactInRoom (sRoomAccountInThisSession, sRoomMemberAccountInThisSession, null, null, null);
	}

	public boolean IsRoomTextMessageMentionedMe (String sRoomTextMessage, String sMyDisplayNameInARoom)
	{
		return net_maclife_wechat_http_BotApp.IsRoomTextMessageMentionedThisOne (sRoomTextMessage, sMyNickName, sMyDisplayNameInARoom);
	}

	public boolean IsRoomTextMessageMentionedMe (String sRoomTextMessage, JsonNode jsonContactInRoom)
	{
		return net_maclife_wechat_http_BotApp.IsRoomTextMessageMentionedThisOne (sRoomTextMessage, jsonContactInRoom);
	}

	public boolean IsRoomTextMessageMentionedMeFirst (String sRoomTextMessage, String sMyDisplayNameInARoom)
	{
		return net_maclife_wechat_http_BotApp.IsRoomTextMessageMentionedThisOneFirst (sRoomTextMessage, sMyNickName, sMyDisplayNameInARoom);
	}

	public boolean IsRoomTextMessageMentionedMeFirst (String sRoomTextMessage, JsonNode jsonContactInRoom)
	{
		return net_maclife_wechat_http_BotApp.IsRoomTextMessageMentionedThisOneFirst (sRoomTextMessage, jsonContactInRoom);
	}


	public static String GetContactName (JsonNode jsonContact)
	{
		if (jsonContact == null)
			return null;

		String sName = null;
		String sRemarkName = net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "RemarkName");
		if (StringUtils.isNotEmpty (sRemarkName))
			sName = sRemarkName;
		else
			sName = net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "NickName");

		return sName;
	}

	/**
	 * 从通信录中获取联系人（包括群）的名称。获取的联系人名称不建议用来 @ 回复（因为可能是你自己给出的备注名称 -- 别人可能不认识该名称）。
	 * 注意： 对于群，有可能没被加在通讯录中，这时从通讯录中会取不到，因此，如果在通讯录中找不到，则需要针对群通讯录单独再取一次“群联系人”
	 * @param sEncryptedContactAccount
	 * @param jsonContact
	 * @return 如果 RemarkName (备注名) 不为空，则取 RemarkName，否则取 NickName (昵称)
	 */
	String GetContactName (String sEncryptedContactAccount, JsonNode jsonContact)
	{
		if (jsonContact == null)
		{
			jsonContact = SearchForSingleContact (sEncryptedContactAccount, null, null, null);
			if (jsonContact == null && net_maclife_wechat_http_BotApp.IsRoomAccount(sEncryptedContactAccount))
			{	// 未加到通讯录的群
				jsonContact = GetRoomByRoomAccount (sEncryptedContactAccount);
				if (jsonContact != null)
				{	// 缓存到内存中
					//((ArrayNode)jsonContacts.get ("MemberList")).add (jsonContact);
					ReplaceOrAddContact (jsonContact);
				}
			}
		}
		if (jsonContact == null)
			return null;

		return GetContactName (jsonContact);
	}
	String GetContactName (String sEncryptedContactAccount)
	{
		return GetContactName (sEncryptedContactAccount, null);
	}

	public static String GetMemberContactNameInRoom (JsonNode jsonRoomMemberContact)
	{
		if (jsonRoomMemberContact == null)
			return null;

		String sName = null;
		String sDisplayName = net_maclife_wechat_http_BotApp.GetJSONText (jsonRoomMemberContact, "DisplayName");
		if (StringUtils.isNotEmpty (sDisplayName))
			sName = sDisplayName;
		else
			sName = net_maclife_wechat_http_BotApp.GetJSONText (jsonRoomMemberContact, "NickName");

		return sName;
	}

	/**
	 * 从聊天室的联系人中获取人名。获取的人名可以用来直接 @ 回复。
	 * @param sEncryptedRoomAccount
	 * @param sEncryptedContactAccount
	 * @param jsonRoomMemberContact
	 * @return 如果 DisplayName (本聊天室的昵称) 不为空，则取 DisplayName，否则取 NickName (昵称)
	 */
	String GetMemberContactNameInRoom (String sEncryptedRoomAccount, String sEncryptedContactAccount, JsonNode jsonRoomMemberContact)
	{
		if (jsonRoomMemberContact == null)
			jsonRoomMemberContact = SearchForSingleMemberContactInRoom (sEncryptedRoomAccount, sEncryptedContactAccount);
		if (jsonRoomMemberContact == null)
		{
			//这里要重新获取一下该群的通讯录，因为，可能是与新成员加入了
			try
			{
				JsonNode jsonThisRoomContact = GetRoomContacts (sEncryptedRoomAccount);
				if (jsonThisRoomContact != null)
				{
					ReplaceOrAddRoomContact (jsonThisRoomContact);
					jsonRoomMemberContact = SearchForSingleMemberContactInRoom (jsonThisRoomContact, sEncryptedContactAccount);
					return GetMemberContactNameInRoom (jsonRoomMemberContact);
				}
			}
			catch (KeyManagementException | UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | CertificateException | IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}

		return GetMemberContactNameInRoom (jsonRoomMemberContact);
	}
	String GetMemberContactNameInRoom (String sEncryptedRoomAccount, String sEncryptedContactAccount)
	{
		return GetMemberContactNameInRoom (sEncryptedRoomAccount, sEncryptedContactAccount, null);
	}

	/**
	 * 用 jsonRoomContact 取代当前群列表中相同 "UserName" 的原群，如果找不到原来的群（比如：未添加到通讯录的群联系人）。
	 * 主要用于 ModContact 事件更新联系人
	 * @param jsonRoomContact
	 * @return 如果找不到原来的联系人，则将该联系人加入到通讯录中，并返回该新联系人。 如果找到并替换，则原样返回被替换的原来的联系人信息
	 */
	public JsonNode ReplaceOrAddRoomContact (JsonNode jsonRoomContact)
	{
		if (jsonRoomsContacts == null)
			return null;
		JsonNode jsonContactsList = jsonRoomsContacts.get ("ContactList");
		if (jsonContactsList == null)
			return null;
		String sAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonRoomContact, "UserName");
		for (int i=0; i<jsonContactsList.size (); i++)
		{
			JsonNode jsonOldContact = jsonContactsList.get (i);
			if (StringUtils.equalsIgnoreCase (sAccount, net_maclife_wechat_http_BotApp.GetJSONText (jsonOldContact, "UserName")))
			{
				((ArrayNode)jsonContactsList).set (i, jsonRoomContact);
				return jsonOldContact;
				//break;
			}
		}
		((ArrayNode)jsonContactsList).add (jsonRoomContact);
		return jsonRoomContact;
	}



	private JsonNode GetSessionCache () throws JsonProcessingException, IOException
	{
		File fSessionCache = new File (sSessionCacheFileName);
		return net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (fSessionCache);
	}

	private void SaveSessionCache (File fSessionCache, JsonNode jsonSyncCheckKey)
	{
		try
		{
			String sSessionCache_JSONString =
				"{\n\tUserID: \"" + sUserID + "\"" +
				",\n	SessionID: \"" + sSessionID + "\"" +
				",\n	SessionKey: \"" + sSessionKey + "\"" +
				",\n	PassTicket: \"" + sPassTicket + "\"" +
				",\n	SyncCheckKeys: " + jsonSyncCheckKey +
				",\n	EncryptedAccountInThisSession: \"" + sMyEncryptedAccountInThisSession + "\"" +
				",\n	CustomAccount: \"" + sMyCustomAccount + "\"" +
				",\n	NickName: \"" + sMyNickName + "\"" +
				"\n}";
			OutputStream os = new FileOutputStream (fSessionCache);
			IOUtils.write (sSessionCache_JSONString, os, net_maclife_wechat_http_BotApp.utf8);
			os.close ();
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
	}

	/**
	 * Bot 引擎线程： 不断循环尝试登录，直到登录成功。如果登录成功后被踢下线，依旧不断循环尝试登录…… 登录成功后，不断同步消息，直到被踢下线（同上，依旧不断循环尝试登录）
	 */
	@Override
	public void run ()
	{
		int nWaitLoginCount = 0;
		int nWaitTimeToRetry = 0;
		try
		{
			String sLoginID = null;
			Map<String, Object> mapWaitLoginResult = null;
			Object o = null;
			boolean bSessionExpired = false;
		_outer_loop:
			while (! Thread.interrupted () && !bStopFlag)
			{
				JsonNode jsonSyncCheckKeys = null;
				try
				{
					File fSessionCache = new File (sSessionCacheFileName);
					if (!bSessionExpired && fSessionCache.exists () && ((fSessionCache.lastModified () + 12 * 3600 * 1000) > System.currentTimeMillis ()))	// 目前的微信 Session 只有 12 小时的生命
					{
						JsonNode jsonSessionCache = GetSessionCache ();
						sUserID     = net_maclife_wechat_http_BotApp.GetJSONText (jsonSessionCache, "UserID");
						sSessionID  = net_maclife_wechat_http_BotApp.GetJSONText (jsonSessionCache, "SessionID");
						sSessionKey = net_maclife_wechat_http_BotApp.GetJSONText (jsonSessionCache, "SessionKey");
						sPassTicket = net_maclife_wechat_http_BotApp.GetJSONText (jsonSessionCache, "PassTicket");
						jsonSyncCheckKeys = jsonSessionCache.get ("SyncCheckKeys");
						sMyEncryptedAccountInThisSession = net_maclife_wechat_http_BotApp.GetJSONText (jsonSessionCache, "EncryptedAccountInThisSession");
						sMyCustomAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonSessionCache, "CustomAccount");
						sMyNickName = net_maclife_wechat_http_BotApp.GetJSONText (jsonSessionCache, "NickName");
net_maclife_wechat_http_BotApp.logger.info ("缓存的 Session 信息\n	UIN: " + sUserID + "\n	SID: " + sSessionID + "\n	SKEY: " + sSessionKey + "\n	TICKET: " + sPassTicket + "\n	EncryptedAccountInThisSession: " + sMyEncryptedAccountInThisSession + "\n	CustomAccount/Alias: " + sMyCustomAccount + "\n	NickName: " + sMyNickName + "\n	SyncCheckKeys: " + jsonSyncCheckKeys + "\n");
					}
					else
					{
						// 1. 获得登录 ID
						sLoginID = net_maclife_wechat_http_BotApp.GetNewLoginID ();

						// 2. 根据登录 ID，获得登录地址的二维码图片 （暂时只能扫描图片，不能根据登录地址自动登录 -- 暂时无法截获手机微信 扫描二维码以及确认登录时 时带的参数，所以无法模拟自动登录）
						net_maclife_wechat_http_BotApp.GetLoginQRCodeImageFile (sLoginID);

						// 3. 等待二维码扫描（）、以及确认登录
						do
						{
							o = net_maclife_wechat_http_BotApp.等待二维码被扫描以便登录 (sLoginID);
							if (o instanceof Integer)
							{
								int n = (Integer) o;
								if (n == 400)	// Bad Request / 二维码已失效
								{
									nWaitLoginCount ++;
									nWaitTimeToRetry = nWaitTimeToRetry + 5 * nWaitLoginCount;
									if (nWaitTimeToRetry > 3600)
										nWaitTimeToRetry = 3600;	// 重试间隔最长不超过 1 小时
net_maclife_wechat_http_BotApp.logger.info (net_maclife_util_ANSIEscapeTool.Gray ("等 " + nWaitTimeToRetry + " 秒后 (" + (new java.sql.Timestamp (System.currentTimeMillis () + nWaitTimeToRetry*1000)) + ") 重试"));
									TimeUnit.SECONDS.sleep (nWaitTimeToRetry);
									continue _outer_loop;
								}
								else	// 大概只有 200 才能出来：当是 200 时，但访问登录页面失败时，可能会跑到此处
								{
									//
								}
							}
						} while (! (o instanceof Map<?, ?>) && !bStopFlag);
						nWaitLoginCount = 0;
						nWaitTimeToRetry = 0;

						mapWaitLoginResult = (Map<String, Object>) o;
						sUserID     = (String) mapWaitLoginResult.get ("UserID");
						sSessionID  = (String) mapWaitLoginResult.get ("SessionID");
						sSessionKey = (String) mapWaitLoginResult.get ("SessionKey");
						sPassTicket = (String) mapWaitLoginResult.get ("PassTicket");
						bSessionExpired = false;
net_maclife_wechat_http_BotApp.logger.info ("新获取到的 Session 信息\n	UIN: " + sUserID + "\n	SID: " + sSessionID + "\n	SKEY: " + sSessionKey + "\n	TICKET: " + sPassTicket + "\n");

						// 4. 确认登录后，初始化 Web 微信，返回初始信息
						JsonNode jsonInit = Init ();
						jsonMe = jsonInit.get ("User");
						sMyEncryptedAccountInThisSession = net_maclife_wechat_http_BotApp.GetJSONText (jsonMe, "UserName");
						sMyCustomAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonMe, "Alias");
						sMyNickName = net_maclife_wechat_http_BotApp.GetJSONText (jsonMe, "NickName");
						jsonSyncCheckKeys = jsonInit.get ("SyncKey");
						SaveSessionCache (fSessionCache, jsonSyncCheckKeys);
					}

					JsonNode jsonStatusNotify = StatusNotify ();

					// 5. 获取联系人
					jsonContacts = GetContacts ();
					List<String> listRoomAccounts = net_maclife_wechat_http_BotApp.GetRoomAccountsFromContacts (jsonContacts);
					jsonRoomsContacts = GetRoomContacts (listRoomAccounts);	// 补全各个群的联系人列表

					// 触发“已登录”事件
					OnLoggedIn ();

					sLastFromAccount = null;
					sLastFromName = null;
					JsonNode jsonMessagePackage = null;
					try
					{
						while (! Thread.interrupted () && !bStopFlag)
						{
							jsonMessagePackage = GetMessagePackage (jsonSyncCheckKeys);
							if (jsonMessagePackage == null)
							{
								TimeUnit.SECONDS.sleep (2);
								continue;
							}

							JsonNode jsonBaseResponse = jsonMessagePackage.get ("BaseResponse");
							int nRet = net_maclife_wechat_http_BotApp.GetJSONInt (jsonBaseResponse, "Ret");
							String sErrMsg = net_maclife_wechat_http_BotApp.GetJSONText (jsonBaseResponse, "ErrMsg");
							if (nRet != 0)
							{
								System.err.print ("同步消息失败: 代码=" + nRet);
								if (StringUtils.isNotEmpty (sErrMsg))
								{
									System.err.print ("，消息=" + sErrMsg);
								}
								System.err.println ();
								TimeUnit.SECONDS.sleep (2);
								continue;
							}

							// 处理“接收”到的（实际是同步获取而来）消息
							jsonSyncCheckKeys = jsonMessagePackage.get ("SyncCheckKey");	// 新的 SyncCheckKeys
							SaveSessionCache (fSessionCache, jsonSyncCheckKeys);

							// 处理（实际上，应该交给 Bot 们处理）
							OnMessagePackageReceived (jsonMessagePackage);
						}
					}
					catch (IllegalStateException e)
					{
						bSessionExpired = true;
						continue _outer_loop;
					}
					catch (Throwable e)
					{
						e.printStackTrace ();
					}
				}
				catch (Throwable e)
				{
					e.printStackTrace ();
					// 因为在循环内，出现异常，先暂停一会，免得不断重复
					TimeUnit.SECONDS.sleep (2);
				}
			}
		}
		catch (Throwable e)
		{
			e.printStackTrace ();
		}

net_maclife_wechat_http_BotApp.logger.warning (net_maclife_util_ANSIEscapeTool.Yellow ("bot 线程退出"));
		OnShutdown ();
	}

	void OnLoggedIn ()
	{
		loggedIn = true;
		DispatchEvent ("OnLoggedIn", null, null, null, null, false, null, null, null, false, null, null, null, false, null, null, null, null, null, null, null, false, false, null, null);
	}

	void OnLoggedOut ()
	{
		DispatchEvent ("OnLoggedOut", null, null, null, null, false, null, null, null, false, null, null, null, false, null, null, null, null, null, null, null, false, false, null, null);
	}

	void OnShutdown ()
	{
		DispatchEvent ("OnShutdown", null, null, null, null, false, null, null, null, false, null, null, null, false, null, null, null, null, null, null, null, false, false, null, null);
	}

	/**
	 * 当收到消息包时…

<table border='1px' cellpadding='2' cellspacing='0'>
	<caption>AddMsg 消息的回复关系表</caption>

	<thead>
		<tr>
			<th>发自 From</th>
			<th>发往 To</th>
			<th>回复给 ReplyTo</th>
			<th>回复给群成员 ReplyTo_RoomMember</th>
			<th>回复给人 ReplyTo_Person</th>
		</tr>
	</thead>

	<tbody>
		<tr>
			<td rowspan='3'>我<br/>这是自己在其他设备（手机）上发的消息。<br/><b>这一点与 IRC 不同 - IRC 收不到自己发出的消息</b></td>
			<td>我</td>
			<td>我 (=To)</td>
			<td>x</td>
			<td>我</td>
		</tr>
		<tr>

			<td>他人</td>
			<td>他人 (=To)</td>
			<td>x</td>
			<td>他人</td>
		</tr>
		<tr>

			<td>群</td>
			<td>群 (=To)</td>
			<td>x</td>
			<td>x</td>
		</tr>
		<tr>
			<td>他人</td>
			<td>我</td>
			<td>他人 (=From)</td>
			<td>x</td>
			<td>他人</td>
		</tr>
		<tr>
			<td>群</td>
			<td>我</td>
			<td>群 (=From)</td>
			<td>群成员 (如果有的话。像是红包提醒的系统消息，则没有这个信息)</td>
			<td>群成员</td>
		</tr>
	</tbody>
</table>
	 * @param jsonMessagePackage 收到的消息包 (JsonNode 数据类型)
	 */
	void OnMessagePackageReceived (JsonNode jsonMessagePackage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
		int i = 0;

		DispatchEvent_WithMultithreadSwitch ("onmessagepackage", 0, jsonMessagePackage, null, null, null, false, null, null, null, false, null, null, null, false, null, null, null, null, null, null, null, false, false);

		int nAddMsgCount = net_maclife_wechat_http_BotApp.GetJSONInt (jsonMessagePackage, "AddMsgCount", 0);
		if (nAddMsgCount != 0)
		{
net_maclife_wechat_http_BotApp.logger.finest ("收到 " + nAddMsgCount + " 条新消息");
		}
		JsonNode jsonAddMsgList = jsonMessagePackage.get ("AddMsgList");
		for (i=0; i<nAddMsgCount; i++)
		{
			JsonNode jsonNode = jsonAddMsgList.get (i);
			String sMsgID = net_maclife_wechat_http_BotApp.GetJSONText (jsonNode, "MsgId");
			int nMsgType = net_maclife_wechat_http_BotApp.GetJSONInt (jsonNode, "MsgType");
			String sContent = net_maclife_wechat_http_BotApp.GetJSONText (jsonNode, "Content");
			sContent = StringUtils.replace (sContent, "<br/>", "\n");
			sContent = StringEscapeUtils.unescapeXml (sContent);
			//sContent = StringUtils.replaceEach (sContent, new String[]{"<br/>", "&lt;", "&gt;", "&amp;"}, new String[]{"\n", "<", ">", "&"});
			//sContent = StringEscapeUtils.unescapeHtml4 (sContent);

			String sFromAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonNode, "FromUserName");	// 发送人帐号，有可能是自己（在其他设备上发的）
			JsonNode jsonFrom = SearchForSingleContact (sFromAccount);
			String sFromName = GetContactName (sFromAccount);
			boolean isFromMe = IsMe (sFromAccount);
			boolean isFromPublicAccount = false;

			String sReplyToAccount = sFromAccount;
			JsonNode jsonReplyTo = jsonFrom;
			String sReplyToName = sFromName;

			String sReplyToAccount_RoomMember = null;
			JsonNode jsonReplyTo_RoomMember = null;
			String sReplyToName_RoomMember = null;

			String sToAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonNode, "ToUserName");	// 接收人帐号，有可能是其他人（自己在其他设备上发的）
			JsonNode jsonTo = SearchForSingleContact (sToAccount);
			String sToName = GetContactName (sToAccount);;
net_maclife_wechat_http_BotApp.logger.severe (net_maclife_util_ANSIEscapeTool.Green (sFromAccount) + " → " + net_maclife_util_ANSIEscapeTool.DarkCyan (sToAccount));	// 用最高级别的日志级别记录 来往 的帐号，用以从命令行发送消息时使用
			boolean isToMe = IsMe (sToAccount);

			if (isFromMe)
			{	// 收到自己的帐号从其他设备发的消息：发件人是自己、收件人是其他人（含自己，私聊）、聊天室（群聊）
				// 这时要改一下 ReplyTo
net_maclife_wechat_http_BotApp.logger.fine ("* 是自己发出的消息，现在改一下“回复给谁 / ReplyTo”");
				sReplyToAccount = sToAccount;
				jsonReplyTo = jsonTo;
				sReplyToName = sToName;
			}
			sLastFromAccount = sReplyToAccount;	// 最后消息的发送帐号要在修改 ReplyTo 后再设置，这样，就可以：在手机上打开一个聊天窗口，然后在 BotApp 命令行中用 /reply 发消息…

			boolean isReplyToRoom = net_maclife_wechat_http_BotApp.IsRoomAccount (sReplyToAccount);	// 是否来自（或者发往（上面改过 ReplyTo 后））群聊/聊天室、对端是否群聊/聊天室
			if (isReplyToRoom)
			{	// 如果是发自聊天室，则从聊天室的成员列表中获取真正的发送人（极有可能不在自己的联系人内，只能从聊天室成员列表中获取）
				// <del>因为之前已经交换过收发人，所以，自己点开群聊窗口后，不再做【获取真实发件人】的处理（只有真正别人在群里发过来的信息才需要这样处理）</del>
				// 自己发送到群聊的信息，也会出现 @xxxx:\n消息内容  的格式，则： 1.取出群聊成员发送人 2.取出去掉群聊成员后的消息内容
				Matcher matcher = PATTERN_GROUP_CHAT_ACTUAL_MEMBER.matcher (sContent);
				if (matcher.matches ())
				{
					//String[] arrayContents = sContent.split (":\n", 2);
					//sFromAccount_RoomMember = arrayContents[0];
					//if (arrayContents.length > 1)
					//	sContent = arrayContents[1];
					//else
					//	sContent = "";
					sReplyToAccount_RoomMember = matcher.group (1);
					sContent = matcher.group (2);
				}
				else
				{	// 像群里发红包的“系统消息”，就没有接收人
					//
				}

				// 找出发送人的 UserID
				if (isFromMe && StringUtils.isEmpty (sReplyToAccount_RoomMember))
				{
					sReplyToAccount_RoomMember = sFromAccount;
				}

				if (StringUtils.isNotEmpty (sReplyToAccount_RoomMember))
				{
					jsonReplyTo_RoomMember = SearchForSingleMemberContactInRoom (sReplyToAccount, sReplyToAccount_RoomMember);
					sReplyToName_RoomMember = GetMemberContactNameInRoom (sReplyToAccount, sReplyToAccount_RoomMember);
				}
				//sReplyToName = GetMemberContactNameInRoom (sReplyToAccount, sReplyToAccount_RoomMember);	// 尽可能的取群昵称
			}
			else
			{	//
				isFromPublicAccount = net_maclife_wechat_http_BotApp.IsPublicAccount (net_maclife_wechat_http_BotApp.GetJSONInt (jsonReplyTo, "VerifyFlag"));
			}
			sLastFromName = sReplyToName;

			String sReplyToAccount_Person = sReplyToAccount;
			JsonNode jsonReplyTo_Person = jsonReplyTo;
			String sReplyToName_Person = sReplyToName;
			if (jsonReplyTo_RoomMember != null)	// 仅在 ReplyTo 是群聊天室的情况下，才有可能出现 RoomMember 不是 null 的情况
			{
				sReplyToAccount_Person = sReplyToAccount_RoomMember;
				jsonReplyTo_Person = jsonReplyTo_RoomMember;
				sReplyToName_Person = sReplyToName_RoomMember;
			}

			if (isFromMe)
			{
net_maclife_wechat_http_BotApp.logger.info
				(
					"收到类型=" + nMsgType + ", ID=" + sMsgID + " 的消息（自己在其他设备上发出的）\n"
					+ " → " + (isToMe ? net_maclife_util_ANSIEscapeTool.DarkCyan ("自己") : (StringUtils.isEmpty (sReplyToName) || StringUtils.equalsIgnoreCase (sReplyToName, "null") ? "" : "【" + net_maclife_util_ANSIEscapeTool.DarkCyan (sReplyToName) + "】")) + ":\n"
					+ (nMsgType == WECHAT_MSG_TYPE__TEXT ? net_maclife_util_ANSIEscapeTool.LightGreen (sContent) : sContent)
				);
			}
			else
			{
net_maclife_wechat_http_BotApp.logger.info
				(
					"收到类型=" + nMsgType + ", ID=" + sMsgID + " 的消息\n"
					+ (StringUtils.isEmpty (sReplyToName) || StringUtils.equalsIgnoreCase (sReplyToName, "null") ? "" : "【" + net_maclife_util_ANSIEscapeTool.Green (sReplyToName) + "】") + (jsonReplyTo_RoomMember == null ? "" : " 群成员 【" + net_maclife_util_ANSIEscapeTool.Green (StringUtils.trimToEmpty (sReplyToName_RoomMember)) + "】")
					+ " → " + (isToMe ? "" : " 【" + sToName + "】") + ":\n"
					+ (nMsgType == WECHAT_MSG_TYPE__TEXT ? net_maclife_util_ANSIEscapeTool.LightGreen (sContent) : sContent)
				);
			}

			if (net_maclife_wechat_http_BotApp.ParseBoolean (net_maclife_wechat_http_BotApp.GetConfig ().getString ("engine.message.ignore-my-own-message", "no"), false))
			{
				if (isFromMe)	// 自己发送的消息，不再处理
				{
net_maclife_wechat_http_BotApp.logger.fine (net_maclife_util_ANSIEscapeTool.Gray ("是自己发的消息，且配置文件里已配置为“忽略自己发的消息”，所以，忽略本消息…"));
					continue;
				}
			}

			if (net_maclife_wechat_http_BotApp.ParseBoolean (net_maclife_wechat_http_BotApp.GetConfig ().getString ("engine.message.ignore-public-account", "no"), false))
			{
				if (isFromPublicAccount)	// 公众号发送的消息，不再处理
				{
net_maclife_wechat_http_BotApp.logger.fine (net_maclife_util_ANSIEscapeTool.Gray ("是公众号发的消息，且配置文件里已配置为“忽略公众号发的消息”，所以，忽略本消息…"));
					continue;
				}
			}

			File fMedia = null;
			switch (nMsgType)
			{
				case WECHAT_MSG_TYPE__TEXT:
					boolean bRoomMessageContentMentionedMe = false;
					boolean bRoomMessageContentMentionedMeFirst = false;

					if (isReplyToRoom)
					{
						JsonNode jsonMeInThisRoom = SearchForSingleMemberContactInRoom (sReplyToAccount, sMyEncryptedAccountInThisSession);
						String sMyDisplayNameInThisRoom = net_maclife_wechat_http_BotApp.GetJSONText (jsonMeInThisRoom, "DisplayName");
						bRoomMessageContentMentionedMe = IsRoomTextMessageMentionedMe (sContent, sMyDisplayNameInThisRoom);
						bRoomMessageContentMentionedMeFirst = IsRoomTextMessageMentionedMeFirst (sContent, sMyDisplayNameInThisRoom);

						if (bRoomMessageContentMentionedMeFirst)
							sContent = StringUtils.substring (sContent, (StringUtils.isNotEmpty (sMyDisplayNameInThisRoom) ? StringUtils.length (sMyDisplayNameInThisRoom) : StringUtils.length (sMyEncryptedAccountInThisSession)) + 1);
					}

					OnTextMessageReceived (jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, bRoomMessageContentMentionedMe, bRoomMessageContentMentionedMeFirst);
					break;
				case WECHAT_MSG_TYPE__IMAGE:
					fMedia = net_maclife_wechat_http_BotApp.WebWeChatGetImage (sSessionKey, sMsgID);
					OnImageMessageReceived (jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, fMedia);
					break;
				case WECHAT_MSG_TYPE__APP:
					break;
				case WECHAT_MSG_TYPE__VOICE:
					fMedia = net_maclife_wechat_http_BotApp.WebWeChatGetVoice (sSessionKey, sMsgID);
					OnVoiceMessageReceived (jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, fMedia);
					break;
				case WECHAT_MSG_TYPE__REQUEST_TO_ADD_FRIEND:
					OnRequestToAddFriendMessageReceived (jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent);
					break;
				//case WECHAT_MSG_TYPE__POSSIBLE_FRIEND_MSG:
				//	break;
				case WECHAT_MSG_TYPE__WECHAT_VCARD:
					OnVCardMessageReceived (jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent);
					break;
				case WECHAT_MSG_TYPE__EMOTION:
					if (StringUtils.isNotEmpty (sContent))
					{	// 测试过程中发现： 表情图消息的 Content 是空的 -- 然后获取文件只会取到 0 字节数据
						fMedia = net_maclife_wechat_http_BotApp.WebWeChatGetImage (sSessionKey, sMsgID);
					}
					else
					{
net_maclife_wechat_http_BotApp.logger.fine ("消息内容没有图片信息。可能需要在手机上查看。");
					}
					OnEmotionMessageReceived (jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, fMedia);
					break;
				//case WECHAT_MSG_TYPE__GPS_POSITION:
				//	break;
				case WECHAT_MSG_TYPE__URL:
					OnURLMessageReceived (jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent);
					break;
				//case WECHAT_MSG_TYPE__VOIP_MSG:
				//	break;
				case WECHAT_MSG_TYPE__OPERATION:
					OnOperationMessageReceived (jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent);
					break;
				//case WECHAT_MSG_TYPE__VOIP_NOTIFY:
				//	break;
				//case WECHAT_MSG_TYPE__VOIP_INVITE:
				//	break;
				case WECHAT_MSG_TYPE__VIDEO_MSG:
				//	break;
				case WECHAT_MSG_TYPE__SHORT_VIDEO:
					fMedia = net_maclife_wechat_http_BotApp.WebWeChatGetVideo (sSessionKey, sMsgID);
					OnVideoMessageReceived (jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, fMedia);
					break;
				//case WECHAT_MSG_TYPE__SYSTEM_NOTICE:
				//	break;
				case WECHAT_MSG_TYPE__SYSTEM:
					OnSystemMessageReceived (jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent);
					break;
				case WECHAT_MSG_TYPE__MSG_REVOKED:
					OnMessageIsRevokedMessageReceived (jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent);
					break;
				default:
					break;
			}
		}

		int nModContactCount = net_maclife_wechat_http_BotApp.GetJSONInt (jsonMessagePackage, "ModContactCount", 0);
		if (nModContactCount != 0)
		{
net_maclife_wechat_http_BotApp.logger.finest ("收到 " + nModContactCount + " 条【修改了联系人】信息");
		}
		JsonNode jsonModContactList = jsonMessagePackage.get ("ModContactList");
		for (i=0; i<nModContactCount; i++)
		{
			JsonNode jsonNode = jsonModContactList.get (i);
			OnContactChanged (jsonNode);
		}

		int nDelContactCount = net_maclife_wechat_http_BotApp.GetJSONInt (jsonMessagePackage, "DelContactCount", 0);
		if (nDelContactCount != 0)
		{
net_maclife_wechat_http_BotApp.logger.finest ("收到 " + nDelContactCount + " 条【删除了联系人】信息");
		}
		JsonNode jsonDelContactList = jsonMessagePackage.get ("DelContactList");
		for (i=0; i<nDelContactCount; i++)
		{
			JsonNode jsonNode = jsonDelContactList.get (i);
			OnContactDeleted (jsonNode);
		}

		int nModChatRoomMemerCount = net_maclife_wechat_http_BotApp.GetJSONInt (jsonMessagePackage, "ModChatRoomMemberCount", 0);
		if (nModChatRoomMemerCount != 0)
		{
net_maclife_wechat_http_BotApp.logger.finest ("收到 " + nModChatRoomMemerCount + " 条【聊天室成员列表变更】信息");
		}
		JsonNode jsonModChatRoomMemerList = jsonMessagePackage.get ("ModChatRoomMemberList");
		for (i=0; i<nModChatRoomMemerCount; i++)
		{
			JsonNode jsonNode = jsonModChatRoomMemerList.get (i);
			OnRoomMemberChanged (jsonNode);
		}
		JsonNode jsonSyncCheckKeys = jsonMessagePackage.get ("SyncCheckKey");	// 新的 SyncCheckKeys
		if (jsonSyncCheckKeys != null && !jsonSyncCheckKeys.isNull ())
		{
net_maclife_wechat_http_BotApp.logger.finest ("收到新的同步检测 Key");
		}
	}

	void OnTextMessageReceived
		(
			JsonNode jsonNode,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, boolean isContentMentionedMe, boolean isContentMentionedMeFirst
		)
	{
		String sURL = net_maclife_wechat_http_BotApp.GetJSONText (jsonNode, "Url");	// http://apis.map.qq.com/uri/v1/geocoder?coord=纬度,经度
		if (StringUtils.isNotEmpty (sURL))
		{
			//URL url = null;
			//try
			//{
			//	url = new URL (sURL);
			//	if (StringUtils.equalsIgnoreCase (url.getHost (), "apis.map.qq.com"))
			//	{	// 地理位置
			//		// <地理位置名称>: /cgi-bin/mmwebwx-bin/webwxgetpubliclinkimg?url=xxx&msgid=*******&pictype=location
			//	}
			//}
			//catch (MalformedURLException e)
			//{
			//	e.printStackTrace();
			//}
			final String QQMAP_URL_PREFIX = "http://apis.map.qq.com/uri/v1/geocoder?coord=";
			if (StringUtils.startsWith (sURL, QQMAP_URL_PREFIX))
			{
				String sCoords = StringUtils.substring (sURL, QQMAP_URL_PREFIX.length ());
				String[] arrayCoords = sCoords.split (",");
				String sLongitude = arrayCoords [1];
				String sLatitude = arrayCoords [0];

				String[] arrayContent = sContent.split (":\\n", 2);
				String sLocation = arrayContent[0];
				// arrayContent[1];	// 该信息忽略吧，暂时无用
				DispatchEvent ("OnGeoLocationMessage", jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sLocation, isContentMentionedMe, isContentMentionedMeFirst, sLongitude, sLatitude);
				return;
			}
		}
		DispatchEvent ("OnTextMessage", jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, isContentMentionedMe, isContentMentionedMeFirst);
	}

	void OnEmotionMessageReceived
		(
			JsonNode jsonNode,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, File fMedia
		)
	{
/*
<msg>
	<emoji
		fromusername = "wxid_**************"
		tousername = "wxid_**************"
		type="2"
		idbuffer="media:0_0"
		md5="85c2e3b767def7d4abc9b709166c6c05"
		len = "753878"
		productid=""
		androidmd5="85c2e3b767def7d4abc9b709166c6c05"
		androidlen="753878"
		s60v3md5 = "85c2e3b767def7d4abc9b709166c6c05"
		s60v3len="753878"
		s60v5md5 = "85c2e3b767def7d4abc9b709166c6c05"
		s60v5len="753878"
		cdnurl = "http://emoji.qpic.cn/wx_emoji/icHGuicAUhag5okZibJNykSkKPyUfiaVkDpt5UTre6qp8eiaWqlYwczIPqA/"	// 这个 url 可以显示图片
		designerid = ""
		thumburl = ""
		encrypturl = "http://emoji.qpic.cn/wx_emoji/J06sWZL5wGv73PIHM84QRZlZtoFPzHVDz5wSH28JnuGKxfMibdHibKdg/"
		aeskey= "43cc024c68be865cefe166114dd4702d"
		width= "192"
		height= "139" >
	</emoji>
</msg>
*/
		String sImageURL = null;
		try
		{
			if (StringUtils.isNotEmpty (sContent))
			{
				nu.xom.Document doc = net_maclife_wechat_http_BotApp.xomBuilder.build (sContent, null);
				Element msg = doc.getRootElement ();
				Element emoji = msg.getFirstChildElement ("emoji");
				sImageURL = net_maclife_wechat_http_BotApp.GetXMLAttributeValue (emoji, "cdnurl");
			}
		}
		catch (ParsingException | IOException e)
		{
			e.printStackTrace();
		}
		DispatchEvent ("OnEmotionMessage", jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, false, false, fMedia, sImageURL);
	}

	void OnImageMessageReceived
		(
			JsonNode jsonNode,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, File fMedia
		)
	{
		DispatchEvent ("OnImageMessage", jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, false, false, fMedia);
	}

	void OnVCardMessageReceived
		(
			JsonNode jsonNode,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent
		)
	{
		try
		{
			JsonNode jsonRecommenedInfo = jsonNode.get ("RecommendInfo");
			String 昵称 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "NickName");
			String 微信号 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "Alias");
			int n性别 = net_maclife_wechat_http_BotApp.GetJSONInt (jsonRecommenedInfo, "Sex");
			String 省 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "Province");
			String 市 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "City");

			nu.xom.Document doc = net_maclife_wechat_http_BotApp.xomBuilder.build (sContent, null);
			Element msg = doc.getRootElement ();
			String 大头像图片网址 = net_maclife_wechat_http_BotApp.GetXMLAttributeValue (msg, "bigheadimgurl");
			String 小头像图片网址 = net_maclife_wechat_http_BotApp.GetXMLAttributeValue (msg, "smallheadimgurl");
			String 地区代码 = net_maclife_wechat_http_BotApp.GetXMLAttributeValue (msg, "regionCode");

			StringBuilder sb = new StringBuilder ();
			if (StringUtils.isNotEmpty (昵称))
			{
				sb.append ("昵称:   ");
				sb.append (昵称);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (微信号))
			{
				sb.append ("微信号: ");
				sb.append (微信号);
				sb.append ("\n");
			}
			if (n性别 != 0)
			{
				sb.append ("性别:   ");
				if (n性别 == 1)
					sb.append ("男");
				else if (n性别 == 2)
					sb.append ("女");
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (省))
			{
				sb.append ("省份:   ");
				sb.append (省);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (市))
			{
				sb.append ("城市:   ");
				sb.append (市);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (地区代码))
			{
				sb.append ("地区代码: ");
				sb.append (地区代码);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (大头像图片网址))
			{
				sb.append ("大头像: ");
				sb.append (大头像图片网址);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (小头像图片网址))
			{
				sb.append ("小头像: ");
				sb.append (小头像图片网址);
				sb.append ("\n");
			}
net_maclife_wechat_http_BotApp.logger.info ("名片消息: \n" + sb);
			DispatchEvent ("OnVCardMessage", jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, false, false, jsonRecommenedInfo, msg);
		}
		catch (ParsingException | IOException e)
		{
			e.printStackTrace();
		}
	}

	void OnURLMessageReceived
		(
			JsonNode jsonNode,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent
		)
	{
		try
		{
			JsonNode jsonAppInfo = jsonNode.get ("AppInfo");
			int 应用程序消息类型 = net_maclife_wechat_http_BotApp.GetJSONInt (jsonNode, "AppMsgType");
			String s应用程序消息类型名称 = String.valueOf (应用程序消息类型);
			switch (应用程序消息类型)
			{
				case 3:
					s应用程序消息类型名称 = "音乐";
					break;
				case 5:
					s应用程序消息类型名称 = "网址";
					break;
				case 7:
					s应用程序消息类型名称 = "微博";
					break;
				default:
					//s应用程序消息类型名称 = String.valueOf (应用程序消息类型);
					break;
			}
			String 应用程序ID = net_maclife_wechat_http_BotApp.GetJSONText (jsonAppInfo, "AppID");
			String sURL = net_maclife_wechat_http_BotApp.GetJSONText (jsonNode, "Url");
			String sFileName = net_maclife_wechat_http_BotApp.GetJSONText (jsonNode, "FileName");

			nu.xom.Document doc = net_maclife_wechat_http_BotApp.xomBuilder.build (sContent, null);
			Element msg = doc.getRootElement ();
			Element appinfo = msg.getFirstChildElement ("appinfo");
			String 应用程序名 = net_maclife_wechat_http_BotApp.GetXMLValue (appinfo, "appname");

			Element appmsg = msg.getFirstChildElement ("appmsg");
			String title = net_maclife_wechat_http_BotApp.GetXMLValue (appmsg, "title");	// 据观察，其数值等于 等于 sFileName
			String description = net_maclife_wechat_http_BotApp.GetXMLValue (appmsg, "des");
			String url_from_xml = net_maclife_wechat_http_BotApp.GetXMLValue (appmsg, "url");	// 据观察，其数值等于 等于 sURL
			String data_url = net_maclife_wechat_http_BotApp.GetXMLValue (appmsg, "dataurl");	// 网易云音乐分享里，这个是个音乐文件

			StringBuilder sb = new StringBuilder ();
			if (StringUtils.isNotEmpty (s应用程序消息类型名称))
			{
				sb.append ("类型:   ");
				sb.append (s应用程序消息类型名称);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (title))
			{
				sb.append ("标题:   ");
				sb.append (title);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (description))
			{
				sb.append ("详细:   ");
				sb.append (description);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (url_from_xml))
			{
				sb.append ("网址:   ");
				sb.append (url_from_xml);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (data_url))
			{
				sb.append ("数据网址: ");
				sb.append (data_url);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (应用程序名))
			{
				sb.append ("程序名: ");
				sb.append (应用程序名);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (应用程序ID))
			{
				sb.append ("程序ID: ");
				sb.append (应用程序ID);
				sb.append ("\n");
			}
net_maclife_wechat_http_BotApp.logger.info ("URL 链接信息：\n" + sb);
			DispatchEvent ("OnURLMessage", jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, false, false, msg, jsonNode);
		}
		catch (ParsingException | IOException e)
		{
			e.printStackTrace();
		}
	}

	void OnRequestToAddFriendMessageReceived
		(
			JsonNode jsonNode,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent
		)
	{
		try
		{
			// 别人请求加好友时的消息，也携带了名片信息
			JsonNode jsonRecommenedInfo = jsonNode.get ("RecommendInfo");
			String 昵称 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "NickName");
			String 微信号 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "Alias");
			int n性别 = net_maclife_wechat_http_BotApp.GetJSONInt (jsonRecommenedInfo, "Sex");
			String 个性签名 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "Signature");
			String 省 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "Province");
			String 市 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "City");
			String 附加内容 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "Content");
			String sTicket = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "Ticket");

			nu.xom.Document doc = net_maclife_wechat_http_BotApp.xomBuilder.build (sContent, null);
			Element msg = doc.getRootElement ();
			String 对方明文ID = net_maclife_wechat_http_BotApp.GetXMLAttributeValue (msg, "fromusername");
			String 对方加密ID = net_maclife_wechat_http_BotApp.GetXMLAttributeValue (msg, "encryptusername");
			assert (StringUtils.endsWithIgnoreCase (对方加密ID, "@stranger"));
			String sFromNickName = net_maclife_wechat_http_BotApp.GetXMLAttributeValue (msg, "fromnickname");
			String 附加内容FromXMLContent = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "Content");
			assert (StringUtils.equalsIgnoreCase (附加内容, 附加内容FromXMLContent));
			String sSign = net_maclife_wechat_http_BotApp.GetXMLAttributeValue (msg, "sign");
			assert (StringUtils.equalsIgnoreCase (个性签名, sSign));
			String sAlias = net_maclife_wechat_http_BotApp.GetXMLAttributeValue (msg, "alias");
			assert (StringUtils.equalsIgnoreCase (微信号, sAlias));

			String 大头像图片网址 = net_maclife_wechat_http_BotApp.GetXMLAttributeValue (msg, "bigheadimgurl");
			String 小头像图片网址 = net_maclife_wechat_http_BotApp.GetXMLAttributeValue (msg, "smallheadimgurl");
			String 地区代码 = net_maclife_wechat_http_BotApp.GetXMLAttributeValue (msg, "regionCode");
			String sTicketFromXMLContent = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "ticket");
			assert (StringUtils.equalsIgnoreCase (sTicket, sTicketFromXMLContent));

			StringBuilder sb = new StringBuilder ();
			if (StringUtils.isNotEmpty (附加内容))
			{
				sb.append ("附加内容: ");
				sb.append (附加内容);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (昵称))
			{
				sb.append ("昵称:   ");
				sb.append (昵称);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (微信号))
			{
				sb.append ("微信号: ");
				sb.append (微信号);
				sb.append ("\n");
			}
			if (n性别 != 0)
			{
				sb.append ("性别:   ");
				if (n性别 == 1)
					sb.append ("男");
				else if (n性别 == 2)
					sb.append ("女");
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (个性签名))
			{
				sb.append ("个性签名: ");
				sb.append (个性签名);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (省))
			{
				sb.append ("省份:   ");
				sb.append (省);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (市))
			{
				sb.append ("城市:   ");
				sb.append (市);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (地区代码))
			{
				sb.append ("地区代码: ");
				sb.append (地区代码);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (大头像图片网址))
			{
				sb.append ("大头像: ");
				sb.append (大头像图片网址);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (小头像图片网址))
			{
				sb.append ("小头像: ");
				sb.append (小头像图片网址);
				sb.append ("\n");
			}
net_maclife_wechat_http_BotApp.logger.info ("请求加好友消息: \n" + sb);
			DispatchEvent ("OnRequestToAddFriendMessage", jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, false, false, jsonRecommenedInfo, msg);
		}
		catch (ParsingException | IOException e)
		{
			e.printStackTrace();
		}
	}

	void OnOperationMessageReceived
		(
			JsonNode jsonNode,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent
		)
	{
		String sOperationType = null;
		String sTargetAccount = null;
		try
		{
			nu.xom.Document doc = net_maclife_wechat_http_BotApp.xomBuilder.build (sContent, null);
			Element msg = doc.getRootElement ();
			Element op = msg.getFirstChildElement ("op");
			sOperationType = net_maclife_wechat_http_BotApp.GetXMLAttributeValue (op, "id");
			switch (sOperationType)
			{
				case "2":	// 微信手机端打开一个聊天窗口时收到该类型的消息
//<msg>
//	<op id='2'>
//		<username>未加密的帐号（打开的联系人的帐号）</username>
//	</op>
//</msg>
					sTargetAccount = net_maclife_wechat_http_BotApp.GetXMLValue (op, "username");
net_maclife_wechat_http_BotApp.logger.info ("手机端打开了新的聊天窗口，联系人/聊天室的未加密的帐号：" + sTargetAccount);
					DispatchEvent ("OnChatWindowOpenedMessage", jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, false, false, sTargetAccount);
					break;
				case "5":	// 微信手机端关闭（后退）订阅号列表窗口时收到该类型的消息
//<msg>
//	<op id='5'>
//		<username>未加密的帐号（打开的联系人的帐号）</username>
//	</op>
//</msg>
					sTargetAccount = net_maclife_wechat_http_BotApp.GetXMLValue (op, "username");
net_maclife_wechat_http_BotApp.logger.info ("手机端退出了订阅号列表窗口，之前打开联系人/聊天室的未加密的帐号：" + sTargetAccount);
					DispatchEvent ("OnChatWindowOpenedMessage", jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, false, false, sTargetAccount);
					break;
				case "4":
//<msg>
//	<op id='4'>
//		<username>
//			// 最近联系的联系人
//			filehelper,xxx@chatroom,wxid_xxx,xxx,...
//		</username>
//		<unreadchatlist>
//			<chat>
//				<username>
//					// 朋友圈
//					MomentsUnreadMsgStatus
//				</username>
//				<lastreadtime>
//					1454502365
//				</lastreadtime>
//			</chat>
//		</unreadchatlist>
//		<unreadfunctionlist>
//			// 未读的功能账号消息，群发助手，漂流瓶等
//		</unreadfunctionlist>
//	</op>
//</msg>
					sTargetAccount = net_maclife_wechat_http_BotApp.GetXMLValue (op, "username");
					break;
			}
		}
		catch (ParsingException | IOException e)
		{
			e.printStackTrace();
		}

		//DispatchEvent ("OnOperationMessage", sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, sContent, null, null);
	}

	void OnMessageIsRevokedMessageReceived
		(
			JsonNode jsonNode,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent
		)
	{
		try
		{
			nu.xom.Document doc = net_maclife_wechat_http_BotApp.xomBuilder.build (sContent, null);
			Element sysmsg = doc.getRootElement ();
			Element revokemsg = sysmsg.getFirstChildElement ("revokemsg");
			String sMsgID_oldversion = net_maclife_wechat_http_BotApp.GetXMLValue (revokemsg, "oldmsgid");
			String sRevokedMsgID = net_maclife_wechat_http_BotApp.GetXMLValue (revokemsg, "msgid");
			String sReplacedByMsg = net_maclife_wechat_http_BotApp.GetXMLValue (revokemsg, "replacemsg");

			StringBuilder sb = new StringBuilder ();
			if (StringUtils.isNotEmpty (sRevokedMsgID))
			{
				sb.append ("被撤回的消息ID: ");
				sb.append (sRevokedMsgID);
				sb.append ("\n");
			}
			if (StringUtils.isNotEmpty (sReplacedByMsg))
			{
				sb.append ("代替的消息内容: ");
				sb.append (sReplacedByMsg);
				sb.append ("\n");
			}
net_maclife_wechat_http_BotApp.logger.info ("“消息已撤回”消息：\n" + sb);
			DispatchEvent ("OnMessageIsRevokedMessage", jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, false, false, sRevokedMsgID, sReplacedByMsg);
		}
		catch (ParsingException | IOException e)
		{
			e.printStackTrace();
		}
	}


	void OnVoiceMessageReceived
		(
			JsonNode jsonNode,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, File fMedia
		)
	{
		DispatchEvent ("OnVoiceMessage", jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, false, false, fMedia);
	}

	void OnVideoMessageReceived
		(
			JsonNode jsonNode,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent, File fMedia
		)
	{
		DispatchEvent ("OnVideoMessage", jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, false, false, fMedia);
	}

	void OnSystemMessageReceived
		(
			JsonNode jsonNode,
			JsonNode jsonFrom, String sFromAccount, String sFromName, boolean isFromMe,
			JsonNode jsonTo, String sToAccount, String sToName, boolean isToMe,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName, boolean isReplyToRoom,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sContent
		)
	{
net_maclife_wechat_http_BotApp.logger.info ("系统消息: " + sContent);
		DispatchEvent ("OnSystemMessage", jsonNode, jsonFrom, sFromAccount, sFromName, isFromMe, jsonTo, sToAccount, sToName, isToMe, jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom, jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember, jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person, sContent, false, false);
	}

	void OnContactChanged (final JsonNode jsonContact)
	{
		JsonNode jsonOldContact = ReplaceOrAddContact (jsonContact);
		if (jsonOldContact == null)
			return;

net_maclife_wechat_http_BotApp.logger.info ("联系人变更: " + GetContactName (jsonOldContact));
		DispatchEvent ("OnContactChanged", jsonContact, null, null, null, false, null, null, null, false, null, null, null, false, null, null, null, null, null, null, null, false, false, jsonOldContact);
	}

	void OnContactDeleted (final JsonNode jsonContact)
	{
		JsonNode jsonOldContact = DeleteContact (jsonContact);
		DispatchEvent ("OnContactDeleted", jsonContact, null, null, null, false, null, null, null, false, null, null, null, false, null, null, null, null, null, null, null, false, false, jsonOldContact);
	}

	void OnRoomMemberChanged (final JsonNode jsonRoom)
	{
		DispatchEvent ("OnRoomMemberChanged", jsonRoom, null, null, null, false, null, null, null, false, null, null, null, false, null, null, null, null, null, null, null, false, false);
	}

	public static boolean IsDispatchEnabledForThisMessage (final String sEvent,
		final JsonNode jsonNode,
		final JsonNode jsonFrom, final String sFromAccount, final String sFromName, final boolean isFromMe,
		final JsonNode jsonTo, final String sToAccount, final String sToName, final boolean isToMe,
		final JsonNode jsonReplyTo, final String sReplyToAccount, final String sReplyToName, final boolean isReplyToRoom,
		final JsonNode jsonReplyTo_RoomMember, final String sReplyToAccount_RoomMember, final String sReplyToName_RoomMember,
		final JsonNode jsonReplyTo_Person, final String sReplyToAccount_Person, final String sReplyToName_Person,
		final String sContent, final boolean isContentMentionedMe, final boolean isContentMentionedMeFirst, final Object... datas
	)
	{
		boolean bEnabled = false;
		String sTriggerMode = null;
		String sAliasAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonReplyTo, "Alias");
		String sRemarkName = net_maclife_wechat_http_BotApp.GetJSONText (jsonReplyTo, "RemarkName");
		String sNickName = net_maclife_wechat_http_BotApp.GetJSONText (jsonReplyTo, "NickName");
		if (isReplyToRoom)
		{
			sTriggerMode = net_maclife_wechat_http_BotApp.GetConfig ().getString ("engine.trigger.mode.group-chat.nick-name." + sNickName);
			if (StringUtils.isEmpty (sTriggerMode))
			{	// 如果没有针对该聊天室单独设置，则寻找群聊的默认设置
				sTriggerMode = net_maclife_wechat_http_BotApp.GetConfig ().getString ("engine.trigger.mode.group-chat");
				if (StringUtils.isEmpty (sTriggerMode))
				{	// 如果也没有针对群聊的默认设置，则寻找全局的默认设置。如果全局默认设置也没有，则默认为 false
					sTriggerMode = net_maclife_wechat_http_BotApp.GetConfig ().getString ("engine.trigger.mode");
				}
			}
		}
		else
		{
			if (StringUtils.isNotEmpty (sAliasAccount))
			{
				sTriggerMode = net_maclife_wechat_http_BotApp.GetConfig ().getString ("engine.trigger.mode.private-chat.alias." + sAliasAccount);
			}
			if (StringUtils.isEmpty (sTriggerMode) && StringUtils.isNotEmpty (sRemarkName))
			{
				sTriggerMode = net_maclife_wechat_http_BotApp.GetConfig ().getString ("engine.trigger.mode.private-chat.remark-name." + sRemarkName);
			}
			if (StringUtils.isEmpty (sTriggerMode) && StringUtils.isNotEmpty (sNickName))
			{
				sTriggerMode = net_maclife_wechat_http_BotApp.GetConfig ().getString ("engine.trigger.mode.private-chat.nick-name." + sNickName);
			}

			if (StringUtils.isEmpty (sTriggerMode))
			{	// 如果没有针对该聊天室单独设置，则寻找群聊的默认设置
				sTriggerMode = net_maclife_wechat_http_BotApp.GetConfig ().getString ("engine.trigger.mode.private-chat");
				if (StringUtils.isEmpty (sTriggerMode))
				{	// 如果也没有针对群聊的默认设置，则寻找全局的默认设置。如果全局默认设置也没有，则默认为 false
					sTriggerMode = net_maclife_wechat_http_BotApp.GetConfig ().getString ("engine.trigger.mode");
				}
			}
		}

		if (StringUtils.isEmpty (sTriggerMode) || StringUtils.equalsIgnoreCase (sTriggerMode, "disabled") || StringUtils.equalsIgnoreCase (sTriggerMode, "none"))
		{
			return false;
		}
		else if (StringUtils.equalsIgnoreCase (sTriggerMode, "*") || StringUtils.equalsIgnoreCase (sTriggerMode, "any") || StringUtils.equalsIgnoreCase (sTriggerMode, "all"))
		{
			return true;
		}
		else if (StringUtils.equalsIgnoreCase (sTriggerMode, "@me"))
		{
			bEnabled = isContentMentionedMe;
		}
		else if (StringUtils.equalsIgnoreCase (sTriggerMode, "@meFromStart"))
		{
			bEnabled = isContentMentionedMeFirst;
		}

		return bEnabled;
	}

	/**
	 * 派发事件/消息。
	 * 关于函数名 DispatchEvent_WithMultithreadSwitch ： 不能使用与下面  DispatchEvent 的重名的函数，因为可变参数 args 的关系，所以在调用的时候，会导致不能确定调用哪个函数的问题（在编译器眼里，args 可能会把 nMultithreadSwitch 涵盖进去）。
	 * @param sEvent 事件名/消息名
	 * @param nMultithreadSwitch 选择是否用多线程模式派发消息。如果小于 0，则采用配置文件里的配置。如果等于 0 则不使用多线程。如果大于 0 则使用多线程。
	 * @param args 各事件的参数
	 */
	void DispatchEvent_WithMultithreadSwitch (final String sEvent, int nMultithreadSwitch,
		final JsonNode jsonNode,
		final JsonNode jsonFrom, final String sFromAccount, final String sFromName, final boolean isFromMe,
		final JsonNode jsonTo, final String sToAccount, final String sToName, final boolean isToMe,
		final JsonNode jsonReplyTo, final String sReplyToAccount, final String sReplyToName, final boolean isReplyToRoom,
		final JsonNode jsonReplyTo_RoomMember, final String sReplyToAccount_RoomMember, final String sReplyToName_RoomMember,
		final JsonNode jsonReplyTo_Person, final String sReplyToAccount_Person, final String sReplyToName_Person,
		final String sContent, final boolean isContentMentionedMe, final boolean isContentMentionedMeFirst, final Object... datas
		)
	{
		/*
		int i = 0;
		final JsonNode jsonNode                   = args.length > i ? (JsonNode)args[i] : null;	i++;

		final JsonNode jsonFrom                   = args.length > i ? (JsonNode)args[i] : null;	i++;
		final String sFromAccount                 = args.length > i ? (String)args[i] : null;	i++;
		final String sFromName                    = args.length > i ? (String)args[i] : null;	i++;
		final boolean isFromMe                    = args.length > i ? (Boolean)args[i] : null;	i++;

		final JsonNode jsonTo                     = args.length > i ? (JsonNode)args[i] : null;	i++;
		final String sToAccount                   = args.length > i ? (String)args[i] : null;	i++;
		final String sToName                      = args.length > i ? (String)args[i] : null;	i++;
		final boolean isToMe                      = args.length > i ? (Boolean)args[i] : null;	i++;

		final JsonNode jsonReplyTo                = args.length > i ? (JsonNode)args[i] : null;	i++;
		final String sReplyToAccount              = args.length > i ? (String)args[i] : null;	i++;
		final String sReplyToName                 = args.length > i ? (String)args[i] : null;	i++;

		final JsonNode jsonReplyTo_RoomMember     = args.length > i ? (JsonNode)args[i] : null;	i++;
		final String sReplyToAccount_RoomMember   = args.length > i ? (String)args[i] : null;	i++;
		final String sReplyToName_RoomMember      = args.length > i ? (String)args[i] : null;	i++;

		final JsonNode jsonReplyTo_Person         = args.length > i ? (JsonNode)args[i] : null;	i++;
		final String sReplyToAccount_Person       = args.length > i ? (String)args[i] : null;	i++;
		final String sReplyToName_Person          = args.length > i ? (String)args[i] : null;	i++;

		final String sContent                     = args.length > i ? (String)args[i] : null;	i++;
		final boolean isContentMentionedMe        = args.length > i ? (Boolean)args[i] : null;	i++;
		final boolean isContentMentionedMeFirst   = args.length > i ? (Boolean)args[i] : null;	i++;
		final Object data                         = args.length > i ? args[i] : null;	i++;
		final Object data2                        = args.length > i ? args[i] : null;	i++;
		final Object data                         = datas.length > i ? datas[i] : null;	i++;
		final Object data2                        = datas.length > i ? datas[i] : null;	i++;
		//*/
		// 检查一下配置，看看是否该派送这个消息/事件
		if (! IsDispatchEnabledForThisMessage
			(
				sEvent,
				jsonNode,
				jsonFrom, sFromAccount, sFromName, isFromMe,
				jsonTo, sToAccount, sToName, isToMe,
				jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom,
				jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
				jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
				sContent, isContentMentionedMe, isContentMentionedMeFirst, datas
			)
		)
		{
net_maclife_wechat_http_BotApp.logger.warning ("因为配置匹配的原因，所以不把这条消息分发到机器人");
			return;
		}

		boolean bMultithreadFromConfigFile = StringUtils.equalsIgnoreCase (net_maclife_wechat_http_BotApp.GetConfig ().getString ("engine.message.dispatch.thread-mode", ""), "multithread");
		boolean bMultithread = true;
		if (nMultithreadSwitch < 0)
			bMultithread = bMultithreadFromConfigFile;
		else if (nMultithreadSwitch == 0)
			bMultithread = false;
		else	// if (nMultithread > 0)
			bMultithread = true;

		//
		int rc = 0;
		for (final net_maclife_wechat_http_Bot bot : listBots)
		{
			if (! bMultithread)
			{	// 单线程或共享 Engine 线程时，才会有 Bot 链的处理机制。
				rc = DoDispatch
					(
						bot, sEvent,
						jsonNode,
						jsonFrom, sFromAccount, sFromName, isFromMe,
						jsonTo, sToAccount, sToName, isToMe,
						jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom,
						jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
						jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
						sContent, isContentMentionedMe, isContentMentionedMeFirst, datas
					);
				if ((rc & BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE) != BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE)
					break;
			}
			else
			{	// 多线程时，不采用 Bot 链的处理机制 -- 全部共同执行。
				net_maclife_wechat_http_BotApp.executor.submit
				(
					new Runnable ()
					{
						@Override
						public void run ()
						{
							DoDispatch
								(
									bot, sEvent,
									jsonNode,
									jsonFrom, sFromAccount, sFromName, isFromMe,
									jsonTo, sToAccount, sToName, isToMe,
									jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom,
									jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
									jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
									sContent, isContentMentionedMe, isContentMentionedMeFirst, datas
								);
						}
					}
				);
			}
		}
	}
	void DispatchEvent (final String sEvent,
		final JsonNode jsonNode,
		final JsonNode jsonFrom, final String sFromAccount, final String sFromName, final boolean isFromMe,
		final JsonNode jsonTo, final String sToAccount, final String sToName, final boolean isToMe,
		final JsonNode jsonReplyTo, final String sReplyToAccount, final String sReplyToName, final boolean isReplyToRoom,
		final JsonNode jsonReplyTo_RoomMember, final String sReplyToAccount_RoomMember, final String sReplyToName_RoomMember,
		final JsonNode jsonReplyTo_Person, final String sReplyToAccount_Person, final String sReplyToName_Person,
		final String sContent, final boolean isContentMentionedMe, final boolean isContentMentionedMeFirst, final Object... datas
		)
	{
		DispatchEvent_WithMultithreadSwitch (sEvent, -1,
				jsonNode,
				jsonFrom, sFromAccount, sFromName, isFromMe,
				jsonTo, sToAccount, sToName, isToMe,
				jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom,
				jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
				jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
				sContent, isContentMentionedMe, isContentMentionedMeFirst, datas
			);
	}

	int DoDispatch
		(
			final net_maclife_wechat_http_Bot bot, final String sType,
			final JsonNode jsonNode,
			final JsonNode jsonFrom, final String sFromAccount, final String sFromName, boolean isFromMe,
			final JsonNode jsonTo, final String sToAccount, final String sToName, boolean isToMe,
			final JsonNode jsonReplyTo, final String sReplyToAccount, final String sReplyToName, final boolean isReplyToRoom,
			final JsonNode jsonReplyTo_RoomMember, final String sReplyToAccount_RoomMember, final String sReplyToName_RoomMember,
			final JsonNode jsonReplyTo_Person, final String sReplyToAccount_Person, final String sReplyToName_Person,
			final String sContent, boolean isContentMentionedMe, boolean isContentMentionedMeFirst, final Object... datas
		)
	{
		try
		{
			switch (StringUtils.lowerCase (sType))
			{
				case "onloggedin":
					return bot.OnLoggedIn ();
				case "onloggedout":
					return bot.OnLoggedOut ();
				case "onshutdown":
					return bot.OnShutdown ();
				case "onmessagepackage":
					return bot.OnMessagePackageReceived (jsonNode);
				case "ontextmessage":
					return bot.OnTextMessageReceived
						(
							jsonNode,
							jsonFrom, sFromAccount, sFromName, isFromMe,
							jsonTo, sToAccount, sToName, isToMe,
							jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom,
							jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
							jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
							sContent, isContentMentionedMe, isContentMentionedMeFirst
						);
				case "ongeolocationmessage":
					return bot.OnGeoLocationMessageReceived
						(
							jsonNode,
							jsonFrom, sFromAccount, sFromName, isFromMe,
							jsonTo, sToAccount, sToName, isToMe,
							jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom,
							jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
							jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
							sContent, (String)datas[0], (String)datas[1]
						);
				case "onurlmessage":
					return bot.OnURLMessageReceived
						(
							jsonNode,
							jsonFrom, sFromAccount, sFromName, isFromMe,
							jsonTo, sToAccount, sToName, isToMe,
							jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom,
							jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
							jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
							(Element)datas[0]
						);
				case "onimagemessage":
					return bot.OnImageMessageReceived
						(
							jsonNode,
							jsonFrom, sFromAccount, sFromName, isFromMe,
							jsonTo, sToAccount, sToName, isToMe,
							jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom,
							jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
							jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
							sContent, (File)datas[0], null
						);
				case "onvoicemessage":
					return bot.OnVoiceMessageReceived
						(
							jsonNode,
							jsonFrom, sFromAccount, sFromName, isFromMe,
							jsonTo, sToAccount, sToName, isToMe,
							jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom,
							jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
							jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
							sContent, (File)datas[0]
						);
				case "onvcardmessage":
					return bot.OnVCardMessageReceived
						(
							jsonNode,
							jsonFrom, sFromAccount, sFromName, isFromMe,
							jsonTo, sToAccount, sToName, isToMe,
							jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom,
							jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
							jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
							sContent, (JsonNode)datas[0], (Element)datas[1]
						);
				case "onrequesttoaddfriendmessage":
					return bot.OnRequestToAddFriendMessageReceived
						(
							jsonNode,
							jsonFrom, sFromAccount, sFromName, isFromMe,
							jsonTo, sToAccount, sToName, isToMe,
							jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom,
							jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
							jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
							sContent, (JsonNode)datas[0], (Element)datas[1]
						);
				case "onvideomessage":
					return bot.OnVideoMessageReceived
						(
							jsonNode,
							jsonFrom, sFromAccount, sFromName, isFromMe,
							jsonTo, sToAccount, sToName, isToMe,
							jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom,
							jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
							jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
							sContent, (File)datas[0]
						);
				case "onemotionmessage":
					return bot.OnEmotionMessageReceived
						(
							jsonNode,
							jsonFrom, sFromAccount, sFromName, isFromMe,
							jsonTo, sToAccount, sToName, isToMe,
							jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom,
							jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
							jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
							sContent, (File)datas[0], (String)datas[1]
						);
				case "onchatwindowopenedmessage":
					return bot.OnChatWindowOpenedMessageReceived
						(
							jsonNode,
							jsonFrom, sFromAccount, sFromName, isFromMe,
							jsonTo, sToAccount, sToName, isToMe,
							jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom,
							jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
							jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
							sContent, (String)datas[0]
						);
				case "onmessageisrevokedmessage":
					return bot.OnMessageIsRevokedMessageReceived
						(
							jsonNode,
							jsonFrom, sFromAccount, sFromName, isFromMe,
							jsonTo, sToAccount, sToName, isToMe,
							jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom,
							jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
							jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
							sContent, (String)datas[0], (String)datas[1]
						);
				case "onsystemmessage":
					return bot.OnSystemMessageReceived
						(
							jsonNode,
							jsonFrom, sFromAccount, sFromName, isFromMe,
							jsonTo, sToAccount, sToName, isToMe,
							jsonReplyTo, sReplyToAccount, sReplyToName, isReplyToRoom,
							jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
							jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
							sContent
						);
				case "oncontactchanged":
					return bot.OnContactChanged (jsonNode);
				case "oncontactdeleted":
					return bot.OnContactDeleted (jsonNode);
				case "onroommemberchanged":
					return bot.OnRoomMemberChanged (jsonNode);
				default:
					break;
			}
		}
		catch (Throwable e)
		{
			e.printStackTrace ();
		}
		return BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}
}