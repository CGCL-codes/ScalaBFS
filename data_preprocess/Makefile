CC:=g++
# generate=GraphToScalaBFS.cpp
generate=GraphToScalaBFS_2.cpp

CFLAGS :=
# CFLAGS += -lpthread
CFLAGS += -fopenmp

all : GraphToScalaBFS

GraphToScalaBFS:$(generate)
	${CC} $(generate) -o undirected_num $(CFLAGS)

clean:
	rm -f *.o
	rm -f GraphToScalaBFS

.PHONY: clean