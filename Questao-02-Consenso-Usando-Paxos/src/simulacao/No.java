package simulacao;

import com.sun.istack.internal.NotNull;

import utilitario.Debug;
import utilitario.Globals;
import utilitario.Message;
import utilitario.Round;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Logger;

import static simulacao.No.State.*;
import static utilitario.Globals.*;
import static utilitario.Message.Type.*;

/**
* A classe No simula um nó distribuído.
  *
  * Um nó é capaz de ler, enviar e armazenar mensagens através do canal.
  * Na execução do programa de nó, a rodada lógica (ou etapa de cálculo) é representada pelo método avançado.
  * Em qualquer etapa de computação, o nó pode estar sujeito a uma quebra.
  * Após um período definido [Globals.BROKEN_TIME] de tempo, o nó pode ser reparado.
 */
public class No extends Thread implements Runnable {
    private int rank;                 // identificador único
    private int value;                // valor inicial atribuído ao nó/processo
    private int exeSpeed;             // velocidade de execução simulada
    private State stato = candidate;  // o estado do nó a qualquer momento
    private boolean decision = false;
    private final Canal channel;
    private final Logger log;
    private final Queue<Message> messageQueue = new ConcurrentLinkedQueue<>();
    private final Set<Integer> nodesAlive     = new ConcurrentSkipListSet<>();  // manter o controle dos nós/processos vivos
    //-----------------------------------------------------
    private Round round;  // aqui armazena o round atual das execuções
    private Round commit;
    private Round lastRound;
    private int lastValue;
    private int proposedValue;
    private long deltaTime = 0;
    //-----------------------------------------------------

 
    No(@NotNull final Canal channel, int rank, int v) {
        super("Processo-" + rank);
        this.log = Logger.getLogger("Processo [" + rank + "]");
        this.rank = rank;

        this.value = v;
        this.lastValue = v;
        this.proposedValue = v;

        this.round  = new Round(0, rank);
        this.commit = this.round.copy();
        this.lastRound = this.round.copy();

        this.channel  = channel;
        this.exeSpeed = 1 + generator.nextInt(MAX_EXE_SPEED);
    }

    @Override
    public void run() {
        deltaTime = currentTime();  // pegar um tempo de execução inicial

        while (!decision) {
            switch (stato) {
                case voter:
                    voterPhase();
                    advance();
                    break;

                case leader:
                    leaderPhase();
                    break;

                case broken:
                    brokenPhase();
                    break;

                case candidate:
                    electionPhase();
                    break;
            }

            if (isElectionTimeoutExpired()) {
                dlog(Debug.ELECTION_TIMEOUT, round, "[Tempo de Eleição expirado] " + toString());
                logIf(Debug.ELECTION_TIMEOUT, "Eleição expirada", toString());

                deltaTime = currentTime();
                stato = candidate;
            }
        }

        logIf(Debug.NODE_STATE, toString());
        dlog(Debug.NODE_STATE, round, "Estado {%s}", this);
    }


    /***
      * A fase do eleitor é dividida em mais 2 fases:
      * - fase 1: leitura de mensagens de coleta, comunicando o [lastRound] e [lastValue];
      * - fase 2: leitura das mensagens de início, aceitando o valor recebido de acordo com [commit]
     */
    private void voterPhase() {
        // consumindo mensagens de coleta
        filterMessages(coletado).forEach(msg -> {
            final Round r = msg.getR1();
            final int sender = msg.getSender();

            if (r.greaterEqual(commit)) {
                channel.send(this, sender,
                        new Message(ultimo, r.copy(), lastRound.copy(), lastValue)
                );

                commit = r.copy();
                channel.summary.updateRound(commit);
            } else {
                channel.send(this, sender, new Message(roundAntigo, r.copy(), commit.copy()));
                dlog(Debug.LOG_OLDROUND, round, "[roundAntigo em coleta] %s", msg);
            }
        });

        // consumindo mensagens iniciais
        filterMessages(comeco).forEach(msg -> {
            final Round r = msg.getR1();
            final int v   = msg.getValue();
            final int sender = msg.getSender();

            if (r.greaterEqual(commit)) {
                channel.send(this, sender, new Message(aceito, round));
                channel.summary.updateRound(r);

                lastRound = r.copy();
                lastValue = v;
            } else {
                channel.send(this, sender, new Message(roundAntigo, r.copy(), commit.copy()));
                dlog(Debug.LOG_OLDROUND, round, "[roundAntigo em começo] %s", msg);
            }
        });
    }


