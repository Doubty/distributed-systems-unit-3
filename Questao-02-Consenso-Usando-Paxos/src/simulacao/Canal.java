package simulacao;

import com.sun.istack.internal.NotNull;

import resumo.Resumo;
import utilitario.Debug;
import utilitario.Message;

import static utilitario.Globals.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Canal é responsável pela criação, comunicação e execução dos nós.
 * As mensagens (enviadas através do canal) podem ser perdidas e / ou duplicadas.
 */
public class Canal {
    private final Logger log = Logger.getLogger("Channel");
    private final List<No> nodes = new ArrayList<>();
    public  final Resumo summary  = new Resumo();


    public Canal(@NotNull int... values) {
        int numNodes = values.length;
        summary.totalNodes = numNodes;

        // criando os processos ou nós
        for (int rank = 0; rank < numNodes; ++rank) {
            nodes.add(new No(this, rank, values[rank]));
        }
    }

    /** starts each node */
    public Canal launch() {
        summary.startTime();  // pegando um tempo inicial

        for (No node: nodes)
            node.start();

        return this;
    }

    /** executando o determinado [retorno de chamada] depois que a execução de todos os nós for encerrada */
    public void onTermination(@NotNull Consumer<Canal> callback) {
        for (No node: nodes) {
            try { node.join(); } catch (InterruptedException ignored) { }
        }

        callback.accept(this);
    }

    /** envia uma [mensagem] através do canal de comunicação simulado */
    public void send(@NotNull final No from, int to, @NotNull final Message message) {
        assert to < nodes.size();

        new SenderThread(from, to, message.copy())
                .start();
    }

    /** transmite a determinada [mensagem] */
    public void broadcast(@NotNull final No from, @NotNull final Message message, boolean sendToMe) {
        for (No node: nodes) {
            if (!sendToMe && from.equals(node))
                continue;

            send(from, node.getRank(), message);
        }
    }


    public void broadcast(@NotNull final No from, @NotNull final Message message) {
        broadcast(from, message, false);
    }

 
    private class SenderThread extends Thread {
        private final No from;
        private final int to;
        private final Message message;

        SenderThread(final No from, final int to, final Message message) {
            this.from = from;
            this.to = to;
            this.message = message;
            this.message.setSender(from.getRank());

            Debug.logIf(Debug.MSG_SENDING, String.format("15%d %s", System.currentTimeMillis(), from.getRound()),
                    "Enviando => {%s} de [%d] para [%d]", message, from.getRank(), to);
            logIf(Debug.MSG_SENDING, "Enviando => {%s} de [%d] para [%d]", message, from.getRank(), to);
            summary.totalMessages++;
        }

        @Override
        public void run() {
            final No receiver = nodes.get(to);

           
            if (from.getRank() != receiver.getRank()) {
                if (channelError()) {
                    summary.lostMessages++;
                    logIf(Debug.MSG_LOST, "Perdido => {%s} de [%d] para [%d]", message, from.getRank(), to);
                    Debug.log(String.format("15%d %s", System.currentTimeMillis(), from.getRound()),
                            "Perdido => {%s} de [%d] para [%d]", message, from.getRank(), to);
                    return;
                }

                sendDelay();
            }

            receiver.receive(message);
        }

        private void sendDelay() {
            final Random generator = new Random();
            try { sleep(generator.nextInt(1 + CHANNEL_DELAY)); } catch (InterruptedException ignored) { }
        }
    }
  
    private boolean channelError() {
        final Random generator = new Random();
        int guess = 1 + generator.nextInt(100);

        return guess <= MESSAGE_LOST_RATE;
    }

    private void logIf(boolean flag, final String format, Object...args) {
        if (Debug.CONSOLE_LOG && (flag || Debug.LOG_ALL))
            log.warning(String.format(format, args));
    }
}
