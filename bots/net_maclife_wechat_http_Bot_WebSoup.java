import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

import javax.net.ssl.*;
import javax.script.*;

import org.apache.commons.io.*;
import org.apache.commons.lang3.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

import com.fasterxml.jackson.databind.*;

/**
 * 抓取网页内容，解析 HTML 或 JSON，获取其中的文字信息，然后发到微信。
 * 解析 HTML/XML/JSON 这一部分可以做成 HTML/XML/JSON 模板、且，微信群里的用户可以自己添加 ---- 让高级用户自己丰富模板库。
 *
	<h3>常用用法</h3>
	<dl>
		<dt>无模板执行(裸执行) - HTML/XML 数据</dt>
		<dd><code>/ht  &lt;网址&gt;  &lt;CSS 选择器&gt;</code></dd>

		<dt>无模板执行(裸执行) - JSON/JS 数据</dt>
		<dd><code>/json  &lt;网址&gt;  &lt;/ss &lt;JavaScript 代码&gt;&gt; [/ss &lt;其他 JavaScript 代码&gt;]...</code></dd>

		<dt>执行 ht 模板</dt>
		<dd><code>$<i>模板名称</i></code> [模板参数]...<br/>
			如：<code>$<i>糗事百科</i> 2</code>
			<br/>
		</dd>

		<dt>添加/保存 ht 模板</dt>
		<dd><code></code></dd>

		<dt>显示/读取 ht 模板</dt>
		<dd><code>/ht.show  &lt;模板数字 ID 或者模板名称&gt;</code></dd>

		<dt>列出 ht 模板</dt>
		<dd><code></code></dd>

		<dt></dt>
		<dd><code></code></dd>
	</dl>

	<h3>高级用法举例</h3>
	<dl>
		<dt>裸执行，抓取 HTML/XML，使用所有参数</dt>
		<dd><code></code></dd>

		<dt>裸执行，抓取 JSON/JS，将 JS 代码中的不必要的头尾部分切除，只剩下有效的 JSON</dt>
		<dd><code></code>有时候，某个接口返回的是一段包含了 JSON 数据的 JavaScript 代码，这时候，就需要对返回来的数据进行处理。模板功能支持掐头、去尾的功能，可以将包裹在里面的 JSON 数据提取出来。</dd>

		<dt>带参数执行模板</dt>
		<dd><code></code>有的模板，在执行时，需要指定参数，这个参数是由添加模板的人自己定义的。指定的参数会按照模板作者的设计进入到 URL 或者 sub selector (JavaScript) 中，从而达到灵活处理的功能。</dd>
	</dl>
 * @author liuyan
 *
 */
public class net_maclife_wechat_http_Bot_WebSoup extends net_maclife_wechat_http_Bot
{
	public static final String SHORTCUT_PREFIX = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.web-soup.template-shortcut-prefix");
	public static final String TABLE_NAME_Tempalte = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.web-soup.table-name.template", "ht_templates");
	public static final String TABLE_NAME_SubSelectors = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.web-soup.table-name.sub-selectors", "ht_templates_other_sub_selectors");
	public static final List<String> COMMANDS_LIST_HTML = net_maclife_wechat_http_BotApp.GetConfig ().getList (String.class, "bot.web-soup.commands.html/xml");
	public static final List<String> COMMANDS_LIST_JSON = net_maclife_wechat_http_BotApp.GetConfig ().getList (String.class, "bot.web-soup.commands.json/js");

	public static final String REGEXP_FindHtParameter = "\\$\\{([pu])(\\d*)(=[^{}]*)?\\}";
	public static Pattern PATTERN_FindHtParameter = Pattern.compile (REGEXP_FindHtParameter, Pattern.CASE_INSENSITIVE);

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
		if ((COMMANDS_LIST_HTML==null || COMMANDS_LIST_HTML.isEmpty ()) && (COMMANDS_LIST_JSON==null || COMMANDS_LIST_JSON.isEmpty ()))	// 如果未配置命令，则不处理
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

		if (! net_maclife_wechat_http_BotApp.hasCommandPrefix (sContent) && ! net_maclife_wechat_http_BotApp.hasCommandPrefix (sContent, SHORTCUT_PREFIX))
		{
			return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
		}
		boolean isUsingHTTemplateNameAsShortcut = false;	// 是否快捷执行模板
		if (net_maclife_wechat_http_BotApp.hasCommandPrefix (sContent))
			sContent = net_maclife_wechat_http_BotApp.StripOutCommandPrefix (sContent);
		else if (net_maclife_wechat_http_BotApp.hasCommandPrefix (sContent, SHORTCUT_PREFIX))
		{
			sContent = net_maclife_wechat_http_BotApp.StripOutCommandPrefix (sContent, SHORTCUT_PREFIX);
			isUsingHTTemplateNameAsShortcut = true;
		}

		String sHTTemplateName = "";
		// 查看 ht 命令的模板表，看看名字是否有，如果有的话，就直接执行之
		try
		{
			String[] arrayMessages = sContent.split ("\\s+", 2);
			if (arrayMessages==null || arrayMessages.length<1)
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

			String sCommandInputed = arrayMessages[0];
			String sCommandParametersInputed = null;
			if (arrayMessages.length >= 2)
				sCommandParametersInputed = arrayMessages[1];

			String[] arrayCommandAndOptions = sCommandInputed.split ("\\" + net_maclife_wechat_http_BotApp.COMMAND_OPTION_SEPARATOR + "+", 2);
			sCommandInputed = arrayCommandAndOptions[0];
			String sCommandOptionsInputed = null;
			if (arrayCommandAndOptions.length >= 2)
				sCommandOptionsInputed = arrayCommandAndOptions[1];

			boolean isCommandValid = false;
			String sFormalCommand = null;
			for (int i=0; i<COMMANDS_LIST_HTML.size (); i++)
			{
				String sCommand = COMMANDS_LIST_HTML.get (i);
				if (! StringUtils.equalsIgnoreCase (sCommandInputed, sCommand))
					continue;

				isCommandValid = true;
				sFormalCommand = "ht";
				break;
			}

			for (int i=0; i<COMMANDS_LIST_JSON.size (); i++)
			{
				String sCommand = COMMANDS_LIST_JSON.get (i);
				if (! StringUtils.equalsIgnoreCase (sCommandInputed, sCommand))
					continue;

				isCommandValid = true;
				sFormalCommand = "json";
				break;
			}

			if (isUsingHTTemplateNameAsShortcut)
			{	// 如果输入的命令是以 ht 模板快捷命令前缀，则尝试检查模板名是否有效
				sFormalCommand = "ht";
				sHTTemplateName = sCommandInputed;
				sCommandParametersInputed = sHTTemplateName + " " + StringUtils.trimToEmpty (sCommandParametersInputed);	// 将模板名当成第一个参数
				isUsingHTTemplateNameAsShortcut = CheckHTTemplateExistence (sHTTemplateName);
			}

			if (!isCommandValid && !isUsingHTTemplateNameAsShortcut)
			//if (StringUtils.isEmpty (sFormalCommand))
				return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;

			ProcessCommand_WebSoup (jsonFrom, sFromAccount, sFromName,
				jsonTo, sToAccount, sToName,
				jsonReplyTo, sReplyToAccount, sReplyToName,
				jsonReplyTo_RoomMember, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
				jsonReplyTo_Person, sReplyToAccount_Person, sReplyToName_Person,
				sFormalCommand, sCommandInputed, isUsingHTTemplateNameAsShortcut, arrayCommandAndOptions, sCommandParametersInputed
			);
		}
		catch (Throwable e)
		{
			e.printStackTrace ();
		}

