import java.io.*;
import java.text.*;
import java.util.*;

import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.*;

import org.apache.commons.lang3.*;
//import org.apache.directory.api.ldap.model.cursor.*;
//import org.apache.directory.api.ldap.model.entry.*;
//import org.apache.directory.api.ldap.model.exception.*;
//import org.apache.directory.api.ldap.model.message.*;
//import org.apache.directory.api.ldap.model.message.controls.*;
//import org.apache.directory.api.ldap.model.name.*;
//import org.apache.directory.ldap.client.api.*;
//import org.apache.directory.api.ldap.model.message.*;

import com.fasterxml.jackson.databind.*;

/**
	微软活动目录通信录 adab：将 Active Directory 中的数据当做通信录来使用（需要系统管理员或者某个系统对 Active Directory 中的数据维护好）。
	<br/>
	通常，在公司内部群中使用 adab 比较合适。
	<br/>
	暂时，只支持连一个 Active Directory 服务器…
 * @author liuyan
 *
 */
public class net_maclife_wechat_http_Bot_ActiveDirectoryAddressBook extends net_maclife_wechat_http_Bot
{
	public static final String DEFAULT_INITIAL_CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
	public static final String DEFAULT_SECURITY_AUTHENTICATION = "simple";	// 默认验证方式：明文。其他：”none", "simple", "strong"; DIGEST-MD5, GSSAPI
	public static final String DEFAULT_REFERRAL = "follow";	// "follow" "ignore" "throw"

	/**
	 *  搜索用户、联系人、组、组织单元的搜索条件/搜索过滤器。
	 *  其中，<code>{0}</code> 是“帐号状态={禁用/启用/全部}”的搜索条件， <code>{1}</code> 是“是否显示组对象”的搜索条件
	 */
	public static final String DEFAULT_SEARCH_FILTER_Person_OrganizationalUnit_Group = "(|(&(objectCategory=person)(|(objectClass=user)(objectClass=contact)){0}){1}(objectCategory=OrganizationalUnit))";
	public static final String DEFAULT_SEARCH_FILTER_Person = "(|(&(objectCategory=person)(|(objectClass=user)(objectClass=contact)){0}))";

	/**
	 * 按关键字搜索时的搜索条件/搜索过滤器。
	 * 其中，{0} 是搜索的关键字，是 DEFAULT_SEARCH_FILTER_Person 的搜索条件，可以合并到关键字搜索条件中来，以达到比较符合人类思维的目的
	 */
	public static final String DEFAULT_SEARCH_FILTER_KeywordSearch = "(&(|(cn={0})(sAMAccountName={0})(displayName={0})(telephoneNumber={0})(otherTelephone={0})(mobile={0})(otherMobile={0})(mail={0})(otherMailbox={0})){1})";
	public static final String SEARCH_FILTER_EnabledUser = "(!(userAccountControl:1.2.840.113556.1.4.803:=2))";
	public static final String SEARCH_FILTER_DisabledUser = "(userAccountControl:1.2.840.113556.1.4.803:=2)";
	public static final int USER_ACCOUNT_STATUS_MASK__ENABLED  = 0x01;
	public static final int USER_ACCOUNT_STATUS_MASK__DISABLED = 0x02;

	public static int DEFAULT_MAX_FETCH_ENTRIES = net_maclife_wechat_http_BotApp.GetConfig().getInt ("bot.active-directory-address-book.max-fetched-entries", 2);

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
		if (! net_maclife_wechat_http_BotApp.hasCommandPrefix (sContent))
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
		//if (net_maclife_wechat_http_BotApp.hasCommandPrefix (sContent))
			sContent = net_maclife_wechat_http_BotApp.StripOutCommandPrefix (sContent);

		List<String> listCommands = net_maclife_wechat_http_BotApp.GetConfig ().getList (String.class, "bot.active-directory-address-book.commands");
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

			// 检查命令是否是 adab 命令
			boolean isCommandValid = false;
			for (int i=0; i<listCommands.size (); i++)
			{
				String sCommand = listCommands.get (i);
				if (StringUtils.equalsIgnoreCase (sCommandInputed, sCommand))
				{
					isCommandValid = true;
					break;
				}
			}
			if (! isCommandValid)
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

