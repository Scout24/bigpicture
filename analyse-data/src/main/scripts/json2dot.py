#!/usr/bin/env python
"""
Takes the json from stdin, returned from the neo4j-query-api, and writes a corresponding .dot file to stdout.

Usage:
    json2dot stdin stdout
"""
import json
import sys
#from pprint import pprint

def color_by_protocol(protocol):
    protocol_to_colors = {
        'socks': 'yellow',
        'nfs': 'red',
        'jdbc': 'orange',
        'http': 'green2',
        'https': 'green4',
        'jms': 'cyan',
        'tcp': 'tan',
        'smtp': 'wheat',
        'ftp': 'peru'}
    return 'black' if not protocol in protocol_to_colors else protocol_to_colors[protocol]

def penwidth_by(num_of_connections):
    penwidth=0.2*num_of_connections
    return penwidth if penwidth<=5 else 5

def color_by_loc(loc):
    loc_to_colors = {'dev':'wheat', 'tuv':'gold', 'ber':'cyan', 'ham':'cyan3'}
    return 'lightgrey' if not loc in loc_to_colors else loc_to_colors[loc]

def print_node(n):
    if not n in nodes:
        print "\"%s\" [color=\"%s\", style=filled];" % (n, color_by_loc(n[:3]))

#json_data=open(sys.argv[1])
json_data = sys.stdin
data = json.load(json_data)
# pprint(data)
# print (data['data'][0][0])
nodes=[]
print "digraph G {"

for row in data['data']:
    n1 = row[0]
    n2 = row[3]

    print_node(n1)
    print_node(n2)

    print "\"%s\" -> \"%s\" [label=\"%s x %s\", color=\"%s\", penwidth=\"%3.1f\"];" % (
    n1, n2, row[2], row[1], color_by_protocol(row[1]),penwidth_by(row[2]))
print "}"

json_data.close()

# SNI
# - legende instead of labels
# - colors(node) by type: prod,tuv,dev
