space = $(eval) $(eval)

LIB_FOLDER = ./lib

LIBS = $(subst $(space),:,$(wildcard $(LIB_FOLDER)/*.jar)):./lib/
SRC = $(wildcard src/**/*.java)

install:
	mkdir -p $(LIB_FOLDER)
	wget https://search.maven.org/remotecontent?filepath=de/cgrotz/kademlia/1.0.1/kademlia-1.0.1.jar -nc -q --show-progress -O $(LIB_FOLDER)/kademlia-1.0.1.jar ||:
	
clean:
	rm -r ./bin

compile: $(SRC)
	mkdir -p ./bin
	javac -d ./bin -cp $(LIBS):./src/ ./src/**/*.java

ARGS ?= ./config.json

run:
	java -cp "./bin/:$(LIBS)" timeline.Application $(ARGS)
