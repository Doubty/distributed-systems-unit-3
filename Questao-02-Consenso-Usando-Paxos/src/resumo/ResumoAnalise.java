package resumo;

import com.sun.istack.internal.NotNull;

import simulacao.Canal;
import utilitario.Debug;

/**
 *  Aqui é onde vou calcular um conjunto de estatísticas relacionadas a um conjunto de execuções
 */
public class ResumoAnalise extends Resumo {
    private int[] initialValues;
    private int executionCount;
    // informações relacionadas as análises do resumo
    private int agreements;
    private float avgBreaking;
    private int minRounds = Integer.MAX_VALUE;
    private int maxRounds = Integer.MIN_VALUE;
    private int avgRounds = 0;
    private int minMessages = Integer.MAX_VALUE;
    private int maxMessages = Integer.MIN_VALUE;


    public ResumoAnalise(int executions, @NotNull int...initialValues) {
        assert executions > 0;
        assert initialValues.length > 0;

        this.initialValues  = initialValues;
        this.executionCount = executions;
    }

    /** simulando dos processos rodando */
    public ResumoAnalise calculate() {
        print("Rodando %d processos...", executionCount);

        for (int i = 0; i < executionCount; ++i) {
            final Canal channel = new Canal(initialValues)
                    .launch();

            Debug.log(String.format("15%d - Processo %d", System.currentTimeMillis(), i + 1),
                    "---------------------------------------------------------------");

            channel.onTermination(ch -> {
                final Resumo summary = ch.summary;
                summary.finishTime();

                totalMessages += summary.totalMessages;
                lostMessages  += summary.lostMessages;
                duplicatedMessages += summary.duplicatedMessages;
                minMessages = Integer.min(minMessages, summary.totalMessages);
                maxMessages = Integer.max(maxMessages, summary.totalMessages);
                brokenEvents += summary.brokenEvents;
                avgRounds += summary.rounds;
                minRounds = Integer.min(minRounds, summary.rounds);
                maxRounds = Integer.max(maxRounds, summary.rounds);
                timeElapsed   += summary.timeElapsed;
                agreements    += summary.agreement ? 1 : 0;
            });

            print("> Processo %d/%d completado", i + 1, executionCount);
        }

        totalMessages = Math.floorDiv(totalMessages, executionCount);
        lostMessages  = Math.floorDiv(lostMessages, executionCount);
        timeElapsed   = Math.floorDiv(timeElapsed, executionCount);
        duplicatedMessages = Math.floorDiv(duplicatedMessages, executionCount);
        avgRounds   = Math.floorDiv(avgRounds, executionCount);
        totalNodes  = initialValues.length;
        avgBreaking = brokenEvents / (float) executionCount;

        return this;
    }

  
    private String percentage(float x, float y) {
        if (x == 0)
            return "0%";

        final String s = String.format("%.2f", x / y * 100f) + "%";
        final String[] split = s.split(",");

        if ("00%".equals(split[1]))
            return split[0] + "%";

        return s.replace(",", ".");
    }

    private void print(String format, Object...args) {
        System.out.println(String.format(format, args));
    }

    @Override
    public String toString() {
        return "\nResumo [\n\t" +
                "> Mensagens:\n\t\t" +
                "- Total: [mínimo: " + minMessages + ", média: " + totalMessages + ", máximo: " + maxMessages + "]\n\t\t" +
                "- Média perdidas: " + percentage(lostMessages, totalMessages) + " (" + lostMessages + ")\n\t\t" +
                "- Média duplicadas: " + percentage(duplicatedMessages, totalMessages) + " (" + duplicatedMessages + ")\n\t" +
                "> Processos:\n\t\t" +
                "- Total: " + totalNodes + "\n\t\t" +
                "- Quebra por round: " + percentage(avgBreaking, totalNodes) + " (" + Math.round(avgBreaking)+ ")\n\t" +
                "> Execuções:\n\t\t" +
                "- Contador: " + executionCount + "\n\t\t" +
                "- Rounds: [min: " + minRounds + ", média: " + avgRounds + ", máximo: " + maxRounds + "]\n\t\t" +
                "- Média de tempo: " + timeElapsed + "ms\n\t\t" +
                "]";
    }
}
