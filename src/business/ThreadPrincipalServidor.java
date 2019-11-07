package business;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.util.Scanner;

public class ThreadPrincipalServidor extends Thread{

    /**
     * Servidor principal, intermediário entre clientes e as suas threads.
     */
    @Override
    public void run() {
        Scanner tecladoCliente = new Scanner(System.in);
        System.out.println("->Insira a porta:");
        System.out.print("ADMIN@projeto-cc>");

        int porta = Integer.valueOf(tecladoCliente.nextLine());

        int threadsCriadas = 0;

        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(porta);
        } catch (SocketException e) {
            System.err.println("NÃO É POSSÍVEL CRIAR SERVIDOR!");
        }

        ServerState serverState = new ServerState(socket); //  INICIALIZAÇÃO DO SERVIDOR

        System.out.println("Servidor inicializado!");
        Pacote pacote = null;
        FireTuple firetuple;

        //  POVOAMENTO
        serverState.clientes.put("1", new Cliente("1", "1"));

        serverState.runing = true;

        while(serverState.runing){
            try {
                pacote = serverState.agente.receberPacote(0, false);   //  receber pacote
            } catch (IOException e) {
                e.printStackTrace();
            }

            firetuple = new FireTuple();    //  é preciso inicializar aqui o firetuple, pq não queremos que ele seja partilhado

            firetuple.ip = serverState.agente.enderecoRemetente;
            firetuple.porta = serverState.agente.portaRemetente;

            if(pacote != null && pacote.type == 'S' && pacote.ack == 0){
                serverState.lockThreads.lock();    //  lock da estrutura

                if(!serverState.threads.containsKey(firetuple.getKey())){

                    AgenteUDP agenteUDPThread = new AgenteUDP(serverState.agente.socket);
                    agenteUDPThread.enderecoRemetente = firetuple.ip;
                    agenteUDPThread.portaRemetente = firetuple.porta;

                    ThreadServidor novaThread = new ThreadServidor(firetuple,agenteUDPThread, serverState);
                    novaThread.lockPacotes.lock();
                    novaThread.pacotes.add(pacote);
                    novaThread.lockPacotes.unlock();

                    serverState.threads.put(firetuple.getKey(),novaThread);
                    threadsCriadas++;
                    novaThread.start();
                }
                serverState.lockThreads.unlock();  //  unlock da estrutura
            }else{
                if(pacote != null){

                    serverState.lockThreads.lock();
                    if(serverState.threads.containsKey(firetuple.getKey())){
                        ThreadServidor threadServidor = serverState.threads.get(firetuple.getKey());
                        threadServidor.lockPacotes.lock();
                        threadServidor.pacotes.add(pacote);
                        threadServidor.haPacote.signal();
                        threadServidor.lockPacotes.unlock();
                    }else{
                        System.err.println(LocalDateTime.now()+"|==> NÃO EXISTE THREAD PARA ESTE CLIENTE!");
                    }
                    serverState.lockThreads.unlock();
                }
            }
        }
    }
}
