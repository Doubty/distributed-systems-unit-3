package utilitario;

/**
 * Um conjunto de constantes globais usadas para definir o ambiente de simulação
 * muitas constantes eu definir, apenas pq me baseada na implementação em python, mas nem cheguei a usar
 */
public class Globals {
    public static int TIMEOUT = 0;                  // tempo (ms) para espera máxima de mensagem
    public static int CHANNEL_DELAY = 0;            // tempo máximo (ms) necessário para enviar uma mensagem
    public static int MESSAGE_LOST_RATE = 0;        // a taxa de mensagens perdidas
    public static int MESSAGE_DUPLICATION_RATE = 0; // a taxa de mensagens duplicadas
    public static int BROKEN_RATE   = 0;            // a taxa de quebra de um processo (depende de sua velocidade de execução)
    public static int BROKEN_TIME   = 0;            // tempo (ms) para reparar um nó/processo
    public static int MAX_EXE_SPEED = 0;            // definir a velocidade máxima de execução de um processo
    public static int ELECTION_TIMEOUT = 0;         // tempo antes de realizar uma nova eleição
}
