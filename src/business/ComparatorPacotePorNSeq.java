package business;

import java.util.Comparator;

public class ComparatorPacotePorNSeq implements Comparator<Pacote> {
    @Override
    public int compare(Pacote o1, Pacote o2) {
        if(o1.nSeq == o2.nSeq)return 0;
        else if(o1.nSeq > o2.nSeq)return 1;
        else return -1;
    }
}
