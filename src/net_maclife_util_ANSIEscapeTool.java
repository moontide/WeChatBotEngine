import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.logging.*;
import java.util.regex.*;

import org.apache.commons.lang3.*;

public class net_maclife_util_ANSIEscapeTool
{
	// http://en.wikipedia.org/wiki/ANSI_escape_code#CSI_codes
	public static final String ESC = "\u001B";
	public static final String CSI = ESC + "[";
	public static final String ESC_REGEXP = "\\e";	// 用于使用规则表达式时
	public static final String CSI_REGEXP = ESC_REGEXP + "\\[";	// 用于使用规则表达式时
/*
	public static final String CSI_CUU_REGEXP_Replace = CSI_REGEXP + "(\\d+)?A";	// CSI n 'A' 	CUU - Cursor Up
	public static final String CSI_CUU_REGEXP = ".*" + CSI_CUU_REGEXP_Replace + ".*";
	public static final String CSI_CUD_REGEXP_Replace = CSI_REGEXP + "(\\d+)?B";	// CSI n 'B' 	CUD - Cursor Down
	public static final String CSI_CUD_REGEXP = ".*" + CSI_CUD_REGEXP_Replace + ".*";
	public static final String CSI_CUF_REGEXP_Replace = CSI_REGEXP + "(\\d+)?C";	// CSI n 'C' 	CUF - Cursor Forward
	public static final String CSI_CUF_REGEXP = ".*" + CSI_CUF_REGEXP_Replace + ".*";
	public static final String CSI_CUB_REGEXP_Replace = CSI_REGEXP + "(\\d+)?D";	// CSI n 'D' 	CUB - Cursor Back
	public static final String CSI_CUB_REGEXP = ".*" + CSI_CUB_REGEXP_Replace + ".*";
	public static final String CSI_CNL_REGEXP_Replace = CSI_REGEXP + "(\\d+)?E";	// CSI n 'E' 	CNL – Cursor Next Line
	public static final String CSI_CNL_REGEXP = ".*" + CSI_CNL_REGEXP_Replace + ".*";
	public static final String CSI_CPL_REGEXP_Replace = CSI_REGEXP + "(\\d+)?F";	// CSI n 'F' 	CPL – Cursor Previous Line
	public static final String CSI_CPL_REGEXP = ".*" + CSI_CPL_REGEXP_Replace + ".*";
	public static final String CSI_CHA_REGEXP_Replace = CSI_REGEXP + "(\\d+)?G";	// CSI n 'G' 	CHA – Cursor Horizontal Absolute
	public static final String CSI_CHA_REGEXP = ".*" + CSI_CHA_REGEXP_Replace + ".*";
*/
	//public static final String CSI_CUP_REGEXP_Replace = CSI_REGEXP + "([\\d;]+)?H";	// CSI n;m 'H' 	CUP – Cursor Position
	//public static final String CSI_CUP_REGEXP = ".*" + CSI_CUP_REGEXP_Replace + ".*";

	//public static final String CSI_CHA_REGEXP_Replace = CSI_REGEXP + "(\\d+)?I";	// CSI n 'I' 	CHT – Cursor Forward Tabulation P s tab stops (default = 1).
/*
	public static final String CSI_ED_REGEXP_Replace = CSI_REGEXP + "([012])?J";	// CSI n 'J' 	ED – Erase Display
	public static final String CSI_ED_REGEXP = ".*" + CSI_ED_REGEXP_Replace + ".*";
	public static final String CSI_EL_REGEXP_Replace = CSI_REGEXP + "([012])?K";	// CSI n 'K'	EL - Erase in Line
	public static final String CSI_EL_REGEXP = ".*" + CSI_EL_REGEXP_Replace + ".*";
	public static final String CSI_SU_REGEXP_Replace = CSI_REGEXP + "([\\d;]+)?S";	// CSI n 'S' 	SU – Scroll Up
	public static final String CSI_SU_REGEXP = ".*" + CSI_SU_REGEXP_Replace + ".*";
	public static final String CSI_SD_REGEXP_Replace = CSI_REGEXP + "([\\d;]+)?T";	// CSI n 'T' 	SD – Scroll Down
	public static final String CSI_SD_REGEXP = ".*" + CSI_SD_REGEXP_Replace + ".*";

	public static final String CSI_HVP_REGEXP_Replace = CSI_REGEXP + "([\\d;]+)?f";	// CSI n 'f'	HVP – Horizontal and Vertical Position, 与 CUP 功能相同
	public static final String CSI_HVP_REGEXP = ".*" + CSI_HVP_REGEXP_Replace + ".*";
*/
	//public static final String CSI_VPA_REGEXP_Replace = CSI_REGEXP + "(\\d+)?d";	// CSI n 'd'	VPA – Line/Vertical Position Absolute [row] (default = [1,column]) (VPA).
	//public static final String CSI_VPR_REGEXP_Replace = CSI_REGEXP + "(\\d+)?e";	// CSI n 'e'	VPR - Line Position Relative [rows] (default = [row+1,column]) (VPR).

	public static final String CSI_SGR_REGEXP_Replace = CSI_REGEXP + "([\\d;]+)?m";	// CSI n 'm'	SGR - Select Graphic Rendition
/*
	public static final String CSI_DSR_REGEXP_Replace = CSI_REGEXP + "6n";	// CSI '6n'	DSR – Device Status Report
	public static final String CSI_DSR_REGEXP = ".*" + CSI_DSR_REGEXP_Replace + ".*";

	public static final String CSI_SCP_REGEXP_Replace = CSI_REGEXP + "([\\d;]+)?s";	// CSI 's' 	SCP – Save Cursor Position
	public static final String CSI_SCP_REGEXP = ".*" + CSI_SCP_REGEXP_Replace + ".*";
	public static final String CSI_RCP_REGEXP_Replace = CSI_REGEXP + "([\\d;]+)?u";	// CSI 'u'	RCP – Restore Cursor Position
	public static final String CSI_RCP_REGEXP = ".*" + CSI_RCP_REGEXP_Replace + ".*";
	public static final String CSI_DECTCEM_HideCursor_REGEXP_Replace = CSI_REGEXP + "\\?([\\d;]+)?l";	// CSI '?25l'	DECTCEM - Hides the cursor. (Note: the trailing character is lowercase L.)
	public static final String CSI_DECTCEM_HideCursor_REGEXP = ".*" + CSI_DECTCEM_HideCursor_REGEXP_Replace + ".*";
	public static final String CSI_DECTCEM_ShowCursor_REGEXP_Replace = CSI_REGEXP + "\\?([\\d;]+)?h";	// CSI '?25h' 	DECTCEM - Shows the cursor.
	public static final String CSI_DECTCEM_ShowCursor_REGEXP = ".*" + CSI_DECTCEM_ShowCursor_REGEXP_Replace + ".*";
*/

	public static final String CSI_CursorMoving_REGEXP_Replace = CSI_REGEXP + "([\\d;]+)?(A|B|C|D|E|F|G|H|d)";

	public static final String CSI_Others_REGEXP_Replace = CSI_REGEXP + "(\\?)?([\\d;]+)?(J|K|S|T|X|f|h|l|n|r|s|u)";	// 转换为 IRC Escape 序列时只需要删除的 ANSI Escape 序列
	//public static final String CSI_CursorControlAndOthers_REGEXP = ".*" + CSI_CursorControlAndOthers_REGEXP_Replace + ".*";

