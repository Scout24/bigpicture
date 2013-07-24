.SECONDARY:

DATAS = $(notdir $(wildcard data/*_state))
TARGETS = $(addprefix out/, $(DATAS:=.reduced_edges.dot.json) $(DATAS:=.reduced_edges.shorter_names.dot.json))
CLASSPATH = lib/gephi-toolkit.jar:lib/gson-2.2.2.jar:lib/uk-ac-ox-oii-sigmaexporter.jar:lib/org-gephi-plugins-layout-noverlap.jar:lib/pdfbox-app-1.8.2.jar:out/classes

all:	$(TARGETS)

clean:
	rm -rf out

# special rules for config_state
out/config_state.dot:	src/main/scripts/build_all_config_models src/main/scripts/build_config_model
	build_all_config_models data/config_state/*

# special rules for actual_state
out/actual_state.dot:	src/main/scripts/build_model src/main/scripts/augment_model
	build_model data/actual_state/*/*/netstat
	augment_model $@

# general rules
%.reduced_edges.dot:	%.dot src/main/scripts/reduce_model
	reduce_model $<

%.reduced_edges.shorter_names.dot:	%.reduced_edges.dot src/main/scripts/shorten_names
	shorten_names $<

%.dot.json:	%.dot src/main/scripts/create_views
	create_views $<

view:	all
	chromium-browser --allow-file-access-from-files file://$(PWD)/view/index.html



render:	out/classes/bigpicture/LiveModelRenderer.class
	echo $(CLASSPATH) > out/.classpath
	src/main/scripts/render_live_model

out/classes/%.class:	src/main/java/%.java
	mkdir -p out/classes
	javac -cp $(CLASSPATH):src/main/java -source 1.6 -d out/classes $<