		return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
	}

	/**
	 * 用于 ht 命令中的网址中的参数展开。<br/>
	 * 类似 Bash 的默认值参数展开 的实现，但与 Bash 的 {@code ${parameter:-默认值}}  不同， :- 用 = 代替，变成 {@code ${parameter=默认值}}，也就是说，C/C++ 语言的默认参数值风格。<br/>
	 * 参数命名规则
		<dl>
			<dt>${p} ${p=默认值} ${p1} ${p1=默认值} ... ${p<font color='red'>N</font>} ${p<font color='red'>N</font>=默认值}</dt>
			<dd>p 是经过 URLEncode.encode() 之后的数值，如“济南”会变成“%E6%B5%8E%E5%8D%97”
				<ul>
					<li>如果没有默认值，则参数 p/p1/pN 必须指定值，否则给出错误提示 sURLParamsHelp。</li>
					<li>如果有默认值，并且该参数未传递值，则该参数会采用默认值</li>
					<li>如果有默认值，并且该参数传递了数值，则该参数会采用传递的数值</li>
				</ul>
			</dd>

			<dt>${u} ${u=默认值} ${u1} ${u1=默认值} ... ${u<font color='red'>N</font>} ${u<font color='red'>N</font>=默认值}</dt>
			</dd>与 p 类似，只不过，u 的数值不会被 URLEncode.encode()。所以，<font color='darkcyan'><b>一般来说， json 的 subselector 脚本中通常应该用 u 参数</b></font></dd>
		</dl>
	 * @param sURL 网址(正常情况下应该含有类似 ${p}、 ${p2=默认值} 的参数)。 如果是 js/json 数据类型，则 subselector 脚本中可能也包含 ${p} ${u} 参数
	 * @param listOrderedParams 参数列表。注意，参数号是从 1 开始的，也就是说，参数列表中的 0 其实是 ht 命令的别名，被忽略
	 * @param sURLParamsHelp 参数帮助信息。如果 URL 中含有参数，但未指定值、也没有默认值，则给出该提示信息。
	 * @return 参数展开后的 url
	 * @throws UnsupportedEncodingException
	 */
	public static String HtParameterExpansion_DefaultValue_CStyle (String sURL, List<String> listOrderedParams, String sURLParamsHelp) throws UnsupportedEncodingException
	{
net_maclife_wechat_http_BotApp.logger.finest ("url: " + sURL);
net_maclife_wechat_http_BotApp.logger.finest ("params: " + listOrderedParams);
		Matcher matcher = PATTERN_FindHtParameter.matcher (sURL);
		StringBuffer sbReplace = new StringBuffer ();
		boolean bMatched = false;
		while (matcher.find ())
		{
			bMatched = true;
			String sParamCommand = matcher.group(1);
			String sN = matcher.group (2);
			String sDefault = matcher.group (3);
			if (StringUtils.startsWith (sDefault, "="))
				sDefault = sDefault.substring (1);
			int n = sN.isEmpty () ? 1 : Integer.parseInt (sN);
			if (sDefault==null && listOrderedParams.size () <= n)
				throw new IllegalArgumentException ("第 " + n + " 个参数未指定参数值，也未设置默认值。 " + (StringUtils.isEmpty (sURLParamsHelp) ? "" : sURLParamsHelp));
			if (StringUtils.equalsIgnoreCase (sParamCommand, "u"))	// "u": unescape
			{
				matcher.appendReplacement (sbReplace, listOrderedParams.size () > n ? listOrderedParams.get (n) : sDefault);
			}
			else
			{
				matcher.appendReplacement (sbReplace, listOrderedParams.size () > n ? URLEncoder.encode (listOrderedParams.get (n), net_maclife_wechat_http_BotApp.utf8) : sDefault);
			}
		}
		matcher.appendTail (sbReplace);
//System.out.println (sbReplace);

		if (bMatched)
			sURL = sbReplace.toString ();

net_maclife_wechat_http_BotApp.logger.fine ("url after parameter expansion: " + sURL);
		return sURL;
	}

	/**
	 * 修复 HT 命令与 SubSelector 参数组相关的参数，使其数量一致。且，至少保证都有 1 条。
	 * @param listSubSelectors
	 * @param listLeftPaddings
	 * @param listExtracts
	 * @param listAttributes
	 * @param listFormatFlags
	 * @param listFormatWidth
	 * @param listRightPaddings
	 */
	void FixHTCommandSubSelectorParameterGroupsSize (List<String> listSubSelectors, List<String> listLeftPaddings, List<String> listExtracts, List<String> listAttributes, List<String> listFormatFlags, List<String> listFormatWidth, List<String> listRightPaddings)
	{
		if (listSubSelectors.isEmpty ())	// 有可能不指定 sub-selector，而只指定了 selector，这时候需要补充参数，否则空的 listSubSelectors 会导致空的输出
			listSubSelectors.add ("");

		for (int i=listLeftPaddings.size (); i<listSubSelectors.size (); i++)
			listLeftPaddings.add ("");
		for (int i=listExtracts.size (); i<listSubSelectors.size (); i++)
			listExtracts.add ("");
		for (int i=listAttributes.size (); i<listSubSelectors.size (); i++)
			listAttributes.add ("");
		for (int i=listFormatFlags.size (); i<listSubSelectors.size (); i++)
			listFormatFlags.add ("");
		for (int i=listFormatWidth.size (); i<listSubSelectors.size (); i++)
			listFormatWidth.add ("");
		for (int i=listRightPaddings.size (); i<listSubSelectors.size (); i++)
			listRightPaddings.add ("");
	}

	/**
	 * 获取任意 HTML 网址的内容，将解析结果显示出来。
	 * 目前支持读取 Content-Type 为 text/*, application/xml, or application/xhtml+xml (这些是 Jsoup 默认支持的内容类型) 和 application/json (这是单独处理的) 的内容的读取。
	 *   - 最直接的方式就是直接执行：  ht  <网址> <CSS选择器>
	 *   - 模板化： 对于经常用到的 html 网址可做成模板，用 ht.run <模板名/或模板ID> 来执行
	 *
	 * @param params
	 */
	void ProcessCommand_WebSoup
		(
			JsonNode jsonFrom, String sFromAccount, String sFromName,
			JsonNode jsonTo, String sToAccount, String sToName,
			JsonNode jsonReplyTo, String sReplyToAccount, String sReplyToName,
			JsonNode jsonReplyTo_RoomMember, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			JsonNode jsonReplyTo_Person, String sReplyToAccount_Person, String sReplyToName_Person,
			String sFormalCommand, String sCommandAlias, boolean isUsingHTTemplateNameAsShortcut, String[] arrayCommandOptions, String params
		) throws Exception
	{
		String botCmdAlias = null;
		boolean usingGFWProxy = false;
		//boolean isOutputScheme = true;	// 是否输出 URL 中的 scheme (如 http://  https://)，这是应对一些不用命令前缀来触发其命令的 bot “智能”输出网页标题 的功能而设置的 (最起码，如果别人发的消息只有 1 个 url，你才输出其标题好吧)

		String sAction = null;
		if (arrayCommandOptions!=null && arrayCommandOptions.length>0)
		{
			for (int i=0; i<arrayCommandOptions.length; i++)
			{
				String env = arrayCommandOptions[i];
				if (StringUtils.equalsAnyIgnoreCase (env, "add"))
					sAction = "+";
				else if (StringUtils.equalsAnyIgnoreCase (env, "run", "go"))
					sAction = "run";
				else if (StringUtils.equalsAnyIgnoreCase (env, "show"))
					sAction = "show";
				else if (StringUtils.equalsAnyIgnoreCase (env, "list", "search"))
					sAction = "list";
				else if (StringUtils.equalsAnyIgnoreCase (env, "stats"))
					sAction = "stats";
				//else if (StringUtils.equalsAnyIgnoreCase (env, "os"))
				//	isOutputScheme = true;
				else if (StringUtils.equalsAnyIgnoreCase (env, "gfw", "ProxyOn"))
					usingGFWProxy = true;
				else
					continue;
			}
		}
		if (isUsingHTTemplateNameAsShortcut && StringUtils.isEmpty (sAction))
		{
			sAction = "run";
System.err.println ("用 ht 模板名快捷 ht 命令，但未指定动作，动作自动变为 " + sAction);
		}
System.err.println ("ht action: " + sAction);

		int opt_max_response_lines = 20;
		boolean opt_max_response_lines_specified = false;

		String sContentType = "html";	// 人工指定的该网址返回的是什么类型的数据，目前支持 html / json
		int nJS_Cut_Start = 0;
		int nJS_Cut_End = 0;
		long nID = 0;
		String sName = null;
		String sURL = null;
		String sURLParamsHelp = null;
		String sSelector = null;
		List<String> listSubSelectors = new ArrayList<String> ();
		List<String> listLeftPaddings = new ArrayList<String> ();
		List<String> listExtracts = new ArrayList<String> ();
		List<String> listAttributes = new ArrayList<String> ();
		List<String> listFormatFlags = new ArrayList<String> ();
		List<String> listFormatWidth = new ArrayList<String> ();
		List<String> listRightPaddings = new ArrayList<String> ();

		String sHTTPUserAgent = null;
		String sHTTPRequestMethod = null;
		String sHTTPReferer = null;

		boolean isIgnoreContentType = false;
		boolean isIgnoreHTTPSCertificateValidation = true;

		long iStart = 0;

		List<String> listParams = net_maclife_wechat_http_BotApp.SplitCommandLine (params);
		List<String> listOrderedParams = new ArrayList<String> ();
		if (listParams != null)
		{
			for (int i=0; i<listParams.size (); i++)
			{
				String param = listParams.get (i);
				if (param.startsWith ("/") || param.startsWith ("-"))
				{
					if (i == listParams.size () - 1)
					{
						SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, param + " 需要指定参数值");
						return;
					}

					param = param.substring (1);

					i++;
					String value = listParams.get (i);
					if (StringUtils.equalsAnyIgnoreCase (param, "ct", "ContentType", "Content-Type"))
					{
						sContentType = value;
						if (StringUtils.equalsAnyIgnoreCase (sContentType, "json", "js"))
						{
							//
						}
						else
							sContentType = "html";	// 其他的默认为 html/xml
					}
					else if (StringUtils.equalsAnyIgnoreCase (param, "jcs"))
						nJS_Cut_Start = Integer.parseInt (value);
					else if (StringUtils.equalsAnyIgnoreCase (param, "jce"))
						nJS_Cut_End = Integer.parseInt (value);
					else if (StringUtils.equalsAnyIgnoreCase (param, "u", "url", "网址"))
					{
						if (StringUtils.equalsAnyIgnoreCase (value, "http://", "https://"))
							sURL = value;
						else
							sURL = "http://" + value;
					}
					else if (StringUtils.equalsAnyIgnoreCase (param, "h", "help", "帮助"))
						sURLParamsHelp = value;
					//else if (StringUtils.equalsAnyIgnoreCase (param, "s" ,"selector" || param.equalsIgnoreCase ("选择器"))
					//	sSelector = value;
					else if (StringUtils.equalsAnyIgnoreCase (param, "ss", "sub-selector", "子选择器"))
					{	// 遇到 /ss 时，把 listSubSelectors listExtracts listAttributes 都添加一项，listExtracts listAttributes 添加的是空字符串，这样是为了保证三个列表的数量相同
						// 另外：  /ss  /e  /a 的参数顺序必须保证 /ss 是在前面的
						listSubSelectors.add (value);
						FixHTCommandSubSelectorParameterGroupsSize (listSubSelectors, listLeftPaddings, listExtracts, listAttributes, listFormatFlags, listFormatWidth, listRightPaddings);
					}
					else if (StringUtils.equalsAnyIgnoreCase (param, "lp", "LeftPadding", "PaddingLeft", "左填充"))
					{
						FixHTCommandSubSelectorParameterGroupsSize (listSubSelectors, listLeftPaddings, listExtracts, listAttributes, listFormatFlags, listFormatWidth, listRightPaddings);
						listLeftPaddings.set (listLeftPaddings.size () - 1, value);
					}
					else if (StringUtils.equalsAnyIgnoreCase (param, "e", "extract", "取", "取值"))
					{
						FixHTCommandSubSelectorParameterGroupsSize (listSubSelectors, listLeftPaddings, listExtracts, listAttributes, listFormatFlags, listFormatWidth, listRightPaddings);
						listExtracts.set (listExtracts.size () - 1, value);	// 在上面的修复参数数量后，更改最后 1 项
					}
					else if (StringUtils.equalsAnyIgnoreCase (param, "a" ,"attr" ,"attribute", "属性", "属性名"))
					{
						FixHTCommandSubSelectorParameterGroupsSize (listSubSelectors, listLeftPaddings, listExtracts, listAttributes, listFormatFlags, listFormatWidth, listRightPaddings);
						listAttributes.set (listAttributes.size () - 1, value);	// 在上面的修复参数数量后，更改最后 1 项
					}
					else if (StringUtils.equalsAnyIgnoreCase (param, "ff" ,"FormatFlags" ,"FormatFlag", "格式化符号"))
					{
						FixHTCommandSubSelectorParameterGroupsSize (listSubSelectors, listLeftPaddings, listExtracts, listAttributes, listFormatFlags, listFormatWidth, listRightPaddings);
						listFormatFlags.set (listFormatFlags.size () - 1, value);	// 在上面的修复参数数量后，更改最后 1 项
					}
					else if (StringUtils.equalsAnyIgnoreCase (param, "fw" ,"FormatWidth", "格式化宽度"))
					{
						FixHTCommandSubSelectorParameterGroupsSize (listSubSelectors, listLeftPaddings, listExtracts, listAttributes, listFormatFlags, listFormatWidth, listRightPaddings);
						listFormatWidth.set (listFormatWidth.size () - 1, value);	// 在上面的修复参数数量后，更改最后 1 项
					}
					else if (StringUtils.equalsAnyIgnoreCase (param, "rp" ,"RightPadding" ,"PaddingRight", "右填充"))
					{
						FixHTCommandSubSelectorParameterGroupsSize (listSubSelectors, listLeftPaddings, listExtracts, listAttributes, listFormatFlags, listFormatWidth, listRightPaddings);
						listRightPaddings.set (listRightPaddings.size () - 1, value);	// 在上面的修复参数数量后，更改最后 1 项
					}
					else if (StringUtils.equalsAnyIgnoreCase (param, "n" ,"name", "名称", "模板名"))
					{
						sName = value;
					}
					else if (StringUtils.equalsAnyIgnoreCase (param, "i" ,"id", "#", "编号"))
					{
						//sID = value;
						nID = Integer.parseInt (value);
					}
					//else if (StringUtils.equalsAnyIgnoreCase (param, "max", "最多"))
					//{
					//	sMax = value;
					//	opt_max_response_lines = Integer.parseInt (value);
					//	if (opt_max_response_lines > MAX_RESPONSE_LINES_LIMIT)
					//		opt_max_response_lines = MAX_RESPONSE_LINES_LIMIT;
					//}
					else if (StringUtils.equalsAnyIgnoreCase (param, "ua", "user-agent", "浏览器"))
						sHTTPUserAgent = value;
					else if (StringUtils.equalsAnyIgnoreCase (param, "m" ,"method", "方法"))
						sHTTPRequestMethod = value;
					else if (StringUtils.equalsAnyIgnoreCase (param, "r" ,"ref" ,"refer" ,"referer", "来源"))
						sHTTPReferer = value;
					else if (StringUtils.equalsAnyIgnoreCase (param, "start" ,"offset", "起始", "偏移量"))
					{
						iStart = Integer.parseInt (value);
						iStart -= 1;
						if (iStart <= 0)
							iStart = 0;
					}
					else if (StringUtils.equalsAnyIgnoreCase (param, "ict" ,"IgnoreContentType"))
					{
						isIgnoreContentType = BooleanUtils.toBoolean (value);
					}
					else if (StringUtils.equalsAnyIgnoreCase (param, "icv" ,"IgnoreHTTPSCertificateValidation"))
					{
						isIgnoreHTTPSCertificateValidation = BooleanUtils.toBoolean (value);
					}
				}
				else
					listOrderedParams.add (param);
			}
		}

		FixHTCommandSubSelectorParameterGroupsSize (listSubSelectors, listLeftPaddings, listExtracts, listAttributes, listFormatFlags, listFormatWidth, listRightPaddings);

		// 处理用 json 命令别名执行命令时的特别设置： (1).强制改变 Content-Type  (2).强制忽略 http 返回的 Content-Type，否则 jsoup 会报错
		if (StringUtils.equalsIgnoreCase (botCmdAlias, "json"))
		{
			sContentType = "json";
			isIgnoreContentType = true;
		}

		// 根据不同的 action 重新解释 listOrderedParams、检查 param 有效性
		if (listOrderedParams.size () > 0)
		{	// 覆盖 /url <URL> 参数值
			String value = listOrderedParams.get (0);
			if (StringUtils.startsWithIgnoreCase (value, "http://") || StringUtils.startsWithIgnoreCase (value, "https://"))
				sURL = value;
			else
				sURL = "http://" + value;
		}
		if (listOrderedParams.size () > 1)
			sSelector = listOrderedParams.get (1);

		if (StringUtils.isEmpty (sAction))
		{
			if (StringUtils.isEmpty (params))
			{
				return;
			}
		}
		else
		{
			if (StringUtils.equalsAnyIgnoreCase (sAction, "show", "run"))
			{
				sURL = null;
				sSelector = null;
				listSubSelectors.clear ();
				listLeftPaddings.clear ();
				listExtracts.clear ();
				listAttributes.clear ();
				listRightPaddings.clear ();

				if (listOrderedParams.size () > 0)
				{
					String value = listOrderedParams.get (0);
					if (listOrderedParams.get (0).matches ("\\d+"))
						nID = Integer.parseInt (value);
					else
						sName = value;
				}
				if (nID == 0 && StringUtils.isEmpty (sName))
				{
					SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "必须指定 <模板编号>(纯数字) 或者 <模板名称>(非纯数字) 参数.");
					return;
				}
			}
			else if (StringUtils.equalsAnyIgnoreCase (sAction, "+"))
			{
				if (StringUtils.isEmpty (sURL) || StringUtils.isEmpty (sName))
				{
					if (StringUtils.equalsAnyIgnoreCase (sContentType, "json", "js")  && listSubSelectors.isEmpty ())
						SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "必须指定 <网址>、/ss <JSON 取值表达式>、/name <模板名称> 参数.");
					else if (StringUtils.isEmpty (sSelector))
						SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "必须指定 <网址>、<CSS 选择器>、/name <模板名称> 参数.");
					return;
				}
				if (StringUtils.isNotEmpty (sName) && sName.matches ("\\d+"))
				{
					SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "模板名称 不能是全数字，在执行模板时，全数字会被当做 ID 使用.");
					return;
				}

				for (int i=0; i<listExtracts.size (); i++)
				{
					String sExtract = listExtracts.get (i);
					if (StringUtils.equalsIgnoreCase (sExtract, "attr"))
					{
						if (listAttributes.size () < (i+1))
						{
							SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "/e 指定了 attr, 但 attr 需要用 /a 指定具体属性名，我已经在属性名列表里找不到剩余的属性名了.");
							return;
						}
						String sAttr = listAttributes.get (i);
						if (StringUtils.isEmpty (sAttr))
						{
							SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "/e 指定了 attr, attr 需要用 /a 指定具体属性名.");
							return;
						}
					}
				}
			}
			else if (StringUtils.equalsIgnoreCase (sAction, "list"))
			{
				//if (StringUtils.isEmpty (sStart))
				//{
				//	SendTextMessage (sReplyToAccount, sReplyToName, sFromAccount_RoomMember, sFromName_RoomMember, "必须指定 /start <列表起点> 参数，另外 /max <数量> 可限制返回的结果数量. 其他参数被当做查询条件使用, 除了 /extract /attr /method 是精确匹配外, 其他都是模糊匹配.");
				//	return;
				//}
			}
		}

		// 实际执行各 Action
		int nLines = 0;
		List<Map<String, Object>> listQueryResult = null;
		try
		{
			if (StringUtils.equalsAnyIgnoreCase (sAction, "show", "run", "list"))
			{
				listQueryResult = ReadTemplate (sAction, nID, sName, sURL, sSelector, sHTTPUserAgent, sHTTPRequestMethod, sHTTPReferer, iStart, opt_max_response_lines);

				if (StringUtils.equalsAnyIgnoreCase (sAction, "show", "list"))
				{
					//if (nLines >= opt_max_response_lines)
					//	break;
					if (StringUtils.equalsIgnoreCase (sAction, "show") && listQueryResult!=null && !listQueryResult.isEmpty ())
					{
						Map<String, Object> mapHTTemplate = listQueryResult.get (0);
						SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, RestoreCommandLineFromTemplate(mapHTTemplate));
					}
					else
					{
						//
					}
					nLines ++;
				}
				if (listQueryResult==null || listQueryResult.isEmpty())
				{
					if (StringUtils.equalsAnyIgnoreCase (sAction, "list"))
						SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "未找到符合条件 的 html/json 解析模板");
					else
						SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "未找到 " + (nID != 0 ? "ID=[#" + nID : "名称=[" + sName) + "] 的 html/json 解析模板");
					return;
				}
				if (StringUtils.equalsAnyIgnoreCase (sAction, "show", "list"))
				{	// show 和 list 两个 action 到这里就处理完了，返回。剩下的 run 继续往下执行
					return;
				}
			}
			else if (StringUtils.equalsAnyIgnoreCase (sAction, "+"))
			{
				SaveTemplate (sFromAccount, sFromName, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
					nID, sName, sURL, sURLParamsHelp, usingGFWProxy, isIgnoreHTTPSCertificateValidation,
					sContentType, isIgnoreContentType,
					nJS_Cut_Start, nJS_Cut_End,
					sSelector,
					listSubSelectors,
					listLeftPaddings,
					listExtracts,
					listAttributes,
					listFormatFlags,
					listFormatWidth,
					listRightPaddings,
					sHTTPUserAgent,
					sHTTPRequestMethod,
					sHTTPReferer,
					opt_max_response_lines
				);
			}

			if (listQueryResult!=null && listQueryResult.size()>0)
			{
				Map<String, Object> mapHTTemplate = listQueryResult.get (0);
				nID = (long)mapHTTemplate.get ("id");
				sName = (String)mapHTTemplate.get ("name");
				sURL = (String)mapHTTemplate.get ("url");
				sURLParamsHelp = (String)mapHTTemplate.get ("url_param_usage");
				usingGFWProxy = (boolean)mapHTTemplate.get ("use_gfw_proxy");
				isIgnoreHTTPSCertificateValidation = (boolean)mapHTTemplate.get ("ignore_https_certificate_validation");
				sContentType = (String)mapHTTemplate.get ("content_type");
				isIgnoreContentType = (boolean)mapHTTemplate.get ("ignore_content_type");
				nJS_Cut_Start = (int)mapHTTemplate.get ("js_cut_start");
				nJS_Cut_End = (int)mapHTTemplate.get ("js_cut_end");
				sSelector = (String)mapHTTemplate.get ("selector");
				listSubSelectors = (List<String>)mapHTTemplate.get ("list sub selectors");
				listLeftPaddings = (List<String>)mapHTTemplate.get ("list left paddings");
				listExtracts = (List<String>)mapHTTemplate.get ("list extracts");
				listAttributes = (List<String>)mapHTTemplate.get ("list attributes");
				listFormatFlags = (List<String>)mapHTTemplate.get ("list format flags");
				listFormatWidth = (List<String>)mapHTTemplate.get ("list format width");
				listRightPaddings = (List<String>)mapHTTemplate.get ("list right paddings");
				sHTTPUserAgent = (String)mapHTTemplate.get ("ua");
				sHTTPRequestMethod = (String)mapHTTemplate.get ("request_method");
				sHTTPReferer = (String)mapHTTemplate.get ("referer");
				opt_max_response_lines = (short)mapHTTemplate.get ("max");
			}

			if (StringUtils.isEmpty (sURL))
			{
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "你想看 hyper text，却不提供网址. 用第一个参数指定 <网址>");
				return;
			}

			if (StringUtils.equalsAnyIgnoreCase (sContentType, "json", "js"))
			{
				if (listSubSelectors.isEmpty ())
				{
					SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "json 必须指定 <网址>、/ss <JSON 取值表达式> 参数.");
					return;
				}
			}
			else if (StringUtils.isEmpty (sSelector))
			{
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "未提供 '选择器'，都不知道你具体想看什么. 用第二个参数指定 <CSS 选择器>. 选择器的写法见参考文档: http://jsoup.org/apidocs/org/jsoup/select/Selector.html");
				return;
			}

			DoFetchHyperText (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
				sAction, listOrderedParams,
				nID, sName, sURL, sURLParamsHelp, usingGFWProxy, isIgnoreHTTPSCertificateValidation,
				sContentType, isIgnoreContentType,
				nJS_Cut_Start, nJS_Cut_End,
				sSelector,
				listSubSelectors,
				listLeftPaddings,
				listExtracts,
				listAttributes,
				listFormatFlags,
				listFormatWidth,
				listRightPaddings,

				sHTTPUserAgent,
				sHTTPRequestMethod,
				sHTTPReferer,

				opt_max_response_lines
			);
		}
		catch (Exception e)
		{
			e.printStackTrace ();
			SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, e.toString ());
		}
		finally
		{
		}
	}

	void DoFetchHyperText (
			String sReplyToAccount, String sReplyToName, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			String sAction, List<String> listOrderedCommandParams,
			long nTemplateID, String sTemplateName, String sURL, String sURLParamsHelp, boolean usingGFWProxy, boolean isIgnoreHTTPSCertificateValidation,
			String sContentType, boolean isIgnoreContentType,
			int nJS_Cut_Start, int nJS_Cut_End,
			String sSelector,
			List<String> listSubSelectors,
			List<String> listLeftPaddings,
			List<String> listExtracts,
			List<String> listAttributes,
			List<String> listFormatFlags,
			List<String> listFormatWidth,
			List<String> listRightPaddings,

			String sHTTPUserAgent,
			String sHTTPRequestMethod,
			String sHTTPReferer,

			int opt_max_response_lines
	)
	{
		int opt_timeout_length_seconds = net_maclife_util_HTTPUtils.DEFAULT_CONNECT_TIMEOUT_SECOND;
		int nLines = 0;
		int iStart = 0;

		try
		{
			// 最后，如果带有 URLParam，将其替换掉 sURL 中的 ${p} 字符串 (${p} ${p=} ${p=默认值} ${p2=...} ... )
			if (StringUtils.equalsAnyIgnoreCase (sAction, "run"))
			{
				sURL = HtParameterExpansion_DefaultValue_CStyle (sURL, listOrderedCommandParams, sURLParamsHelp);
			}

			Document doc = null;
			String sQueryString = null;
			if (StringUtils.equalsIgnoreCase (sHTTPRequestMethod, "POST") && sURL.contains ("?"))
			{
				int i = sURL.indexOf ('?');
				sQueryString = sURL.substring (i+1);	// 得到 QueryString，用来将其传递到 POST 消息体中
				sURL = sURL.substring (0, i);	// 将 URL 中的 QueryString 剔除掉
			}
System.out.println (sURL);

			Proxy proxy = null;
			if (usingGFWProxy)
			{
				proxy = new Proxy (Proxy.Type.valueOf (System.getProperty ("GFWProxy.Type")), new InetSocketAddress(System.getProperty ("GFWProxy.Host"), Integer.parseInt (System.getProperty ("GFWProxy.Port"))));
				System.out.println (proxy);
			}
			if (StringUtils.equalsIgnoreCase (sContentType, "json")
				//|| jsoup_conn.response ().contentType ().equalsIgnoreCase ("application/json")
				|| StringUtils.equalsIgnoreCase (sContentType, "js")
				//|| jsoup_conn.response ().contentType ().equalsIgnoreCase ("application/javascript")
			)
			{	// 处理 JSON 数据

				URL url = new URL (sURL);
				HttpURLConnection http = null;
				HttpsURLConnection https = null;
				if (url.getProtocol ().equalsIgnoreCase ("https"))
				{
					if (proxy != null)
						https = (HttpsURLConnection)url.openConnection (proxy);
					else
						https = (HttpsURLConnection)url.openConnection ();

					http = https;
				}
				else
				{
					if (proxy != null)
						http = (HttpURLConnection)url.openConnection (proxy);
					else
						http = (HttpURLConnection)url.openConnection ();
				}

				http.setConnectTimeout (opt_timeout_length_seconds * 1000);
				http.setReadTimeout (opt_timeout_length_seconds * 1000);
				//HttpURLConnection.setFollowRedirects (false);
				//http.setInstanceFollowRedirects (false);    // 不自动跟随重定向连接，我们只需要得到这个重定向连接 URL
				http.setDefaultUseCaches (false);
				http.setUseCaches (false);
				if (https != null && isIgnoreHTTPSCertificateValidation)
				{
					// 参见: http://www.nakov.com/blog/2009/07/16/disable-certificate-validation-in-java-ssl-connections/
					https.setSSLSocketFactory (net_maclife_util_HTTPUtils.sslContext_TrustAllCertificates.getSocketFactory());
					https.setHostnameVerifier (net_maclife_util_HTTPUtils.hvAllowAllHostnames);
				}
				if (StringUtils.isNotEmpty (sHTTPUserAgent))
				{
System.out.println (sHTTPUserAgent);
					http.setRequestProperty ("User-Agent", sHTTPUserAgent);
				}
				if (StringUtils.isNotEmpty (sHTTPReferer))
				{
System.out.println (sHTTPReferer);
					http.setRequestProperty ("Referer", sHTTPReferer);
				}

				if (StringUtils.equalsIgnoreCase (sHTTPRequestMethod, "POST"))
				{
					http.setDoOutput (true);
					//http.setRequestProperty ("Content-Type", "application/x-www-form-urlencoded");
					http.setRequestProperty ("Content-Length", sQueryString);
				}
				http.connect ();
				if (StringUtils.equalsIgnoreCase (sHTTPRequestMethod, "POST"))
				{
					DataOutputStream dos = new DataOutputStream (http.getOutputStream());
					dos.writeBytes (sQueryString);
					dos.flush ();
					dos.close ();
				}
				int iResponseCode = http.getResponseCode();
				String sStatusLine = http.getHeaderField(0);    // HTTP/1.1 200 OK、HTTP/1.1 404 Not Found
				String sContent = "";

				int iMainResponseCode = iResponseCode/100;
				if (iMainResponseCode==2)
				{
					InputStream is = null;
					is = http.getInputStream();
					sContent = IOUtils.toString (is, net_maclife_util_HTTPUtils.GetContentEncodingFromHTTPHead (http, net_maclife_wechat_http_BotApp.utf8));
					sContent = StringUtils.stripToEmpty (sContent);

					if (sContent.isEmpty ())
						throw new RuntimeException ("返回的是空内容，不是有效的 json 数据");
				}
				else
				{
					InputStream is = null;
					try
					{
						if (iMainResponseCode >= 4)
							is = http.getErrorStream();
						else
							is = http.getInputStream();
						//s = new DataInputStream (is).readUTF();
						sContent = IOUtils.toString (is, net_maclife_util_HTTPUtils.GetContentEncodingFromHTTPHead (http, net_maclife_wechat_http_BotApp.utf8));
					}
					catch (Exception e)
					{
					    //e.printStackTrace ();
					    System.err.println (e);
					}

					throw new RuntimeException ("HTTP 响应不是 2XX: " + sStatusLine + "\n" + sContent);
				}

				// JSON 数据对 selector sub-selector extract attribute 做了另外的解释：
				//  selector 或 subselector -- 这是类似  a.b[0].c.d 一样的 JSON 表达式，用于取出对象的数值
				//  extract -- 取出 value 还是变量名(var)，还是数组索引号(index)
				//  attr -- 无用
				StringBuilder sbJSON = new StringBuilder ();
				String sJSON;
File f = new File ("ht-js-content.wechatbotengine.js");
FileWriter fw = new FileWriter (f);
fw.write (sContent);
fw.close ();
//System.err.println (sContent);
				//if (StringUtils.equalsIgnoreCase (sContentType, "json") )	//|| jsoup_conn.response ().contentType ().equalsIgnoreCase ("application/json"))
				{
					if (nJS_Cut_Start > 0)
						sContent = sContent.substring (nJS_Cut_Start);
					if (nJS_Cut_End > 0)
						sContent = sContent.substring (0, sContent.length () - nJS_Cut_End);
					sbJSON.append ("var o = " + sContent + ";");
				}
				//else
				//	sbJSON.append (sContent + ";\n");

				//ObjectMapper om = new ObjectMapper();
				//om.configure (JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
				//JsonNode node = om.readTree (sJSON);
				ScriptEngine jse = net_maclife_wechat_http_BotApp.public_jse;
				ScriptContext jsContext = new SimpleScriptContext ();
				StringWriter stdout = new StringWriter ();
				StringWriter stderr = new StringWriter ();
				jsContext.setWriter (stdout);
				jsContext.setErrorWriter (stderr);

				sJSON = sbJSON.toString ();
				Object evaluateResult = jse.eval (sJSON, jsContext);	// 先执行 json
				StringBuilder sbText = new StringBuilder ();
				for (int i=0; i<listSubSelectors.size (); i++)	// 再依次执行各个 sub selector 的求值表达式 (javascript)
				{
					String sSubSelector = listSubSelectors.get (i);
					String sLeftPadding = listLeftPaddings.get (i);
					String sRightPadding = listRightPaddings.get (i);
					try
					{
						// 由于 JavaScript 代码中可能需要读取参数 1 2 3.. N ...，所以，要识别 javascript 代码中的 ${p} ${p2} ${p3}...${pN} 参数声明，并将其替换为参数值
						sSubSelector = HtParameterExpansion_DefaultValue_CStyle (sSubSelector, listOrderedCommandParams, sURLParamsHelp);

						evaluateResult = jse.eval (sSubSelector, jsContext);
						if (evaluateResult == null)
						{
net_maclife_wechat_http_BotApp.logger.warning ("javascript 求值返回 null 结果。脚本概览: " + StringUtils.left (sSubSelector, 80));
						}
						else if (StringUtils.isNotEmpty (evaluateResult.toString ()))
						{	// 仅当要输出的字符串有内容时才会输出（并且也输出前填充、后填充）
							if (StringUtils.isNotEmpty (sLeftPadding))
								sbText.append (sLeftPadding);

							sbText.append (evaluateResult);

							if (StringUtils.isNotEmpty (sRightPadding))
								sbText.append (sRightPadding);
						}
					}
					catch (Exception e)
					{	// javascript 求值可能出错，这里不给用户报错，只在后台打印
						e.printStackTrace ();
					}
				}

				String sText = sbText.toString ();
				Matcher mat = PAT_IRC_ColorSequence.matcher (sText);
				sText = mat.replaceAll ("");	// 先剔除掉 IRC 颜色序列
				sText = StringUtils.replaceEach (sText, arrayReplaceControlCharacters_Sources, arrayReplaceControlCharacters_Replacements);	// 再替换（或剔除）其他特殊字符
System.out.println (sText);
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, sText);
			}
			else
			{
				org.jsoup.Connection jsoup_conn = null;
				jsoup_conn = org.jsoup.Jsoup.connect (sURL);
				if (usingGFWProxy)
				{
					jsoup_conn.proxy (proxy);
				}

				//if (sURL.startsWith ("https") && isIgnoreHTTPSCertificateValidation)
				//{	// 由于 Jsoup 还没有设置 https 参数的地方，所以，要设置成全局/默认的（不过，这样设置后，就影响到后续的 https 访问，即使是没设置 IgnoreHTTPSCertificateValidation 的……）
				//	// 参见: http://www.nakov.com/blog/2009/07/16/disable-certificate-validation-in-java-ssl-connections/
				//	HttpsURLConnection.setDefaultSSLSocketFactory (sslContext_TrustAllCertificates.getSocketFactory());
				//	HttpsURLConnection.setDefaultHostnameVerifier (hvAllowAllHostnames);
				//}
				jsoup_conn.validateTLSCertificates (isIgnoreHTTPSCertificateValidation);	// jsoup 1.8.2 增加了“是否忽略证书”的设置
				jsoup_conn.ignoreHttpErrors (true)
						.ignoreContentType (isIgnoreContentType)
						.timeout (opt_timeout_length_seconds * 1000)
						;
				if (StringUtils.isNotEmpty (sHTTPUserAgent))
				{
System.out.println (sHTTPUserAgent);
					jsoup_conn.userAgent (sHTTPUserAgent);
				}
				if (StringUtils.isNotEmpty (sHTTPReferer))
				{
System.out.println (sHTTPReferer);
					jsoup_conn.referrer (sHTTPReferer);
				}

				if (StringUtils.equalsIgnoreCase (sHTTPRequestMethod, "POST"))
				{
System.out.println (sHTTPRequestMethod);
					if (StringUtils.isNotEmpty (sQueryString))
					{
System.out.println (sQueryString);
						Map<String, String> mapParams = new HashMap<String, String> ();	// 蛋疼的 jsoup 不支持直接把 sQueryString 统一传递过去，只能重新分解成单独的参数
						String[] arrayQueryParams = sQueryString.split ("&");
						for (String param : arrayQueryParams)
						{
							String[] arrayParam = param.split ("=");
							mapParams.put (arrayParam[0], arrayParam[1]);
						}
						//doc = jsoup_conn.data (sQueryString).post ();
						doc = jsoup_conn.data (mapParams).post ();
					}
					else
						doc = jsoup_conn.post ();
				}
				else
					doc = jsoup_conn.get();

				Elements es = doc.select (sSelector);
				if (es.size () == 0)
				{
					SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "选择器 " + sSelector + " 没有选中任何元素");
					return;
				}

				StringBuilder sbText = new StringBuilder ();
			ht_main_loop:
				for (int iElementIndex=iStart; iElementIndex<es.size (); iElementIndex++)
				{
					Element e = es.get (iElementIndex);
					if (nLines >= opt_max_response_lines)
					{
						//SendTextMessage (sReplyToAccount, sReplyToName, sFromAccount_RoomMember, sFromName_RoomMember, "略……");
						break;
					}

					//System.out.println (e.text());
					Element sub_e = e;
					String text = "";

//System.err.println ("处理 " + e);
				ht_sub_loop:
					for (int iSS=0; iSS<listSubSelectors.size (); iSS++)
					{
						String sSubSelector = listSubSelectors.get (iSS);
						String sLeftPadding = listLeftPaddings.get (iSS);
						String sExtract = listExtracts.get (iSS);
						String sAttr = listAttributes.get (iSS);
						String sFormatFlags = listFormatFlags.get (iSS);
						String sFormatWidth = listFormatWidth.get (iSS);
						String sRightPadding = listRightPaddings.get (iSS);

						if (StringUtils.isEmpty (sSubSelector))
						{	// 如果子选择器为空，则采用主选择器选出的元素
							sub_e = e;
							text = ExtractTextFromElement (sub_e, sbText, true, sLeftPadding, sExtract, sAttr, sFormatFlags, sFormatWidth, sRightPadding, sURL);
							nLines += StringUtils.countMatches (text, '\n');	// 左右填充字符串可能会包含换行符，所以输出行数要加上这些
							if (nLines >= opt_max_response_lines)
								break;
							continue;
						}

						Elements sub_elements = e.select (sSubSelector);
						if (sub_elements.isEmpty ())
						{
System.err.println ("	子选择器 " + (iSS+1) + " " + net_maclife_util_ANSIEscapeTool.CSI + "1m" + sSubSelector + net_maclife_util_ANSIEscapeTool.CSI + "m" + " 没有选中任何元素");
						}
						for (int iSSE=0; iSSE<sub_elements.size (); iSSE++)
						{
							sub_e = sub_elements.get (iSSE);
System.err.println ("	子选择器 " + (iSS+1) + " " + net_maclife_util_ANSIEscapeTool.CSI + "1m" + sSubSelector + net_maclife_util_ANSIEscapeTool.CSI + "m" + " 选出了第 " + (iSSE+1) + " 项: " + sub_e);
							//if (sub_e == null)	// 子选择器可能选择到不存在的，则忽略该条（比如 糗事百科的贴图，并不是每个糗事都有贴图）
							//	continue;
							text = ExtractTextFromElement (sub_e, sbText, true, sLeftPadding, sExtract, sAttr, sFormatFlags, sFormatWidth, sRightPadding, sURL);
							nLines += StringUtils.countMatches (text, '\n');	// 左右填充字符串可能会包含换行符，所以输出行数要加上这些
							if (nLines >= opt_max_response_lines)
								break ht_sub_loop;
						}
					}
					sbText.append ('\n');	// 与 IRC 中不同，微信中可以输出多行文字，所以，每

					nLines ++;
				}

				String sText = sbText.toString ();
				Matcher mat = PAT_IRC_ColorSequence.matcher (sText);
				sText = mat.replaceAll ("");	// 先剔除掉 IRC 颜色序列
				sText = StringUtils.replaceEach (sText, arrayReplaceControlCharacters_Sources, arrayReplaceControlCharacters_Replacements);	// 再替换（或剔除）其他特殊字符
System.out.println (sText);
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, sText);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
	}
	void DoFetchHyperText (
			String sReplyToAccount, String sReplyToName, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			String sAction, List<String> listOrderedCommandParams,
			Map<String, Object> mapHTTemplate
	)
	{
		DoFetchHyperText (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember,
			sAction, listOrderedCommandParams,
			(long)mapHTTemplate.get ("id"), (String)mapHTTemplate.get ("name"), (String)mapHTTemplate.get ("url"), (String)mapHTTemplate.get ("url_param_usage"), (boolean)mapHTTemplate.get ("use_gfw_proxy"), (boolean)mapHTTemplate.get ("ignore_https_certificate_validation"),
			(String)mapHTTemplate.get ("content_type"), (boolean)mapHTTemplate.get ("ignore_content_type"),
			(int)mapHTTemplate.get ("js_cut_start"), (int)mapHTTemplate.get ("js_cut_end"),
			(String)mapHTTemplate.get ("selector"),
			(List<String>)mapHTTemplate.get ("list sub selectors"),
			(List<String>)mapHTTemplate.get ("list left paddings"),
			(List<String>)mapHTTemplate.get ("list extracts"),
			(List<String>)mapHTTemplate.get ("list attributes"),
			(List<String>)mapHTTemplate.get ("list format flags"),
			(List<String>)mapHTTemplate.get ("list format width"),
			(List<String>)mapHTTemplate.get ("list right paddings"),
			(String)mapHTTemplate.get ("ua"),
			(String)mapHTTemplate.get ("request_method"),
			(String)mapHTTemplate.get ("referer"),
			(short)mapHTTemplate.get ("max")
		);
	}
	static final String[] arrayReplaceControlCharacters_Sources =
		{	// 与 IRCBot 不同，这里要剔除掉 IRC 转义字符。 '\u0003' 在微信中会以 'U+0003' 这样的形势的字符串出现，没有必要
			"\u0000",
			"\u0001",
			"\u0002",	// IRC 粗体、高亮
			"\u0003",	// IRC 颜色开始
			"\u0004",
			"\u0005",
			"\u0006",
			"\u0007",

			"\u0008",
			//"\u0009",	//\t
			//"\n",	// 0x0A 换行符
			"\u000B",
			"\u000C",
			"\r",	// 0x0D 回车符
			"\u000E",
			"\u000F",	// IRC 恢复正常

			"\u0010",
			"\u0011",
			"\u0012",
			"\u0013",
			"\u0014",
			"\u0015",
			"\u0016",	// IRC 反色
			"\u0017",

			"\u0018",
			"\u0019",
			"\u001A",
			//"\u001B",	// ANSI Escape ESC，留着把，不替换了
			"\u001C",
			"\u001D",
			"\u001E",
			"\u001F",	// IRC 下划线
		};
	static final String[] arrayReplaceControlCharacters_Replacements =
		{
			"",
			"",
			"",
			"",
			"",
			"",
			"",
			"",

			"",
			//" ",
			//" ",
			"",
			" ",
			"",
			"",
			"",

			"",
			"",
			"",
			//"",
			"",
			"",
			"",
			"",

			"",
			"",
			"",
			"",
			"",
			"",
			"",
			"",
		};
		/*
		new String[]
		{
			"␀", "␁", "␂", "␃", "␄", "␅", "␆", "␇",
			"␈", "␉", "␊", "␋", "␌", "␍", "␎", "␏",
			"␐", "␑", "␒", "␓", "␔", "␕", "␖", "␗",
			"␘", "␙", "␚", "␛", "␜", "␝", "␞", "␟",
		}
		*/
	static final Pattern PAT_IRC_ColorSequence = Pattern.compile ("\u0003\\d{1,2}");
	/**
	 * 从 Element 对象根据要提取的数据类型来取出文字。
	 * @param e Element 对象
	 * @param sb 字符串缓冲区
	 * @param isOutputScheme 是否输出 a 链接的 scheme (https:// 或者 http://)
	 * @param sLeftPadding 左填充
	 * @param sExtract 要提取的数据类型
	 * @param sAttr 当 sExtract 为 <code>attr</code> 时，要通过此参数指定属性名
	 * @param sFormatFlags 格式化字符串的标志。默认为空 - 无格式标志。
	 * @param sFormatWidth 格式化字符串的宽度。默认为空 - 不指定宽度。当 sFormatWidth 和 sFormatWidth 都是空时，不执行格式化字符串操作。
	 * @param sRightPadding 右填充
	 * @param strings
	 * @return
	 */
	public String ExtractTextFromElement (Element e, StringBuilder sb, boolean isOutputScheme, String sLeftPadding, String sExtract, String sAttr, String sFormatFlags, String sFormatWidth, String sRightPadding, String...strings)
	{
		String text = "";
		if (StringUtils.isEmpty (sExtract))	// 如果 Extract 是空的话，对 tagName 是 a 的做特殊处理
		{
			if (e.tagName ().equalsIgnoreCase ("a"))
			{
				String sHref = e.attr ("abs:href");
				// 微信机器人中，就不再像 IRC 机器人那样关心其他机器人了，直接输出 scheme。在微信中，如果不输出 scheme，在微信中就无法点击这些超链接
				//if (! isOutputScheme)
				//{
				//	if (StringUtils.startsWithIgnoreCase (sHref, "https://"))
				//		sHref = sHref.substring (8);
				//	else if (StringUtils.startsWithIgnoreCase (sHref, "http://"))
				//		sHref = sHref.substring (7);
				//}
				text = sHref + " " + e.text ();	// abs:href : 将相对地址转换为全地址
			}
			else
				text = e.text ();
		}
		else if (StringUtils.equalsAnyIgnoreCase (sExtract, "text"))
				text = e.text ();
		else if (StringUtils.equalsAnyIgnoreCase (sExtract, "html" ,"innerhtml" ,"inner"))
			text = e.html ();
		else if (StringUtils.equalsAnyIgnoreCase (sExtract, "outerhtml" ,"outer"))
			text = e.outerHtml ();
		else if (StringUtils.equalsAnyIgnoreCase (sExtract, "attr" ,"attribute"))
		{
			text = e.attr (sAttr);
			//if (e.tagName ().equalsIgnoreCase ("a") && (sAttr.equalsIgnoreCase ("href") || sAttr.equalsIgnoreCase ("abs:href")))
			//{
			//	if (! isOutputScheme)
			//	{
			//		if (StringUtils.startsWithIgnoreCase (text, "https://"))
			//			text = text.substring (8);
			//		else if (StringUtils.startsWithIgnoreCase (text, "http://"))
			//			text = text.substring (7);
			//	}
			//}
//System.err.println ("	sExtract " + sExtract + " 榨取属性 " + sAttr + " = " + text);
		}
		else if (StringUtils.equalsAnyIgnoreCase (sExtract, "TagName"))
			text = e.tagName ();
		else if (StringUtils.equalsAnyIgnoreCase (sExtract, "NodeName"))
			text = e.nodeName ();
		else if (StringUtils.equalsAnyIgnoreCase (sExtract, "OwnText"))
			text = e.ownText ();
		else if (StringUtils.equalsAnyIgnoreCase (sExtract, "data"))
			text = e.data ();
		else if (StringUtils.equalsAnyIgnoreCase (sExtract, "ClassName"))
			text = e.className ();
		else if (StringUtils.equalsAnyIgnoreCase (sExtract, "id"))
			text = e.id ();
		else if (StringUtils.equalsAnyIgnoreCase (sExtract, "val" ,"value"))
			text = e.val ();
		else
		{
			text = sExtract;	// 如果以上都不是，则把 sExtract 的字符串加进去，此举是为了能自己增加一些明文文字（通常是一些分隔符、label）
		}

		if (StringUtils.isNotEmpty (text))
		{	// 仅当要输出的字符串有内容时才会输出（并且也输出前填充、后填充）
			if (StringUtils.isNotEmpty (sLeftPadding))
				sb.append (sLeftPadding);

			if (StringUtils.isNoneEmpty (sFormatFlags) || StringUtils.isNoneEmpty (sFormatWidth))
			{
				String sFormatString = "%" + sFormatFlags + sFormatWidth + "s";
//System.out.print (sFormatString);
//System.out.print ("	");
//System.out.print (text);
				text = String.format (sFormatString, text);
//System.out.print ("	[");
//System.out.print (text);
//System.out.println ("]");
			}
			sb.append (text);

			if (StringUtils.isNotEmpty (sRightPadding))
				sb.append (sRightPadding);
		}
		return text;
	}

	boolean CheckHTTemplateExistence (String sHTTemplateName)
	{
		boolean isUsingHTTemplateNameAsShortcut = false;
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;

		try
		{
			net_maclife_wechat_http_BotApp.SetupDataSource ();
			conn = net_maclife_wechat_http_BotApp.botDS.getConnection ();
			stmt = conn.prepareStatement ("SELECT id FROM  " + TABLE_NAME_Tempalte + "  WHERE name=?");
				stmt.setString (1, sHTTemplateName);
			rs = stmt.executeQuery ();
			while (rs.next ())
			{
				isUsingHTTemplateNameAsShortcut = true;
				break;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
		finally
		{
			try { if (rs != null) rs.close(); } catch(Exception e) { }
			try { if (stmt != null) stmt.close(); } catch(Exception e) { }
			try { if (conn != null) conn.close(); } catch(Exception e) { }
		}

		return isUsingHTTemplateNameAsShortcut;
	}

	void SaveTemplate (
			String sReplyToAccount, String sReplyToName, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember,
			long nTemplateID, String sTemplateName, String sURL, String sURLParamsHelp, boolean usingGFWProxy, boolean isIgnoreHTTPSCertificateValidation,
			String sContentType, boolean isIgnoreContentType,
			int nJS_Cut_Start, int nJS_Cut_End,
			String sSelector,
			List<String> listSubSelectors,
			List<String> listLeftPaddings,
			List<String> listExtracts,
			List<String> listAttributes,
			List<String> listFormatFlags,
			List<String> listFormatWidth,
			List<String> listRightPaddings,

			String sHTTPUserAgent,
			String sHTTPRequestMethod,
			String sHTTPReferer,

			int opt_max_response_lines
		)
	{
		Connection conn = null;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		int nRowsAffected = 0;
		long nID = 0;

		try
		{
			net_maclife_wechat_http_BotApp.SetupDataSource ();
			conn = net_maclife_wechat_http_BotApp.botDS.getConnection ();
			StringBuilder sbSQL = new StringBuilder ();
			sbSQL.append ("INSERT  " + TABLE_NAME_Tempalte + "  (name, url, url_param_usage, use_gfw_proxy, ignore_https_certificate_validation, content_type, ignore_content_type, js_cut_start, js_cut_end, selector, sub_selector, padding_left, extract, attr, format_flags, format_width, padding_right, ua, request_method, referer, max, added_by, added_by_user, added_by_host, added_time)\n");
			sbSQL.append ("VALUES (?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,CURRENT_TIMESTAMP)");

			conn.setAutoCommit (false);
			stmt = conn.prepareStatement (sbSQL.toString (), new String[]{"id"});
			int iParam = 1;
			stmt.setString (iParam++, sTemplateName);
			stmt.setString (iParam++, sURL);
			stmt.setString (iParam++, StringUtils.stripToEmpty (sURLParamsHelp));
			stmt.setBoolean (iParam++, usingGFWProxy);
			stmt.setBoolean (iParam++, isIgnoreHTTPSCertificateValidation);
			stmt.setString (iParam++, StringUtils.equalsIgnoreCase (sContentType, "html") ? "" : sContentType);
			stmt.setBoolean (iParam++, isIgnoreContentType);

			stmt.setInt (iParam++, nJS_Cut_Start);
			stmt.setInt (iParam++, nJS_Cut_End);
			stmt.setString (iParam++, StringUtils.stripToEmpty (sSelector));
			stmt.setString (iParam++, StringUtils.stripToEmpty (listSubSelectors.get (0)));
			stmt.setString (iParam++, listLeftPaddings.get (0));
			stmt.setString (iParam++, StringUtils.stripToEmpty (listExtracts.get (0)));
			stmt.setString (iParam++, StringUtils.stripToEmpty (listAttributes.get (0)));
			stmt.setString (iParam++, StringUtils.stripToEmpty (listFormatFlags.get (0)));
			stmt.setString (iParam++, StringUtils.stripToEmpty (listFormatWidth.get (0)));
			stmt.setString (iParam++, listRightPaddings.get (0));

			stmt.setString (iParam++, StringUtils.stripToEmpty (sHTTPUserAgent));
			stmt.setString (iParam++, StringUtils.stripToEmpty (sHTTPRequestMethod));
			stmt.setString (iParam++, StringUtils.stripToEmpty (sHTTPReferer));

			stmt.setInt (iParam++, opt_max_response_lines);

			stmt.setString (iParam++, sReplyToName);	// nick
			stmt.setString (iParam++, "微信帐号");	// login
			stmt.setString (iParam++, "微信");	// hostname

			nRowsAffected = stmt.executeUpdate ();
			rs = stmt.getGeneratedKeys ();
			while (rs.next ())
			{
				nID = rs.getLong (1);
			}
			rs.close ();
			stmt.close ();

			stmt = conn.prepareStatement ("INSERT  " + TABLE_NAME_SubSelectors + "  (template_id, sub_selector, padding_left, extract, attr, format_flags, format_width, padding_right) VALUES (?,?,?,?,?, ?,?,?)", new String[]{"sub_selector_id"});
			for (int i=1; i<listSubSelectors.size (); i++)
			{
				iParam = 1;
				stmt.setLong (iParam++, nID);
				stmt.setString (iParam++, StringUtils.stripToEmpty (listSubSelectors.get (i)));
				stmt.setString (iParam++, listLeftPaddings.get (i));
				stmt.setString (iParam++, StringUtils.stripToEmpty (listExtracts.get (i)));
				stmt.setString (iParam++, StringUtils.stripToEmpty (listAttributes.get (i)));
				stmt.setString (iParam++, StringUtils.stripToEmpty (listFormatFlags.get (i)));
				stmt.setString (iParam++, StringUtils.stripToEmpty (listFormatWidth.get (i)));
				stmt.setString (iParam++, listRightPaddings.get (i));

				nRowsAffected += stmt.executeUpdate ();
			}

			conn.commit ();
			conn.close ();

			Matcher matcher = PATTERN_FindHtParameter.matcher (sURL);
			if (nRowsAffected > 0)
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "✓ 保存成功。#" + nID + (matcher.matches () ? "    由于你添加的 URL 是带参数的，所以在执行此模板时要加参数，比如: ht.run  '" + sTemplateName + "'  <c++>" : ""));
			else
				SendTextMessage (sReplyToAccount, sReplyToName, sReplyToAccount_RoomMember, sReplyToName_RoomMember, "保存失败。 这条信息应该不会出现……");

			if (matcher.matches ())	// 如果添加的 url 是带参数的模板，则不继续执行，直接返回
				return;
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
	}

	/**
	 * 从数据库读取 ht 模板
	 * @param sAction ht 子命令/子动作。目前，当为 <code>list</code> 时，会根据 sQueryParam_TemplateName 到 sQueryParam_HTTPReferer 的查询参数查询数据库（可能返回多条）； 否则，只根据 nQueryParam_TemplateID 或 sQueryParam_TemplateName 查询数据库（只返回一条，或 null），当 nQueryParam_TemplateID 不为 0 时，按 ID 查询，否则按 Name 查询
	 * @param nQueryParam_TemplateID 查询参数：模板ID
	 * @param sQueryParam_TemplateName 查询参数：模板名
	 * @param sQueryParam_URL 查询参数：网址
	 * @param sQueryParam_Selector 查询参数：选择器
	 * @param sQueryParam_HTTPUserAgent 查询参数：User Agent
	 * @param sQueryParam_HTTPRequestMethod 查询参数：请求方法 （GET POST）
	 * @param sQueryParam_HTTPReferer 查询参数：http 请求来源
	 * @param iStart MySQL LIMIT 子句的偏移量
	 * @param opt_max_response_lines MySQL LIMIT 子句的数据量
	 * @return
	 */
	List<Map<String, Object>> ReadTemplate (
			String sAction,
			long nQueryParam_TemplateID,
			String sQueryParam_TemplateName,
			String sQueryParam_URL,
			String sQueryParam_Selector,
			String sQueryParam_HTTPUserAgent,
			String sQueryParam_HTTPRequestMethod,
			String sQueryParam_HTTPReferer,
			long iStart,
			int opt_max_response_lines
	)
	{
		List<Map<String, Object>> listResults = null;

		Connection conn = null;
		PreparedStatement stmt = null;
		PreparedStatement stmt_GetSubSelectors = null;
		ResultSet rs = null;
		ResultSet rs_GetSubSelectors = null;

		StringBuilder sbSQL = null;
		String sSQL_GetSubSelectors = null;
		try
		{
			net_maclife_wechat_http_BotApp.SetupDataSource ();
			conn = net_maclife_wechat_http_BotApp.botDS.getConnection ();

			sbSQL = new StringBuilder ();
			sSQL_GetSubSelectors = "SELECT * FROM ht_templates_other_sub_selectors WHERE template_id=?";

			// 生成 SQL 查询语句
			sbSQL.append ("SELECT * FROM  " + TABLE_NAME_Tempalte + "  WHERE ");
			List<String> listSQLParams = new ArrayList<String> ();
			if (StringUtils.equalsIgnoreCase (sAction, "list"))
			{
				sbSQL.append ("1=1\n");
				if (StringUtils.isNotEmpty (sQueryParam_TemplateName))
				{
					sbSQL.append ("	AND name LIKE ?\n");
					listSQLParams.add ("%" + sQueryParam_TemplateName + "%");
				}
				if (StringUtils.isNotEmpty (sQueryParam_URL))
				{
					sbSQL.append ("	AND url LIKE ?\n");
					listSQLParams.add ("%" + sQueryParam_URL + "%");
				}
				if (StringUtils.isNotEmpty (sQueryParam_Selector))
				{
					sbSQL.append ("	AND selector LIKE ?\n");
					listSQLParams.add ("%" + sQueryParam_Selector + "%");
				}
				if (StringUtils.isNotEmpty (sQueryParam_HTTPUserAgent))
				{
					sbSQL.append ("	AND ua LIKE ?\n");
					listSQLParams.add ("%" + sQueryParam_HTTPUserAgent + "%");
				}
				if (StringUtils.isNotEmpty (sQueryParam_HTTPRequestMethod))
				{
					sbSQL.append ("	AND request_method = ?\n");
					listSQLParams.add (sQueryParam_HTTPRequestMethod);
				}
				if (StringUtils.isNotEmpty (sQueryParam_HTTPReferer))
				{
					sbSQL.append ("	AND referer LIKE ?\n");
					listSQLParams.add ("%" + sQueryParam_HTTPReferer + "%");
				}

				sbSQL.append ("LIMIT " + iStart + "," + opt_max_response_lines);
			}
			else
			{
				if (nQueryParam_TemplateID != 0)
					sbSQL.append ("id=?");
				else
					sbSQL.append ("name=?");
			}

			// 准备语句
			stmt_GetSubSelectors = conn.prepareStatement (sSQL_GetSubSelectors);
			stmt = conn.prepareStatement (sbSQL.toString ());
			int nParam = 1;
			// 植入 SQL 参数值
			if (StringUtils.equalsIgnoreCase (sAction, "list"))
			{
				for (int i=0; i<listSQLParams.size (); i++)
				{
					stmt.setString (i+1, listSQLParams.get (i));
				}
			}
			else
			{
				if (nQueryParam_TemplateID != 0)
					stmt.setLong (nParam ++, nQueryParam_TemplateID);
				else
					stmt.setString (nParam ++, sQueryParam_TemplateName);
			}

			listResults = new ArrayList<Map<String, Object>> ();
			// 执行，并取出结果值
			rs = stmt.executeQuery ();
			while (rs.next ())
			{
				Map<String, Object> mapHTTemplate = new HashMap<String, Object> ();
				listResults.add (mapHTTemplate);
				mapHTTemplate.put ("id", rs.getLong ("id"));
				mapHTTemplate.put ("name", rs.getString ("name"));
				mapHTTemplate.put ("url", rs.getString ("url"));
				mapHTTemplate.put ("use_gfw_proxy", rs.getBoolean ("use_gfw_proxy"));
				mapHTTemplate.put ("ignore_https_certificate_validation", rs.getBoolean ("ignore_https_certificate_validation"));
				mapHTTemplate.put ("content_type", rs.getString ("content_type"));
				if (StringUtils.equalsAnyIgnoreCase (rs.getString ("content_type"), "json", "js"))
					// 对于 json/js 类型的内容，强制忽略 content_type，因为 jsoup 支持的 content_type 不包含 json/js
					mapHTTemplate.put ("ignore_content_type", true);
				else
					mapHTTemplate.put ("ignore_content_type", rs.getBoolean ("ignore_content_type"));

				mapHTTemplate.put ("js_cut_start", rs.getInt ("js_cut_start"));
				mapHTTemplate.put ("js_cut_end", rs.getInt ("js_cut_end"));
				mapHTTemplate.put ("url_param_usage", rs.getString ("url_param_usage"));

				mapHTTemplate.put ("ua", rs.getString ("ua"));
				mapHTTemplate.put ("request_method", rs.getString ("request_method"));
				mapHTTemplate.put ("referer", rs.getString ("referer"));
				mapHTTemplate.put ("max", rs.getShort ("max"));

				//mapHTTemplate.put ("source_type", rs.getString ("source_type"));
				mapHTTemplate.put ("added_by", rs.getString ("added_by"));
				mapHTTemplate.put ("added_by_user", rs.getString ("added_by_user"));
				mapHTTemplate.put ("added_by_host", rs.getString ("added_by_host"));
				mapHTTemplate.put ("added_time", rs.getTimestamp ("added_time"));
				mapHTTemplate.put ("updated_by", rs.getString ("updated_by"));
				mapHTTemplate.put ("updated_by_user", rs.getString ("updated_by_user"));
				mapHTTemplate.put ("updated_by_host", rs.getString ("updated_by_host"));
				mapHTTemplate.put ("updated_time", rs.getTimestamp ("updated_time"));
				mapHTTemplate.put ("updated_times", rs.getInt ("updated_times"));

				mapHTTemplate.put ("selector", rs.getString ("selector"));
				List<String> listSubSelectors = new ArrayList<String> ();
				List<String> listLeftPaddings = new ArrayList<String> ();
				List<String> listExtracts = new ArrayList<String> ();
				List<String> listAttributes = new ArrayList<String> ();
				List<String> listFormatFlags = new ArrayList<String> ();
				List<String> listFormatWidth = new ArrayList<String> ();
				List<String> listRightPaddings = new ArrayList<String> ();
				mapHTTemplate.put ("list sub selectors", listSubSelectors);
				mapHTTemplate.put ("list left paddings", listLeftPaddings);
				mapHTTemplate.put ("list extracts", listExtracts);
				mapHTTemplate.put ("list attributes", listAttributes);
				mapHTTemplate.put ("list format flags", listFormatFlags);
				mapHTTemplate.put ("list format width", listFormatWidth);
				mapHTTemplate.put ("list right paddings", listRightPaddings);

				// 主表里的 sub_selector 信息
				listSubSelectors.add (new String(rs.getString ("sub_selector").getBytes ("iso-8859-1")));
				listLeftPaddings.add (rs.getString ("padding_left"));
				listExtracts.add (rs.getString ("extract"));
				listAttributes.add (rs.getString ("attr"));
				listFormatFlags.add (rs.getString ("format_flags"));
				listFormatWidth.add (rs.getString ("format_width"));
				listRightPaddings.add (rs.getString ("padding_right"));
				// 再读取从表里的额外 sub_selector 信息
				try
				{
					stmt_GetSubSelectors.setInt (1, rs.getInt ("id"));
					rs_GetSubSelectors = stmt_GetSubSelectors.executeQuery ();
					while (rs_GetSubSelectors.next ())
					{
						listSubSelectors.add (new String(rs_GetSubSelectors.getString("sub_selector").getBytes ("iso-8859-1")));
						listLeftPaddings.add (rs_GetSubSelectors.getString ("padding_left"));
						listExtracts.add (rs_GetSubSelectors.getString("extract"));
						listAttributes.add (rs_GetSubSelectors.getString("attr"));
						listFormatFlags.add (rs_GetSubSelectors.getString ("format_flags"));
						listFormatWidth.add (rs_GetSubSelectors.getString ("format_width"));
						listRightPaddings.add (rs_GetSubSelectors.getString ("padding_right"));
					}
				}
				catch (Exception e)
				{
					e.printStackTrace ();
				}
				finally
				{
					try { if (rs_GetSubSelectors != null) rs_GetSubSelectors.close(); } catch(Exception e) { }
					//try { if (stmt_GetSubSelectors != null) stmt_GetSubSelectors.close(); } catch(Exception e) { }
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace ();
		}
		finally
		{
			try { if (rs != null) rs.close(); } catch(Exception e) { }
			try { if (stmt != null) stmt.close(); } catch(Exception e) { }
			try { if (conn != null) conn.close(); } catch(Exception e) { }
			//try { if (rs_GetSubSelectors != null) rs_GetSubSelectors.close(); } catch(Exception e) { }
			try { if (stmt_GetSubSelectors != null) stmt_GetSubSelectors.close(); } catch(Exception e) { }
		}
		return listResults;
	}
	List<Map<String, Object>> ReadTemplate (
			long nQueryParam_TemplateID,
			String sQueryParam_TemplateName
	)
	{
		return ReadTemplate (null, nQueryParam_TemplateID, sQueryParam_TemplateName, null, null, null, null, null, 0, 0);
	}

	String RestoreCommandLineFromTemplate (Map<String, Object> mapHTTemplate)
	{
		StringBuilder sbHelp = new StringBuilder ();
		sbHelp.append (
			"模板序号: " + mapHTTemplate.get ("id") + "\n" +
			"模板名称: '" + mapHTTemplate.get ("name") + "\n" +
			"不用模板时的裸执行命令行:\n\n/ht" +
			net_maclife_wechat_http_BotApp.COMMAND_OPTION_SEPARATOR + mapHTTemplate.get ("max") +
			"'  '" + mapHTTemplate.get ("url") +
			"'  '" + mapHTTemplate.get ("selector") +
			"'"
			);

		String sContentType = (String)mapHTTemplate.get ("content_type");
		if (StringUtils.isNotEmpty (sContentType) && !StringUtils.equalsIgnoreCase (sContentType, "html"))
		{	// 对于 JSON/JS 的 ht 模板，要特殊处理
			int nJS_Cut_Start = (int)mapHTTemplate.get ("js_cut_start");
			int nJS_Cut_End = (int)mapHTTemplate.get ("js_cut_end");
			// 指定内容类型
			sbHelp.append ("  /ct '");
			sbHelp.append (sContentType);
			sbHelp.append ("'");

			// 掐头、去尾 参数
			if (nJS_Cut_Start > 0)
			{
				sbHelp.append ("  /jcs ");
				sbHelp.append (nJS_Cut_Start);
			}
			if (nJS_Cut_End > 0)
			{
				sbHelp.append ("  /jce ");
				sbHelp.append (nJS_Cut_End);
			}
		}

		List<String> listSubSelectors = (List<String>)mapHTTemplate.get ("list sub selectors");
		List<String> listLeftPaddings = (List<String>)mapHTTemplate.get ("list left paddings");
		List<String> listExtracts = (List<String>)mapHTTemplate.get ("list extracts");
		List<String> listAttributes = (List<String>)mapHTTemplate.get ("list attributes");
		List<String> listFormatFlags = (List<String>)mapHTTemplate.get ("list format flags");
		List<String> listFormatWidth = (List<String>)mapHTTemplate.get ("list format width");
		List<String> listRightPaddings = (List<String>)mapHTTemplate.get ("list right paddings");

		for (int iSS=0; iSS<listSubSelectors.size (); iSS++)
		{
			String sSubSelector = listSubSelectors.get (iSS);
			String sLeftPadding = listLeftPaddings.get (iSS);
			String sExtract = listExtracts.get (iSS);
			String sAttr = listAttributes.get (iSS);
			String sFormatFlags = listFormatFlags.get (iSS);
			String sFormatWidth = listFormatWidth.get (iSS);
			String sRightPadding = listRightPaddings.get (iSS);

			if (StringUtils.isNotEmpty (sSubSelector))
			{
				sbHelp.append ("  /ss '");
				sbHelp.append (sSubSelector);
				sbHelp.append ("'");
			}

			if (StringUtils.isNotEmpty (sLeftPadding))
			{
				sbHelp.append (" /lp '");
				sbHelp.append (sLeftPadding);
				sbHelp.append ("'");
			}

			if (StringUtils.isNotEmpty (sExtract))
			{
				sbHelp.append (" /e '");
				sbHelp.append (sExtract);
				sbHelp.append ("'");
			}

			if (StringUtils.isNotEmpty (sAttr))
			{
				sbHelp.append (" /a ");
				sbHelp.append (sAttr);
			}

			if (StringUtils.isNotEmpty (sFormatFlags))
			{
				sbHelp.append (" /ff ");
				sbHelp.append (sFormatFlags);
			}

			if (StringUtils.isNotEmpty (sFormatWidth))
			{
				sbHelp.append (" /fw ");
				sbHelp.append (sFormatWidth);
			}

			if (StringUtils.isNotEmpty (sRightPadding))
			{
				sbHelp.append (" /rp '");
				sbHelp.append (sRightPadding);
				sbHelp.append ("'");
			}
		}
		if (StringUtils.isNotEmpty ((String)mapHTTemplate.get ("ua")))
		{
			sbHelp.append (" /ua '");
			sbHelp.append ((String)mapHTTemplate.get ("ua"));
			sbHelp.append ("'");
		}
		if (StringUtils.isNotEmpty ((String)mapHTTemplate.get ("request_method")))
		{
			sbHelp.append (" /m ");
			sbHelp.append ((String)mapHTTemplate.get ("request_method"));
		}
		if (StringUtils.isNotEmpty ((String)mapHTTemplate.get ("referer")))
		{
			sbHelp.append (" /r ");
			sbHelp.append ((String)mapHTTemplate.get ("referer"));
		}

		if (StringUtils.isNotEmpty ((String)mapHTTemplate.get ("url_param_usage")))
		{
			sbHelp.append ("\n\n该模板的帮助信息:\n");
			sbHelp.append ((String)mapHTTemplate.get ("url_param_usage"));
			//sbHelp.append ("");
		}
		sbHelp.append ("\n\n添加人: ");
		sbHelp.append ((String)mapHTTemplate.get ("added_by"));
		sbHelp.append ("\n添加时间: ");
		sbHelp.append (((Timestamp)mapHTTemplate.get ("added_time")).toString ().substring (0, 19));
		if ((int)mapHTTemplate.get ("updated_times") > 0)
		{
			sbHelp.append ("\n最后更新人: ");
			sbHelp.append ((String)mapHTTemplate.get ("updated_by"));
			sbHelp.append ("\n最后更新时间: ");
			sbHelp.append (((Timestamp)mapHTTemplate.get ("updated_time")).toString ().substring (0, 19));
			sbHelp.append ("\n共更新了 ");
			sbHelp.append ((int)mapHTTemplate.get ("updated_times"));
			sbHelp.append (" 次");
		}
		return sbHelp.toString ();
	}

	void ListTemplates (String sReplyToAccount, String sReplyToName, String sReplyToAccount_RoomMember, String sReplyToName_RoomMember, List<Map<String, Object>> listHTTemplates)
	{

	}

	public static void main (String[] args) throws Exception
	{
		net_maclife_wechat_http_Bot_WebSoup bot = new net_maclife_wechat_http_Bot_WebSoup ();

		/*

		// 测试检查名称为模板名的模板是否存在
		System.out.println ("糗事百科 模板 是否存在？");
		System.out.println (bot.CheckHTTemplateExistence ("糗事百科"));

		List<Map<String, Object>> listResult = null;
		// 测试读取一个模板
		listResult = bot.ReadTemplate (1, null);
		System.out.println (listResult);

		// 测试读取多个模板
		listResult = bot.ReadTemplate ("list", 0, null, null, null, null, null, null, 1, 20);
		for (Map<String, Object> mapResult : listResult)
			System.out.println (mapResult);

		//*/

		bot.ProcessCommand_WebSoup (null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, "ht", "ht", false, null, "www.kernel.org  '#releases tr'");
		//bot.DoFetchHyperText ();
	}
}