	// htop 输出的一些未知的转义序列
/*
	public static final String CSI_UNKNOWN_？h_REGEXP_Replace = CSI_REGEXP + "\\?(\\d+)h";
	public static final String CSI_UNKNOWN_？h_REGEXP = ".*" + CSI_UNKNOWN_？h_REGEXP_Replace + ".*";

	public static final String CSI_UNKNOWN_d_REGEXP_Replace = CSI_REGEXP + "(\\d+)?d";
	public static final String CSI_UNKNOWN_d_REGEXP = ".*" + CSI_UNKNOWN_d_REGEXP_Replace + ".*";
	public static final String CSI_UNKNOWN_l_REGEXP_Replace = CSI_REGEXP + "(\\d+)?l";
	public static final String CSI_UNKNOWN_l_REGEXP = ".*" + CSI_UNKNOWN_l_REGEXP_Replace + ".*";
	public static final String CSI_UNKNOWN_r_REGEXP_Replace = CSI_REGEXP + "([\\d+;])?r";
	public static final String CSI_UNKNOWN_r_REGEXP = ".*" + CSI_UNKNOWN_r_REGEXP_Replace + ".*";
	public static final String CSI_UNKNOWN_X_REGEXP_Replace = CSI_REGEXP + "(\\d+)?d";
	public static final String CSI_UNKNOWN_X_REGEXP = ".*" + CSI_UNKNOWN_X_REGEXP_Replace + ".*";

	public static final String CSI_UNKNOWN_REGEXP_Replace = CSI_REGEXP + "(\\?)?([\\d+;])?(d|h|l|r|X)";
	public static final String CSI_UNKNOWN_REGEXP = ".*" + CSI_UNKNOWN_REGEXP_Replace + ".*";
*/
	// http://sof2go.net/man/wtn/wtncevt/en/Anx_A_Page.htm
	//public static final String VT220_SCS_B_REGEXP_Replace = "\u001B[\\(,\\)\\-\\*\\.+/]B";
	//public static final String VT220_SCS_DECSpecialGraphics_REGEXP_Replace = "\u001B[\\(,\\)\\-\\*\\.+/]\\<";
	//public static final String VT220_SCS_DECSupplemental_REGEXP_Replace = "\u001B[\\(,\\)\\-\\*\\.+/]0";

	public static final String VT220_SCS_REGEXP_Replace = ESC_REGEXP + "[\\(,\\)\\-\\*\\.+/][B\\<0]";
	//public static final String VT220_SCS_REGEXP = ".*" + VT220_SCS_REGEXP_Replace + ".*";

	public static final String XTERM_VT100_TwoCharEscapeSequences_REGEXP_Replace = ESC_REGEXP + "[=\\>]";
	//public static final String XTERM_VT100_TwoCharEscapeSequences_REGEXP = ".*" + XTERM_VT100_TwoCharEscapeSequences_REGEXP_Replace + ".*";

	//Pattern CSI_SGR_PATTERN = Pattern.compile (CSI_SGR_REGEXP);
	public static Pattern CSI_SGR_PATTERN_Replace = Pattern.compile (CSI_SGR_REGEXP_Replace);
	//Pattern CSI_EL_PATTERN = Pattern.compile (CSI_EL_REGEXP);
	//Pattern CSI_EL_PATTERN_Replace = Pattern.compile (CSI_EL_REGEXP_Replace);

	//Pattern CSI_CUP_PATTERN_Replace = Pattern.compile (CSI_CUP_REGEXP_Replace);
	//Pattern CSI_VPA_PATTERN_Replace = Pattern.compile (CSI_VPA_REGEXP_Replace);
	public static Pattern CSI_CursorMoving_PATTERN_Replace = Pattern.compile (CSI_CursorMoving_REGEXP_Replace);

	public static final String CSI_SGR_CursorMoving_EraseText_REGEXP_Replace = CSI_REGEXP + "([\\d;]+)?(A|B|C|D|E|F|G|H|f|I|J|K|d|m|s|u)";
	public static Pattern CSI_SGR_CursorMoving_EraseText_PATTERN_Replace = Pattern.compile (CSI_SGR_CursorMoving_EraseText_REGEXP_Replace);
	public static final String CSI_Others2_REGEXP_Replace = CSI_REGEXP + "(\\?)?([\\d;]+)?(S|T|X|h|l|n|r)";	// 转换为 IRC Escape 序列时只需要删除的 ANSI Escape 序列

	//Pattern CSI_CursorControlAndOthers_PATTERN_Replace = Pattern.compile (CSI_CursorControlAndOthers_REGEXP);

	//Pattern VT220_SCS_PATTERN_Replace = Pattern.compile (VT220_SCS_REGEXP_Replace);

	public static char[] ASCII_ControlCharacters =
	{
		'␀', '␁', '␂', '␃', '␄', '␅', '␆', '␇',
		'␈', '␉', '␊', '␋', '␌', '␍', '␎', '␏',
		'␐', '␑', '␒', '␓', '␔', '␕', '␖', '␗',
		'␘', '␙', '␚', '␛', '␜', '␝', '␞', '␟',
	};

	/**
	 * 16 色 ANSI 颜色数组，便于用索引号访问颜色
	 */
	public static String[] ANSI_16_COLORS =
	{
		"30",   "31",   "32",   "33",   "34",   "35",   "36",   "37",
		"30;1", "31;1", "32;1", "33;1", "34;1", "35;1", "36;1", "37;1",
	};
	/**
	 * 8 色 ANSI 背景颜色数组，便于用索引号访问颜色
	 */
	public static String[] ANSI_8_BACKGROUND_COLORS =
	{
		"40",   "41",   "42",   "43",   "44",   "45",   "46",   "47",
	};
	/**
	 * ANSI 彩虹色（12 个颜色，按“<font color='red'>红</font><font color='orange'>橙</font><font color='yellow'>黄</font><font color='green'>绿</font><font color='blue'>蓝</font><font color='cyan'>青</font><font color='purple'>紫</font>”顺序）
	 * 此颜色不包含 $CSI (ESC + "[")，这是为了便于前景、背景通用
	 */
	public static final String[] ANSI_Rainbow_COLORS =
	{
		"31",   "33",   "31;1", "33;1",
		"32;1", "32",   "34",   "34;1",
		"36;1", "36",   "35",   "35;1",
	};

	public static String ColorEscape (String sSrc, String sForegroundColor, String sBackgroundColor, boolean bEnd)
	{
		if (System.console () == null)
			return sSrc;

		return CSI + sForegroundColor + (StringUtils.isNotEmpty (sBackgroundColor) ? "," + sBackgroundColor : "" ) + "m" + sSrc + (bEnd ? CSI + "m" : "");
	}

	public static String Black (String sSrc, boolean bEnd)
	{
		return ColorEscape (sSrc, ANSI_16_COLORS[0], null, bEnd);
	}
	public static String Black (String sSrc)
	{
		return Black (sSrc, true);
	}

	public static String DarkRed (String sSrc, boolean bEnd)
	{
		return ColorEscape (sSrc, ANSI_16_COLORS[1], null, bEnd);
	}
	public static String DarkRed (String sSrc)
	{
		return DarkRed (sSrc, true);
	}

	public static String Green (String sSrc, boolean bEnd)
	{
		return ColorEscape (sSrc, ANSI_16_COLORS[2], null, bEnd);
	}
	public static String Green (String sSrc)
	{
		return Green (sSrc, true);
	}

	public static String Brown (String sSrc, boolean bEnd)
	{
		return ColorEscape (sSrc, ANSI_16_COLORS[3], null, bEnd);
	}
	public static String Brown (String sSrc)
	{
		return Brown (sSrc, true);
	}

