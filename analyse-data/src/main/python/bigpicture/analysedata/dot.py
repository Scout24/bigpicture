#!/usr/bin/env python
import re
import json
import pygraphviz


def dot2sparsejson(filename):
    g = pygraphviz.AGraph(filename)

    connections = {}
    for edge in g.edges_iter():
        source, target = edge
        protocol = edge.attr['protocol']
        if source not in connections:
            connections[source] = {}
        targets = connections[source]
        if target not in targets:
            targets[target] = []
        protocols = targets[target]
        protocols.append(protocol)
    return json.dumps(connections)


def model2json(model, filename):
    nodes = []
    node_map = {}

    def get_or_add_node_index(name):
        if name not in node_map:
            node_map[name] = len(nodes)
            tokens = name.split()
            if len(tokens) == 2:
                new_name, ip = tokens
                new_name = new_name.split(".", 1)[0]
            else:
                new_name = name
                ip = name
            nodes.append({"name": new_name, "ip": ip})
        return node_map[name]

    def calc_labels(edge):
        def calc_shortname(name):
            if re.search("[a-z]", name):
                return name.split(".", 1)[0]
            return name
        return (calc_shortname(edge[0]), calc_shortname(edge[1]))

    links = []
    for u, v, d in model.edges_iter(data=True):
        index_1 = get_or_add_node_index(u)
        index_2 = get_or_add_node_index(v)

        protocol = d.get('protocol', '__unknown__')
        links.append({"source": index_1, "target": index_2, "protocol": protocol})

    with open(filename, 'w') as json_file:
        json.dump({"nodes": nodes, "links": links}, json_file)
