package business;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Cliente{
	String username;
	String password;
	
	boolean autenticado;

	long tempoInicio;
	long tempoFinal;

	long tempoTotal = 500;

	Lock lock;

	public Cliente(){
		username = "";
		password = "";
		lock = new ReentrantLock();
		autenticado = false;
	}

	public Cliente(String username, String password){
		this.username = username;
		this.password = password;

		this.lock = new ReentrantLock();
	}

	public boolean equals(Object o){
		if(o == this) return true;
		if(o == null || this.getClass() != o.getClass()) return false;
		Cliente c = (Cliente) o;
		return this.username.equals(c.username)
				&& this.autenticado == autenticado
				&& this.password.equals(c.password);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("Cliente={");
		sb.append("username=").append(username);
		sb.append(";password=").append(password);
		sb.append(";autenticado=").append(autenticado).append("};");

		return sb.toString();
	}

	/**
	 * Calcular o tempo de espera.
	 */
	public void calcularTempo(){
		if((this.tempoFinal-this.tempoInicio) > this.tempoTotal)
			this.tempoTotal += 0.1*(this.tempoFinal-tempoInicio);
		else
			this.tempoTotal -= 0.1*(this.tempoFinal-this.tempoInicio);

		if(this.tempoTotal < 300) this.tempoTotal = 300;
		else if(this.tempoTotal > 5000) this.tempoTotal = 5000;
	}

	/**
	 * Calcular o tempo de espera, ao receber vários pacotes.
	 * @param nPackets número de pacotes
	 */
	public void calcularTempo(int nPackets){
		if(((this.tempoFinal-this.tempoInicio)/nPackets) > this.tempoTotal) this.tempoTotal += 0.10*((this.tempoFinal-this.tempoInicio)/nPackets);
		else this.tempoTotal -= 0.10*((this.tempoFinal-this.tempoInicio)/nPackets);

		if(this.tempoTotal < 300) this.tempoTotal = 300;
		else if(this.tempoTotal > 5000) this.tempoTotal = 5000;
	}
}