	public static String DarkBlue (String sSrc, boolean bEnd)
	{
		return ColorEscape (sSrc, ANSI_16_COLORS[4], null, bEnd);
	}
	public static String DarkBlue (String sSrc)
	{
		return DarkBlue (sSrc, true);
	}

	public static String Magenta (String sSrc, boolean bEnd)
	{
		return ColorEscape (sSrc, ANSI_16_COLORS[5], null, bEnd);
	}
	public static String Magenta (String sSrc)
	{
		return Magenta (sSrc, true);
	}

	public static String DarkCyan (String sSrc, boolean bEnd)
	{
		return ColorEscape (sSrc, ANSI_16_COLORS[6], null, bEnd);
	}
	public static String DarkCyan (String sSrc)
	{
		return DarkCyan (sSrc, true);
	}

	public static String Gray (String sSrc, boolean bEnd)
	{
		return ColorEscape (sSrc, ANSI_16_COLORS[7], null, bEnd);
	}
	public static String Gray (String sSrc)
	{
		return Gray (sSrc, true);
	}

	public static String DarkGray (String sSrc, boolean bEnd)
	{
		return ColorEscape (sSrc, ANSI_16_COLORS[8], null, bEnd);
	}
	public static String DarkGray (String sSrc)
	{
		return DarkGray (sSrc, true);
	}

	public static String Red (String sSrc, boolean bEnd)
	{
		return ColorEscape (sSrc, ANSI_16_COLORS[9], null, bEnd);
	}
	public static String Red (String sSrc)
	{
		return Red (sSrc, true);
	}

	public static String LightGreen (String sSrc, boolean bEnd)
	{
		return ColorEscape (sSrc, ANSI_16_COLORS[10], null, bEnd);
	}
	public static String LightGreen (String sSrc)
	{
		return LightGreen (sSrc, true);
	}

	public static String Yellow (String sSrc, boolean bEnd)
	{
		return ColorEscape (sSrc, ANSI_16_COLORS[11], null, bEnd);
	}
	public static String Yellow (String sSrc)
	{
		return Yellow (sSrc, true);
	}

	public static String Blue (String sSrc, boolean bEnd)
	{
		return ColorEscape (sSrc, ANSI_16_COLORS[12], null, bEnd);
	}
	public static String Blue (String sSrc)
	{
		return Blue (sSrc, true);
	}

	public static String Pink (String sSrc, boolean bEnd)
	{
		return ColorEscape (sSrc, ANSI_16_COLORS[13], null, bEnd);
	}
	public static String Pink (String sSrc)
	{
		return Pink (sSrc, true);
	}

	public static String Cyan (String sSrc, boolean bEnd)
	{
		return ColorEscape (sSrc, ANSI_16_COLORS[14], null, bEnd);
	}
	public static String Cyan (String sSrc)
	{
		return Cyan (sSrc, true);
	}

	public static String White (String sSrc, boolean bEnd)
	{
		return ColorEscape (sSrc, ANSI_16_COLORS[15], null, bEnd);
	}
	public static String White (String sSrc)
	{
		return White (sSrc, true);
	}

	public static final int HEX_DUMP_BYTES_PER_LINE = 16;
	public static final int HEX_DUMP_BYTES_PER_HALF_LINE = HEX_DUMP_BYTES_PER_LINE / 2;
	public static void HexDump (byte[] buf, int nLength)
	{
		StringBuilder sb = new StringBuilder ();
		StringBuilder sb_ascii = new StringBuilder ();
		int i=0;
		for (byte b : buf)
		{
			i++;
			if (i > nLength)
				break;

			int b2 = b&0xFF;
			sb.append (String.format("%02X ", b&0xFF));
			if (b2 >= ' ' && b2<= '~')	// 0x20 - 0x7E
				sb_ascii.append ((char)b2);
			else if (b2 == 0x7F)
				sb_ascii.append ((char)b2);
			else if (b2 >= 0 && b2 <= 0x1F)
				sb_ascii.append (ASCII_ControlCharacters[b2]);
			else
				sb_ascii.append (".");

			if (i%HEX_DUMP_BYTES_PER_HALF_LINE ==0)
			{
				sb.append (" ");
				//sb_ascii.append (" ");
			}
			if (i%HEX_DUMP_BYTES_PER_LINE == 0)
			{
				sb.append (sb_ascii);
				sb_ascii.setLength (0);
				sb.append ("\n");
			}
		}
		if (sb_ascii.length() > 0)
		{
			int j = i%HEX_DUMP_BYTES_PER_LINE;
			for (int k=0; k<HEX_DUMP_BYTES_PER_LINE - j; k++)	// 补足最后一行的空格，以对齐显示
				sb.append ("   ");

			sb.append (" ");
			if (j<=HEX_DUMP_BYTES_PER_HALF_LINE)
				sb.append (" ");
			sb.append (sb_ascii);
			//sb_ascii.setLength (0);
			//sb.append ("\n");
		}
		net_maclife_wechat_http_BotApp.logger.fine (sb.toString());
	}
	public static void HexDump (byte[] buf)
	{
		HexDump (buf, buf.length);
	}
	public static void HexDump (String s)
	{
//System.out.println (s);
		byte[] lineBytes = s.getBytes();
		HexDump (lineBytes);
	}

