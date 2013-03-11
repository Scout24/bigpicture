#!/usr/bin/env python
from datetime import datetime
import logging
import os
import re
import subprocess


OUT_DIR = "out"
VIEW_DIR = "view"
DOT_FILE = os.path.join(OUT_DIR, "netstat.dot")
MAPPING_FILE = os.path.join(OUT_DIR, "ip_host.mapping")

BLACKLIST = set(["localhost"])


def fetch_data(hosts, jumphost, parallel):
    now = datetime.now().strftime("%Y-%m-%d--%H-%M-%S")
    out_dir = os.path.join(OUT_DIR, "actual_state")
    cmd = "netstat"

    cmds = ["/usr/bin/pdsh", "-w", "-", "-R", "ssh", "-f", str(parallel),
        "netstat -a -t --numeric-hosts"]
    if jumphost:
        cmds = ["/usr/bin/ssh", jumphost] + cmds

    process = subprocess.Popen(cmds,
            stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    (stdout, stderr) = process.communicate(input="\n".join(hosts))

    out_files = {}
    for line in stdout.splitlines():
        try:
            host, data = line.split(": ", 1)
        except ValueError, e:
            logging.warning(str(e))
            logging.warning(line)
            continue
        if host not in out_files:
            host_out_dir = os.path.join(out_dir, host, now)
            if not os.path.exists(host_out_dir):
                os.makedirs(host_out_dir)
            out_filename = os.path.join(host_out_dir, cmd)
            out_files[host] = open(out_filename, "w")
        out_files[host].write(data + "\n")
    for out_file in out_files.values():
        logging.info("created file %s" % out_file)
        out_file.close()
    returncode = process.wait()

    logging.info("return code %i" % returncode)
    return returncode


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
            try:
                proto, recv, send, local, remote, state = line.split(None, 5)
                local_host, local_port = local.split(":")
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
                yield '"%(from_host)s" -> "%(to_host)s" [protocol="%(protocol)s", input="ACTUAL_STATE", inputdetail="netstat", id="%(protocol)s", label="%(protocol)s", timestamp="%(timestamp)s"];' % locals()
            except Exception, e:
                logging.error(line)
                logging.exception(e)


def lookup_ips(ips, jumphost=None):
    mapping = {}
    cmd = "while read h; do host $h | sed \"s/.* /$h /;s/.$//\"; done"
    if jumphost:
        cmd = "/usr/bin/ssh %s '%s'" % (jumphost, cmd)

    process = subprocess.Popen(cmd,
            shell=True,
            stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    (stdout, stderr) = process.communicate(input="\n".join(ips))
    for line in stdout.splitlines():
        ip, hostname = line.split()
        if re.search("NXDOMAIN", hostname):
            continue
        mapping[ip] = hostname
    return mapping