    /**
      * A fase do Líder:
      * - parte 1: coleta da maioria dos valores
      * - parte 2: propor um valor e, em seguida, confirmar sua aceitação pelos demais
      *
      * A recepção de mensagens [antigas] fez com que o atual líder perdesse sua "liderança" e se tornasse eleitor.
      * O líder é, ao mesmo tempo, eleitor. Assim, as mensagens [coletar] e [começar] são enviadas para ele mesmo.
     */
    private void leaderPhase() {
        round = nextRound();
        channel.summary.updateRound(round);

  
        channel.broadcast(this, new Message(coletado, round), true);
        dlog(round, "[Lider-%d] coleta", rank);

        // espera a maioria das últimas mensagens
        long last_timeout = currentTime() + TIMEOUT;
        final Set<Integer> lastCount = new TreeSet<>();
        boolean last_majority = false;

        while (currentTime() < last_timeout) {
            voterPhase();

            if (filterMessages(roundAntigo).size() > 0) {
                logIf(Debug.LOG_OLDROUND, "Recebido: roundAntigo em coleta");
                dlog(round, "[Lider-%d] recebeu 'roundAntigo' em coleta", rank);
                stato = voter;
                return; 
            }

            final List<Message> lastMessages = filterMessages(ultimo);
            lastCount.addAll(Message.uniqueSenders(lastMessages));

            // considere o valor de [v] associado à maior [round]
            for (Message msg: lastMessages) {
                final Round r = msg.getR1();

                if (r.greaterEqual(lastRound)) {
                    lastRound = r.copy();
                    proposedValue = msg.getValue();
                }
            }

            if (majority(lastCount.size())) {
                last_majority = true;
                break;
            }

            if (advance() == Status.changed)
                return;
        }

        if (!last_majority) {
  
            logIf(Debug.LOG_TIMEOUT, "Tempo expirado: nenhum último como majoritário");
            dlog(Debug.LOG_TIMEOUT, round, "[Lider-%d] Tempo Expirado: nenhum último como majoritário", rank);
            return;
        }

        // -- fase 2
        // -------------------------------------------------
        channel.broadcast(this, new Message(comeco, round, proposedValue), true);
        dlog(round, "[Lider-%d] começo", rank);

        // espere a maioria das mensagens aceitas
        long accept_timeout = currentTime() + TIMEOUT;
        final Set<Integer> acceptCount = new TreeSet<>();

        while (currentTime() < accept_timeout) {
            voterPhase();

            if (filterMessages(roundAntigo).size() > 0) {
                logIf(Debug.LOG_OLDROUND, "Recebido: roundAntigo em começo");
                dlog(round, "[Lider-%d] recebido 'roundAntigo'  em começo", rank);
                stato = voter;
                return;  
            }

            final List<Message> acceptMessages = filterMessages(aceito);
            acceptCount.addAll(Message.uniqueSenders(acceptMessages));

            if (majority(acceptCount.size())) {
                // onde é feito a decisão em si
                decision = true;
                value = proposedValue;
                channel.summary.decidedValue(rank, value);
                channel.broadcast(this, new Message(sucesso, value));
                dlog(round, "[Lider-%d] 'sucesso ao escolher lider' => %d", rank, value);
                return;  // terminado
            }

            if (advance() == Status.changed)
                return;
        }

        logIf(Debug.LOG_TIMEOUT, String.format("15%d %s", currentTime(), round),
                "Tempo expirado: nenhum lider escolhido");
        dlog(Debug.LOG_TIMEOUT, round,
                "[Lider-%d] tempo expirado: nenhum lider escolhido", rank);
    }


