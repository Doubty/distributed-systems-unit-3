package resumo;

import com.sun.istack.internal.NotNull;

import utilitario.Round;

import java.util.Map;
import java.util.TreeMap;

/**
 * Resumo para calcular as estatísticas para uma única execução
 */
public class Resumo {
    // mensagens
    public int totalMessages;
    public int lostMessages;
    public int duplicatedMessages;
    // processos
    public int totalNodes;
    public int brokenEvents;
    // execução
    public int rounds;
    public long timeElapsed;
    public boolean agreement = false;
    private Map<Integer, Integer> decisions = new TreeMap<>();


    public void startTime() {
        timeElapsed = System.currentTimeMillis();  // tempo
    }

    public void finishTime() {
        timeElapsed = System.currentTimeMillis() - timeElapsed;
    }

    public synchronized void updateRound(@NotNull Round round) {
        if (round.getCount() > rounds)
            rounds = round.getCount();
    }

  
    public synchronized void decidedValue(int rank, int value) {
        decisions.put(rank, value);
        agreement = decisions.values().stream().allMatch(v -> v == value);
    }

    public void print() {
        System.out.println(this);
    }

    @Override
    public String toString() {
        return "Summary [\n\t" +
                "> Mensagens:\n\t\t" +
                "- Total: " + totalMessages + "\n\t\t" +
                "- Perdidas: " + lostMessages + "\n\t\t" +
                "- Duplicadas: " + duplicatedMessages + "\n\t" +
                "> Processos:\n\t\t" +
                "- Total: " + totalNodes + "\n\t\t" +
                "- Eventos perdidos: " + brokenEvents + "\n\t" +
                "> Execuçãoss:\n\t\t" +
                "- Média de rounds: " + rounds + "\n\t\t" +
                "- Tempo decorrido: " + timeElapsed + "ms\n\t\t" +
                "- ACDS: " + agreement + "\n\t\t" +
                "- Decisões: " + decisions.values() + "\n\t\t" +
                "]";
    }
}
