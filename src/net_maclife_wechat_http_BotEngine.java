import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;

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

	// 消息类型列表
	// 参考自: https://github.com/Urinx/WeixinBot/blob/master/README.md ，但做了一些改动
	//
	public static final int WECHAT_MSG_TYPE__TEXT                  = 1;
	public static final int WECHAT_MSG_TYPE__IMAGE                 = 3;
	public static final int WECHAT_MSG_TYPE__APP                   = 6;	// 上面的参考中没有的
	public static final int WECHAT_MSG_TYPE__VOICE                 = 34;
	public static final int WECHAT_MSG_TYPE__VERIFY_MSG            = 37;
	public static final int WECHAT_MSG_TYPE__POSSIBLE_FRIEND_MSG   = 40;
	public static final int WECHAT_MSG_TYPE__WECHAT_VCARD          = 42;
	public static final int WECHAT_MSG_TYPE__VIDEO_CALL            = 43;
	public static final int WECHAT_MSG_TYPE__EMOTION               = 47;
	public static final int WECHAT_MSG_TYPE__GPS_POSITION          = 48;
	public static final int WECHAT_MSG_TYPE__URL                   = 49;
	public static final int WECHAT_MSG_TYPE__VOIP_MSG              = 50;
	public static final int WECHAT_MSG_TYPE__OPERATION             = 51;	// 上面的参考文档认为是初始化消息，我这里看起来更像是一个“操作”消息
	public static final int WECHAT_MSG_TYPE__VOIP_NOTIFY           = 52;
	public static final int WECHAT_MSG_TYPE__VOIP_INVITE           = 53;
	public static final int WECHAT_MSG_TYPE__SHORT_VIDEO           = 62;
	public static final int WECHAT_MSG_TYPE__SYSTEM_NOTICE         = 9999;
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
		VerifyMsg (WECHAT_MSG_TYPE__VERIFY_MSG),
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
	}
	*/

	Future<?> engineTask = null;
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
	JsonNode jsonContacts = null;
	JsonNode jsonRoomContacts = null;

	volatile boolean bStopFlag = false;
	boolean bMultithread = false;

	public net_maclife_wechat_http_BotEngine ()
	{
		bMultithread = StringUtils.equalsIgnoreCase (net_maclife_wechat_http_BotApp.config.getString ("engine.message.dispatch.thread-mode", ""), "multithread");
	}

	public void Start ()
	{
		bStopFlag = false;
		LoadBots ();
		engineTask = net_maclife_wechat_http_BotApp.executor.submit (this);
	}

	public void Stop ()
	{
		bStopFlag = true;
		UnloadAllBots ();

		if (engineTask!=null && !engineTask.isCancelled ())
		{
			engineTask.cancel (true);
		}
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
net_maclife_wechat_http_BotApp.logger.warning ("已经加载过 " + bot.GetName() + " 机器人，不重复加载");
							break;
						}
					}

					if (! bAlreadyLoaded)
					{
						newBot.SetEngine (this);
						newBot.Start ();
						listBots.add (newBot);
net_maclife_wechat_http_BotApp.logger.info (newBot.GetName () + " 机器人已创建并加载");
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
		List<String> listBotClassNames = net_maclife_wechat_http_BotApp.config.getList (String.class, "engine.bots.load.classNames");
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
net_maclife_wechat_http_BotApp.logger.info (bot.GetName () + " 机器人已被卸载");
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
net_maclife_wechat_http_BotApp.logger.warning ("在已加载的机器人列表中找不到 " + sBotFullClassName + " 机器人");
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
net_maclife_wechat_http_BotApp.logger.info (bot.GetName () + " (" + bot.getClass ().getCanonicalName () + ")");
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

	JsonNode StatusNotify () throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		return net_maclife_wechat_http_BotApp.WebWeChatStatusNotify (sUserID, sSessionID, sSessionKey, sPassTicket, sMyEncryptedAccountInThisSession);
	}

	JsonNode GetContacts () throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		return net_maclife_wechat_http_BotApp.WebWeChatGetContacts (sUserID, sSessionID, sSessionKey, sPassTicket);
	}

	JsonNode GetRoomContacts (List<String> listRoomIDs) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		return net_maclife_wechat_http_BotApp.WebWeChatGetRoomContacts (sUserID, sSessionID, sSessionKey, sPassTicket, listRoomIDs);
	}

	JsonNode GetMessagePackage (JsonNode jsonSyncCheckKeys) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, ScriptException, URISyntaxException
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
	 * @param sTo_EncryptedRoomAccount
	 * @param sTo_EncryptedAccount
	 * @param sTo_Name
	 * @param sMessage
	 * @param bInsertExtraNewLineBeforeTimestamp
	 * @param bMentionedMeInIncomingRoomMessage
	 * @param bMentionedMeFirstnIncomingRoomMessage
	 * @throws KeyManagementException
	 * @throws UnrecoverableKeyException
	 * @throws JsonProcessingException
	 * @throws NoSuchAlgorithmException
	 * @throws KeyStoreException
	 * @throws CertificateException
	 * @throws IOException
	 */
	public void SendTextMessage (String sTo_EncryptedRoomAccount, String sTo_EncryptedAccount, String sTo_Name, String sMessage, boolean bInsertExtraNewLineBeforeTimestamp, boolean bMentionedMeInIncomingRoomMessage, boolean bMentionedMeFirstnIncomingRoomMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		if (net_maclife_wechat_http_BotApp.ParseBoolean (net_maclife_wechat_http_BotApp.config.getString ("engine.message.text.append-timestamp", "yes")))
		{
			sMessage = sMessage + (bInsertExtraNewLineBeforeTimestamp ? "\n" : "") + "\n" + new java.sql.Timestamp (System.currentTimeMillis ());
		}
		if (StringUtils.isEmpty (sTo_EncryptedRoomAccount))
		{	// 私信，直接发送
			net_maclife_wechat_http_BotApp.WebWeChatSendTextMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sMyEncryptedAccountInThisSession, sTo_EncryptedAccount, sMessage);
		}
		else
		{	// 聊天室，需要做一下处理： @一下发送人，然后是消息
			net_maclife_wechat_http_BotApp.WebWeChatSendTextMessage (sUserID, sSessionID, sSessionKey, sPassTicket, sMyEncryptedAccountInThisSession, sTo_EncryptedRoomAccount, (bMentionedMeFirstnIncomingRoomMessage && StringUtils.isNotEmpty (sTo_Name) ? "@" + sTo_Name + "\n" : "") + sMessage);
		}
	}

	public void SendTextMessage (String sTo_EncryptedRoomAccount, String sTo_EncryptedAccount, String sTo_Name, String sMessage, boolean bInsertExtraNewLineBeforeTimestamp) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		SendTextMessage (sTo_EncryptedRoomAccount, sTo_EncryptedAccount, sTo_Name, sMessage, bInsertExtraNewLineBeforeTimestamp, false, false);
	}
	public void SendTextMessage (String sTo_EncryptedRoomAccount, String sTo_EncryptedAccount, String sTo_Name, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		SendTextMessage (sTo_EncryptedRoomAccount, sTo_EncryptedAccount, sTo_Name, sMessage, true);
	}
	public void SendTextMessage (String sTo_EncryptedAccount, String sTo_Name, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		SendTextMessage (null, sTo_EncryptedAccount, sTo_Name, sMessage);
	}
	public void SendTextMessage (String sTo_EncryptedAccount, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		SendTextMessage (null, sTo_EncryptedAccount, null, sMessage);
	}

	/**
	 * Bot 发送文本消息。Bot 发送文本消息时，会把消息前后的空白剔除，然后根据配置，可能附加上 Bot 名称。
	 * @param bot
	 * @param sTo_EncryptedRoomAccount
	 * @param sTo_EncryptedAccount
	 * @param sTo_Name
	 * @param sMessage
	 * @throws KeyManagementException
	 * @throws UnrecoverableKeyException
	 * @throws JsonProcessingException
	 * @throws NoSuchAlgorithmException
	 * @throws KeyStoreException
	 * @throws CertificateException
	 * @throws IOException
	 */
	public void BotSendTextMessage (net_maclife_wechat_http_Bot bot, String sTo_EncryptedRoomAccount, String sTo_EncryptedAccount, String sTo_Name, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		sMessage = StringUtils.trimToEmpty (sMessage);
		if (net_maclife_wechat_http_BotApp.ParseBoolean (net_maclife_wechat_http_BotApp.config.getString ("engine.message.text.append-bot-name", "yes")))
		{
			sMessage = sMessage + "\n\n-- " + bot.GetName ();
			SendTextMessage (sTo_EncryptedRoomAccount, sTo_EncryptedAccount, sTo_Name, sMessage, false);
		}
		else
			SendTextMessage (sTo_EncryptedRoomAccount, sTo_EncryptedAccount, sTo_Name, sMessage, true);
	}
	public void BotSendTextMessage (net_maclife_wechat_http_Bot bot, String sTo_EncryptedAccount, String sTo_Name, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		BotSendTextMessage (bot, null, sTo_EncryptedAccount, sTo_Name, sMessage);
	}
	public void BotSendTextMessage (net_maclife_wechat_http_Bot bot, String sTo_EncryptedAccount, String sMessage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException
	{
		BotSendTextMessage (bot, null, sTo_EncryptedAccount, null, sMessage);
	}

	public List<JsonNode> SearchForContacts (String sEncryptedAccountInThisSession, String sAliasAccount, String sRemarkName, String sNickName)
	{
		return net_maclife_wechat_http_BotApp.SearchForContacts (jsonContacts.get ("MemberList"), sEncryptedAccountInThisSession, sAliasAccount, sRemarkName, null, sNickName);
	}
	public JsonNode SearchForSingleContact (String sEncryptedAccountInThisSession, String sAliasAccount, String sRemarkName, String sNickName)
	{
		return net_maclife_wechat_http_BotApp.SearchForSingleContact (jsonContacts.get ("MemberList"), sEncryptedAccountInThisSession, sAliasAccount, sRemarkName, null, sNickName);
	}

	public JsonNode GetRoomByRoomEncryptedAccount (String sRoomEncryptedAccountInThisSession)
	{
		JsonNode jsonRooms = jsonRoomContacts.get ("ContactList");
		JsonNode jsonRoom = null;
		for (int i=0; i<jsonRooms.size (); i++)
		{
			jsonRoom = jsonRooms.get (i);
			if (StringUtils.equalsIgnoreCase (sRoomEncryptedAccountInThisSession, net_maclife_wechat_http_BotApp.GetJSONText (jsonRoom, "UserName")))
				return jsonRoom;
		}

		// 如果找不到聊天室，则尝试重新获取一次
		List<String> listRoomIDs = new ArrayList<String> ();
		listRoomIDs.add (sRoomEncryptedAccountInThisSession);
		try
		{
			JsonNode jsonThisRoomContact = net_maclife_wechat_http_BotApp.WebWeChatGetRoomContacts (sUserID, sSessionID, sSessionKey, sPassTicket, listRoomIDs);
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
	public List<JsonNode> SearchForContactsInRoom (String sRoomEncryptedAccountInThisSession, String sEncryptedAccountInThisSession, String sAliasAccount, String sDisplayName, String sNickName)
	{
		JsonNode jsonRoom = GetRoomByRoomEncryptedAccount (sRoomEncryptedAccountInThisSession);
		return net_maclife_wechat_http_BotApp.SearchForContacts (jsonRoom.get ("MemberList"), sEncryptedAccountInThisSession, sAliasAccount, null, sDisplayName, sNickName);
	}
	public JsonNode SearchForSingleContactInRoom (String sRoomEncryptedAccountInThisSession, String sEncryptedAccountInThisSession, String sAliasAccount, String sDisplayName, String sNickName)
	{
		JsonNode jsonRoom = GetRoomByRoomEncryptedAccount (sRoomEncryptedAccountInThisSession);
		return net_maclife_wechat_http_BotApp.SearchForSingleContact (jsonRoom.get ("MemberList"), sEncryptedAccountInThisSession, sAliasAccount, null, sDisplayName, sNickName);
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
									continue _outer_loop;
								}
								else	// 大概只有 200 才能出来：当是 200 时，但访问登录页面失败时，可能会跑到此处
								{
									//
								}
							}
						} while (! (o instanceof Map<?, ?>) && !bStopFlag);
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
					List<String> listRoomIDs = net_maclife_wechat_http_BotApp.GetRoomIDsFromContacts (jsonContacts);
					jsonRoomContacts = GetRoomContacts (listRoomIDs);	// 补全各个群的联系人列表

					// 触发“已登录”事件
					OnLoggedIn ();

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
				}
				catch (Exception e)
				{
					e.printStackTrace ();
					// 因为在循环内，出现异常，先暂停一会，免得不断重复
					TimeUnit.SECONDS.sleep (2);
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}

