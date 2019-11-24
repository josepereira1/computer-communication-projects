package business;

public class TransfereCC {
    public static void main(String[] args){

        //  CORRER A APLICAÇÃO DO SERVIDOR
        if(args[0].equals("servidor")){
            new ThreadPrincipalServidor().start();
        }

        //  CORRER A APLICAÇÃO DO CLIENTE
        if(args[0].equals("cliente")){
            new ThreadCliente().start();
        }
    }
}
