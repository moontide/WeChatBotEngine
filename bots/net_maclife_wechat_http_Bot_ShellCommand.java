import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;

import org.apache.commons.io.*;
import org.apache.commons.lang3.*;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;

/**
 * 执行操作系统命令，返回 stdout/stderr 的输出结果。
 *
 * 处于安全角度考虑，建议只允许自己使用该命令。
 * @author liuyan
 *
 */
public class net_maclife_wechat_http_Bot_ShellCommand extends net_maclife_wechat_http_Bot
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
		List<String> listCommands = net_maclife_wechat_http_BotApp.GetConfig ().getList (String.class, "bot.command-line.commands");
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

			String[] arrayCommandOptions = sCommandInputed.split ("\\.+", 2);
			sCommandInputed = arrayCommandOptions[0];
			String sCommandOptionsInputed = null;
			if (arrayCommandOptions.length >= 2)
				sCommandOptionsInputed = arrayCommandOptions[1];

			for (int i=0; i<listCommands.size (); i++)
			{
				String sCommand = listCommands.get (i);
				if (StringUtils.equalsIgnoreCase (sCommandInputed, sCommand))
				{
					boolean bOnlyMe = net_maclife_wechat_http_BotApp.ParseBoolean (net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.command-line.permission.only-me"), true);
					if (bOnlyMe && ! engine.IsMe (sFromAccount_Person))
					{
						SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, GetName() + " 配置为：只允许主人使用。");
						return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					}

					// 只有命令时，打印帮助信息
					if (StringUtils.isEmpty (sCommandParametersInputed))
					{
						SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, GetName() + " 需要指定要执行的命令。\n\n用法:\n" + sCommand + "[.行数][.stderr]  <系统命令> [及其参数]...\n\n.行数：用来指定最多返回该命令的输出内容的行数。如 .3 最多返回前 3 行。\n.stderr: 也返回 stderr 的内容");
						return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					}

					// 解析命令“选项”： .stderr .行数
					boolean bRedirectStdErr = false;
					int nMaxLinesReturns = 5;
					if (StringUtils.isNotEmpty (sCommandOptionsInputed))
					{
						arrayCommandOptions = sCommandOptionsInputed.split ("\\.+");
						for (String sCommandOption : arrayCommandOptions)
						{
							if (StringUtils.equalsIgnoreCase (sCommandOption, "stderr"))
							{
								bRedirectStdErr = true;
							}
							else
							{
								try
								{
									nMaxLinesReturns = Integer.parseInt (sCommandOption);
									if (nMaxLinesReturns < 0)
										nMaxLinesReturns = 0;	// 0: 不限制输出行数
								}
								catch (NumberFormatException e)
								{
									e.printStackTrace ();
								}
							}
						}
					}

					//
					String sShell = net_maclife_wechat_http_BotApp.GetConfig ().getString ("bot.command-line.shell");
					if (StringUtils.isEmpty (sShell))
					{
						SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, GetName() + " 尚未配置 Shell，无法执行。");
						return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					}
					try
					{
						String sCommandOutput = ProcessShellCommand (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, sShell, sCommandParametersInputed, bRedirectStdErr, nMaxLinesReturns);
						if (StringUtils.isNotEmpty (sCommandOutput))
						{
							SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, sCommandOutput);
						}
					}
					catch (Throwable e)
					{
						e.printStackTrace ();
						SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, e.toString ());
					}
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

	/**
	 * 执行 Shell 命令。
	 * 这里的实现方法与我的 IRC 机器人 TideBot 中的实现方式不同：这里不解析命令行，直接交给一个 Shell 处理
	 * @param sShell
	 * @param sCommandLineString
	 * @param bRedirectStdErr 是否将 stderr 重定向到 stdout
	 * @param nMaxLinesReturns 最多返回多少行
	 */
	String ProcessShellCommand (String sFromAccount, String sFromName, String sFromAccount_RoomMember, String sFromName_RoomMember, String sShell, String sCommandLineString, boolean bRedirectStdErr, int nMaxLinesReturns) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, InterruptedException
	{
		//List<String> listCommandLineParameters = new ArrayList<>
		ProcessBuilder pb = new ProcessBuilder (sShell, "-c", sCommandLineString);
		// pb.inheritIO ();	// 这个不能要，因为不是打印在后台，而是要取出结果，并返回
		if (bRedirectStdErr)
			pb.redirectErrorStream ();

		Process p = null;
		int nLines = 0;
		int nTotalLines = 0;
		String sLine = null;
		StringBuilder sb = new StringBuilder ();
		//try
		{
			p = pb.start ();
			InputStream in = p.getInputStream ();
			InputStream err = p.getErrorStream ();
			//IOUtils.toString (in);
			BufferedReader br = null;
			br = new BufferedReader (new InputStreamReader (in));
			while ((sLine = br.readLine ()) != null)
			{
				nTotalLines ++;
				if (nMaxLinesReturns != 0  && nLines >= nMaxLinesReturns)
					continue;
				//if (nMaxLinesReturns == 0 || (nMaxLinesReturns != 0  && nLines < nMaxLinesReturns))
				{
					sb.append (sLine);
					sb.append ('\n');
					nLines ++;
				}
			}
			if (! bRedirectStdErr)
			{
				br = new BufferedReader (new InputStreamReader (err));
				while ((sLine = br.readLine ()) != null)
				{
					nTotalLines ++;
					if (nMaxLinesReturns != 0  && nLines >= nMaxLinesReturns)
						continue;
					//if (nMaxLinesReturns == 0 || (nMaxLinesReturns != 0  && nLines < nMaxLinesReturns))
					{
						sb.append (sLine);
						sb.append ('\n');
						nLines ++;
					}
				}
			}

			if (nMaxLinesReturns != 0 && nLines < nTotalLines)
			{
				sb.append ('\n');
				sb.append ("(总共 ");
				sb.append (nTotalLines);
				sb.append (" 行，只显示前 ");
				sb.append (nLines);
				sb.append (" 行)");
			}

			int rc = p.waitFor ();
			assert (rc == 0);
			if (rc != 0)
			{
net_maclife_wechat_http_BotApp.logger.severe (sCommandLineString + " 执行失败");
			}
		}
		//catch (IOException e)
		{
		//	SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, GetName() + " 尚未配置 Shell，无法执行。");
		//	e.printStackTrace();
		}

		return sb.toString ();
	}
}