	/**
	 * 用当前属性向缓冲区中输出字符串。
	 * 当缓冲区不足时（行数或者列数），会自动补充缓冲区。输出结束后，当前属性中的当前行号、当前列号变为输出结束后的行号、列号位置。
	 *
	 * <h3>此缓冲区与实际 terminal 输出的区别</h3>
	 * <dl>
	 * 	<dt>换行</dt>
	 * 	<dd>实际 terminal 输出时，即使换行，其字符属性也继承上一行（光标跳转之前的行）的属性。但在 IRC 中每行都是一个新的开始，所以不能/无法继承之前行的属性，需要特殊处理</dd>
	 *
	 * 	<dt>无输出的地方</dt>
	 * 	<dd>实际 terminal 输出时，如果光标跳过了一段区域（比如：光标向后跳了 n 列），那么中间的内容是“空的”，而在 IRC 中，这些“空的”内容必须以空格补充，而补充的空格如果不做特殊处理，将会产生意外的效果（比如前面已有背景色，那么输出空格会导致背景色输出）</dd>
	 * </dl>
	 *
	 * @param listVirtualTerminalBuffer 字符缓冲区。List 类型，每行占用 list 的一个元素。每个元素代表一行，其类型是 CharBuffer
	 * @param sbSrc 要输出的源字符串
	 * @param currentAttribute “屏幕”当前属性。当前属性中的 key 定义
	 * <dl>
	 * 	<dt>TERMINAL_COLUMNS</dt>
	 * 	<dd>“终端/屏幕”最大宽度（单位: 字符，每个汉字占 2 个字符宽度）。整数类型。</dd>
	 *
	 * 	<dt>LINE_NO</dt>
	 * 	<dd>当前行号。整数类型。行号列号从 1 开始计数。</dd>
	 *
	 * 	<dt>COLUMN_NO</dt>
	 * 	<dd>当前列号。整数类型。行号列号从 1 开始计数。</dd>
	 *
	 * 	<dt>bold</dt>
	 * 	<dd>是否高亮/粗体。boolean 类型，可能为 null，若为 null 则等同于 false。</dd>
	 *
	 * 	<dt>underline</dt>
	 * 	<dd>是否下划线。boolean 类型，可能为 null，若为 null 则等同于 false。</dd>
	 *
	 * 	<dt>reverse</dt>
	 * 	<dd>是否反色。boolean 类型，可能为 null，若为 null 则等同于 false。</dd>
	 *
	 * 	<dt></dt>
	 * 	<dd></dd>
	 *
	 * 	<dt>256color</dt>
	 * 	<dd>是否 256 色。boolean 类型，可能为 null，若为 null 则等同于 false。</dd>
	 *
	 * 	<dt>fg</dt>
	 * 	<dd>背景色。整数类型。该数值为 ANSI 的颜色数值。如 31 37 38 或 256 色的 100 等。</dd>
	 *
	 * 	<dt>bg</dt>
	 * 	<dd>字符颜色/前景色。整数类型。该数值为 ANSI 的颜色数值。如 31 37 38 或 256 色的 100 等。</dd>
	 * </dl>
	 */
	public static void PutsToScreenBuffer (List<CharBuffer> listVirtualTerminalBuffer, List<List<Map<String, Object>>>listLinesCharactersAttributes, StringBuffer sbSrc, final Map<String, Object> currentAttribute, int TERMINAL_COLUMNS)
	{
		int i=0, j=0;
		int nLineNO = 1;
		int nColumnNO = 1;
		//int TERMINAL_COLUMNS = DEFAULT_SCREEN_COLUMNS;
		if (currentAttribute.get ("LINE_NO") != null)
			nLineNO = (int)currentAttribute.get ("LINE_NO");
		if (currentAttribute.get ("COLUMN_NO") != null)
			nColumnNO = (int)currentAttribute.get ("COLUMN_NO");
		//if (currentAttribute.get ("TERMINAL_COLUMNS") != null)
		//	TERMINAL_COLUMNS = (int)currentAttribute.get ("TERMINAL_COLUMNS");

		int iLineIndex = nLineNO - 1, iColumnIndex = nColumnNO - 1;
		boolean isOutputingFirstCharacterInThisLine = true;

		net_maclife_wechat_http_BotApp.logger.finest (nLineNO + " 行 " + nColumnNO + " 列 输出：" + sbSrc);
		net_maclife_wechat_http_BotApp.logger.finest ("属性: " + currentAttribute);
		net_maclife_wechat_http_BotApp.logger.finest ("-------------------------------------------------");

		CharBuffer cbLine = null;
		List<Map<String, Object>> listLineCharactersAttributes = null;
		for (i=0; i<sbSrc.length (); i++)
		{
			char ch = sbSrc.charAt (i);
			if (ch == '\r' || ch == '\n')
			{
				if (ch == '\r')
				{
					if (i < sbSrc.length () - 1  && sbSrc.charAt (i+1) == '\n')
					{
						i++;
						net_maclife_wechat_http_BotApp.logger.finer (i + " 处为 Windows 回车换行符号，只计做一个回车符");
					}
					else
						net_maclife_wechat_http_BotApp.logger.warning ((i+1) + " 处只遇到了 \\r 回车符号");
				}
				else if (ch == '\n')
				{
					if (i < sbSrc.length () - 1  && sbSrc.charAt (i+1) == '\r')
					{
						i++;
						net_maclife_wechat_http_BotApp.logger.finer (i + " 处为 MacOS 换行回车符号，只计做一个回车符");
					}
					else
						net_maclife_wechat_http_BotApp.logger.finer ((i+1) + " 处遇到了 \\n 换行符号");
				}
			}

			if (nColumnNO > TERMINAL_COLUMNS)
			{	// 当前列号超过屏幕宽度后，自动换到下一行，光标到行首
				net_maclife_wechat_http_BotApp.logger.fine ("当前列号 " + nColumnNO + " 已超过屏幕宽度 " + TERMINAL_COLUMNS + "，将光标移到下一行行首");
				nLineNO ++;	iLineIndex++;
				nColumnNO = 1;	iColumnIndex = 0;
			}
			if (ch == '\r' || ch == '\n' )
			{
				nLineNO ++;	iLineIndex++;
				nColumnNO = 1;	iColumnIndex = 0;
				isOutputingFirstCharacterInThisLine = true;
				net_maclife_wechat_http_BotApp.logger.fine ("当前字符是回车/换行符，换到新一行 " + nLineNO + ", 并跳过输出该符号");
				continue;
			}

			// 先准备好足够多的空间
			if (nLineNO > listVirtualTerminalBuffer.size ())
			{
				int nSupplement = nLineNO - listVirtualTerminalBuffer.size ();
				for (j=0; j<nSupplement; j++)
				{
					cbLine = CharBuffer.allocate (TERMINAL_COLUMNS);
					listVirtualTerminalBuffer.add (cbLine);

					listLineCharactersAttributes = new ArrayList<Map<String, Object>>();
					listLinesCharactersAttributes.add (listLineCharactersAttributes);
				}
			}
			else
			{
				cbLine = listVirtualTerminalBuffer.get (iLineIndex);
				listLineCharactersAttributes = listLinesCharactersAttributes.get (iLineIndex);
			}

			if (isOutputingFirstCharacterInThisLine)	// 当前行写入第一个字符前，先在前面 null 的地方填充空格； 属性列表中补充 null
			{
				for (j=0; j<iColumnIndex; j++)
				{
					if (cbLine.get (j) == 0)
						cbLine.put (j, ' ');	// 如果没有数据，则用空格填充
				}
				for (j=listLineCharactersAttributes.size (); j<iColumnIndex; j++)
				{
					listLineCharactersAttributes.add (null);
				}
			}

			cbLine.put (iColumnIndex, ch);
			Map<String, Object> attr = new HashMap<String, Object> ();
			attr.putAll (currentAttribute);	// 复制一份属性，不共用原来属性，避免修改一个属性影响到其他
			//System.err.print (nLineNO + " 行已有属性数量 " + listLineCharactersAttributes.size () + " 个, iColumnIndex 索引=" + iColumnIndex + ", 输出字符=[" + String.format ("%c 0x%02X", ch, (int)ch) + "].");	// 复位=" + currentAttribute.get ("reset"
			if (listLineCharactersAttributes.size () <= iColumnIndex)
				listLineCharactersAttributes.add (attr);
			else
				listLineCharactersAttributes.set (iColumnIndex, attr);

			if (currentAttribute.containsKey ("reset"))
				currentAttribute.remove ("reset");	// 去掉属性。在组成字符串时，只需要判断当前属性有没有 reset，有就输出相应的 reset 属性。

			isOutputingFirstCharacterInThisLine = false;
			nColumnNO ++;	iColumnIndex++;
			//System.err.println ("光标移到 " + nLineNO + " 行 " + nColumnNO + " 列");
			//if (ch > 256)	// 汉字，占两个字符宽度...
			//	nCurrentColumnNO ++;
		}
		currentAttribute.put ("LINE_NO", nLineNO);
		currentAttribute.put ("COLUMN_NO", nColumnNO);

		//System.err.println ("----");
		//System.err.println (ToIRCEscapeSequence (listVirtualTerminalBuffer, listLinesCharactersAttributes));
	}

	public static String GetANSIColorName (boolean isForegroundOrBackgroundColor, int nColor)
	{
		int i = isForegroundOrBackgroundColor ? nColor - 30 : nColor - 40;
		switch (i)
		{
			case 0:	// 黑色
				return "黑色";
			case 1:	// 深红色 / 浅红色
				return "红色/浅绿";
			case 2:	// 深绿色 / 浅绿
				return "绿色/浅绿";
			case 3:	// 深黄色(棕色) / 浅黄
				return "黄色";
			case 4:	// 深蓝色 / 浅蓝
				return "蓝色/浅蓝";
			case 5:	// 紫色 / 粉红
				return "紫色/粉红";
			case 6:	// 青色
				return "青色";
			case 7:	// 浅灰 / 白色
				return "灰色/白色";
		}
		return "未知颜色 " + nColor;
	}

