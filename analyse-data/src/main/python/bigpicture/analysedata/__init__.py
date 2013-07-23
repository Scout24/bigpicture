#!/usr/bin/env python
import logging
import os
import re


OUT_DIR = "out"
VIEW_DIR = "view"
DOT_FILE = os.path.join(OUT_DIR, "actual_state.dot")
MAPPING_FILE = os.path.join(OUT_DIR, "ip_host.mapping")
LB_NODE_FILE = os.path.join(OUT_DIR, "ip_node.mapping")
BLACKLIST = set(["localhost"])


def makedirs(d):
    try:
        os.makedirs(d)
    except OSError, e:
        if e.errno != 17:
            logging.exception(e)
            logging.critical("cannot create dir %s")
            raise e


def is_blacklisted(host):
    for blacklist_regexp in BLACKLIST:
        if re.search(blacklist_regexp, host):
            return True
    return False


def netstats2dot(files):
    lines = [
        "digraph G{",
        "  graph [rankdir=LR];",
        "  node [shape=none];"
    ]
    connection_lines = set()
    for file in files:
        connection_lines.update([line for line in netstat2dot(file)])
    lines.extend(connection_lines)
    lines.append("}")
    return "\n".join(lines)


def netstat2dot(file):
    listenports = set()
    timestamp = os.path.basename(os.path.dirname(file))
    date = timestamp.split('--', 1)[0]
    with open(file) as listen_file:
        for line in listen_file.readlines():
            if re.search("LISTEN", line):
                try:
                    proto, recv, send, local, remote, state = line.split()
                    host, port = local.split(":")
                    listenports.add(port)
                except:
                    pass

    with open(file) as established_file:
        for line in established_file.readlines()[2:]:
            if re.search("LISTEN", line):
                continue
            line = line.rstrip()
            if not line:
                continue
            try:
                proto, recv, send, local, remote, state = line.split(None, 5)
                local = local.replace("::ffff:", "")
                local_host, local_port = local.split(":")
                remote = remote.replace("::ffff:", "")
                remote_host, remote_port = remote.split(":")
                if local_host == remote_host:
                    continue
                if local_port in listenports:
                    from_host = remote_host
                    to_host = local_host
                    protocol = local_port
                else:
                    from_host = local_host
                    to_host = remote_host
                    protocol = remote_port
                yield '  "%(from_host)s" -> "%(to_host)s" [protocol="%(protocol)s", input="LIVE_STATE", inputdetail="netstat", label="%(protocol)s", timestamp="%(timestamp)s", date="%(date)s"];' % locals()
            except Exception:
                logging.warn("cannot parse %s" % line)
