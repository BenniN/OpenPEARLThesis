
all: convertList
	(cd src; make;) 

convertList: convertList.c
	gcc convertList.c -o convertList

clean:
	(cd doc; make clean;)
	(cd src; make clean;)

install: all
	(cd src; make install;)

prepare:
	(cd src; make prepare;)


