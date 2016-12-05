import java.io.*;
import java.net.*;
import java.util.*;

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
	To:	// 消息接收人，接收人可以是单个对象、也可以多个（数组）。因为微信不能用一个唯一 ID (加密且每次登录会改变) 来指定接收人，
		// 所以需要通过指定 Alias、RemarkName、NickName、DisplayName 几个名称中的一个或多个的方式来定位该接收人。
		[
			{	// 这几个字段，至少需要指定其中的一个。
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
	//public static final List<String> listListenAddresses = net_maclife_wechat_http_BotApp.config.getList (String.class, "bot.relay.listen.address");
	//public static final List<Integer> listListenPorts = net_maclife_wechat_http_BotApp.config.getList (int.class, "bot.relay.listen.port");
	public static final String sListenddress = net_maclife_wechat_http_BotApp.config.getString ("bot.relay.listen.address");
	public static final int nListenPort = net_maclife_wechat_http_BotApp.config.getInt ("bot.relay.listen.port");

	ServerSocket ss = null;

	@Override
	public void Start ()
	{
		try
		{
			ss = new ServerSocket ();
			ss.bind (new InetSocketAddress (InetAddress.getByName (sListenddress), nListenPort));
		}
		catch (IOException e)
		{
net_maclife_wechat_http_BotApp.logger.severe ("bot 启动失败: " + e);
			e.printStackTrace();
		}

		botTask = net_maclife_wechat_http_BotApp.executor.submit (this);
	}

	@Override
	public void Stop ()
	{
		try
		{
			if (botTask != null && !botTask.isCancelled ())
			{
				botTask.cancel (true);
			}
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
				//IOUtils.toString (is, net_maclife_wechat_http_BotApp.utf8);
				JsonNode jsonNode = net_maclife_wechat_http_BotApp.jacksonObjectMapper_Loose.readTree (is);
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
				sMessage = sMessage + "\n\t-- " + sFrom;
			}

			JsonNode jsonTOs = jsonMessageToRelay.get ("To");
			List<String> listTOs = new ArrayList<String> ();
			if (jsonTOs.isArray ())
			{
				for (int i=0; i<jsonTOs.size (); i++)
				{
					JsonNode jsonTo = jsonTOs.get (i);
					String sAliasAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonTo, "Alias");
					String sRemarkName = net_maclife_wechat_http_BotApp.GetJSONText (jsonTo, "RemarkName");
					String sNickName = net_maclife_wechat_http_BotApp.GetJSONText (jsonTo, "NickName");
					//String sDisplayName = net_maclife_wechat_http_BotApp.GetJSONText (jsonTo, "DisplayName");

					JsonNode jsonContact = engine.SearchForSingleContact (null, sAliasAccount, sRemarkName, sNickName);
					if (jsonContact != null)
						listTOs.add (net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "UserName"));
				}
			}
			else
			{
				JsonNode jsonTo = jsonTOs;
				String sAliasAccount = net_maclife_wechat_http_BotApp.GetJSONText (jsonTo, "Alias");
				String sRemarkName = net_maclife_wechat_http_BotApp.GetJSONText (jsonTo, "RemarkName");
				String sNickName = net_maclife_wechat_http_BotApp.GetJSONText (jsonTo, "NickName");
				//String sDisplayName = net_maclife_wechat_http_BotApp.GetJSONText (jsonTo, "DisplayName");

				JsonNode jsonContact = engine.SearchForSingleContact (null, sAliasAccount, sRemarkName, sNickName);
				if (jsonContact != null)
					listTOs.add (net_maclife_wechat_http_BotApp.GetJSONText (jsonContact, "UserName"));
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

}