net_maclife_wechat_http_BotApp.logger.warning ("bot 线程退出");
		OnShutdown ();
	}

	void OnLoggedIn ()
	{
		loggedIn = true;
		DispatchEvent ("OnLoggedIn", null, null, null, null, null, null, null, null, null, null);
	}

	void OnLoggedOut ()
	{
		DispatchEvent ("OnLoggedOut", null, null, null, null, null, null, null, null, null, null);
	}

	void OnShutdown ()
	{
		DispatchEvent ("OnShutdown", null, null, null, null, null, null, null, null, null, null);
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

	/**
	 * 从通信录中获取人名。获取的人名不建议用来 @ 回复（因为可能是你自己给出的备注名称 -- 别人可能不认识该名称）。
	 * @param sEncryptedContactAccount
	 * @return 如果 RemarkName (备注名) 不为空，则取 RemarkName，否则取 NickName (昵称)
	 */
	String GetContactName (String sEncryptedContactAccount, JsonNode jsonContact)
	{
		if (jsonContact == null)
			jsonContact = SearchForSingleContact (sEncryptedContactAccount, null, null, null);
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
	String GetContactName (String sEncryptedContactAccount)
	{
		return GetContactName (sEncryptedContactAccount, null);
	}
	/**
	 * 从聊天室的联系人中获取人名。获取的人名可以用来直接 @ 回复。
	 * @param sEncryptedRoomAccount
	 * @param sEncryptedContactAccount
	 * @return 如果 DisplayName (本聊天室的昵称) 不为空，则取 DisplayName，否则取 NickName (昵称)
	 */
	String GetContactNameInRoom (String sEncryptedRoomAccount, String sEncryptedContactAccount, JsonNode jsonContact)
	{
		if (jsonContact == null)
			jsonContact = SearchForSingleContactInRoom (sEncryptedRoomAccount, sEncryptedContactAccount, null, null, null);
		if (jsonContact == null)
			return null;

		String sName = null;
		String sDisplayName = net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "DisplayName");
		if (StringUtils.isNotEmpty (sDisplayName))
			sName = sDisplayName;
		else
			sName = net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "NickName");

		return sName;
	}
	String GetContactNameInRoom (String sEncryptedRoomAccount, String sEncryptedContactAccount)
	{
		return GetContactNameInRoom (sEncryptedRoomAccount, sEncryptedContactAccount, null);
	}

	void OnMessagePackageReceived (JsonNode jsonMessagePackage) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, URISyntaxException
	{
		int i = 0;

		int nAddMsgCount = net_maclife_wechat_http_BotApp.GetJSONInt (jsonMessagePackage, "AddMsgCount", 0);
		if (nAddMsgCount != 0)
		{
net_maclife_wechat_http_BotApp.logger.info ("收到 " + nAddMsgCount + " 个新消息");
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

			String sEncryptedRoomAccount = null;

			String sRoomNickName = null;
			String sEncryptedFromAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonNode, "FromUserName");
			String sFromNickName = null;
			String sFromDisplayName = null;
			String sFromName = null;
			JsonNode jsonFrom = null;
			boolean isFromMe = IsMe (sEncryptedFromAccount);	// 要在交换 From 和 To 之前判断
			boolean isFromPublicAccount = false;
			String sEncryptedToAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonNode, "ToUserName");
			boolean isToMe = IsMe (sEncryptedToAccount);	// 要在交换 From 和 To 之前判断
			if (isFromMe)
			{	// 收到自己的帐号从其他设备发的消息：发件人是自己、收件人是其他人（含自己，私聊）、聊天室（群聊）
				// 交换一下 From 和 To
				String sTemp = sEncryptedFromAccount;
				sEncryptedFromAccount = sEncryptedToAccount;
				sEncryptedToAccount = sTemp;
net_maclife_wechat_http_BotApp.logger.fine ("* 是自己发出的消息，现在交换一下收发人");
			}
			String sToNickName = null;
			String sToName = null;
			JsonNode jsonTo = null;
			boolean isFromOrToRoom = net_maclife_wechat_http_BotApp.IsRoomAccount (sEncryptedFromAccount);	// 是否来自（或者发往（上面交换 From To 后））
			if (isFromOrToRoom)
			{	// 如果是发自聊天室，则从聊天室的成员列表中获取真正的发送人（可能不在自己的联系人内，只能从聊天室成员列表中获取），使其跟 IRC 机器人类似
				sEncryptedRoomAccount = sEncryptedFromAccount;
				JsonNode jsonRoom = GetRoomByRoomEncryptedAccount (sEncryptedRoomAccount);
				sRoomNickName = net_maclife_wechat_http_BotApp.GetJSONText (jsonRoom, "NickName");

				// 找出发送人的 UserID
				if (isFromMe)
				{
					sEncryptedFromAccount = sEncryptedToAccount;
					//sFromName = GetContactName (sEncryptedFromAccount);
					sFromName = GetContactNameInRoom (sEncryptedRoomAccount, sEncryptedFromAccount);
					sToName = sFromName;
				}
				else
				{	// 因为之前已经交换过收发人，所以，自己点开群聊窗口后，不再做【获取真实发件人】的处理（只有真正别人在群里发过来的信息才需要这样处理）
					String[] arrayContents = sContent.split (":\n", 2);
					sEncryptedFromAccount = arrayContents[0];
					//jsonFrom = SearchForSingleContactInRoom (sEncryptedRoomAccount, sEncryptedFromAccount, null, null, null);
					sFromName = GetContactNameInRoom (sEncryptedRoomAccount, sEncryptedFromAccount);
					//sFromDisplayName = net_maclife_wechat_http_BotApp.GetJSONText (jsonFrom, "DisplayName");
					//if (StringUtils.isNotEmpty (sFromDisplayName))
					//	sFromNickName = sFromDisplayName;
					//else
					//	sFromNickName = net_maclife_wechat_http_BotApp.GetJSONText (jsonFrom, "NickName");

					if (arrayContents.length > 1)
						sContent = arrayContents[1];
					else
						sContent = "";

					sToName = this.sMyNickName;
				}
			}
			else
			{	//
				jsonFrom = SearchForSingleContact (sEncryptedFromAccount, null, null, null);
				//String sFrom_DisplayName = net_maclife_wechat_http_BotApp.GetJSONText (jsonFrom, "DisplayName");
				//if (StringUtils.isNotEmpty(sFrom_DisplayName))
				//	sFromName = sFrom_DisplayName;
				//else
				//	sFromName = net_maclife_wechat_http_BotApp.GetJSONText (jsonFrom, "NickName");
				sFromName = GetContactName (sEncryptedFromAccount);
				isFromPublicAccount = net_maclife_wechat_http_BotApp.IsPublicAccount (net_maclife_wechat_http_BotApp.GetJSONInt (jsonFrom, "VerifyFlag"));

				jsonTo = SearchForSingleContact (sEncryptedToAccount, null, null, null);
				sToName = net_maclife_wechat_http_BotApp.GetJSONText (jsonTo, "NickName");
			}

			/*
			if (isToRoom)
			{	// 如果是发送到聊天室，通常、几乎就是自己的帐号在其他设备上发的
				// 首先，先找出自己在该聊天室/群中的昵称
				JsonNode jsonFrom = SearchForSingleContactInRoom (sEncryptedToAccount, sEncryptedFromAccount, null, null, null);
				String sFrom_DisplayName = net_maclife_wechat_http_BotApp.GetJSONText (jsonFrom, "DisplayName");
				if (StringUtils.isNotEmpty(sFrom_DisplayName))
					sFromNickName = sFrom_DisplayName;
				else
					sFromNickName = net_maclife_wechat_http_BotApp.GetJSONText (jsonFrom, "NickName");

				JsonNode jsonRoom = GetRoomByRoomEncryptedAccount (sEncryptedToAccount);
				sToNickName = net_maclife_wechat_http_BotApp.GetJSONText (jsonRoom, "NickName");
				if (isFromMe)
				{	// 能够收到自己的帐号从其他设备发的消息：发件人是自己、收件人人是聊天室
					sEncryptedRoomAccount = sEncryptedToAccount;
					//sTo = sFrom;	// 这么做，在自己的 BotEngine 内应该没啥问题吧
				}
			}
			else
			{
				jsonTo = SearchForSingleContact (sEncryptedToAccount, null, null, null);
				sToNickName = net_maclife_wechat_http_BotApp.GetJSONText (jsonTo, "NickName");
			}
			//*/

			if (isFromMe)
			{
net_maclife_wechat_http_BotApp.logger.info ("收到 自己 在其他设备上发给 " + (StringUtils.isEmpty (sRoomNickName) || StringUtils.equalsIgnoreCase (sRoomNickName, "null") ? "" : "【" + sRoomNickName + "】 ") + (isToMe ? "自己" : sFromName) + " 的消息 (类型=" + nMsgType + ")：\n" + sContent);
			}
			else
			{
net_maclife_wechat_http_BotApp.logger.info ("收到来自 " + (StringUtils.isEmpty (sRoomNickName) || StringUtils.equalsIgnoreCase (sRoomNickName, "null") ? "" : "【" + sRoomNickName + "】 ") + sFromName + " 发给 " + (isToMe ? "自己" : sToName) + " 的消息 (类型=" + nMsgType + ")：\n" + sContent);
			}

			if (net_maclife_wechat_http_BotApp.ParseBoolean (net_maclife_wechat_http_BotApp.config.getString ("engine.message.ignore-my-own-message", "no"), false))
			{
				if (isFromMe)	// 自己发送的消息，不再处理
				{
net_maclife_wechat_http_BotApp.logger.fine ("是自己发的消息，且配置文件里已配置为“忽略自己发的消息”，所以，忽略本消息…");
					continue;
				}
			}

			if (net_maclife_wechat_http_BotApp.ParseBoolean (net_maclife_wechat_http_BotApp.config.getString ("engine.message.ignore-public-account", "no"), false))
			{
				if (isFromPublicAccount)	// 公众号发送的消息，不再处理
				{
net_maclife_wechat_http_BotApp.logger.fine ("是公众号发的消息，且配置文件里已配置为“忽略公众号发的消息”，所以，忽略本消息…");
					continue;
				}
			}

			File fMedia = null;
			switch (nMsgType)
			{
				case WECHAT_MSG_TYPE__TEXT:
					boolean bMentionedMeInRoomMessage = false;
					boolean bMentionedMeFirstInRoomMessage = false;

					if (isFromOrToRoom)
					{
						JsonNode jsonMeInThisRoom = SearchForSingleContactInRoom (sEncryptedRoomAccount, sMyEncryptedAccountInThisSession, null, null, null);
						String sMyDisplayNameInThisRoom = net_maclife_wechat_http_BotApp.GetJSONText (jsonMeInThisRoom, "DisplayName");
						bMentionedMeInRoomMessage = IsRoomTextMessageMentionedMe (sContent, sMyDisplayNameInThisRoom);
						bMentionedMeFirstInRoomMessage = IsRoomTextMessageMentionedMeFirst (sContent, sMyDisplayNameInThisRoom);

						if (bMentionedMeFirstInRoomMessage)
							sContent = StringUtils.substring (sContent, (StringUtils.isNotEmpty (sMyDisplayNameInThisRoom) ? StringUtils.length (sMyDisplayNameInThisRoom) : StringUtils.length (sMyEncryptedAccountInThisSession)) + 1);
					}

					OnTextMessageReceived (sEncryptedRoomAccount, sRoomNickName, sEncryptedFromAccount, sFromName, sEncryptedToAccount, sToName, jsonNode, sContent, false, false);
					break;
				case WECHAT_MSG_TYPE__IMAGE:
					fMedia = net_maclife_wechat_http_BotApp.WebWeChatGetImage (sSessionKey, sMsgID);
					OnImageMessageReceived (sEncryptedRoomAccount, sRoomNickName, sEncryptedFromAccount, sFromName, sEncryptedToAccount, sToName, jsonNode, sContent, fMedia);
					break;
				case WECHAT_MSG_TYPE__APP:
					break;
				case WECHAT_MSG_TYPE__VOICE:
					fMedia = net_maclife_wechat_http_BotApp.WebWeChatGetVoice (sSessionKey, sMsgID);
					OnVoiceMessageReceived (sEncryptedRoomAccount, sRoomNickName, sEncryptedFromAccount, sFromName, sEncryptedToAccount, sToName, jsonNode, sContent, fMedia);
					break;
				case WECHAT_MSG_TYPE__VERIFY_MSG:
					break;
				case WECHAT_MSG_TYPE__POSSIBLE_FRIEND_MSG:
					break;
				case WECHAT_MSG_TYPE__WECHAT_VCARD:
					OnVCardMessageReceived (sEncryptedRoomAccount, sRoomNickName, sEncryptedFromAccount, sFromName, sEncryptedToAccount, sToName, jsonNode, sContent);
					break;
				case WECHAT_MSG_TYPE__VIDEO_CALL:
					break;
				case WECHAT_MSG_TYPE__EMOTION:
					fMedia = net_maclife_wechat_http_BotApp.WebWeChatGetImage (sSessionKey, sMsgID);
					OnEmotionMessageReceived (sEncryptedRoomAccount, sRoomNickName, sEncryptedFromAccount, sFromName, sEncryptedToAccount, sToName, jsonNode, sContent, fMedia);
					break;
				case WECHAT_MSG_TYPE__GPS_POSITION:
					break;
				case WECHAT_MSG_TYPE__URL:
					OnURLMessageReceived (sEncryptedRoomAccount, sRoomNickName, sEncryptedFromAccount, sFromName, sEncryptedToAccount, sToName, jsonNode, sContent);
					break;
				case WECHAT_MSG_TYPE__VOIP_MSG:
					break;
				case WECHAT_MSG_TYPE__OPERATION:
					OnOperationMessageReceived (sEncryptedRoomAccount, sRoomNickName, sEncryptedFromAccount, sFromName, sEncryptedToAccount, sToName, jsonNode, sContent);
					break;
				case WECHAT_MSG_TYPE__VOIP_NOTIFY:
					break;
				case WECHAT_MSG_TYPE__VOIP_INVITE:
					break;
				case WECHAT_MSG_TYPE__SHORT_VIDEO:
					fMedia = net_maclife_wechat_http_BotApp.WebWeChatGetVideo (sSessionKey, sMsgID);
					OnVideoMessageReceived (sEncryptedRoomAccount, sRoomNickName, sEncryptedFromAccount, sFromName, sEncryptedToAccount, sToName, jsonNode, sContent, fMedia);
					break;
				case WECHAT_MSG_TYPE__SYSTEM_NOTICE:
					break;
				case WECHAT_MSG_TYPE__SYSTEM:
					break;
				case WECHAT_MSG_TYPE__MSG_REVOKED:
					break;
				default:
					break;
			}
		}

		int nModContactCount = net_maclife_wechat_http_BotApp.GetJSONInt (jsonMessagePackage, "ModContactCount", 0);
		if (nModContactCount != 0)
		{
net_maclife_wechat_http_BotApp.logger.info ("收到 " + nModContactCount + " 个【修改了联系人】信息");
		}
		JsonNode jsonModContactList = jsonMessagePackage.get ("ModContactList");
		for (i=0; i<nModContactCount; i++)
		{
			JsonNode jsonNode = jsonAddMsgList.get (i);
		}

		int nDelContactCount = net_maclife_wechat_http_BotApp.GetJSONInt (jsonMessagePackage, "DelContactCount", 0);
		if (nModContactCount != 0)
		{
net_maclife_wechat_http_BotApp.logger.info ("收到 " + nDelContactCount + " 个【删除了联系人】信息");
		}
		JsonNode jsonDelContactList = jsonMessagePackage.get ("DelContactList");
		for (i=0; i<nDelContactCount; i++)
		{
			JsonNode jsonNode = jsonAddMsgList.get (i);
		}

		int nModChatRoomMemerCount = net_maclife_wechat_http_BotApp.GetJSONInt (jsonMessagePackage, "ModChatRoomMemberCount", 0);
		if (nModChatRoomMemerCount != 0)
		{
net_maclife_wechat_http_BotApp.logger.info ("收到 " + nModChatRoomMemerCount + " 个【聊天室成员列表变更】信息");
		}
		JsonNode jsonModChatRoomMemerList = jsonMessagePackage.get ("ModChatRoomMemberList");
		for (i=0; i<nModChatRoomMemerCount; i++)
		{
			JsonNode jsonNode = jsonAddMsgList.get (i);
		}
	}

	void OnTextMessageReceived (final String sFrom_EncryptedRoomAccount, final String sFrom_RoomNickName, final String sFrom_EncryptedAccount, final String sFrom_Name, final String sTo_EncryptedAccount, final String sTo_Name, final JsonNode jsonMessage, final String sMessage, final boolean bMentionedMe, final boolean bMentionedMeFirst)
	{
		String sURL = net_maclife_wechat_http_BotApp.GetJSONText (jsonMessage, "Url");	// http://apis.map.qq.com/uri/v1/geocoder?coord=纬度,经度
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

				String[] arrayContent = sMessage.split (":\\n", 2);
				String sLocation = arrayContent[0];
				// arrayContent[1];	// 该信息忽略吧，暂时无用
				DispatchEvent ("OnGeoLocationMessage", sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonMessage, sLocation, sLongitude, sLatitude);
				return;
			}
		}
		DispatchEvent ("OnTextMessage", sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonMessage, sMessage, bMentionedMe, bMentionedMeFirst);
	}

	void OnEmotionMessageReceived (final String sFrom_EncryptedRoomAccount, final String sFrom_RoomNickName, final String sFrom_EncryptedAccount, final String sFrom_Name, final String sTo_EncryptedAccount, final String sTo_Name, final JsonNode jsonMessage, final String sContent, final File fMedia)
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
			nu.xom.Document doc = net_maclife_wechat_http_BotApp.xomBuilder.build (sContent, null);
			Element msg = doc.getRootElement ();
			sImageURL = msg.getFirstChildElement ("emoji").getAttributeValue ("cdnurl");
		}
		catch (ParsingException | IOException e)
		{
			e.printStackTrace();
		}
		DispatchEvent ("OnEmotionMessage", sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonMessage, sContent, fMedia, sImageURL);
	}

	void OnImageMessageReceived (final String sFrom_EncryptedRoomAccount, final String sFrom_RoomNickName, final String sFrom_EncryptedAccount, final String sFrom_Name, final String sTo_EncryptedAccount, final String sTo_Name, final JsonNode jsonMessage, final String sContent, final File fMedia)
	{
		DispatchEvent ("OnImageMessage", sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonMessage, sContent, fMedia, null);
	}

	void OnVCardMessageReceived (final String sFrom_EncryptedRoomAccount, final String sFrom_RoomNickName, final String sFrom_EncryptedAccount, final String sFrom_Name, final String sTo_EncryptedAccount, final String sTo_Name, final JsonNode jsonMessage, final String sContent)
	{
		try
		{
			JsonNode jsonRecommenedInfo = jsonMessage.get ("RecommendInfo");
			String 昵称 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "NickName");
			String 微信号 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "Alias");
			int n性别 = net_maclife_wechat_http_BotApp.GetJSONInt (jsonRecommenedInfo, "Sex");
			String 省 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "Province");
			String 市 = net_maclife_wechat_http_BotApp.GetJSONText (jsonRecommenedInfo, "City");

			nu.xom.Document doc = net_maclife_wechat_http_BotApp.xomBuilder.build (sContent, null);
			Element msg = doc.getRootElement ();
			String 大头像图片网址 = msg.getAttributeValue ("bigheadimgurl");
			String 小头像图片网址 = msg.getAttributeValue ("smallheadimgurl");
			String 地区代码 = msg.getAttributeValue ("regionCode");

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
net_maclife_wechat_http_BotApp.logger.info ("名片信息：\n" + sb);
			DispatchEvent ("OnVCardMessage", sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonMessage, sContent, jsonRecommenedInfo, msg);
		}
		catch (ParsingException | IOException e)
		{
			e.printStackTrace();
		}
	}

	void OnURLMessageReceived (final String sFrom_EncryptedRoomAccount, final String sFrom_RoomNickName, final String sFrom_EncryptedAccount, final String sFrom_Name, final String sTo_EncryptedAccount, final String sTo_Name, final JsonNode jsonMessage, final String sContent)
	{
		try
		{
			JsonNode jsonAppInfo = jsonMessage.get ("AppInfo");
			int 应用程序消息类型 = net_maclife_wechat_http_BotApp.GetJSONInt (jsonMessage, "AppMsgType");
			String 应用程序ID = net_maclife_wechat_http_BotApp.GetJSONText (jsonAppInfo, "AppID");
			String sURL = net_maclife_wechat_http_BotApp.GetJSONText (jsonMessage, "Url");
			String sFileName = net_maclife_wechat_http_BotApp.GetJSONText (jsonMessage, "FileName");

			nu.xom.Document doc = net_maclife_wechat_http_BotApp.xomBuilder.build (sContent, null);
			Element msg = doc.getRootElement ();
			String 应用程序名 = msg.getFirstChildElement ("appinfo").getFirstChildElement ("appname").getValue ();

			Element appmsg = msg.getFirstChildElement ("appmsg");
			String title = appmsg.getFirstChildElement ("title").getValue ();	// 据观察，其数值等于 等于 sFileName
			String description = appmsg.getFirstChildElement ("des").getValue ();
			String url_from_xml = appmsg.getFirstChildElement ("url").getValue ();	// 据观察，其数值等于 等于 sURL
			String data_url = appmsg.getFirstChildElement ("dataurl").getValue ();	// 网易云音乐分享里，这个是个音乐文件

			StringBuilder sb = new StringBuilder ();
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
			DispatchEvent ("OnURLMessage", sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonMessage, sContent, msg, jsonMessage);
		}
		catch (ParsingException | IOException e)
		{
			e.printStackTrace();
		}
	}

	void OnOperationMessageReceived (final String sFrom_EncryptedRoomAccount, final String sFrom_RoomNickName, final String sFrom_EncryptedAccount, final String sFrom_Name, final String sTo_EncryptedAccount, final String sTo_Name, final JsonNode jsonMessage, final String sContent)
	{
		String sOperationType = null;
		String sTargetAccount = null;
		try
		{
			nu.xom.Document doc = net_maclife_wechat_http_BotApp.xomBuilder.build (sContent, null);
			Element msg = doc.getRootElement ();
			Element op = msg.getFirstChildElement ("op");
			sOperationType = op.getAttributeValue ("id");
			switch (sOperationType)
			{
				case "2":	// 微信手机端打开一个聊天窗口时收到该类型的消息
//<msg>
//	<op id='2'>
//		<username>未加密的帐号（打开的联系人的帐号）</username>
//	</op>
//</msg>
					sTargetAccount = op.getFirstChildElement ("username").getValue ();
net_maclife_wechat_http_BotApp.logger.info ("手机端打开了新的聊天窗口，联系人/聊天室的未加密的帐号：" + sTargetAccount);
					DispatchEvent ("OnChatWindowOpenedMessage", sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonMessage, sContent, sTargetAccount, null);
					break;
				case "5":	// 微信手机端关闭（后退）订阅号列表窗口时收到该类型的消息
//<msg>
//	<op id='5'>
//		<username>未加密的帐号（打开的联系人的帐号）</username>
//	</op>
//</msg>
					sTargetAccount = op.getFirstChildElement ("username").getValue ();
net_maclife_wechat_http_BotApp.logger.info ("手机端退出了订阅号列表窗口，之前打开联系人/聊天室的未加密的帐号：" + sTargetAccount);
					DispatchEvent ("OnChatWindowOpenedMessage", sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonMessage, sContent, sTargetAccount, null);
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
					sTargetAccount = op.getFirstChildElement ("username").getValue ();
					break;
			}
		}
		catch (ParsingException | IOException e)
		{
			e.printStackTrace();
		}

		//DispatchEvent ("OnOperationMessage", sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, sContent, null, null);
	}

	void OnVoiceMessageReceived (final String sFrom_EncryptedRoomAccount, final String sFrom_RoomNickName, final String sFrom_EncryptedAccount, final String sFrom_Name, final String sTo_EncryptedAccount, final String sTo_Name, final JsonNode jsonMessage, final String sContent, final File fMedia)
	{
		DispatchEvent ("OnVoiceMessage", sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonMessage, sContent, fMedia, null);
	}

	void OnVideoMessageReceived (final String sFrom_EncryptedRoomAccount, final String sFrom_RoomNickName, final String sFrom_EncryptedAccount, final String sFrom_Name, final String sTo_EncryptedAccount, final String sTo_Name, final JsonNode jsonMessage, final String sContent, final File fMedia)
	{
		DispatchEvent ("OnVideoMessage", sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonMessage, sContent, fMedia, null);
	}

	public static boolean IsDispatchEnabledForThisMessage (final String sEvent, final String sFrom_EncryptedRoomAccount, final String sFrom_RoomNickName, final String sFrom_EncryptedAccount, final String sFrom_Name, final String sTo_EncryptedAccount, final String sTo_Name, final JsonNode jsonNode, final String sContent, final Object data, final Object data2)
	{
		boolean bEnabled = false;
		String sTriggerMode = null;
		if (StringUtils.isNotEmpty (sFrom_EncryptedRoomAccount))
		{
			sTriggerMode = net_maclife_wechat_http_BotApp.config.getString ("engine.trigger.mode.group-chat.nick-name." + sFrom_RoomNickName);
			if (StringUtils.isEmpty (sTriggerMode))
			{	// 如果没有针对该聊天室单独设置，则寻找群聊的默认设置
				sTriggerMode = net_maclife_wechat_http_BotApp.config.getString ("engine.trigger.mode.group-chat");
				if (StringUtils.isEmpty (sTriggerMode))
				{	// 如果也没有针对群聊的默认设置，则寻找全局的默认设置。如果全局默认设置也没有，则默认为 false
					sTriggerMode = net_maclife_wechat_http_BotApp.config.getString ("engine.trigger.mode.default");
				}
			}
		}
		else
		{
			// TODO
			sTriggerMode = net_maclife_wechat_http_BotApp.config.getString ("engine.trigger.mode.private-chat.alias." + sTo_Name);
			if (StringUtils.isEmpty (sTriggerMode))
				sTriggerMode = net_maclife_wechat_http_BotApp.config.getString ("engine.trigger.mode.private-chat.remark-name." + sTo_Name);
			if (StringUtils.isEmpty (sTriggerMode))
				sTriggerMode = net_maclife_wechat_http_BotApp.config.getString ("engine.trigger.mode.private-chat.nick-name." + sTo_Name);

			if (StringUtils.isEmpty (sTriggerMode))
			{	// 如果没有针对该聊天室单独设置，则寻找群聊的默认设置
				sTriggerMode = net_maclife_wechat_http_BotApp.config.getString ("engine.trigger.mode.private-chat");
				if (StringUtils.isEmpty (sTriggerMode))
				{	// 如果也没有针对群聊的默认设置，则寻找全局的默认设置。如果全局默认设置也没有，则默认为 false
					sTriggerMode = net_maclife_wechat_http_BotApp.config.getString ("engine.trigger.mode.default");
				}
			}
		}

		if (StringUtils.isEmpty (sTriggerMode) || StringUtils.equalsIgnoreCase (sTriggerMode, "disabled") || StringUtils.equalsIgnoreCase (sTriggerMode, "none"))
			return false;
		else if (StringUtils.equalsIgnoreCase (sTriggerMode, "*") || StringUtils.equalsIgnoreCase (sTriggerMode, "any") || StringUtils.equalsIgnoreCase (sTriggerMode, "all"))
			return true;
		else if (StringUtils.equalsIgnoreCase (sTriggerMode, "@me"))
		{
			boolean bMentionedMe = StringUtils.containsIgnoreCase (sContent, "@" + sTo_Name);
			bEnabled = bMentionedMe;
		}
		else if (StringUtils.equalsIgnoreCase (sTriggerMode, "@me") || StringUtils.equalsIgnoreCase (sTriggerMode, "@meFromStart"))
		{
			boolean bMentionedMeFromStart = StringUtils.startsWithIgnoreCase (sContent, "@" + sTo_Name);
			bEnabled = bMentionedMeFromStart;
		}

		return bEnabled;
	}

	void DispatchEvent (final String sEvent, final String sFrom_EncryptedRoomAccount, final String sFrom_RoomNickName, final String sFrom_EncryptedAccount, final String sFrom_Name, final String sTo_EncryptedAccount, final String sTo_Name, final JsonNode jsonNode, final String sContent, final Object data, final Object data2)
	{
		// 检查一下配置，看看是否该派送这个消息/事件
		if (! true)
		{

		}

		//
		int rc = 0;
		for (final net_maclife_wechat_http_Bot bot : listBots)
		{
			if (! bMultithread)
			{	// 单线程或共享 Engine 线程时，才会有 Bot 链的处理机制。
				rc = DoDispatch (bot, sEvent, sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonNode, sContent, data, data2);
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
							DoDispatch (bot, sEvent, sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonNode, sContent, data, data2);
						}
					}
				);
			}
		}
	}

	int DoDispatch (final net_maclife_wechat_http_Bot bot, final String sType, final String sFrom_EncryptedRoomAccount, final String sFrom_RoomNickName, final String sFrom_EncryptedAccount, final String sFrom_Name, final String sTo_EncryptedAccount, final String sTo_Name, final JsonNode jsonNode, final String sContent, final Object data, final Object data2)
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
				case "onmessage":
					return bot.OnMessageReceived (sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, (JsonNode)data);
				case "ontextmessage":
					return bot.OnTextMessageReceived (sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonNode, sContent, (boolean)data, (boolean)data2);
				case "ongeolocationmessage":
					return bot.OnGeoLocationMessageReceived (sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonNode, sContent, (String)data, (String)data2);
				case "onurlmessage":
					return bot.OnURLMessageReceived (sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonNode, (Element)data);
				case "onimagemessage":
					return bot.OnImageMessageReceived (sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonNode, sContent, (File)data, (String)data2);
				case "onvoicemessage":
					return bot.OnVoiceMessageReceived (sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonNode, sContent, (File)data);
				case "onvcardmessage":
					return bot.OnVCardMessageReceived (sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonNode, sContent, (JsonNode)data, (Element)data2);
				case "onvideomessage":
					return bot.OnVideoMessageReceived (sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonNode, sContent, (File)data);
				case "onemotionmessage":
					return bot.OnEmotionMessageReceived (sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonNode, sContent, (File)data, (String)data2);
				case "onchatwindowopenedmessage":
					return bot.OnChatWindowOpenedMessageReceived (sFrom_EncryptedRoomAccount, sFrom_RoomNickName, sFrom_EncryptedAccount, sFrom_Name, sTo_EncryptedAccount, sTo_Name, jsonNode, sContent, (String)data);
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