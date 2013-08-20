.SECONDARY:

OUTDIR = out
TARGET = $(OUTDIR)/live-state.html

$(TARGET):	*.rst *.css res/*
	rm -rf $(OUTDIR)
	mkdir -p $(OUTDIR)
	hovercraft live-state.rst $(OUTDIR)

clean:
	rm -rf $(OUTDIR) $(TARGET)
