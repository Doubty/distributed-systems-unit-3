package utilitario;

import com.sun.istack.internal.NotNull;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Mensagens enviadas entre processos ou os nós
 */
public class Message {
    private Type type;
    private Round r1;
    private Round r2;
    private int value  = Integer.MIN_VALUE;
    private int sender = Integer.MIN_VALUE;


    public Message(@NotNull Type type) {
        this.type = type;
    }

    public Message(Type type, @NotNull Round r) {
        this(type);
        this.r1 = r;
    }


    public Message(@NotNull Type type, int value) {
        this.type  = type;
        this.value = value;
    }

    public Message(Type type, Round r, int value) {
        this(type, r);
        this.value = value;
    }

 
    public Message(Type type, Round r1, @NotNull Round r2) {
        this(type, r1);
        this.r2 = r2;
    }

  
    public Message(Type type, Round r1, Round r2, int value) {
        this(type, r1, r2);
        this.value = value;
    }

    public Type getType() {
        return type;
    }

    public Round getR1() {
        if (r1 == null)
            return Round.empty();

        return r1;

    }

    public Round getR2() {
        if (r2 == null)
            return Round.empty();

        return r2;
    }

    public int getValue() {
        return value;
    }

    public int getSender() { return sender; }

    public void setSender(int sender) { this.sender = sender; }

    public Message copy() {
        Message m = new Message(type);
        m.r1 = (r1 == null) ? Round.empty() : r1.copy();
        m.r2 = (r2 == null) ? Round.empty() : r2.copy();
        m.value  = value;
        m.sender = sender;
        return m;
    }

    /**
     * Retorna um conjunto de identificadores exclusivos de remetentes (classificações)
     */
    public static Set<Integer> uniqueSenders(@NotNull List<Message> messages){
        final Set<Integer> senders = new TreeSet<>();

        // apenas pega o id dos remetentes e adicione-o ao conjunto
        messages.stream().mapToInt(Message::getSender).forEach(senders::add);

        return senders;
    }

    public enum Type {
        coletado,
        sucesso,
        ultimo,
        roundAntigo,
        aceito,
        comeco,
        buscado,
        encontrado,
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Message message = (Message) o;

        if (value != message.value) return false;
        if (type != message.type) return false;
        if (r1 != null ? !r1.equals(message.r1) : message.r1 != null) return false;
        return r2 != null ? r2.equals(message.r2) : message.r2 == null;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (r1 != null ? r1.hashCode() : 0);
        result = 31 * result + (r2 != null ? r2.hashCode() : 0);
        result = 31 * result + value;
        return result;
    }

    @Override
    public String toString() {
        switch (type) {
            case encontrado:
            case buscado:
                return String.format("Mensagem [%s, Remetente: %d]",
                        type, sender);
            case ultimo:
                return String.format("Mensagem [ultimo, round: %s, ultimoRound: %s, valorAntigo: %s, Remetente: %d]",
                        r1, r2, value, sender);
            case coletado:
                return String.format("Mensagem [coleção, round: %s, Remetente: %d]",
                        r1, sender);
            case aceito:
                return String.format("Mensagem [aceito, round: %s, Remetente: %d]",
                        r1, sender);
            case comeco:
                return String.format("Mensagem [começo, round: %s, valorProposto: %d, Remetente: %d]",
                        r1, value, sender);
            case sucesso:
                return String.format("Mensagem [sucesso, valor: %d, Remetente: %d]",
                        value, sender);
            case roundAntigo:
                return String.format("Mensagem [valorAntigo, round: %s, commitado: %s, Remetente: %d]",
                        r1, r2, sender);
        }

        return String.format("Mensagem [tipo: %s, valor: %d, Remetente: %d]",
                type, value, sender);
    }
}