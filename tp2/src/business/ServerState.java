package business;

import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ServerState {
    Map<String, Cliente> clientes;
    Map<String, ThreadServidor> threads;
    Lock lockThreads;
    Lock lockClientes;
    AgenteUDP agente;
    boolean runing;

    int conexoes = 0;
    int naoConexoes = 0;

    Lock lockConexoes = new ReentrantLock();
    Lock lockNaoConexoes = new ReentrantLock();

    public ServerState(DatagramSocket socket){
        clientes = new HashMap<>();
        threads = new HashMap<>();
        lockThreads = new ReentrantLock();
        lockClientes = new ReentrantLock();
        agente = new AgenteUDP(socket);
        runing = false;
    }

    @Override
    public String toString() {
        return "ServerState{" +
                "clientes=" + clientes +
                ", threads=" + threads +
                ", lockThreads=" + lockThreads +
                ", lockClientes=" + lockClientes +
                ", agente=" + agente +
                ", runing=" + runing +
                '}';
    }
}
