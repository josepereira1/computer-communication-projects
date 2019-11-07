JC=javac
J=java
FILES = business/*.java
APP = business/TransfereCC

install:
	clear
	$(JC) $(FILES)

start-server: install
	java $(APP) servidor

start-client: install
	clear
	java  $(APP) cliente

clean:
	rm -r -f business/*.class 	
