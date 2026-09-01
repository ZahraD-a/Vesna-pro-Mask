package vesna;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * One line per message, prefixed with the agent that produced it.
 *
 * Jason names each agent's logger "jason.asSemantics.TransitionSystem.<agent>", which is
 * accurate and unreadable when it sits in front of every line of a conversation. This keeps
 * only the last segment, so the transcript reads as dialogue:
 *
 *     [alice]    -> bob : pair_up
 *     [bob]   pair_up in work  ->  cold
 *     [alice]    <- bob : cold
 *
 * Referenced from logging.properties. Messages from plain System.out.println (the mask
 * report) bypass logging entirely and print unprefixed, which is what we want -- the report
 * is not something an agent said.
 */
public class AgentLogFormatter extends Formatter {

    @Override
    public String format(LogRecord record) {
        String name = record.getLoggerName();
        if (name == null) name = "";
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) name = name.substring(dot + 1);
        return String.format("[%-6s] %s%n", name, formatMessage(record));
    }
}
