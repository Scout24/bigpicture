#!/usr/bin/env python
from __future__ import print_function

import pygraphviz
from py2neo import neo4j

graph_db = neo4j.GraphDatabaseService("http://localhost:7474/db/data/")
graph_db.clear()

g = pygraphviz.AGraph()
g.read("out/netstat-established.dot")

print(graph_db.get_node_count())
for edge in g.edges_iter():
    label = (edge[0].split(".be")[0], edge[1].split(".be")[0])
    print("%s -> %s" % label)
    node_a = graph_db.get_or_create_indexed_node("hosts", "name", edge[0], {"label": label[0]})
    node_b = graph_db.get_or_create_indexed_node("hosts", "name", edge[1], {"label": label[1]})
    node_a.create_relationship_to(node_b, edge.attr['label'])
print(graph_db.get_node_count())
