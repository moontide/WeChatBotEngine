import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;

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
public class net_maclife_wechat_http_Bot_ShellCommand extends net_maclife_wechat_http_Bot implements Runnable
{
	public static final int DEFAULT_WATCH_DOG_TIMEOUT = 5;
	public static final int DEFAULT_MAX_LINES_RETURNS = 5;

	Map<Process, Long> mapProcessesRunning = new HashMap<Process, Long> ();

	@Override
	public void Start ()
	{
		if (botTask == null)
		{
			botTask = net_maclife_wechat_http_BotApp.executor.submit (this);
		}
	}

	@Override
	public void Stop ()
	{
		StopAllProcesses ();
		super.Stop ();
	}

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
						SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, GetName() + " 需要指定要执行的命令。\n\n用法:\n" + sCommand + "[.行数][.stderr][.t=超时秒数]  <系统命令> [及其参数]...\n\n.行数：用来指定最多返回该命令的输出内容的行数。如 .3 最多返回前 3 行。\n.stderr: stderr 与 stdout 合并。\n.t=超时秒数: 设置进程的超时时长");
						return net_maclife_wechat_http_BotEngine.BOT_CHAIN_PROCESS_MODE_MASK__CONTINUE;
					}

					// 解析命令“选项”： .stderr .行数 .t=超时时长（秒） .rt
					boolean bRedirectStdErr = false;
					boolean bResponseLineByLine = false;
					int nMaxLinesReturns = DEFAULT_MAX_LINES_RETURNS;
					int nTimeout = net_maclife_wechat_http_BotApp.GetConfig ().getInt ("bot.command-line.process.timeout.default", DEFAULT_WATCH_DOG_TIMEOUT);
					if (StringUtils.isNotEmpty (sCommandOptionsInputed))
					{
						arrayCommandOptions = sCommandOptionsInputed.split ("\\.+");
						for (String sCommandOption : arrayCommandOptions)
						{
							if (StringUtils.equalsIgnoreCase (sCommandOption, "stderr"))
							{
								bRedirectStdErr = true;
							}
							else if (StringUtils.startsWithIgnoreCase (sCommandOption, "t="))
							{	// 如果用 t= 指定超时时长，则用指定的数值代替默认值
								sCommandOption = StringUtils.substring (sCommandOption, 2);
								try
								{
									nTimeout = Integer.parseInt (sCommandOption);
									if (nTimeout < 1)
										nTimeout = 1;	// 至少 1 秒…
								}
								catch (NumberFormatException e)
								{
									e.printStackTrace ();
								}
							}
							else if (
								StringUtils.equalsIgnoreCase (sCommandOption, "ll")	// Line by Line
								|| StringUtils.equalsIgnoreCase (sCommandOption, "lbl")	// Line By Line
								|| StringUtils.equalsIgnoreCase (sCommandOption, "rt")	// RealTime
								|| StringUtils.equalsIgnoreCase (sCommandOption, "rtr")	// RealTime Response
								|| StringUtils.equalsIgnoreCase (sCommandOption, "zss")	//　z准s实s时
								|| StringUtils.equalsIgnoreCase (sCommandOption, "ss")	//　s实s时
								|| StringUtils.equalsIgnoreCase (sCommandOption, "准实时")	//
								|| StringUtils.equalsIgnoreCase (sCommandOption, "实时")	//
								)
							{
								bResponseLineByLine = true;
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
						String sCommandOutput = ProcessShellCommand (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, sShell, sCommandParametersInputed, bRedirectStdErr, bResponseLineByLine, nMaxLinesReturns, nTimeout);
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
	 * @param bResponseLineByLine 是否每行都单独响应。这个选项，相当于“是否准实时输出”，对于执行类似 ping 命令这样不断定时输出行的命令，好处：可以使用这个选项让人直观的感到是否正在输出。 弊端：因为每行都产生一个微信消息，会影响到群里其他人手机 -- 不断提醒。
	 * @param nMaxLinesReturns 最多返回多少行
	 * @param nTimeout 进程运行超时时长。如果超过这个时长进程还未结束，则杀死该进程。
	 */
	String ProcessShellCommand (String sFromAccount, String sFromName, String sFromAccount_RoomMember, String sFromName_RoomMember, String sShell, String sCommandLineString, boolean bRedirectStdErr, boolean bResponseLineByLine, int nMaxLinesReturns, int nTimeout) throws KeyManagementException, UnrecoverableKeyException, JsonProcessingException, NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException, InterruptedException
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
		StringBuilder sb = bResponseLineByLine ? null : new StringBuilder ();
		//try
		{
			p = pb.start ();
			mapProcessesRunning.put (p, System.currentTimeMillis () + nTimeout * 1000);
			InputStream in = p.getInputStream ();
			InputStream err = p.getErrorStream ();
			//IOUtils.toString (in);
			BufferedReader br = null;
			br = new BufferedReader (new InputStreamReader (in));
			while ((sLine = br.readLine ()) != null)
			{
//System.out.println (sLine);
				nTotalLines ++;
				if (nMaxLinesReturns != 0  && nLines >= nMaxLinesReturns)
					continue;
				//if (nMaxLinesReturns == 0 || (nMaxLinesReturns != 0  && nLines < nMaxLinesReturns))
				if (bResponseLineByLine)
				{
					SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, sLine);
				}
				else
				{
					sb.append (sLine);
					sb.append ('\n');
				}
				nLines ++;
			}
			br.close ();
			if (! bRedirectStdErr)
			{
				br = new BufferedReader (new InputStreamReader (err));
				while ((sLine = br.readLine ()) != null)
				{
//System.err.println (sLine);
					nTotalLines ++;
					if (nMaxLinesReturns != 0  && nLines >= nMaxLinesReturns)
						continue;
					//if (nMaxLinesReturns == 0 || (nMaxLinesReturns != 0  && nLines < nMaxLinesReturns))
					if (bResponseLineByLine)
					{
						SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, sLine);
					}
					else
					{
						sb.append (sLine);
						sb.append ('\n');
					}
					nLines ++;
				}
				br.close ();
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
			mapProcessesRunning.remove (p);
			assert (rc == 0);
			if (rc != 0)
			{
net_maclife_wechat_http_BotApp.logger.severe ("'" + sCommandLineString + "' 执行失败（返回代码不是 0，而是 " + rc + "）。");
			}
		}
		//catch (IOException e)
		{
		//	SendTextMessage (sFromAccount, sFromName, sFromAccount_RoomMember, sFromName_RoomMember, GetName() + " 尚未配置 Shell，无法执行。");
		//	e.printStackTrace();
		}

		return sb==null ? null : sb.toString ();
	}

	public void StopAllProcesses ()
	{
		for (Process p : mapProcessesRunning.keySet ())
		{
			p.destroy ();
		}
		mapProcessesRunning.clear ();
	}

	/**
	 * 监视线程：杀死超时的进程。类似 apache commaons exec 里的 WatchDog。
	 * 目前，监视的频率为：每隔 1 秒检查一次。
	 */
	@Override
	public void run ()
	{
		while (! Thread.currentThread ().interrupted ())
		{
			try
			{
				TimeUnit.SECONDS.sleep (1);
				for (Process p : mapProcessesRunning.keySet ())
				{
					long lExpectedStopTime = mapProcessesRunning.get (p);
					if (System.currentTimeMillis () > lExpectedStopTime)
					{
						p.destroy ();
						mapProcessesRunning.remove (p);
					}
				}
			}
			catch (InterruptedException e)
			{
				e.printStackTrace ();
				break;
			}
			catch (Throwable e)
			{
				e.printStackTrace ();
			}
		}
	}

	public static void main (String[] args)
	{
		net_maclife_wechat_http_Bot_ShellCommand cmd = new net_maclife_wechat_http_Bot_ShellCommand ();
		cmd.Start ();

		final int 运行秒数 = 7;
		final int 延时退出秒数 = 7;	// 得比运行秒数数值大
		final String sShell = "bash";
		String 命令行 = null;
		命令行 = "ping 127.0.0.1";
		//命令行 = "htop";
		//命令行 = "nmap -T4 127.0.0.1";
		boolean b准实时输出 = false;
		try
		{
System.err.println ("运行 " + 运行秒数 + " 秒钟的 " + 命令行);
			String sResult = cmd.ProcessShellCommand (null, null, null, null, sShell, 命令行, true, b准实时输出, 0, 运行秒数);
System.out.println (命令行 + " 输出:\n" + sResult);
//System.err.println ("等待 " + 延时退出秒数 + " 秒钟退出");
//			TimeUnit.SECONDS.sleep (延时退出秒数);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		cmd.Stop ();
		net_maclife_wechat_http_BotApp.executor.shutdownNow ();
	}
}
