JC=javac
J=java
FILES = business/*.java
APP = business/TransfereCC

install:
	clear
	$(JC) $(FILES)

runServidor: install
	java $(APP) servidor

runCliente: install
	clear
	java  $(APP) cliente

runTeste: install
	clear
	java $(APP) teste

clean:
	rm -r -f business/*.class 	
