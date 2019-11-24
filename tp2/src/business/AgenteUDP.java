package business;
import java.io.IOException;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.math.BigInteger; 

public class AgenteUDP {
    DatagramSocket socket;

    InetAddress enderecoRemetente;
    int portaRemetente;

    int id;

    private byte[] buf;
    Lock l = new ReentrantLock();

    public AgenteUDP(DatagramSocket socket){
        id = 0;
        this.socket = socket;
    }

    public AgenteUDP(){
        try {
            this.socket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    static String sha(String input) throws NoSuchAlgorithmException {
        return sha1(sha512(input));
    }

    static String sha1(String input) throws NoSuchAlgorithmException {
        MessageDigest mDigest = MessageDigest.getInstance("SHA1");
        byte[] result = mDigest.digest(input.getBytes());
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < result.length; i++) {
            sb.append(Integer.toString((result[i] & 0xff) + 0x100, 16).substring(1));
        }

         return sb.toString();
    }

    static String sha512(String input) throws NoSuchAlgorithmException {
        String hashtext;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512"); 

            byte[] messageDigest = md.digest(input.getBytes()); 

            BigInteger no = new BigInteger(1, messageDigest); 

            hashtext = no.toString(16); 

            while (hashtext.length() < 32) { 
                hashtext = "0" + hashtext; 
            } 
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e); 
        }
        return hashtext;
    }

    public static boolean verifyChecksum(String verifying, String testChecksum) throws NoSuchAlgorithmException, IOException {
        String str = sha(verifying);
        return str.equals(testChecksum);
    }

    /**
     * Receber um pacote
     * @param tempoEspera tempo que espera
     * @param temTempoEspera se espera pelo pacote
     * @return
     * @throws IOException
     */
    public Pacote receberPacote(long tempoEspera, boolean temTempoEspera) throws IOException {
        if(temTempoEspera)this.socket.setSoTimeout((int)tempoEspera);
        buf = new byte[3000];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);

        this.enderecoRemetente = packet.getAddress();
        this.portaRemetente = packet.getPort();
        Pacote p = null;
        String msgDecode = null;

        try{
            p = Pacote.converterParaPacote(packet.getData());
            msgDecode  = new String(p.data, "UTF-8");
            if(verifyChecksum(msgDecode,p.checkSum)){
                return p;
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Enviar pacote ao servidor.
     * @param pacote pacote a enviar.
     * @return retorna a resposta do servidor.
     * @throws Exception
     */
    //  o id recebido como argumento se for -1 significa que é para usar o id que vem com o pacote recebido como argumento, CC usar a variável de instância id do agente
    public void enviarPacote(Pacote pacote, InetAddress enderecoDestino, int portaDestino) throws IOException,NoSuchAlgorithmException {
        pacote.id = this.id;
        pacote.checkSum = sha(new String(pacote.data, "UTF-8"));
        this.id++;
        buf = pacote.converterParaBytes();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, enderecoDestino, portaDestino);
        socket.send(packet);
    }

    /**
     * Termina a conexão do Cliente com o servidor
     */
    public void terminarConexao() {
        socket.close();
    }

    @Override
    public String toString() {
        return "AgenteUDP{" +
                "socket=" + socket +
                ", enderecoRemetente=" + enderecoRemetente +
                ", portaRemetente=" + portaRemetente +
                ", id=" + id +
                ", buf=" + Arrays.toString(buf) +
                ", l=" + l +
                '}';
    }
}