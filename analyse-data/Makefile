.SECONDARY:

DATAS = $(notdir $(wildcard data/*_state))
TARGETS = $(addprefix out/, $(DATAS:=.reduced_edges.dot.json) $(DATAS:=.reduced_edges.shorter_names.dot.json))


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