			// 现在开始查询 LDAP
			//if (! isReplyToRoom)
			//{
			//	SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, GetName() + " 需要在群聊中执行。如果你确实是在群聊中执行的，请务必设置好群名称，并告知管理员（我）。");
			//	return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
			//}
			if (StringUtils.isEmpty (sCommandParametersInputed))
			{
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
					GetName() + " 在查询时需要指定搜索条件。\n\n用法:\n" + sCommandInputed + "  <搜索条件>\n\n" +
					"搜索条件，通常是姓名、手机号码、座机电话号码、邮箱帐号这些内容，不用写完整（当然写全了更好 -- 精准匹配搜索），" +
					"比如：如果只记得姓名中的某个字、电话号码里的某几个号、邮箱帐号某几个字，则可以使用类似这样搜索关键字：\n" +
					"刘 -- 姓名包含“刘”的\n" +
					"1178 -- 电话号码包含 “1178”的\n" +
					"liu.yan -- 邮箱帐号包含“liu.yan”的\n\n" +
					"管理员需注意：实际上，搜索时，是根据 bot.active-directory-address-book.<通讯簿名称>.ldap.search.filter 配置的过滤器来搜索的，" +
					"默认的过滤器将从姓名、手机号码、座机号码、邮箱帐号、域帐号这几项中模糊匹配搜索条件，" +
					"如果你更改了过滤器内容，将按照你改过的过滤器进行匹配");
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
			}

			try
			{
				String sResult = Query (sCommandParametersInputed, sReplyToName, USER_ACCOUNT_STATUS_MASK__ENABLED /* | USER_ACCOUNT_STATUS_MASK__DISABLED */);
				if (StringUtils.isEmpty (sResult))
				{
					SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "在与【" + sReplyToName + "】关联的 AD 通讯簿中没找到关键字为【" + sCommandParametersInputed + "】的联系信息");
					return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
				}
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, sResult);
			}
			catch (Exception e)
			{
				e.printStackTrace ();
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "查询出错: " + e);
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

	String Query (String sKeyword, String sAddressBookName_NickName, int nUserAccountStatusMask)
	{
		if (StringUtils.isEmpty (sAddressBookName_NickName))
			return null;
		// 查找 sAddressBookName_RoomName 是否关联了通讯簿
		List<String> listAddressBookNames = net_maclife_wechat_http_BotApp.GetConfig().getList (String.class, "bot.active-directory-address-book.map." + sAddressBookName_NickName);
		if (listAddressBookNames==null || listAddressBookNames.isEmpty ())
		{
			return null;
		}

		//sKeyword = sKeyword + "*";	// 搜索项的数值以关键字开头
		//sKeyword = "*" + sKeyword;	// 搜索项的数值以关键字结尾
		sKeyword = "*" + sKeyword + "*";	// 搜索项的数值包含关键字（模糊搜索）
		String sSearchFilter_Person = "";
		if ((nUserAccountStatusMask & USER_ACCOUNT_STATUS_MASK__ENABLED) == USER_ACCOUNT_STATUS_MASK__ENABLED  &&  (nUserAccountStatusMask & USER_ACCOUNT_STATUS_MASK__DISABLED) == USER_ACCOUNT_STATUS_MASK__DISABLED)
			sSearchFilter_Person = MessageFormat.format (DEFAULT_SEARCH_FILTER_Person, "");
		else if ((nUserAccountStatusMask & USER_ACCOUNT_STATUS_MASK__ENABLED) == USER_ACCOUNT_STATUS_MASK__ENABLED)
			sSearchFilter_Person = MessageFormat.format (DEFAULT_SEARCH_FILTER_Person, SEARCH_FILTER_EnabledUser);
		else if ((nUserAccountStatusMask & USER_ACCOUNT_STATUS_MASK__DISABLED) == USER_ACCOUNT_STATUS_MASK__DISABLED)
			sSearchFilter_Person = MessageFormat.format (DEFAULT_SEARCH_FILTER_Person, SEARCH_FILTER_DisabledUser);

		StringBuilder sb = null;
		int nBaseDN = 0;
		boolean hasMultipleEntry = false;
		try
		{
			sb = new StringBuilder ();
			for (String sAddressBookName : listAddressBookNames)
			{
				String sLDAPURL, sLDAPScheme, sLDAPServerAddress, sLDAPServerPort, sLDAPBindUser, sLDAPBindUserPassword, sLDAPSearchBaseDN, sLDAPSearchFilter;
				int nLDAPServerPort, nMaxFetchedEntries;
				List<String> listLDAPSearchBaseDNs = null, listSearchControlSortAttributeNames;
				sLDAPURL = net_maclife_wechat_http_BotApp.GetConfig().getString ("bot.active-directory-address-book." + sAddressBookName + ".ldap.url");
				sLDAPScheme = net_maclife_wechat_http_BotApp.GetConfig().getString ("bot.active-directory-address-book." + sAddressBookName + ".ldap.scheme");
				sLDAPServerAddress = net_maclife_wechat_http_BotApp.GetConfig().getString ("bot.active-directory-address-book." + sAddressBookName + ".ldap.server.address");
				sLDAPServerPort = net_maclife_wechat_http_BotApp.GetConfig().getString ("bot.active-directory-address-book." + sAddressBookName + ".ldap.server.port");
				if (StringUtils.isEmpty (sLDAPServerPort))
					nLDAPServerPort = 389;
				else
					nLDAPServerPort = net_maclife_wechat_http_BotApp.GetConfig().getInt ("bot.active-directory-address-book." + sAddressBookName + ".ldap.server.port", 389);

				sLDAPBindUser = net_maclife_wechat_http_BotApp.GetConfig().getString ("bot.active-directory-address-book." + sAddressBookName + ".ldap.bind.user-name");
				sLDAPBindUserPassword = net_maclife_wechat_http_BotApp.GetConfig().getString ("bot.active-directory-address-book." + sAddressBookName + ".ldap.bind.user-password");
				sLDAPSearchBaseDN = net_maclife_wechat_http_BotApp.GetConfig().getString ("bot.active-directory-address-book." + sAddressBookName + ".ldap.search.base-dn");
				listLDAPSearchBaseDNs = net_maclife_wechat_http_BotApp.GetConfig().getList (String.class, "bot.active-directory-address-book." + sAddressBookName + ".ldap.search.base-dn");
				sLDAPSearchFilter = net_maclife_wechat_http_BotApp.GetConfig().getString ("bot.active-directory-address-book." + sAddressBookName + ".ldap.search.filter");
				listSearchControlSortAttributeNames = net_maclife_wechat_http_BotApp.GetConfig().getList (String.class, "bot.active-directory-address-book." + sAddressBookName + ".ldap.search.control.sort.attribute-names");
				nMaxFetchedEntries = net_maclife_wechat_http_BotApp.GetConfig().getInt ("bot.active-directory-address-book." + sAddressBookName + ".max-fetched-entries", DEFAULT_MAX_FETCH_ENTRIES);

				if (StringUtils.isEmpty (sLDAPURL))
				{
					if (StringUtils.isEmpty (sLDAPServerAddress))
					{
						throw new RuntimeException ("没有为域通讯簿【" + sAddressBookName + "】配置有效的 LDAP 参数(LDAP URL、服务器地址、服务器端口)，无法进行查询");
					}
				}
				if (StringUtils.isEmpty (sLDAPSearchBaseDN) || listLDAPSearchBaseDNs==null || listLDAPSearchBaseDNs.isEmpty ())
				{
					throw new RuntimeException ("没有为域通讯簿【" + sAddressBookName + "】配置有效的 LDAP 参数(搜索的起始点/BaseDN)，无法进行查询");
				}
				if (StringUtils.isEmpty (sLDAPSearchFilter))
				{	// 如果未设置搜索过滤器/搜索条件，则使用默认的“关键字搜索”过滤器
					sLDAPSearchFilter = DEFAULT_SEARCH_FILTER_KeywordSearch;
				}

				//SortRequest ctrlSortControl = null;
				//if (listSearchControlSortAttributeNames!=null && !listSearchControlSortAttributeNames.isEmpty ())
				//{
				//	ctrlSortControl = new SortRequestControlImpl ();
				//	for (String sAttributeName : listSearchControlSortAttributeNames)
				//	{
				//		ctrlSortControl.addSortKey (new SortKey(sAttributeName));
				//	}
				//}
				byte[] arrayPagingCookie = null;
				List<Control> listRequestControls = new ArrayList<Control> ();
				listRequestControls.add (new PagedResultsControl (nMaxFetchedEntries, Control.CRITICAL));
				if (listSearchControlSortAttributeNames!=null && !listSearchControlSortAttributeNames.isEmpty ())
				{
					for (int i=0; i<listSearchControlSortAttributeNames.size(); i++)
					{
						// 第一个排序属性当成[必须遵守的/关键的]
						listRequestControls.add (new SortControl (listSearchControlSortAttributeNames.get (i), i==0 ? Control.CRITICAL : Control.NONCRITICAL));
					}
				}
				Control[] arrayRequestControls = listRequestControls.toArray (new Control[0]);

				//LdapConnection lc = null;
				//lc = new LdapNetworkConnection (sLDAPServerAddress, nLDAPServerPort, StringUtils.equalsAnyIgnoreCase (sLDAPScheme, "ldaps"/*, "tls", "ssl"*/));
				////lc.bind (sLDAPBindUser, sLDAPBindUserPassword);
				//BindRequest br = new BindRequestImpl ().setSimple (true).setName (sLDAPBindUser).setCredentials (sLDAPBindUserPassword);
				//lc.bind (br);
				Hashtable<String, Object> env = new Hashtable<String, Object> ();
				LdapContext ctx = null;
				String sInitialContextFactory = DEFAULT_INITIAL_CONTEXT_FACTORY;	// TODO 临时
				String sSecurityAuthentication = DEFAULT_SECURITY_AUTHENTICATION;	// TODO 临时
				String sReferral = DEFAULT_REFERRAL;	// TODO 临时
				String sLDAPServerURL = sLDAPScheme + "://" + sLDAPServerAddress + ":" + nLDAPServerPort;
				env.put (Context.INITIAL_CONTEXT_FACTORY, sInitialContextFactory);
				env.put (Context.PROVIDER_URL, sLDAPServerURL);
				env.put (Context.SECURITY_AUTHENTICATION, sSecurityAuthentication);
				env.put (Context.REFERRAL, sReferral);
				if (StringUtils.equalsAnyIgnoreCase (sLDAPScheme, "ldaps"/*, "tls", "ssl"*/))
				{
					env.put (Context.SECURITY_PROTOCOL, "tls");	// "ssl"
				}
				env.put (Context.SECURITY_PRINCIPAL, sLDAPBindUser);
				env.put (Context.SECURITY_CREDENTIALS, sLDAPBindUserPassword);
				ctx = new InitialLdapContext (env, null);	// 若无异常抛出，则认为验证通过;

				String sSearchFilter = MessageFormat.format (sLDAPSearchFilter, sKeyword, sSearchFilter_Person);
net_maclife_wechat_http_BotApp.logger.finer ("LDAP 搜索过滤器: " + sSearchFilter);

				ctx.setRequestControls (arrayRequestControls);
				for (String sBaseDN : listLDAPSearchBaseDNs)
				{
net_maclife_wechat_http_BotApp.logger.finer ("LDAP 搜索 Base DN: " + sBaseDN);
					nBaseDN ++;
					//SearchRequest sr = new SearchRequestImpl ();
					//sr.setBase (new Dn(sBaseDN)).setFilter (sSearchFilter).setScope (SearchScope.SUBTREE).setSizeLimit (nMaxFetchedEntries).ignoreReferrals ();
					//if (ctrlSortControl != null)
					//	sr.addControl (ctrlSortControl);
					//SearchCursor sc = lc.search (sr);
					////EntryCursor ec = lc.search (sBaseDN, sSearchFilter, SearchScope.SUBTREE);
					int nFetchedEntries = 0;
					//while (ec.next ())

					NamingEnumeration<SearchResult> enumSearchResults = null;
					SearchControls sc = new SearchControls ();
					sc.setSearchScope (SearchControls.SUBTREE_SCOPE);
					//enumSearchResults = ctx.search (sBaseDN, sSearchFilter, null, sc);
					enumSearchResults = ctx.search (sBaseDN, sSearchFilter, sc);
					// 所有条目
					////for (SearchResult entry : answer)
					//while (sc.next () && sc.isEntry ())
					while (enumSearchResults.hasMore ())
					{
						//Entry e = null;
						////e = ec.get ();
						//e = sc.getEntry ();
						SearchResult sr = enumSearchResults.next ();
						Attributes as = sr.getAttributes ();

						//
						// 按 Active Directory 的属性名，输出信息。这也是为什么本 Bot 不叫 LDAP 通信录的原因：属性名是按 AD 预先定义的属性名来获取的，schema aware
						//
						sb.append ("--------------------\n");	// 分割线

						// 基本名称信息
						ReadEntryValueToStringBuilder  (as, "name", "　　姓名", sb);
						ReadEntryValueToStringBuilder  (as, "displayName", "　显示名", sb);
						ReadEntryValueToStringBuilder  (as, "sn", "　　　姓", sb);
						ReadEntryValueToStringBuilder  (as, "givenName", "　　　名", sb);
						ReadEntryValueToStringBuilder  (as, "initials", "首字母缩写", sb);

						// 种联系信息
						ReadEntryValueToStringBuilder  (as, "mobile", "手机号码", sb);
						ReadEntryValuesToStringBuilder (as, "otherMobile", "其他手机", sb);
						ReadEntryValueToStringBuilder  (as, "telephoneNumber", "办公电话", sb);
						ReadEntryValuesToStringBuilder (as, "otherTelephone", "其他办公", sb);
						ReadEntryValueToStringBuilder  (as, "homePhone", "家庭电话", sb);
						ReadEntryValuesToStringBuilder (as, "otherHomePhone", "其他家庭", sb);
						ReadEntryValueToStringBuilder  (as, "ipPhone", "ＩＰ电话", sb);
						ReadEntryValuesToStringBuilder (as, "otherIpPhone", "其他ＩＰ号码", sb);
						ReadEntryValueToStringBuilder  (as, "facsimileTelephoneNumber", "传真电话", sb);
						ReadEntryValuesToStringBuilder (as, "otherFacsimileTelephoneNumber", "其他传真", sb);
						ReadEntryValueToStringBuilder  (as, "mail", "电子邮箱", sb);
						ReadEntryValuesToStringBuilder (as, "otherMailbox", "其他邮箱", sb);
						ReadEntryValueToStringBuilder  (as, "info", "电话备注", sb);

						// 再输出与公司相关的信息、职位信息
						ReadEntryValueToStringBuilder  (as, "company", "　　公司", sb);	// 如果有很多分公司，在这里保存分公司名称
						ReadEntryValueToStringBuilder  (as, "department", "　　部门", sb);	// 公司/分公司的部门名称
						ReadEntryValueToStringBuilder  (as, "title", "　　职位", sb);	// 职位
						ReadEntryValueToStringBuilder  (as, "manager", "　　上级", sb);	// 被谁管理，上级是谁
						ReadEntryValueToStringBuilder  (as, "physicalDeliveryOfficeName", "办公位置", sb);
						ReadEntryValueToStringBuilder  (as, "description", "　　说明", sb);

						// 地址信息（省、市、邮政）
						ReadEntryValueToStringBuilder  (as, "co", "　　国家", sb);
						ReadEntryValueToStringBuilder  (as, "st", "省自治区", sb);
						ReadEntryValueToStringBuilder  (as, "l", "城市／县", sb);
						ReadEntryValueToStringBuilder  (as, "streetAddress", "　　街道", sb);
						ReadEntryValueToStringBuilder  (as, "postOfficeBox", "邮政信箱", sb);
						ReadEntryValueToStringBuilder  (as, "postalCode", "邮政编码", sb);

						//if (! ec.isLast ())	// java.lang.UnsupportedOperationException: ERR_02014_UNSUPPORTED_OPERATION The method method org.apache.directory.ldap.client.api.EntryCursorImpl.isLast() is not supported
						//{
						//	hasMultipleEntry = true;
						//}
						//
						nFetchedEntries ++;
//System.err.println ("nFetchedEntries=" + nFetchedEntries + ", nMaxFetchedEntries=" + nMaxFetchedEntries + ", DEFAULT_MAX_FETCH_ENTRIES=" + DEFAULT_MAX_FETCH_ENTRIES);
						if (nFetchedEntries >= nMaxFetchedEntries)
						{	// 到达 nMaxFetchedEntries 后，本次 BaseDN 的 Search 操作就结束，继续下一个 AD 通讯簿的下一个 BaseDN
							break;
						}
					}
					//ec.close ();
					//sc.close ();
					enumSearchResults.close ();
				}
				//lc.unBind ();
				//lc.close ();
				ctx.close ();
			}
			if (listAddressBookNames.size () > 1 || nBaseDN > 1)
			{
				sb.append ("本地查询搜索了");
				if (listAddressBookNames.size () > 1)
				{
					sb.append (" ");
					sb.append (listAddressBookNames.size ());
					sb.append (" 个通讯簿");
				}
				if (nBaseDN > 1)
				{
					sb.append (" ");
					sb.append (nBaseDN);
					sb.append (" 个目录");
				}
				sb.append ('\n');
			}
			if (hasMultipleEntry)
			{
				sb.append ("本地查询在某个目录下搜索到多条通信录，请修改搜索关键字使结果精确匹配到 1 条");
				sb.append ('\n');
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return sb==null ? "" : sb.toString ();
	}

	//public static void ReadEntryValueToStringBuilder (Entry e, String sAttributeName, String sAttributeDescription, StringBuilder sb) throws LdapInvalidAttributeValueException
	//{
	//	Attribute a = e.get (sAttributeName);
	//	if (a!=null && StringUtils.isNotEmpty (a.getString()))
	//	{
	//		sb.append (sAttributeDescription);
	//		sb.append (": ");
	//		sb.append (a.getString ());
	//		sb.append ('\n');
	//	}
	//}
	public static void ReadEntryValueToStringBuilder (Attributes as, String sAttributeName, String sAttributeDescription, StringBuilder sb) throws NamingException
	{
		Attribute a = as.get (sAttributeName);
		if (a!=null && a.size () > 0)
		{
			sb.append (sAttributeDescription);
			sb.append (": ");
			sb.append (a.get (0));
			sb.append ('\n');
		}
	}

	//public static void ReadEntryValuesToStringBuilder (Entry e, String sAttributeName, String sAttributeDescription, StringBuilder sb)
	//{
	//	Attribute a = e.get (sAttributeName);
	//	if (a!=null && a.size ()>0)
	//	{
	//		sb.append (sAttributeDescription);
	//		sb.append (": ");
	//		for (Value<?> v : a)
	//		{
	//			sb.append (v.getString ());
	//			sb.append (' ');
	//		}
	//		sb.append ('\n');
	//	}
	//}
	public static void ReadEntryValuesToStringBuilder (Attributes as, String sAttributeName, String sAttributeDescription, StringBuilder sb) throws NamingException
	{
		Attribute a = as.get (sAttributeName);
		if (a!=null && a.size ()>0)
		{
			sb.append (sAttributeDescription);
			sb.append (": ");
			NamingEnumeration<?> attributeValuesEnum = a.getAll();
			while (attributeValuesEnum.hasMore ())
			{
				Object v = attributeValuesEnum.next ();
				sb.append (v);
				sb.append (' ');
			}
			sb.append ('\n');
		}
	}
}