	public static int DEFAULT_FOREGROUND_COLOR = 0x30;
	public static int DEFAULT_BACKGROUND_COLOR = 0x40;
	public static int DEFAULT_SCREEN_COLUMNS = 80;

	public static List<String> ConvertAnsiEscapeTo (String sANSIString)
	{
		return ConvertAnsiEscapeTo (sANSIString, DEFAULT_SCREEN_COLUMNS);
	}

	/**
	 * <ul>
	 * <li>最常用的颜色转义序列，即： CSI n 'm'</li>
	 * <li>光标移动转义序列，处理光标前进、向下的移动，也处理回退、向上的移动</li>
	 * <li>清屏、清除行</li>
	 * <li>其他的转义序列，全部清空</li>
	 * </ul>
	 * @param sANSIString 输入的 ANSI 转义序列字符串
	 * @param TERMINAL_COLUMNS
	 * @return
	 */
	public static List<String> ConvertAnsiEscapeTo (String sANSIString, final int TERMINAL_COLUMNS)
	{
		// 注意：
		//  (1). 首先，行号、列号从 1 开始计数，比从 0 开始计数的情况大了 1。
		//  (2). 当前的行号、列号实际上是下一个输出的字符所处的位置，而不是当前字符的位置，所以，相对于当前字符的位置，又大了 1
		//
		// 所以，如果 行号=1，列号=2，实际上，里面只有 1 个字符，而这个字符的索引号是 0 (buf[0])
		int nCurrentLineNO = 1;	// 当前行号，行号从 1 开始
		int nCurrentColumnNO = 1;	// 当前列号，列号从 1 开始

		String sNewLineNO = "";	// 行号
		String sNewColumnNO = "";	// 列号
		int nNewLineNO = 1;
		int nNewColumnNO = 1;

		int nSavedLineNO = 1;
		int nSavedColumnNO = 1;

		int nDelta = 1;

		List<CharBuffer> listScreenBuffer = new ArrayList<CharBuffer>();
		/**
		 * Object[] 属性列表
		 * #0: boolean 是否 bold, true | false
		 * # : boolean 是否 256 色
		 * #1: String 前景色
		 * #1: String 背景色
		 * #n: byte 字符宽度，ASCII 1个字符，汉字、及（todo 其他，该怎么定义这个其他）2个
		 * #n: Object[] 从前面继承而来的属性。 注意： 如果当前没有属性，而有从前面继承而来的属性，则在输出时，不输出任何属性，只输出字符； 但在读取时，需要从继承的属性中读取。
		 */
		List<List<Map<String, Object>>> listAttributes = new ArrayList<List<Map<String, Object>>>();
		Map<String, Object> previousCharacterAttribute = new HashMap<String, Object> ();
		Map<String, Object> currentCharacterAttribute = null;
/*
// Reset defaults for character insertion.
void
_vte_terminal_set_default_attributes(VteTerminal *terminal)
{
	VteScreen *screen;

	screen = terminal->pvt->screen;

	screen->defaults.c = 0;
	screen->defaults.attr.columns = 1;
	screen->defaults.attr.fragment = 0;
	screen->defaults.attr.fore = VTE_DEF_FG;
	screen->defaults.attr.back = VTE_DEF_BG;
	screen->defaults.attr.reverse = 0;
	screen->defaults.attr.bold = 0;
	screen->defaults.attr.invisible = 0;
	// unused; bug 499893
	screen->defaults.attr.protect = 0;
	//
	screen->defaults.attr.standout = 0;
	screen->defaults.attr.underline = 0;
	screen->defaults.attr.strikethrough = 0;
	screen->defaults.attr.half = 0;
	screen->defaults.attr.blink = 0;
	screen->basic_defaults = screen->defaults;
	screen->color_defaults = screen->defaults;
	screen->fill_defaults = screen->defaults;
} */
//HexDump (line);

		sANSIString = sANSIString.replaceAll (CSI_Others2_REGEXP_Replace, "");

		sANSIString = sANSIString.replaceAll (VT220_SCS_REGEXP_Replace, "");

		sANSIString = sANSIString.replaceAll (XTERM_VT100_TwoCharEscapeSequences_REGEXP_Replace, "");

		int i = 0;
		Matcher matcher = null;
		StringBuffer sbReplace = new StringBuffer ();

		//int iBold = 0;
		matcher = CSI_SGR_CursorMoving_EraseText_PATTERN_Replace.matcher (sANSIString);
		while (matcher.find())
		{
			// 因为不是流式处理，所以，遇到 regexp 时，需要先用 之前的属性 输出之前的字符（如果有的话）
			matcher.appendReplacement (sbReplace, "");
			if (sbReplace.length () > 0)
			{
				//System.err.println ("写入缓冲区前的光标位置=" + nCurrentLineNO + " 行 " + nCurrentColumnNO + " 列");
				previousCharacterAttribute.put ("LINE_NO", nCurrentLineNO);
				previousCharacterAttribute.put ("COLUMN_NO", nCurrentColumnNO);
				previousCharacterAttribute.put ("TERMINAL_COLUMNS", TERMINAL_COLUMNS);
				PutsToScreenBuffer (listScreenBuffer, listAttributes, sbReplace, previousCharacterAttribute, TERMINAL_COLUMNS);	// 因为 regexp 每次遇到新的 ANSI Escape 时，之前的字符串还没输出，所以，需要用前面的属性输出前面的字符串
				nCurrentLineNO = (int)previousCharacterAttribute.get ("LINE_NO");	// 因为写入字符串后，“光标”位置会变化，所以，要再读出来，供下次计算位置
				nCurrentColumnNO = (int)previousCharacterAttribute.get ("COLUMN_NO");

				sbReplace.delete (0, sbReplace.length ());
			}

			// 然后，当前(或者说： 下一个)属性
			currentCharacterAttribute = new HashMap<String, Object> ();
			currentCharacterAttribute.putAll (previousCharacterAttribute);	// 复制前面的属性
			//currentCharacterAttribute.remove ("reset");
			previousCharacterAttribute = currentCharacterAttribute;

			// 处理下一个属性
			String ansi_escape_sequence = matcher.group();
			net_maclife_wechat_http_BotApp.logger.finer ("CSI 参数: " + ansi_escape_sequence.substring (1));
			String sEscParams = matcher.group (1);
			String sEscCmd = matcher.group (2);
			if (sEscParams == null)
				sEscParams = "";
			char chEscCmd = sEscCmd.charAt (0);

			if (chEscCmd=='m')
			{
				// CSI n 'm' 序列: SGR – Select Graphic Rendition
				String sgr_parameters;
				int nFG = -1, nBG = -1;
				//String sIRC_FG = "", sIRC_BG = "";	// ANSI 字符颜色 / ANSI 背景颜色，之所以要加这两个变量，因为: 在 ANSI Escape 中，前景背景并无前后顺序之分，而 IRC Escape 则有顺序

				sgr_parameters = sEscParams;
				//net_maclife_wechat_http_BotApp.logger.fine ("SGR 所有参数: " + sgr_parameters);
				String[] arraySGR = sgr_parameters.split (";");
				for (i=0; i<arraySGR.length; i++)
				{
					String sgrParam = arraySGR[i];
					net_maclife_wechat_http_BotApp.logger.finer ("SGR 参数 #" + (i+1) + ": " + sgrParam);

					int nSGRParam = 0;
					try {
						if (! sgrParam.isEmpty())
							nSGRParam = Integer.parseInt (sgrParam);
					} catch (Exception e) {
						//e.printStackTrace ();
					}
					switch (nSGRParam)
					{
						case 0:	// 关闭
							currentCharacterAttribute.clear ();
							currentCharacterAttribute.put ("reset", true);
							net_maclife_wechat_http_BotApp.logger.finer ("复位/关闭所有属性");
							break;

						case 39:	// 默认前景色
							currentCharacterAttribute.remove ("fg");
							net_maclife_wechat_http_BotApp.logger.finer ("默认前景色");
							break;
						case 49:	// 默认背景色
							currentCharacterAttribute.remove ("bg");
							net_maclife_wechat_http_BotApp.logger.finer ("默认背景色");
							break;

						case 1:	// 粗体/高亮
							currentCharacterAttribute.put ("bold", true);
							net_maclife_wechat_http_BotApp.logger.finer ("高亮 开");
							break;
						case 21:	// 关闭高亮 或者 双下划线
							currentCharacterAttribute.put ("bold", false);
							net_maclife_wechat_http_BotApp.logger.finer ("高亮 关");
							break;

						// unbuffer 命令在 TERM=screen 执行 cal 命令时，输出 ESC [3m 和 ESC [23m
						case 3:	// Italic: on      not widely supported. Sometimes treated as inverse.
						// unbuffer 命令在 TERM=xterm-256color 或者 TERM=linux 执行 cal 命令时，输出 ESC [7m 和 ESC [27m
						case 7:	// Image: Negative 前景背景色反转 inverse or reverse; swap foreground and background (reverse video)
							currentCharacterAttribute.put ("reverse", true);
							net_maclife_wechat_http_BotApp.logger.finer ("反色 开");
							break;
						case 23:	// Not italic, not Fraktur
						case 27:	// Image: Positive 前景背景色正常。由于 IRC 没有这一项，所以，应该替换为 Colors.NORMAL 或者 再次翻转(反反得正)
							currentCharacterAttribute.put ("reverse", false);
							net_maclife_wechat_http_BotApp.logger.finer ("反色 关");
							break;

						case 4:	// 单下划线
							currentCharacterAttribute.put ("underline", true);
							net_maclife_wechat_http_BotApp.logger.finer ("下划线 开");
							break;
						case 24:	// 下划线关闭
							currentCharacterAttribute.put ("underline", false);
							net_maclife_wechat_http_BotApp.logger.finer ("下划线 关");
							break;

						case 5:
							net_maclife_wechat_http_BotApp.logger.finer ("闪烁 开(作废，IRC 不支持)");
							break;
						case 6:
							net_maclife_wechat_http_BotApp.logger.finer ("闪烁 开(作废，IRC 不支持)");
							break;
						case 25:	// blink off
							net_maclife_wechat_http_BotApp.logger.finer ("闪烁 关(作废，IRC 不支持)");
							break;

						case 30:	// 黑色
						case 31:	// 深红色 / 浅红色
						case 32:	// 深绿色 / 浅绿
						case 33:	// 深黄色(棕色) / 浅黄
						case 34:	// 深蓝色 / 浅蓝
						case 35:	// 紫色 / 粉红
						case 36:	// 青色
						case 37:	// 浅灰 / 白色
							nFG = nSGRParam;
							currentCharacterAttribute.put ("fg", nFG);
							net_maclife_wechat_http_BotApp.logger.finer (GetANSIColorName(true, nSGRParam));
							break;
						case 40:	// 黑色
						case 41:	// 深红色 / 浅红色
						case 42:	// 深绿色 / 浅绿
						case 43:	// 深黄色(棕色) / 浅黄
						case 44:	// 深蓝色 / 浅蓝
						case 45:	// 紫色 / 粉红
						case 46:	// 青色
						case 47:	// 浅灰 / 白色
							nBG = nSGRParam;
							currentCharacterAttribute.put ("bg", nBG);
							net_maclife_wechat_http_BotApp.logger.finer (GetANSIColorName(false, nSGRParam) + "　背景");
							break;
						case 38:
						case 48:	// xterm-256 前景/背景颜色扩展，后续参数 '5;' x ，x 是 0-255 的颜色索引号
							assert i<arraySGR.length-2;
							assert arraySGR[i+1].equals("5");
							try {
								int iColorIndex = Integer.parseInt (arraySGR[i+2]) % 256;
								if (nSGRParam==38)
								{
									currentCharacterAttribute.put ("256color-fg", true);
									currentCharacterAttribute.put ("fg", iColorIndex);
									net_maclife_wechat_http_BotApp.logger.finer ("256　色前景色 " + iColorIndex);
								}
								else if (nSGRParam==48)
								{
									currentCharacterAttribute.put ("256color-bg", true);
									currentCharacterAttribute.put ("bg", iColorIndex);
									net_maclife_wechat_http_BotApp.logger.finer ("256　色背景色 " + iColorIndex);
								}
							} catch (NumberFormatException e) {
								e.printStackTrace ();
							}
							i += 2;
							break;
						default:
							net_maclife_wechat_http_BotApp.logger.warning ("不支持的 SGR 参数: " + nSGRParam);
							break;
					}
				}
			}

			else if (
					chEscCmd=='A' || chEscCmd=='B' || chEscCmd=='C' || chEscCmd=='D'
					|| chEscCmd=='E' || chEscCmd=='F' || chEscCmd=='G' || chEscCmd=='H' || chEscCmd=='f'
					|| chEscCmd=='d'|| chEscCmd=='e'
					|| chEscCmd=='s'|| chEscCmd=='u'
				)
			{
				//net_maclife_wechat_http_BotApp.logger.fine ("↑↓←→光标移动 ANSI 转义序列: " + ansi_escape_sequence.substring(1));
				nNewLineNO = nCurrentLineNO;
				nNewColumnNO = nCurrentColumnNO;
				nDelta = 1;

				char chCursorCommand = chEscCmd;;
				try
				{
					if (!sEscParams.isEmpty())
						nDelta = Integer.parseInt (sEscParams);
				}
				catch (Exception e)
				{
					//e.printStackTrace ();
				}
				switch (chCursorCommand)
				{
					case 'A':	// 向上
					case 'F':	// 向上，光标移动到最前
						if (chCursorCommand == 'F')
							nNewColumnNO = 1;
						else
							nNewColumnNO = nCurrentColumnNO;

						nNewLineNO = nCurrentLineNO - nDelta;
						if (nNewLineNO < 1)
							nNewLineNO = 1;
						net_maclife_wechat_http_BotApp.logger.finer ("↑光标从当前行 " + nCurrentLineNO + " 向上 " + nDelta + " 行，到 " + nNewLineNO + " 行" + (chCursorCommand == 'F' ? "，光标移动到行首" : ""));
						break;
					case 'B':	// 向下
					case 'E':	// 向下，光标移动到最前
						if (chCursorCommand == 'E')
							nNewColumnNO = 1;
						else
							nNewColumnNO = nCurrentColumnNO;

						nNewLineNO = nCurrentLineNO + nDelta;
						net_maclife_wechat_http_BotApp.logger.finer ("↓光标从当前行 " + nCurrentLineNO + " 向下 " + nDelta + " 行，到 " + nNewLineNO + " 行" + (chCursorCommand == 'E' ? "，光标移动到行首" : ""));
						break;
					case 'C':	// 向右/前进
						nNewLineNO = nCurrentLineNO;
						nNewColumnNO = nCurrentColumnNO + nDelta;
						if (nNewColumnNO > TERMINAL_COLUMNS)
							nNewColumnNO = TERMINAL_COLUMNS;
						net_maclife_wechat_http_BotApp.logger.finer ("→光标从当前列 " + nCurrentColumnNO + " 向右 " + nDelta + " 列，到 " + nNewColumnNO + " 列");
						break;
					case 'D':	// 向左/回退
						nNewLineNO = nCurrentLineNO;
						nNewColumnNO = nCurrentColumnNO - nDelta;
						if (nNewColumnNO < 1)
							nNewColumnNO = 1;
						net_maclife_wechat_http_BotApp.logger.finer ("←光标从当前列 " + nCurrentColumnNO + " 向左 " + nDelta + " 列，到 " + nNewColumnNO + " 列");
						break;
					case 'G':	// 光标水平绝对位置
						nNewLineNO = nCurrentLineNO;
						nNewColumnNO = nDelta;
						if (nNewColumnNO < 1)
							nNewColumnNO = 1;
						else if (nNewColumnNO > TERMINAL_COLUMNS)
							nNewColumnNO = TERMINAL_COLUMNS;
						net_maclife_wechat_http_BotApp.logger.finer ("←→光标从当前列 " + nCurrentColumnNO + " 水平移动到第 " + nNewColumnNO + " 列");
						break;
					case 'H':	// 指定位置 CUP – Cursor Position
					case 'f':	// 指定位置 HVP – Horizontal and Vertical Position
						nNewLineNO = 1;
						nNewColumnNO = 1;
						sNewLineNO = "";
						sNewColumnNO = "";
						if (!sEscParams.isEmpty())
						{
							String[] arrayCursorPosition = sEscParams.split (";", 2);
							sNewLineNO = arrayCursorPosition[0];
							if (arrayCursorPosition.length>1)
								sNewColumnNO = arrayCursorPosition[1];
						}
						if (!sNewLineNO.isEmpty())
							nNewLineNO = Integer.parseInt (sNewLineNO);
						if (!sNewColumnNO.isEmpty())
							nNewColumnNO = Integer.parseInt (sNewColumnNO);
						net_maclife_wechat_http_BotApp.logger.finer ("光标定位到: " + nNewLineNO + " 行 " + nNewColumnNO + " 列");
						break;
					case 'd':	// 光标绝对行号 VPA – Line/Vertical Position Absolute [row] (default = [1,column]) (VPA).
					case 'e':	// 光标相对行号 VPR - Line Position Relative [rows] (default = [row+1,column]) (VPR).
						nNewColumnNO = nCurrentColumnNO;
						if (chCursorCommand == 'd')
						{
							nNewLineNO = nDelta;
							net_maclife_wechat_http_BotApp.logger.fine ("↑↓光标跳至 " + nNewLineNO + " 行");
						}
						else if (chCursorCommand == 'e')
						{
							nNewLineNO += nDelta;
							net_maclife_wechat_http_BotApp.logger.finer ("↑↓光标跳 " + nDelta + " 行，到 " + nNewLineNO + " 行");
						}
						break;
					case 's':
						net_maclife_wechat_http_BotApp.logger.finer ("记忆光标位置: " + nCurrentLineNO + " 行 " + nCurrentColumnNO + " 列");
						nSavedLineNO = nCurrentLineNO;
						nSavedColumnNO = nCurrentColumnNO;
						break;
					case 'u':
						nNewLineNO = nSavedLineNO;
						nNewColumnNO = nSavedColumnNO;
						net_maclife_wechat_http_BotApp.logger.finer ("光标位置恢复到记忆位置 " + nNewLineNO + " 行 " + nNewColumnNO + " 列");
						break;
				}

				nCurrentLineNO = nNewLineNO;
				nCurrentColumnNO = nNewColumnNO;
			}

			else if (chEscCmd=='J' || chEscCmd=='K')
			{
				nDelta = 0;
				try
				{
					if (!sEscParams.isEmpty())
						nDelta = Integer.parseInt (sEscParams);
				}
				catch (Exception e)
				{
					//e.printStackTrace ();
				}

				ClearText (listScreenBuffer, listAttributes, currentCharacterAttribute, chEscCmd, nDelta, nCurrentLineNO, nCurrentColumnNO, TERMINAL_COLUMNS);
			}
		}
		matcher.appendTail (sbReplace);
		previousCharacterAttribute.put ("LINE_NO", nCurrentLineNO);
		previousCharacterAttribute.put ("COLUMN_NO", nCurrentColumnNO);
		previousCharacterAttribute.put ("TERMINAL_COLUMNS", TERMINAL_COLUMNS);
		net_maclife_wechat_http_BotApp.logger.finer ("光标在将最后一个字符串写入缓冲区前的位置=" + nCurrentLineNO + " 行 " + nCurrentColumnNO + " 列");
		PutsToScreenBuffer (listScreenBuffer, listAttributes, sbReplace, previousCharacterAttribute, TERMINAL_COLUMNS);

		//return ToIRCEscapeSequence (listScreenBuffer, listAttributes);
		return null;
	}

