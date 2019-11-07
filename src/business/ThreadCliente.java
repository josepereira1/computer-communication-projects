package business;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

public class ThreadCliente extends  Thread{

    static final int TEMPOADICIONAL = 350;

    Cliente cliente;
    AgenteUDP agente;
    InetAddress servidor;
    int porta;

    public ThreadCliente(){
        cliente = new Cliente();
        agente = new AgenteUDP();
    }

    /**
     * Thread do lado do cliente.
     */
    @Override
    public void run() {
        boolean runing = false;

        String opcao;
        Scanner tecladoCliente = new Scanner(System.in);

        System.out.println("->Insira o endereço:");
        System.out.print("projeto-cc> ");
        opcao = tecladoCliente.nextLine();

        try {
            this.servidor = InetAddress.getByName(opcao);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        System.out.println("->Insira a porta:");
        System.out.print("projeto-cc> ");

        opcao = tecladoCliente.nextLine();

        this.porta = Integer.valueOf(opcao);

        // ---------------------------------------- establecimento de conexão --------------------------------------------------------------------
        Pacote pacote = null;
        runing = testeConexao();

        // ---------------------------------------- establecimento de conexão --------------------------------------------------------------------

        do{
            try{
                pacote = agente.receberPacote(1000,true);
                if(pacote.type == 'E'){
                    System.out.println("Ocorreu um erro, reinicialize a aplicação!");
                    return;
                }
            }catch (SocketTimeoutException e){
                pacote = null;
            }catch (IOException e){
                e.printStackTrace();
            }
        }while (pacote != null);

        while (runing) {

            System.out.print("projeto-cc> ");

            if (tecladoCliente.hasNextLine()) opcao = tecladoCliente.nextLine();
            else {
                try {
                    agente.enviarPacote(new Pacote(0,'X', 0,-1,-1,"","".getBytes()),servidor,porta);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                runing = false;
            }

            String[] argumentos = opcao.split(" ");

            switch (argumentos[0]) {
                case "help":
                    System.out.println("==>autenticar");
                    System.out.println("==>registar");
                    System.out.println("==>listar");
                    System.out.println("==>get nomeFicheiro");
                    System.out.println("==>sair");
                    break;

                case "autenticar":
                    if(cliente.autenticado && argumentos.length != 1){
                        System.out.println("Argumentos desnecessários ou já está autenticado");
                        break;
                    }else{
                        try {
                            cliente.autenticado = autenticar();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                case "registar":
                    if(argumentos.length != 1){
                        System.out.println("Argumentos desnecessários");
                        break;
                    }else{
                        try {
                            registar();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                case "listar":
                    if(argumentos.length != 1){
                        System.out.println("Argumentos desnecessários");
                        break;
                    }else {
                        if (!cliente.autenticado) {
                            System.out.println("WARNING: NÃO ESTÁ AUTENTICADO!");
                            break;
                        }
                        list();
                        break;
                    }
                case "get":
                    if(!cliente.autenticado){System.out.println("WARNING: NÃO ESTÁ AUTENTICADO!");break;}
                    if(argumentos.length == 1){
                        System.out.println("Escolha ficheiros para transferir!");
                        break;
                    }else {
                        getFile(argumentos);
                        break;
                    }
                case "sair":
                    if(argumentos.length != 1){
                        System.out.println("Argumentos desnecessários");
                        break;
                    }
                    try {
                        agente.enviarPacote(new Pacote(0,'X', 0,-1,-1,"","".getBytes()),servidor,porta);
                        agente.terminarConexao();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    runing = false;
                    break;

                default:
                    System.out.println("Não existe essa opção, faça help para saber quais existem!");
                    break;
            }
        }
    }

    /**
     * Teste de conexão.
     * @return retorna true se correu bem e false caso contrário.
     */
    public boolean testeConexao(){
        Pacote pacote = null;

        try {
            cliente.tempoInicio = System.currentTimeMillis();
            agente.enviarPacote(new Pacote(0,'S', 0, -1, -1, "", "".getBytes()), servidor, porta);   //  SYN ACK = 0 (PRIMEIRO SYN)
            pacote = agente.receberPacote(1500, true);
            cliente.tempoFinal = System.currentTimeMillis();
            cliente.calcularTempo();
        } catch (SocketTimeoutException e) {
            try {
                cliente.tempoInicio = System.currentTimeMillis();
                agente.enviarPacote(new Pacote(0,'S', 0, -1, -1, "", "".getBytes()), servidor, porta);   //  SYN ACK = 0 (PRIMEIRO SYN
            } catch (Exception e1) {
                e1.printStackTrace();
            }
            try {
                pacote = this.agente.receberPacote(5000, true);
                cliente.tempoFinal = System.currentTimeMillis();
                cliente.calcularTempo();
            } catch (SocketTimeoutException e1) {
                pacote = null;
            }catch (IOException e1){
                e.printStackTrace();
            }
        }catch (Exception e){
        }

        if (pacote != null && pacote.type == 'S' && pacote.ack == 0) {
            try {
                agente.enviarPacote(new Pacote(0, 'S', 1, -1, -1, "", "".getBytes()), servidor, porta);   //  SYN ACK = 1 (SEGUNDO SYN)
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;

        } else {
            System.err.println("ERRO: IMPOSSÍVEL CONECTAR!");
            return false;    //  fim da aplicação
        }
    }

    /**
     * Autenticação do cliente.
     * @return retorna true se autenticou e false caso contrário.
     * @throws Exception
     */
    public boolean autenticar() throws Exception {
        String username, password, sentence;
        Scanner tecladoCliente = new Scanner(System.in);
        Pacote pacoteEnviar, pacoteRecebido = null;

        int tentativas = 4;

        limparSocketBuffer();
        while(this.cliente.autenticado == false && tentativas >= 0){ // autenticação
            tentativas--;
            System.out.print("->username: ");
            username = tecladoCliente.nextLine();
            System.out.print("->password: ");
            password = tecladoCliente.nextLine();

            if(username.length() == 0 || password.length() == 0){
                System.err.println("Username ou password inválidos!");
                continue;
            }

            sentence = username + ";" + password;   //username;password
            pacoteEnviar = new Pacote(0,'A',-1,-1,-1,"",sentence.getBytes());

            try{
                cliente.tempoInicio = System.currentTimeMillis();
                agente.enviarPacote(pacoteEnviar, servidor, porta);
                pacoteRecebido = agente.receberPacote(cliente.tempoTotal + TEMPOADICIONAL,true);
                cliente.tempoFinal = System.currentTimeMillis();
                cliente.calcularTempo();
            }catch (SocketTimeoutException e){
                cliente.tempoInicio = System.currentTimeMillis();
                agente.enviarPacote(pacoteEnviar, servidor, porta);
                try{
                    pacoteRecebido = agente.receberPacote(cliente.tempoTotal + TEMPOADICIONAL*2,true);
                    cliente.tempoFinal = System.currentTimeMillis();
                    cliente.calcularTempo();
                }catch (SocketTimeoutException e1){
                    pacoteRecebido = null;
                }
            } catch(Exception e){
                e.printStackTrace();
            }

            if(pacoteRecebido == null){
                System.err.println("Servidor não está a responder, reiniciar a aplicação!");
                return false;
            }else{
                if(pacoteRecebido.type == 'A' && pacoteRecebido.ack == 0){
                    cliente.autenticado = true;
                    System.out.println("SUCESSO: Autenticado com sucesso!");
                    return true;
                } else if(pacoteRecebido.type == 'A' && pacoteRecebido.ack == 1){
                    System.out.println("ERRO: password errada, tente novamente.");

                }else if(pacoteRecebido.type == 'A' && pacoteRecebido.ack == 2){
                    System.out.println("ERRO: username não existe, tente novamente!");
                }else if(pacoteRecebido.type == 'A' && pacoteRecebido.ack == 3){
                    System.out.println("WARNING: Já está autenticado!");
                }
            }

        }

        if(tentativas <= 0){
            System.out.println("Número de tentativas ultrapassadas!");
            return false;
        }
        return  false;
    }

    /**
     * Registar o cliente no servidor.
     * @return retorna true caso haja sucesso no registo e false caso contrário.
     * @throws Exception
     */
    public boolean registar() throws Exception{
        String username, password;
        boolean runing = true;
        Scanner tecladoCliente = new Scanner(System.in);
        Pacote pacoteEnviar, pacoteReceber = null;
        int tentativas = 4;
        limparSocketBuffer();

        while (runing && tentativas >= 0){
            tentativas--;
            System.out.print("->username: ");
            username = tecladoCliente.nextLine();
            System.out.print("->password: ");
            password = tecladoCliente.nextLine();

            if(username.length() == 0 || password.length() == 0){
                System.err.println("Username ou password inválidos!");
                continue;
            }

            StringBuilder sb = new StringBuilder(username);
            sb.append(";").append(password);
            pacoteEnviar = new Pacote(0,'R', -1,-1,-1,"", sb.toString().getBytes());

            try{
                cliente.tempoInicio = System.currentTimeMillis();
                agente.enviarPacote(pacoteEnviar,servidor,porta);
                pacoteReceber = agente.receberPacote(cliente.tempoTotal+TEMPOADICIONAL,true);
                cliente.tempoFinal = System.currentTimeMillis();
                cliente.calcularTempo();
            }catch (SocketTimeoutException e){
                e.printStackTrace();
                cliente.tempoInicio = System.currentTimeMillis();
                agente.enviarPacote(pacoteEnviar,servidor,porta);
                pacoteReceber = agente.receberPacote(cliente.tempoTotal + TEMPOADICIONAL*2,true);
                cliente.tempoFinal = System.currentTimeMillis();
                cliente.calcularTempo();
            }catch(Exception e){
                e.printStackTrace();
            }

            if(pacoteReceber != null && pacoteReceber.ack == 1){
                System.out.println("INSUCESSO: O username já está a ser usado por outro utilizador, insira um novo username.");
            }else if(pacoteReceber != null && pacoteReceber.ack == 0){
                System.out.println("SUCESSO: Registado com sucesso.");
                return true;
            } else {
                System.out.println("ERRO: Impossível registar, servidor não está a responder, tente mais tarde!");
            }
        }
        return false;
    }

    /**
     * Listar os ficheiros do servidor.
     */
    public void list(){
        Pacote pacoteEnviar, pacoteReceber;
        String[] resposta;
        limparSocketBuffer();
        try{
            pacoteEnviar = new Pacote(0,'L', -1,-1,-1,"", "".getBytes());
            cliente.tempoInicio = System.currentTimeMillis();
            agente.enviarPacote(pacoteEnviar, servidor, porta);

            pacoteReceber = agente.receberPacote(cliente.tempoTotal + TEMPOADICIONAL+1000, true);
            cliente.tempoFinal = System.currentTimeMillis();

            cliente.calcularTempo();

            resposta = new String(pacoteReceber.data).split(";");   //  POSSO FAZER ISTO PQ SEI QUE É UMA STRING

            for(String s : resposta) System.out.println(s);

        }catch (SocketTimeoutException e){
            e.printStackTrace();
        }catch (SocketException e){
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Limpar o buffer do socket quando há pacotes perdidos.
     */
    public void limparSocketBuffer(){
        Pacote pacoteReceber;
        int tentativa = 10;

        try{
            while(tentativa > 0){
                pacoteReceber = agente.receberPacote(50, true);
                if(pacoteReceber == null) tentativa--;
                else tentativa = 10;
            }
        } catch(Exception e){
        }

        //if(pacoteReceber != null)System.out.println("TENTATIVAS = "+tentativas+"PACOTES ENCONTRADOS NO BUFFER="+pacoteReceber);
    }

    /**
     * Obter uma lista de ficheiros.
     * @param ficheiros recebe a lista de ficheiros a transferir
     */
    public void getFile(String[] ficheiros){

        Pacote pacoteEnviar, pacoteReceber = null;
        String[] infoInicial;
        int iteracao = 0;
        boolean recebeu;

        for(String ficheiro : ficheiros){
            limparSocketBuffer();
            if(iteracao != 0){

                pacoteEnviar = new Pacote(0,'T', -1,-1,-1,"", ficheiro.getBytes());

                try {
                    cliente.tempoInicio =System.currentTimeMillis();
                    agente.enviarPacote(pacoteEnviar, servidor, porta);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    pacoteReceber = agente.receberPacote(cliente.tempoTotal + TEMPOADICIONAL+2000, true);
                    cliente.tempoFinal = System.currentTimeMillis();
                    cliente.calcularTempo();
                } catch (SocketTimeoutException e){
                    pacoteReceber = null;
                }catch (IOException e) {
                    e.printStackTrace();
                }

                if(pacoteReceber != null && pacoteReceber.type == 'T' && pacoteReceber.ack == 0 && pacoteReceber.nSeq == -1){
                    infoInicial = new String(pacoteReceber.data).split(";");
                    recebeu = receberFicheiro(Integer.valueOf(infoInicial[0]), infoInicial[1], Integer.valueOf(infoInicial[2]));
                    if(recebeu == false) System.out.println("ERRO: OCORREU UM ERRO NA TRANSFERÊNCIA.");
                    limparSocketBuffer();
                } else if(pacoteReceber != null && pacoteReceber.type == 'E' && pacoteReceber.ack == 1){
                    System.out.println("ERRO: Ficheiro não existe.");
                    return;
                }else{
                    limparSocketBuffer();
                }

            }
            iteracao++;
        }
    }

    /**
     * Recebe um ficheiro.
     * @param numeroTotalPacotes Número de pacotes a receber
     * @param nomeFicheiro nome do ficheiro
     * @param tamanhoDaJanela tamanho da janela
     * @return retorna true se conseguir transferir.
     */
    public boolean receberFicheiro(int numeroTotalPacotes, String nomeFicheiro, int tamanhoDaJanela){
        StringBuilder lost;
        int tentativas = 10, janelaAtual = 0, pacotesPerdidos = 0;
        Pacote pacoteEnviar;
        Map<Integer,Pacote> pacotesDestaJanela = new HashMap<>(numeroTotalPacotes);
        FileOutputStream fos = null;

        File file = new File(nomeFicheiro);

        file.delete();

        long tempoAntes = System.currentTimeMillis();

        try {
            fos = new FileOutputStream(nomeFicheiro, true);
        } catch (FileNotFoundException e) {
        }

        while(tentativas >= 0 && janelaAtual < numeroTotalPacotes){
            receberPacotesJanela(pacotesDestaJanela, tamanhoDaJanela,janelaAtual);

            if(pacotesDestaJanela.size() == tamanhoDaJanela){  //  CONFIRMA SE RECEBEU TODOS

                for(Pacote pacote : pacotesDestaJanela.values().stream().sorted(new ComparatorPacotePorNSeq()).collect(Collectors.toList())) {
                    try {
                        fos.write(pacote.data,0,pacote.data.length);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    cliente.tempoInicio = System.currentTimeMillis();
                    pacoteEnviar = new Pacote(1, 'K', 1, janelaAtual, this.cliente.tempoTotal, "", "".getBytes());
                    agente.enviarPacote(pacoteEnviar, servidor,porta);
                    //System.out.println("RECEBI OS PACOTES TODOS="+pacotesDestaJanela.keySet());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                janelaAtual += tamanhoDaJanela; //  ATUALIZA O PONTEIRO DA JANELA
                System.out.printf("%d percentagem\r",(100*janelaAtual/numeroTotalPacotes));
                tentativas = 10;
                pacotesDestaJanela = new HashMap<>();

                if(janelaAtual + tamanhoDaJanela > numeroTotalPacotes)tamanhoDaJanela = numeroTotalPacotes-janelaAtual;
            }else {
                lost = new StringBuilder();
                for (int i = janelaAtual; i < (janelaAtual + tamanhoDaJanela); i++) {
                    if (!pacotesDestaJanela.containsKey(i)){
                        lost.append(i).append(";");
                        pacotesPerdidos++;
                    }
                }
                pacoteEnviar = new Pacote(1, 'K', 1, janelaAtual, -1, "", lost.toString().getBytes());
                try {
                    cliente.tempoInicio = System.currentTimeMillis();
                    agente.enviarPacote(pacoteEnviar, servidor,porta);
                    tentativas--;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if(tentativas < 0){
            System.out.println("AVISO: IMPOSSÍVEL FAZER DOWNLOAD DO FICHEIRO "+nomeFicheiro+", tente mais tarde.");
            return false;
        }

        if(janelaAtual == numeroTotalPacotes){
            long tempoDepois = System.currentTimeMillis();
            System.out.println("INFORMAÇÃO: Ficheiro : " + nomeFicheiro + " descarregado com sucesso em "+(tempoDepois-tempoAntes)+" ms, com "+pacotesPerdidos+" pacotes perdidos!");
            return true;
        }else{
            file = new File(nomeFicheiro);
            file.delete();  //  APAGAR O FICHEIRO PQ NÃO CONCLUIMOS A TRANSFERÊNCIA
            return false;
        }
    }

    /**
     * Esta função recebe os pacotes todos de uma janela e coloca-os na estrutura Map
     * @param pacotes estrutura onde são guardados os pacotes
     * @param tamanhoJanela tamanho da janela, número de pacotes a receber
     * @param janelaAtual janela atual
     */
    public void receberPacotesJanela(Map<Integer, Pacote> pacotes, int tamanhoJanela, int janelaAtual){
        int tentativas = 5;
        Pacote pacoteReceber = null;

        while (tentativas > 0 && pacotes.size() != tamanhoJanela){
            try {
                pacoteReceber = agente.receberPacote((cliente.tempoTotal) + TEMPOADICIONAL, true);
            } catch (SocketTimeoutException e){
                tentativas--;
                pacoteReceber =null;
            }catch (IOException e) {
                e.printStackTrace();
            }
            if(pacoteReceber != null && (pacoteReceber.nSeq >= janelaAtual && pacoteReceber.nSeq <= (janelaAtual + tamanhoJanela))){
                pacotes.put(pacoteReceber.nSeq, pacoteReceber);
                tentativas = 5;
            }
        }
        if(pacotes.size() == tamanhoJanela){
            cliente.tempoFinal = System.currentTimeMillis();
            cliente.calcularTempo(tamanhoJanela+1);
        }
    }
}
