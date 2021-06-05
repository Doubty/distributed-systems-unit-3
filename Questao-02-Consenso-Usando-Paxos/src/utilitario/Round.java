package utilitario;

/**
 * Objeto definido como um par (contagem, identificador).
 * Este tipo de definição permite uma relação de ordenação total entre as rodadas
 */

public class Round implements Comparable<Round> {
    private int count;
    private int id;
    private static final Round DEFAULT = new Round(Integer.MIN_VALUE, Integer.MIN_VALUE);

    public Round(int count, int id) {
        this.count = count;
        this.id = id;
    }

    /** aumentar em 1 o campo [count] */
    public Round increase() {
        this.count++;
        return this;
    }

    public int getCount() {
        return count;
    }

    public int getId() {
        return id;
    }

    /** verifique se a rodada não foi inicializada */
    public boolean isEmpty() {
        return this.equals(DEFAULT);
    }

    /** apenas uma rodada padrão */
    public static Round empty() {
        return DEFAULT;
    }

    @Override
    public int compareTo(Round b) {
        if (b == null)
            return -1;

        if (count < b.count)
            return -1;

        if (count > b.count)
            return 1;

        if (count == b.count)
            if (id < b.id)
                return -1;
            else if (id > b.id)
                return 1;

        return 0;
    }

    public boolean greaterEqual(Round b) {
        return b == null || count > b.count || count == b.count && id >= b.id;
    }

    public Round copy() {
        return new Round(count, id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Round round = (Round) o;
        return count == round.count && id == round.id;
    }

    @Override
    public int hashCode() {
        int result = count;
        result = 31 * result + id;
        return result;
    }

    @Override
    public String toString() {
        if (isEmpty())
            return "(NaN, NaN)";

        return String.format("(%d, %d)", count, id);
    }
}
