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
* A classe No simula um n� distribu�do.
  *
  * Um n� � capaz de ler, enviar e armazenar mensagens atrav�s do canal.
  * Na execu��o do programa de n�, a rodada l�gica (ou etapa de c�lculo) � representada pelo m�todo avan�ado.
  * Em qualquer etapa de computa��o, o n� pode estar sujeito a uma quebra.
  * Ap�s um per�odo definido [Globals.BROKEN_TIME] de tempo, o n� pode ser reparado.
 */
public class No extends Thread implements Runnable {
    private int rank;                 // identificador �nico
    private int value;                // valor inicial atribu�do ao n�/processo
    private int exeSpeed;             // velocidade de execu��o simulada
    private State stato = candidate;  // o estado do n� a qualquer momento
    private boolean decision = false;
    private final Canal channel;
    private final Logger log;
    private final Queue<Message> messageQueue = new ConcurrentLinkedQueue<>();
    private final Set<Integer> nodesAlive     = new ConcurrentSkipListSet<>();  // manter o controle dos n�s/processos vivos
    //-----------------------------------------------------
    private Round round;  // aqui armazena o round atual das execu��es
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
        deltaTime = currentTime();  // pegar um tempo de execu��o inicial

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
                dlog(Debug.ELECTION_TIMEOUT, round, "[Tempo de Elei��o expirado] " + toString());
                logIf(Debug.ELECTION_TIMEOUT, "Elei��o expirada", toString());

                deltaTime = currentTime();
                stato = candidate;
            }
        }

        logIf(Debug.NODE_STATE, toString());
        dlog(Debug.NODE_STATE, round, "Estado {%s}", this);
    }


    /***
      * A fase do eleitor � dividida em mais 2 fases:
      * - fase 1: leitura de mensagens de coleta, comunicando o [lastRound] e [lastValue];
      * - fase 2: leitura das mensagens de in�cio, aceitando o valor recebido de acordo com [commit]
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
                dlog(Debug.LOG_OLDROUND, round, "[roundAntigo em come�o] %s", msg);
            }
        });
    }


    /**
      * A fase do L�der:
      * - parte 1: coleta da maioria dos valores
      * - parte 2: propor um valor e, em seguida, confirmar sua aceita��o pelos demais
      *
      * A recep��o de mensagens [antigas] fez com que o atual l�der perdesse sua "lideran�a" e se tornasse eleitor.
      * O l�der �, ao mesmo tempo, eleitor. Assim, as mensagens [coletar] e [come�ar] s�o enviadas para ele mesmo.
     */
    private void leaderPhase() {
        round = nextRound();
        channel.summary.updateRound(round);

  
        channel.broadcast(this, new Message(coletado, round), true);
        dlog(round, "[Lider-%d] coleta", rank);

        // espera a maioria das �ltimas mensagens
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

            // considere o valor de [v] associado � maior [round]
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
  
            logIf(Debug.LOG_TIMEOUT, "Tempo expirado: nenhum �ltimo como majorit�rio");
            dlog(Debug.LOG_TIMEOUT, round, "[Lider-%d] Tempo Expirado: nenhum �ltimo como majorit�rio", rank);
            return;
        }

        // -- fase 2
        // -------------------------------------------------
        channel.broadcast(this, new Message(comeco, round, proposedValue), true);
        dlog(round, "[Lider-%d] come�o", rank);

        // espere a maioria das mensagens aceitas
        long accept_timeout = currentTime() + TIMEOUT;
        final Set<Integer> acceptCount = new TreeSet<>();

        while (currentTime() < accept_timeout) {
            voterPhase();

            if (filterMessages(roundAntigo).size() > 0) {
                logIf(Debug.LOG_OLDROUND, "Recebido: roundAntigo em come�o");
                dlog(round, "[Lider-%d] recebido 'roundAntigo'  em come�o", rank);
                stato = voter;
                return;  
            }

            final List<Message> acceptMessages = filterMessages(aceito);
            acceptCount.addAll(Message.uniqueSenders(acceptMessages));

            if (majority(acceptCount.size())) {
                // onde � feito a decis�o em si
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
      * A fase de elei��o:
      * Cada n� (ativo - n�o quebrado) envia uma mensagem para conhecer os participantes.
      * O l�der se tornou o n� com a classifica��o mais baixa (de acordo com os n�s conhecidos por cada um deles).
      *
      * � poss�vel, devido � perda de mensagens, que um ou mais n�s se tornem l�deres.
     */
    
    private void electionPhase() {
        long timeout = currentTime() + TIMEOUT;

        nodesAlive.clear();
        nodesAlive.add(rank);
        dlog(round, "[Candidato-%d] come�o de elei��o", rank);

        // tenta reconhecer os outros processos ou n�s, um broadcast no meu canal
        channel.broadcast(this, new Message(buscado), true);
        int minRank = rank;

        while (currentTime() < timeout) {
            filterMessages(encontrado); // apenas consome mensagens ativas (a classifica��o � obtida ao receb�-las)

            if (advance() == Status.changed)
                return;
        }

     // encontre a classifica��o mais baixa conhecida
        for (Integer id: nodesAlive) {
            if (id < minRank)
                minRank = id;
        }

     // elege o n� conhecido com a classifica��o mais baixa
        stato = (rank == minRank) ? leader : voter;
        dlog(round, "Elei��o terminada {%s}", this);
    }

    /**
      * A fase quebrada:
      * De acordo com [Globals.BROKEN_RATE], um n� pode ser quebrado.
      * Em caso afirmativo, o estado do n� (estado, n�s conhecidos, rodadas e �ltimos valores) � restaurado.
      * O n� reparado come�a novamente como candidato.
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
      * Recep��o de mensagem.
      * As mensagens s�o recebidas apenas se o n� n�o for interrompido e elas s�o armazenadas em uma fila.
     */
    public void receive(@NotNull Message msg) {
        // receba mensagens apenas se n�o estiver quebrado
        if (broken.equals(stato))
            return;

        logIf(Debug.MSG_RECEPTION, "Mensagem recebida: %s", msg);
        dlog(Debug.MSG_RECEPTION, round, "Recep��o => [Processo-%d] de {%s}", rank, msg);

        // atualizar o conjunto de n�s/processos conhecidos
        nodesAlive.add(msg.getSender());

        // enfileirar a mensagem recebida
        messageQueue.add(msg);

        // duplication event
        if (msg.getSender() != rank && duplication()) {
            dlog(Debug.MSG_DUPLICATED, round, "Duplica��o => {%s} de [%d] para [%d]",
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
    //-- Utilit�rios definidos
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

    /** simular o evento de quebra do processo ou n� */
    private boolean canBroke() {
        return BROKEN_RATE >= 1 + generator.nextInt(1000 * Globals.MAX_EXE_SPEED);
    }

    private boolean isElectionTimeoutExpired() {
        return (currentTime() - deltaTime > Globals.ELECTION_TIMEOUT);
    }

    /** simular a duplica��o de um evento ou mensagem */
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

    /** obtenha o valor para a pr�xima rodada de acordo com as rodadas conhecidas */
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
