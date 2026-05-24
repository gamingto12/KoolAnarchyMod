package eu.koolfreedom.util;

import eu.koolfreedom.KoolAnarchyMod;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.slf4j.Logger;

public class FLog
{
    private static final ComponentLogger logger = ComponentLogger.logger("");
    private static boolean debugEnabled;

    public static void info(String message, Object... strings)
    {
        for (int i = 0; i < strings.length; i++)
        {
            if (strings[i] == null)
            {
                continue;
            }
            if (message.contains("{" + i + "}"))
            {
                message = message.replace("{" + i + "}", strings[i].toString());
            }
        }
        logger.info(FUtil.miniMessage("<yellow>[KoolAnarchyMod] <gray>" + message));
    }

    public static void info(Component component)
    {
        logger.info(Component.text("[KoolAnarchyMod] ").color(NamedTextColor.YELLOW).append(component).colorIfAbsent(NamedTextColor.GRAY));
    }

    public static void error(String message, Object... strings)
    {
        for (int i = 0; i < strings.length; i++)
        {
            if (strings[i] == null)
            {
                continue;
            }
            if (message.contains("{" + i + "}"))
            {
                message = message.replace("{" + i + "}", strings[i].toString());
            }
        }
        logger.error(FUtil.miniMessage("<red>[KoolAnarchyError] <gold>" + message));
    }

    public static void warning(String message, Object... strings)
    {
        for (int i = 0; i < strings.length; i++)
        {
            if (strings[i] == null)
            {
                continue;
            }
            if (message.contains("{" + i + "}"))
            {
                message = message.replace("{" + i + "}", strings[i].toString());
            }
        }
        logger.warn(FUtil.miniMessage("<#eb7c0e>[KoolAnarchyWarning] <gold>" + message));
    }

    public static void setDebugEnabled(boolean debugEnabled)
    {
        FLog.debugEnabled = debugEnabled;
    }

    public static void debug(String message, Object... strings)
    {
        if (debugEnabled)
        {
            for (int i = 0; i < strings.length; i++)
            {
                if (strings[i] == null)
                {
                    continue;
                }
                if (message.contains("{" + i + "}"))
                {
                    message = message.replace("{" + i + "}", strings[i].toString());
                }
            }
            logger.info(FUtil.miniMessage("<dark_purple>[KoolAnarchyDebug] <gold>" + message));
        }
    }
}