    /**
      * A fase de eleição:
      * Cada nó (ativo - não quebrado) envia uma mensagem para conhecer os participantes.
      * O líder se tornou o nó com a classificação mais baixa (de acordo com os nós conhecidos por cada um deles).
      *
      * É possível, devido à perda de mensagens, que um ou mais nós se tornem líderes.
     */
    
    private void electionPhase() {
        long timeout = currentTime() + TIMEOUT;

        nodesAlive.clear();
        nodesAlive.add(rank);
        dlog(round, "[Candidato-%d] começo de eleição", rank);

        // tenta reconhecer os outros processos ou nós, um broadcast no meu canal
        channel.broadcast(this, new Message(buscado), true);
        int minRank = rank;

        while (currentTime() < timeout) {
            filterMessages(encontrado); // apenas consome mensagens ativas (a classificação é obtida ao recebê-las)

            if (advance() == Status.changed)
                return;
        }

     // encontre a classificação mais baixa conhecida
        for (Integer id: nodesAlive) {
            if (id < minRank)
                minRank = id;
        }

     // elege o nó conhecido com a classificação mais baixa
        stato = (rank == minRank) ? leader : voter;
        dlog(round, "Eleição terminada {%s}", this);
    }

    /**
      * A fase quebrada:
      * De acordo com [Globals.BROKEN_RATE], um nó pode ser quebrado.
      * Em caso afirmativo, o estado do nó (estado, nós conhecidos, rodadas e últimos valores) é restaurado.
      * O nó reparado começa novamente como candidato.
     */
    private void brokenPhase() {
        long broken_wait = currentTime() + BROKEN_TIME;

        while (currentTime() < broken_wait)
            delay();

        messageQueue.clear();
        nodesAlive.clear();
        stato = candidate;

        dlog(Debug.NODE_REPAIRED, round, "Reparado de falha [Node-%d]", rank);
        logIf(Debug.NODE_REPAIRED, "Reparado [Node-%d]", rank);

        lastValue     = value;
        proposedValue = value;
        round  = new Round(0, rank);
        commit = round.copy();
        lastRound = round.copy();
    }

    /**
      * Recepção de mensagem.
      * As mensagens são recebidas apenas se o nó não for interrompido e elas são armazenadas em uma fila.
     */
    public void receive(@NotNull Message msg) {
        // receba mensagens apenas se não estiver quebrado
        if (broken.equals(stato))
            return;

        logIf(Debug.MSG_RECEPTION, "Mensagem recebida: %s", msg);
        dlog(Debug.MSG_RECEPTION, round, "Recepção => [Processo-%d] de {%s}", rank, msg);

        // atualizar o conjunto de nós/processos conhecidos
        nodesAlive.add(msg.getSender());

        // enfileirar a mensagem recebida
        messageQueue.add(msg);

        // duplication event
        if (msg.getSender() != rank && duplication()) {
            dlog(Debug.MSG_DUPLICATED, round, "Duplicação => {%s} de [%d] para [%d]",
                    msg, msg.getSender(), rank
            );

            channel.summary.duplicatedMessages++;
            messageQueue.add(msg);
        }
    }



