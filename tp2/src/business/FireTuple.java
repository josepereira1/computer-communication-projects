package business;

import java.net.*;


public class FireTuple{
	public InetAddress ip;
	public int porta;

	public FireTuple(){
		this.porta = -1;
	}

	public FireTuple(InetAddress  ip, int porta){
	    this.ip = ip;
	    this.porta = porta;
	}

	@Override
	public boolean equals(Object o){
		if(o == this) return true;
		if(o == null || this.getClass() != o.getClass()) return false;

		FireTuple f = (FireTuple) o;
		return this.porta == f.porta && this.ip.hashCode() == f.ip.hashCode();
	}

	/**
	 * Converte o firetuple para String, para ser usado como chave.
	 * @return retorna a string do firetuple.
	 */
	public String getKey(){
		return this.ip.toString()+";"+this.porta;
	}

	@Override
	public String toString() {
		return "FireTuple{" +
				"porta=" + porta +
				", ip=" + ip +
				'}';
	}
}