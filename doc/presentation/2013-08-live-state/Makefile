

PROJECT = bigpicture
OUTDIR = out
TARGET = $(OUTDIR)/$(PROJECT).html
AVALANCHEDIR = $(OUTDIR)/avalanche

$(TARGET):	$(PROJECT).md *.css *.cfg $(AVALANCHEDIR) Makefile
	mkdir -p $(OUTDIR)
	landslide $(PROJECT).cfg

$(AVALANCHEDIR):
	git clone https://github.com/akrabat/avalanche.git $(AVALANCHEDIR)

clean:
	rm -rf $(TARGET)

mrproper:
	rm -rf $(OUTDIR)
