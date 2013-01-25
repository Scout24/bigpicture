#!/usr/bin/env python
import re
import json
import pygraphviz

import bigpicture


def dot2json(filename):
    g = pygraphviz.AGraph(filename)

    nodes = []
    node_map = {}

    def get_or_add_node_index(name):
        if name not in node_map:
            node_map[name] = len(nodes)
            tokens = name.split()
            if len(tokens) > 1:
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
    for edge in g.edges_iter():
        #labels = calc_labels(edge)
        labels = edge
        index_1 = get_or_add_node_index(labels[0])
        index_2 = get_or_add_node_index(labels[1])

        protocol = edge.attr['protocol']
        links.append({"source": index_1, "target": index_2, "protocol": protocol})

    return json.dumps({"nodes": nodes, "links": links})


def extract_ips(file=bigpicture.DOT_FILE):
    ips = set()
    g = pygraphviz.AGraph(file)
    for edge in g.edges_iter():
        ips.add(edge[0])
        ips.add(edge[1])
    return ips
