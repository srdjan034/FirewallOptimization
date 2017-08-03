#
# Run 'make' to build all files

CC = gcc
FLAGS = -Wall -O3
UNAME := $(shell uname -s)

main: firewall_stdio.exe firewall_socket.exe

clean:
	rm -f moeaframework.o libmoea.a firewall_stdio.exe firewall_socket.exe

libmoea.a: moeaframework.c moeaframework.h
	$(CC) $(FLAGS) -o moeaframework.o -c moeaframework.c
	ar rcs libmoea.a moeaframework.o
	rm -f moeaframework.o

firewall_stdio.exe: libmoea.a firewall.c
	${CC} ${FLAGS} -o firewall_stdio.exe firewall.c -L. -lmoea -lm

firewall_socket.exe: libmoea.a firewall.c
ifeq ($(UNAME),Linux)
	${CC} ${FLAGS} -DUSE_SOCKET -o firewall_socket.exe firewall.c -L. -lmoea -lm
endif