    private Status advance() {
        delay();

        if (canBroke()) {
            dlog(round, "Falhado {%s}", this);
            channel.summary.brokenEvents++;
            stato = broken;
            return Status.changed;
        }
       
        //tratamento dos casos das mensagens enviadas
     
        final List<Message> successMessages = filterMessages(sucesso);

     
        for (Message msg: filterMessages(buscado)) {
            channel.send(this, msg.getSender(), new Message(encontrado));
        }

      
        if (successMessages.size() > 0) {
            int valueDecided = successMessages.get(0).getValue();
            decision = true;
            value = valueDecided;
            channel.summary.decidedValue(rank, value);

            logIf(Debug.NODE_DECISION, "Decidido %d", value);
            dlog(round, "[Processo-%d-%s] Decidido %d", rank, stato, value);

            channel.broadcast(this, new Message(sucesso, value));

            return Status.changed;
        }

        logIf(Debug.NODE_STATE, this.toString());
        dlog(Debug.NODE_STATE, round, toString());

        return Status.alive;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        No node = (No) o;

        return rank == node.rank;
    }

    public int getRank() {

        return rank;
    }

    public int getValue() {
        return value;
    }

    public Round getRound() {
        return round;
    }

  
    enum State {
        leader,
        voter,
        broken,
        candidate,
    }


    enum Status {
        alive,
        changed,
    }

    @Override
    public String toString() {
        return String.format("Processo-%d [%s, round: %s, commit: %s, valor: %d, processosConhecidos: %s]",
                rank, stato, round, commit, proposedValue, nodesAlive);
    }

    //------------------------------------------------------------------------------------------------------------------
    //-- Utilitários definidos
    //------------------------------------------------------------------------------------------------------------------

    private void delay() {
        try { sleep(exeSpeed); } catch (InterruptedException ignored) { }
    }

    private long currentTime() {
        return java.lang.System.currentTimeMillis();
    }

    private boolean majority(int amount) {
        return (amount >= (nodesAlive.size() + 1) / 2);
    }

    /** simular o evento de quebra do processo ou nó */
    private boolean canBroke() {
        return BROKEN_RATE >= 1 + generator.nextInt(1000 * Globals.MAX_EXE_SPEED);
    }

    private boolean isElectionTimeoutExpired() {
        return (currentTime() - deltaTime > Globals.ELECTION_TIMEOUT);
    }

    /** simular a duplicação de um evento ou mensagem */
    private boolean duplication() {
        int guess = 1 + generator.nextInt(100);
        return guess <= MESSAGE_DUPLICATION_RATE;
    }

    /** obter uma lista de mensagens de acordo com o tipo fornecido */
    private List<Message> filterMessages(Message.Type type) {
        List<Message> selected = new ArrayList<>();

        for (Message message: messageQueue) {
            if (type.equals(message.getType())) {
                selected.add(message);
                messageQueue.remove(message);
            }
        }

        return selected;
    }

    /** obtenha o valor para a próxima rodada de acordo com as rodadas conhecidas */
    private Round nextRound() {
        if (lastRound.greaterEqual(round))
            return new Round(lastRound.getCount() + 1, this.rank);
        else
            return round.increase();
    }

    //------------------------------------------------------------------------------------------------------------------
    // -- O log
    //------------------------------------------------------------------------------------------------------------------
    private void log(final String format, Object...args) {
        if (Debug.CONSOLE_LOG)
            log.warning("[" + rank + "] " + String.format(format, args));
    }

    private void logIf(boolean flag, final String format, Object...args) {
        if (flag || Debug.LOG_ALL)
            log(format, args);
    }

  
    private void dlog(final String key, final String format, Object...args) {
        Debug.log(key, format, args);
    }

    private void dlog(final Round round, final String format, Object...args) {
        final String key = String.format("15%d %s", currentTime(), round);
        Debug.log(key, format, args);
    }

    private void dlog(boolean flag, final String key, final String format, Object...args) {
        Debug.logIf(flag, key, format, args);
    }

    private void dlog(boolean flag, final Round round, final String format, Object...args) {
        final String key = String.format("15%d %s", currentTime(), round);
        Debug.logIf(flag, key, format, args);
    }

    //------------------------------------------------------------------------------------------------------------------
    // -- Constantes
    //------------------------------------------------------------------------------------------------------------------
    private static final Random generator = new Random();
}
