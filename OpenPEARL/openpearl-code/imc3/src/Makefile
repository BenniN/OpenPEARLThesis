include ../../configuration/include/auto.conf

PACKAGES=imc imc.main imc.types imc.checks imc.utilities
DIRS =$(subst .,/,$(PACKAGES))
SRCS:= $(foreach dir,$(DIRS),$(filter-out $(dir)/package-info.java,$(wildcard $(dir)/*.java)))


.PHONY: all clean tests

all: ../out/imc.jar 

../out/imc.jar: $(SRCS)
	mkdir -p  out
	javac -d ../out  imc/main/InterModuleChecker.java
	(cd ../out;			\
	 jar cf imc.jar imc/main/*.class imc/types/*.class 	\
	 imc/utilities/*.class imc/checks/*.class;)

clean:
	find . -name *.class -exec rm {} \;
	rm -rf ../out
	mkdir ../out
	rm -f ../imc.inc
	rm -rf html

install: ../out/imc.jar
	cp ../out/imc.jar $(CONFIG_INSTALL_Target)/bin/imc.jar
	echo 'java -cp $(CONFIG_INSTALL_Target)/bin/imc.jar imc/main/InterModuleChecker -noNameSpace -b $$PLATTFORM  -I $(CONFIG_INSTALL_Target) -o system.cc $$fn' > ../imc.inc

prepare:
	touch $(CONFIG_INSTALL_Target)/bin/imc.jar
	chmod 777  $(CONFIG_INSTALL_Target)/bin/imc.jar

javadoc: html/index.html

html/index.html: $(SRCS)
	javadoc -overview overview.html -package \
	 -d html \
	-windowtitle 'Inter Module Checker (IMC)'	\
	-doctitle 'Inter Module Checker (IMC)'		\
	-header 'IMC' \
	imc imc.main imc.types imc.utilities imc.checks 

