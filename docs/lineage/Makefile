
.PHONY: clean
targets = demo-lineage.png

all: ${targets}

${targets}: %.png : %.dot
	dot -Tpng -o $@ $<

clean:
	rm -f ${targets}


