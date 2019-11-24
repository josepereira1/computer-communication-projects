package business;

import java.io.*;

public class Pacote implements Serializable{

    int id;
    char type;
    int ack;
    int nSeq;
    long tempo;
    String checkSum;
    byte[] data;

    public Pacote(int id, char type, int ack, int nSeq, long tempo, String checkSum, byte[] data) {
        this.id = id;
        this.type = type;
        this.ack = ack;
        this.nSeq = nSeq;
        this.tempo = tempo;
        this.checkSum = checkSum;
        this.data = data;
    }

    /**
     * Converte o pacote para array de bytes.
     * @return retorna o pacote num array de bytes.
     * @throws IOException
     */
    public byte[] converterParaBytes() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(this);
        oos.flush();
        return bos.toByteArray();
    }

    /**
     * Converte um array de bytes, num pacote.
     * @param buf array de bytes
     * @return retorna um array de bytes.
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static Pacote converterParaPacote(byte[] buf) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(buf);
        ObjectInputStream oos = new ObjectInputStream(bis);
        Pacote pacote= (Pacote) oos.readObject();
        oos.close();
        return pacote;
    }

    /**
     * Apenas para debug.
     * @return
     */
    public String parse(){
        return new StringBuilder().append("ID=").append(id).append(";TYPE=").append(type).append(";ACK").append(ack).append(";nSEQ=").append(nSeq).append(";CHECKSUM=").append(checkSum).append(";DATA=").append(new String(this.data)).toString();
    }

    @Override
    public String toString() {
        return "Pacote{" +
                "id=" + id +
                ", type=" + type +
                ", ack=" + ack +
                ", nSeq=" + nSeq +
                ", tempo=" + tempo +
                ", checkSum=" + checkSum +
                ", data='" + data + '\'' +
                '}';
    }
}