	/**
	 * 清除文字。
	 * 用空格清除屏幕
	 * @param listScreenBuffer 屏幕缓冲区
	 * @param listAttributes 屏幕缓冲区的字符属性
	 * @param currentCharacterAttribute 当前字符属性
	 * @param cmd 清除命令，只能为 <code>J</code>--屏幕内清除 或者 <code>K</code>--行内清除
	 * @param param 只能为 <code>0</code> 或者 <code>1</code> 或者 <code>2</code>
	 * @param nLineNO 当前行号
	 * @param nColumnNO 当前列号
	 * @param TERMINAL_COLUMNS 屏幕宽度（每行字符数量）
	 */
	public static void ClearText (List<CharBuffer> listScreenBuffer, List<List<Map<String, Object>>>listAttributes, Map<String, Object> currentCharacterAttribute, char cmd, int param, int nLineNO, int nColumnNO, final int TERMINAL_COLUMNS)
	{
		if (param > 2)
		{
			//throw new RuntimeException ("文本清除 ANSI 序列的数字参数为 " + nDelta + ", 是无效的");
			param = 2;
		}

		CharBuffer cbLine = null;
		List<Map<String, Object>> lineAttrs = null;
		int i=0, nStartLineNO=0, nStartColumnNO=0, nEndLineNO=0, nEndColumnNO=0, nOtherLines=0;
		StringBuffer sb = new StringBuffer ();	// 用空格去“清除/覆盖”字符，而不是把字符+属性全部删除

		// 设置行内列号取值范围
		if (param == 0 || param == 1)
		{
			if (param == 0)
			{
				nStartColumnNO = nColumnNO;
				nEndColumnNO = TERMINAL_COLUMNS + 1;
			}
			else if (param == 1)
			{
				nStartColumnNO = 1;
				nEndColumnNO = nColumnNO;
			}
			for (int col=nStartColumnNO; col<nEndColumnNO; col++)	// 行内应该覆盖的字符数量
				sb.append (" ");
		}

		Map<String, Object> attr = new HashMap<String, Object> ();
		attr.putAll (currentCharacterAttribute);
		//attr.remove ("reset");

		if (cmd=='J')
		{
			switch (param)
			{
				case 0:
				case 1:
					if (param == 0)
					{
						nStartLineNO = nLineNO;
						//nEndLineNO = listScreenBuffer.size () + 1;
						if (nLineNO >= listScreenBuffer.size ())	// 如果当前行已经 是/超过 最后一行，则没有其他行需要清除
							nOtherLines = 0;
						else
							nOtherLines = listScreenBuffer.size() - nLineNO;
						net_maclife_wechat_http_BotApp.logger.finer ("从 " + nLineNO + " 行 " + nColumnNO + " 列向下向后 清屏，到最后一行 " + listScreenBuffer.size () + " 行为止");
					}
					else if (param == 1)
					{
						nStartLineNO = 1;
						//nEndLineNO = nLineNO;
						nOtherLines = nLineNO - 1;
						net_maclife_wechat_http_BotApp.logger.finer ("从 " + nLineNO + " 行 " + nColumnNO + " 列向上向前 清屏，到第一行");
					}
					for (i=0; i<nOtherLines; i++)	// 本行外其他行覆盖的字符数量
					{
						for (int col=0; col<TERMINAL_COLUMNS; col++)
							sb.append (" ");
					}
					break;
				case 2:
					net_maclife_wechat_http_BotApp.logger.finer (nLineNO + " 行 " + nColumnNO + " 列 全部清屏");
					//sb.delete (0, sb.length ());
					nStartLineNO = 1;
					nStartColumnNO = 1;
					nOtherLines = listScreenBuffer.size ();
					for (i=0; i<nOtherLines; i++)	// 全屏幕（不完全正确，确切的说是全缓冲区，因为这不是真实屏幕，没有“高度”这个概念）
					{
						for (int col=0; col<TERMINAL_COLUMNS; col++)
							sb.append (" ");
					}
					break;
			}
			attr.put ("LINE_NO", nStartLineNO);
			attr.put ("COLUMN_NO", nStartColumnNO);

			PutsToScreenBuffer (listScreenBuffer, listAttributes, sb, attr, TERMINAL_COLUMNS);	// 覆盖/清屏
		}
		else if (cmd=='K')
		{
			switch (param)
			{
				case 0:
				case 1:
					if (param == 0)
						net_maclife_wechat_http_BotApp.logger.finer (nLineNO + " 行从 " + nColumnNO + " 列 向后/向右清除字符");
					else if (param == 1)
						net_maclife_wechat_http_BotApp.logger.finer (nLineNO + " 行从 " + nColumnNO + " 列 向前/向左清除字符");
					break;
				case 2:
					net_maclife_wechat_http_BotApp.logger.finer (nLineNO + " 行 全部清除");
					nStartColumnNO = 1;
					for (int col=0; col<TERMINAL_COLUMNS; col++)
						sb.append (" ");
					break;
			}
			attr.put ("LINE_NO", nLineNO);
			attr.put ("COLUMN_NO", nStartColumnNO);

			PutsToScreenBuffer (listScreenBuffer, listAttributes, sb, attr, TERMINAL_COLUMNS);	// 覆盖/清屏
		}
	}

