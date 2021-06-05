package utilitario;

import com.sun.istack.internal.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


public class Debug {
    // flags definidas para fazer a analise de log
	// me baseiei no sistema de flag de acordo com uma implementação em python que eu vi
    public static boolean CONSOLE_LOG = true;  // para habilitar ou desabilitar o log
    public static boolean LOG_ALL = false;     // para ativar todas as flags
    public static boolean MSG_RECEPTION;
    public static boolean MSG_SENDING;
    public static boolean MSG_LOST;
    public static boolean MSG_DUPLICATED;
    public static boolean NODE_STATE;
    public static boolean NODE_BROKEN;
    public static boolean NODE_REPAIRED;
    public static boolean NODE_DECISION;
    public static boolean LOG_OLDROUND;
    public static boolean LOG_TIMEOUT;
    public static boolean ELECTION_TIMEOUT;

    private static final ConcurrentHashMap<String, List<String>> executionLog = new ConcurrentHashMap<>();

    /** adiciona uma nova entrada para o log */
    public static synchronized void log(@NotNull String name, @NotNull String format, Object...args) {
        List<String> logs = executionLog.containsKey(name) ? executionLog.get(name) : new ArrayList<>();
        logs.add(String.format(format, args));

        executionLog.put(name, logs);
    }

    /** fazendo o log por round */
    public static void log(@NotNull Round round, @NotNull String format, Object...args) {
        log("Round (" + round.getCount() + "):", format, args);
    }

    /** log condicionalmente: de acordo com uma bandeira */
    public static void logIf(boolean flag, @NotNull String name, @NotNull String format, Object...args) {
        if (flag || LOG_ALL)
            log(name, format, args);
    }

    public static void logIf(boolean flag, @NotNull Round round, @NotNull String format, Object...args) {
        logIf(flag, "Round (" + round.getCount() + "):", format, args);
    }

    /**
     * mostra os logs coletados de todas as execuções
     */
    public static synchronized void printExecutionsLog() {
        final StringBuilder sb = new StringBuilder()
                .append("---------------------------------------------------------------------\n")
                .append("------------------------  Log das execuções ----------------------------\n")
                .append("---------------------------------------------------------------------\n");

        final ArrayList<String> keys = new ArrayList<>(executionLog.keySet());
        keys.sort(String::compareTo);

        for (String key: keys) {
            sb.append(key)
              .append("\n");

            executionLog.get(key).forEach(s -> sb.append("\t> ")
              .append(s)
              .append("\n"));
        }
        System.out.println(sb.toString());
    }
}
