package simulacao;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.media.jfxmedia.logging.Logger;
import resumo.ResumoAnalise;
import utilitario.Debug;
import utilitario.Globals;
import java.util.Scanner;
import java.util.function.Consumer;


public class Paxos {
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        Logger.setLevel(Logger.DEBUG);
        title();

        // parâmetros de ambiente usados
        Globals.CHANNEL_DELAY     = 100;
        Globals.TIMEOUT           = (Globals.CHANNEL_DELAY * 3);
        Globals.MESSAGE_LOST_RATE = 35+5;
        Globals.BROKEN_RATE       = 10;
        Globals.MESSAGE_DUPLICATION_RATE = 15;
        Globals.MAX_EXE_SPEED     = 10;
        Globals.BROKEN_TIME       = Globals.CHANNEL_DELAY * 4;
        Globals.ELECTION_TIMEOUT  = Globals.TIMEOUT + Globals.BROKEN_TIME;

      
        Debug.CONSOLE_LOG = false; 
        Debug.MSG_RECEPTION = true;
        Debug.LOG_OLDROUND  = true;
        Debug.ELECTION_TIMEOUT = true;

        // vou inicializar com o sumário
        prompt("Digite o número de execuções: ", null, input -> {
            int num = 1;
            try { num = Integer.parseInt(input); } catch (RuntimeException ignored) {}

            new ResumoAnalise(num, 1, 2, 0, 3)
                    .calculate()
                    .print();

            prompt("\nMostrar o log das execuções dos processos? (s/n)", "s",
                    x -> Debug.printExecutionsLog());
        });

        System.exit(0);
    }

    // -----------------------------------------------------------------------------------------------------------------

    private static void prompt(@NotNull String ask, @Nullable String answer, @NotNull Consumer<String> callback) {
        System.out.println(ask);
        final String input = scanner.nextLine().trim().toLowerCase();

        if (answer == null || answer.equals(input))
            callback.accept(input);
    }

    private static void title() {
    	System.out.println("------------------------- Questão - 2 ---------------------------");
        System.out.println("-----------------------------------------------------------------");
        System.out.println("-------- Problema do consenso distribuído usando Paxos ----------");
        System.out.println("-----------------------------------------------------------------");
        System.out.println("-----------------------------------------------------------------");

    }
}