	public static String[] IBM_437_ControlCharacters =
	{
		"\u0001",
		"\u0002",
		"\u0003",
		"\u0004",
		"\u0005",
		"\u0006",
		"\u0007",
		"\u0008",
		//"\u0009",	// \t
		//"\n",	// \u000A

		//"\u000B",
		"\u000C",
		//"\r",	// \u000D
		"\u000E",
		"\u000F",
		"\u0010",
		"\u0011",
		"\u0012",
		"\u0013",
		"\u0014",

		"\u0015",
		"\u0016",
		"\u0017",
		"\u0018",
		"\u0019",
		"\u001A",
		//"\u001B",
		"\u001C",
		"\u001D",
		"\u001E",

		"\u001F",
		"\u007F",
	};

	// http://stackoverflow.com/questions/14553178/encoding-%E2%98%BA-as-ibm-437-fails-while-other-valid-characters-like-%C3%A9-succeed
	// http://stackoverflow.com/questions/14553178/encoding-☺-as-ibm-437-fails-while-other-valid-characters-like-é-succeed
	public static String[] IBM_437_ControlCharacters_Translate =
	{
		// echo -ne '\x26\x3A\x26\x3B\x26\x65\x26\x66\x26\x63\x26\x60\x20\x22\x25\xD8\x25\xCB\x25\xD9\x26\x42\x26\x40\x26\x6A\x26\x6B\x26\x3C\x25\xBA\x25\xC4\x21\x95\x20\x3C\x00\xB6\x00\xA7\x25\xAC\x21\xA8\x21\x91\x21\x93\x21\x92\x21\x90\x22\x1F\x21\x94\x25\xB2\x25\xBC\x23\x02' | iconv -f utf16be
		//
		"\u263A",	// ☺
		"\u263B",	// ☻
		"\u2665",	// ♥
		"\u2666",	// ♦
		"\u2663",	// ♣
		"\u2660",	// ♠
		"\u2022",	// •
		"\u25D8",	// ◘
		//"\u25CB",	// ○
		//"\u25D9",	// ◙

		//"\u2642",	// ♂
		"\u2640",	// ♀
		//"\u266A",	// ♪
		"\u266B",	// ♫
		"\u263C",	// ☼
		"\u25BA",	// ►
		"\u25C4",	// ◄
		"\u2195",	// ↕
		"\u203C",	// ‼
		"\u00B6",	// ¶

		"\u00A7",	// §
		"\u25AC",	// ▬
		"\u21A8",	// ↨
		"\u2191",	// ↑
		"\u2193",	// ↓
		"\u2192",	// →
		//"\u2190",	// ←
		"\u221F",	// ∟
		"\u2194",	// ↔
		"\u25B2",	// ▲

		"\u25BC",	// ▼
		"\u2302",	// ⌂
	};
	public static String Fix437Characters (String src)
	{
		return StringUtils.replaceEach (src, IBM_437_ControlCharacters, IBM_437_ControlCharacters_Translate);
	}

