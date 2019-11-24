package business;

import java.util.Comparator;

public class ComparatorPacotePorId implements Comparator<Pacote> {
    @Override
    public int compare(Pacote o1, Pacote o2) {
        if(o1.id == o2.id)return 0;
        else if(o1.id > o2.id)return 1;
        else return -1;
    }
}
