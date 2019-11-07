package business;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class ThreadServidor extends Thread {

    final static int TEMPOADICIONAL = 1300; //  1300

    String diretoriaDoServidor = "files/";
    FireTuple fireTuple;
    AgenteUDP agente;
    ServerState serverState;
    Pacote pacote;
    TreeSet<Pacote> pacotes;
    Lock lockPacotes = new ReentrantLock();
    Condition haPacote = lockPacotes.newCondition();
    Lock lockThread = new ReentrantLock();
    Cliente cliente;
    boolean runing = true;
    int mtu;

    public ThreadServidor(FireTuple fireTuple, AgenteUDP agente, ServerState serverState){
        super();
        this.cliente = new Cliente();
        this.fireTuple = fireTuple;
        this.agente = agente;
        this.serverState = serverState;
        this.pacotes = new TreeSet<>(new ComparatorPacotePorId());
        this.mtu = 1024;    //  1300
    }

    /**
     * Thread que processa os pacotes de um determinado cliente.
     */
    public void run(){
        while (runing){

            System.out.println("|====> THREAD_CLIENTE["+fireTuple+"] - THREAD ACORDOU!!!");

            this.lockPacotes.lock();
            this.pacote = this.pacotes.first(); //  BUSCAR O PRÓXIMO PACOTE
            this.pacotes.remove(this.pacote);   //  REMOVER DO BUFFER
            this.lockPacotes.unlock();

            if(this.pacote != null) {

                System.out.println("|====> THREAD_CLIENTE["+fireTuple+"] - PACOTE_RECEBIDO = "+pacote.parse());

                switch (this.pacote.type) {
                    case 'S':   //  SINCRONIZAÇÃO, TESTE DE CONEXÃO
                        sincronizacao();
                        break;
                    case 'A':   //  AUTENTICAÇÃO
                        autenticar();
                        break;
                    case 'R':   //  REGISTO
                        registar();
                        break;
                    case 'L':   //  LISTAR FICHEIROS
                        if (this.cliente.autenticado)listFiles();
                        break;
                    case 'T':  //   TRANSFERIR FICHEIROS
                        if (this.cliente.autenticado) transferirFicheiro();
                        break;
                    case 'X':
                        terminarConexao();
                        break;
                    default:
                        System.err.println("|====> THREAD_CLIENTE[" + fireTuple + "] - PACOTE_DESCARTADO="+this.pacote.parse());
                        break;
                }
            }
            //  ADORMECER A THREAD CASO NÃO HAJA PACOTES PARA PROCESSAR, OU SEJA, SIZE == 0
            threadSleep();
        }
    }

    /**
     * Estabelecimento de conexão, ou seja, teste de conexão do lado do servidor.
     */
    public void sincronizacao(){
        Pacote tmpPacoteEnviar;
        int tentativas = 4;

        if (this.pacote.ack == 0) {
            tmpPacoteEnviar = new Pacote(0,'S', 0, -1, -1, "", "".getBytes());

            this.pacote = null;

            do{
                try {
                    this.agente.enviarPacote(tmpPacoteEnviar, fireTuple.ip, fireTuple.porta);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                System.out.println("|====> THREAD_CLIENTE[" + fireTuple + "] - PACOTE_ENVIADO = " + tmpPacoteEnviar);
                System.out.println("|====> THREAD_CLIENTE[" + fireTuple + "] - THREAD À ESPERA DE RESPOSTA (DORMIR)!");

                this.lockPacotes.lock();
                try {
                    this.haPacote.await(1500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }finally {
                    this.lockPacotes.unlock();
                }

                this.lockPacotes.lock();
                if(this.pacotes.size() != 0){
                    this.pacote = this.pacotes.first();
                    this.cliente.tempoFinal = System.currentTimeMillis();
                    this.cliente.calcularTempo();
                    this.pacotes.remove(this.pacote);
                    tentativas = 0;
                }else{
                    tentativas--;
                }
                this.lockPacotes.unlock();

            }while (tentativas > 0);

            if (this.pacote != null && this.pacote.ack == 1) {
                System.out.println("|====> THREAD_CLIENTE[" + fireTuple + "] - SUCESSO NO TESTE DE CONEXÃO!");
                serverState.lockConexoes.lock();
                serverState.conexoes = serverState.conexoes+1;
                serverState.lockConexoes.unlock();

            } else { //  MATAR A THREAD E REMOVER DO MAP!!!!
                try {
                    tmpPacoteEnviar = new Pacote(0,'E', -1, -1, -1, "", "".getBytes());
                    this.agente.enviarPacote( tmpPacoteEnviar, fireTuple.ip, fireTuple.porta);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                serverState.lockNaoConexoes.lock();
                serverState.naoConexoes = serverState.naoConexoes +1;
                serverState.lockNaoConexoes.unlock();
                terminarConexao();
            }
        }
    }

    /**
     * Autenticação do cliente lado do servidor.
     */
    public void autenticar(){
        String[] credenciais = new String(this.pacote.data).split(";");
        String username, password;
        Pacote tmpPacoteEnviar;

        username = credenciais[0];
        password = credenciais[1];

        if (this.cliente.autenticado){
            tmpPacoteEnviar = new Pacote(0,'A',3, 0,0,"","".getBytes());
            try {
                agente.enviarPacote(tmpPacoteEnviar, fireTuple.ip, fireTuple.porta);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else {

            this.serverState.lockClientes.lock();

            if (this.serverState.clientes.containsKey(username)) {
                Cliente cliente = this.serverState.clientes.get(username);

                if (cliente.password.equals(password) && !cliente.autenticado) {
                    try {
                        tmpPacoteEnviar = new Pacote(0, 'A', 0, -1, -1, "", "".getBytes());
                        System.out.println("|====> THREAD_CLIENTE[" + fireTuple + "] - PACOTE_ENVIADO = " + tmpPacoteEnviar);
                        agente.enviarPacote(tmpPacoteEnviar, fireTuple.ip, fireTuple.porta);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    cliente.autenticado = true;
                    this.cliente = cliente;
                    System.out.println("|====> THREAD_CLIENTE[" + fireTuple + "] - AUTENTICADO COM SUCESSO!");
                } else {
                    try {
                        tmpPacoteEnviar = new Pacote(0, 'A', 1, -1, -1, "", "".getBytes());
                        System.out.println("|====> THREAD_CLIENTE[" + fireTuple + "] - PACOTE_ENVIADO = " + tmpPacoteEnviar);
                        this.agente.enviarPacote(tmpPacoteEnviar, fireTuple.ip, fireTuple.porta);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    System.err.println("|====> THREAD_CLIENTE[" + fireTuple + "] - AUTENTICAÇÃO FALHOU - PASSWORD ERRADA!");
                }
            } else {
                try {
                    tmpPacoteEnviar = new Pacote(0 , 'A', 2, -1, -1, "", "".getBytes());
                    System.out.println("|====> THREAD_CLIENTE[" + fireTuple + "] - PACOTE_ENVIADO = " + tmpPacoteEnviar);
                    this.agente.enviarPacote(tmpPacoteEnviar, fireTuple.ip, fireTuple.porta);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.err.println("|====> THREAD_CLIENTE[" + fireTuple + "] - AUTENTICAÇÃO FALHOU - USERNAME NÃO EXISTE!");
            }
            this.serverState.lockClientes.unlock();
        }
    }

    /**
     * Registo do cliente do lado do servidor.
     */
    public void registar(){
        String[] credenciais = new String(this.pacote.data).split(";");
        String username,password;
        Pacote tmpPacoteEnviar;
        username = credenciais[0];
        password = credenciais[1];

        this.serverState.lockClientes.lock();
        if (this.serverState.clientes.containsKey(username)) {
            try {
                tmpPacoteEnviar = new Pacote(0,'A', 1, -1, -1, "", "".getBytes());
                System.out.println("|====> THREAD_CLIENTE[" + fireTuple + "] - PACOTE_ENVIADO = " + tmpPacoteEnviar);
                agente.enviarPacote(tmpPacoteEnviar, fireTuple.ip, fireTuple.porta);
                System.err.println("|====> THREAD_CLIENTE[" + fireTuple + "] - USERNAME OCUPADO!");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            this.cliente.username = username;
            this.cliente.password = password;
            this.serverState.clientes.put(username, this.cliente);
            try {
                tmpPacoteEnviar = new Pacote(0,'A', 0, -1, -1, "", "".getBytes());
                System.out.println("|====> THREAD_CLIENTE[" + fireTuple + "] - PACOTE_ENVIADO = " + tmpPacoteEnviar);
                agente.enviarPacote(tmpPacoteEnviar, fireTuple.ip, fireTuple.porta);
                System.out.println("|====> THREAD_CLIENTE[" + fireTuple + "] - REGISTADO COM SUCESSO!");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        this.serverState.lockClientes.unlock();
    }

    /**
     * Listagem dos ficheiros do servidor.
     */
    public void listFiles(){
        List<String> ficheiros = getFileList();
        Pacote tmpPacoteEnviar;
        StringBuilder fileNames = new StringBuilder();

        for(String f : ficheiros) fileNames.append(f).append(";");

        try {
            tmpPacoteEnviar = new Pacote(0,'L', 0, -1, -1, "", fileNames.toString().getBytes());
            agente.enviarPacote(tmpPacoteEnviar, fireTuple.ip, fireTuple.porta);
            System.out.println("|====> THREAD_CLIENTE[" + fireTuple + "] - PACOTE_ENVIADO = " + tmpPacoteEnviar.parse());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Transferir um ficheiro
     */
    public void transferirFicheiro() {
        int tamanhoDaJanela = 4, numPacotesEnviados = 0, len, numeroTotalPacotes, tentativas, limite, pacotesPerdidos = 0, confirmacoesPerdidas = 0;
        boolean janelaConfirmada;
        Pacote pacoteEnviar;
        String[] confirmacaoPacotes;
        List<Integer> pacotesEnviar;
        String nomeFicheiro = new String(this.pacote.data);
        List<String> ficheiros = getFileList();
        RandomAccessFile is = null;
        File ficheiro;

        if(!ficheiros.contains(nomeFicheiro)){
            pacoteEnviar = new Pacote(0, 'E', 1, -1, -1, "", "".getBytes());
            try {
                agente.enviarPacote(pacoteEnviar, fireTuple.ip, fireTuple.porta);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        ficheiro = new File(diretoriaDoServidor+nomeFicheiro);

        try {
            is = new RandomAccessFile(ficheiro, "r");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if ((len = (int) ficheiro.length()) == 0) return;

        if(len % mtu == 0)numeroTotalPacotes = (len / mtu);
        else numeroTotalPacotes = (len/mtu) +1;

        if(numeroTotalPacotes < tamanhoDaJanela) tamanhoDaJanela = numeroTotalPacotes;

        //  ENVIA O PRIMEIRO PACOTE A INFORMAR QUANTOS PACOTES VAI MANDAR NOME DO FICHEIRO E TAMANHO DA JANELA
        try {
            pacoteEnviar = new Pacote(0, 'T', 0, -1, -1, "", new String (numeroTotalPacotes + ";" + nomeFicheiro+";"+tamanhoDaJanela).getBytes());
            agente.enviarPacote(pacoteEnviar, fireTuple.ip, fireTuple.porta);
            System.out.println("|====> THREAD_CLIENTE[" + fireTuple + "] - FICHEIRO = "+nomeFicheiro+" = {"+len+" BYTES} - PACOTE_ENVIADO = " + pacoteEnviar.parse());
        } catch (Exception e) {
            e.printStackTrace();
        }

        boolean enviar = true;

        while (numPacotesEnviados < numeroTotalPacotes) {
            pacotesEnviar = new ArrayList<>();
            janelaConfirmada = false;
            tentativas = 10;
            if((numPacotesEnviados + tamanhoDaJanela) > numeroTotalPacotes)limite = numeroTotalPacotes-numPacotesEnviados;
            else limite = tamanhoDaJanela;

            for (int i = 0; i < limite; i++) {
                pacotesEnviar.add(numPacotesEnviados + i);
            }

            while (!janelaConfirmada && tentativas >= 0) {
                if(enviar)enviaListaPacotes(pacotesEnviar, is,  len, numPacotesEnviados);

                this.lockPacotes.lock();
                try {
                    //System.err.println(LocalDateTime.now() + "|==> THREAD_CLIENTE[" + fireTuple + "] - THREAD ENVIOU OS PACOTES E AGORA ESTÁ À ESPERA DE CONFIRMAÇÃO DE PACOTES!");
                    this.haPacote.await(((this.cliente.tempoTotal*2)*tamanhoDaJanela + TEMPOADICIONAL), TimeUnit.MILLISECONDS);
                    //System.err.println(LocalDateTime.now() + "|==> THREAD_CLIENTE[" + fireTuple + "] - THREAD ACORDOU!");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    this.lockPacotes.unlock();
                }

                this.lockThread.lock();
                this.lockPacotes.lock();
                this.pacote = null;
                if (this.pacotes.size() != 0) {
                    this.pacote = this.pacotes.first(); //  vai se buscar o pacote
                    this.pacotes.remove(this.pacote);
                }
                this.lockPacotes.unlock();
                this.lockThread.unlock();

                if (this.pacote != null && this.pacote.type == 'K' && this.pacote.ack == 1 && this.pacote.nSeq == numPacotesEnviados) {
                    enviar = true;
                    if(!(new String(this.pacote.data)).contains(";")){
                        janelaConfirmada = true;
                        numPacotesEnviados += tamanhoDaJanela;    //  RODAR A JANELA
                        this.cliente.tempoTotal = this.pacote.tempo;
                    }else{
                        confirmacaoPacotes = null;
                        janelaConfirmada = false;
                        confirmacaoPacotes = new String(this.pacote.data).split(";");
                        pacotesEnviar = new ArrayList<>();
                        tentativas--;

                        for (String numeroPacote : confirmacaoPacotes) {    //  VERIFICAÇÃO DOS PERDIDOS
                            pacotesPerdidos++;
                            pacotesEnviar.add(Integer.valueOf(numeroPacote));
                        }
                        System.out.println("|==> THREAD_CLIENTE[" + fireTuple + "] - OCORRERAM PERDAS - PACOTES A SER REENVIADOS {"+pacotesEnviar+"}.");
                    }
                }else{
                    tentativas--;
                    System.err.println("ERRO: NÃO RECEBI CONFIRMAÇÃO!!!");
                    continue;
                }
            }

            if(tentativas < 0) {
                //  TODO ENVIAR PACOTE A DIZER QUE HOUVE UM ERRO
                return;
            }
        }
        System.out.println("|====> THREAD_CLIENTE[" + fireTuple + "] - PACOTES PERDIDOS = "+pacotesPerdidos+" CONFIRMAÇÕES PERDIDAS = "+confirmacoesPerdidas);
    }

    /**
     * Enviar um lista de pacotes, através do seu número de sequência.
     * @param numerosPacotes número de sequência dos pacotes a enviar
     * @param is objeto para ler do ficheiro
     * @param tamanhoDoficheiro tamanho do ficheiro
     * @param numPacotesEnviados número de pacotes já enviados
     */
    public void enviaListaPacotes(List<Integer> numerosPacotes, RandomAccessFile is, int tamanhoDoficheiro, int numPacotesEnviados){
        byte [] sendData;
        Pacote pacoteEnviar;

        Random rand = new Random();

        for(Integer numeroPacote : numerosPacotes){
            if(this.mtu*numeroPacote + this.mtu > tamanhoDoficheiro){
                sendData  = new byte[tamanhoDoficheiro-mtu*numeroPacote];
                try {
                    is.seek(numeroPacote*mtu);
                    is.read(sendData, 0, tamanhoDoficheiro-this.mtu*numeroPacote);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else{
                sendData = new byte[this.mtu];
                try {
                    is.seek(numeroPacote*mtu);
                    is.read(sendData, 0, this.mtu );
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            pacoteEnviar = new Pacote(0, 'T',1,numeroPacote,-1,"", sendData);
            try {
                //  ESTE CÓDIGO É PARA PROVOCAR PERDAS
                /*if(Math.random() > 0.001){
                    agente.enviarPacote(pacoteEnviar,fireTuple.ip,fireTuple.porta);
                } else{
                    System.out.println("============================> OCORREU UMA PERDAAAAAA! | NSEQ = "+pacoteEnviar.nSeq);
                }*/
                agente.enviarPacote(pacoteEnviar,fireTuple.ip,fireTuple.porta);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Obter lista dos ficheiros do servidor.
     * @return
     */
    public List<String> getFileList(){
        List<String> listaFicheiros = new ArrayList<>();
        for(File f: new File(diretoriaDoServidor).listFiles())
            if (f.isFile())listaFicheiros.add(f.getName());

            System.out.println("|====> THREAD_CLIENTE[" + fireTuple + "] - LISTA OBTIDA COM SUCESSO!");
        return listaFicheiros;
    }

    /**
     * Terminar conexão, matar thread.
     */
    public void terminarConexao(){
        this.runing = false; //  matar a thread caso o cliente não responda
        this.serverState.lockThreads.lock();
        if(this.serverState.threads.containsKey(fireTuple.getKey())){
            this.serverState.threads.remove(fireTuple.getKey());
        }
        this.serverState.lockThreads.unlock();
        System.err.println("|====> THREAD_CLIENTE[" + fireTuple + "] - TERMINAR CONEXÃO - THREAD DESLIGADA E REMOVIDA!");
    }

    @Override
    public String toString() {
        return "ThreadServidor{" +
                "fireTuple=" + fireTuple +
                '}';
    }

    /**
     * Adormecer a thread.
     */
    public void threadSleep(){
        lockPacotes.lock();
        if(this.pacotes.size() == 0){
                try {
                    System.out.println("|====> THREAD_CLIENTE[" + fireTuple + "] - THREAD ADORMECIDA!");
                    this.haPacote.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }finally {
                    this.lockPacotes.unlock();
                }
        }
    }
}