	public static void main (String[] args) throws IOException
	{
		if (args.length < 1)
		{
			System.err.println ("用法： java ANSIEscapeTool <ANSI 输入文件> [文件编码，默认为 437]");
			return;
		}

		long MAX_FILE_SIZE = 1024*1024;
		String sFileName = args[0];
		File f = new File (sFileName);

		if (f.length () > MAX_FILE_SIZE)
		{
			System.err.println ("文件大小 " + f.length () + " 超过允许的大小 " + MAX_FILE_SIZE);
			return;
		}
		byte[] buf = new byte[(int)f.length ()];

		String sCharSet = "437";
		if (args.length >= 2)
		{
			sCharSet = args[1];
			System.err.println ("输入文件的字符集编码采用 " + sCharSet + " 编码");
		}
		else
			System.err.println ("输入文件的字符集编码采用默认的 " + sCharSet + " 编码");

		Charset cs = Charset.forName (sCharSet);
		FileInputStream fis = new FileInputStream(f);
		fis.read (buf);

		String sSrc = new String (buf, sCharSet);
		if (cs.equals (Charset.forName("437")))
		{
			sSrc = Fix437Characters (sSrc);
		}

		Handler[] handlers = net_maclife_wechat_http_BotApp.logger.getHandlers ();
		for (Handler handler : handlers)
		{
			if (handler instanceof ConsoleHandler)
			{
				System.err.println ("控制台日志处理器 " + handler);
				handler.setLevel (Level.ALL);
			}
			else
				System.err.println ("日志处理器 " + handler);
		}
		net_maclife_wechat_http_BotApp.logger.setLevel (Level.ALL);
		List<String> listLines = ConvertAnsiEscapeTo (sSrc);
		for (String line : listLines)
		{
			System.out.println (line);
		}

		// echo -e '\e[H\e[2J\e[41m\e[2K\e[20CTitle'

		// echo -e '\e[2J\e[H \\\e[B\\/\e[A/\n\n'
/*
 \  /
  \/
//*/
		// 读取 stdin
	}

}
