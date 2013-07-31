#!/usr/bin/env python
from datetime import datetime
import os
import subprocess


def fetch_live_data(hosts, out_dir, parallel, now=None):
    if not now:
        now = datetime.now().strftime("%Y-%m-%d--%H-%M-%S")

    cmds = ["/usr/bin/pdsh", "-w", "-", "-R", "ssh", "-f", str(parallel),
            "; ".join([
                "netstat -a -t --numeric-hosts | sed 's/^/netstat: /'",
                "mount | grep 'type nfs' | sed 's/^/mount: /'"
            ])
    ]

    pdsh_env = os.environ.update({
        'PDSH_SSH_ARGS_APPEND': '-o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -q'
    })
    process = subprocess.Popen(cmds,
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, env=pdsh_env)
    (stdout, stderr) = process.communicate(input="\n".join(hosts))

    parse_live_data(stdout, out_dir, now)
    returncode = process.wait()

    print "---- return code %i" % returncode
    return returncode


def parse_live_data(stream, out_dir, now=None):
    if not now:
        now = datetime.now().strftime("%Y-%m-%d--%H-%M-%S")

    out_files = {}
    for line in stream.splitlines():
        try:
            host, data = line.split(": ", 1)
        except ValueError, e:
            print "---- %s: %s" % (str(e), line)
            continue
        if host not in out_files:
            host_out_dir = os.path.join(out_dir, host)
            out_filename = os.path.join(host_out_dir, now)
            if not os.path.exists(host_out_dir):
                os.makedirs(host_out_dir)
            out_files[host] = open(out_filename, "w")
        out_files[host].write(data + "\n")
    for out_file in out_files.values():
        out_file.close()
    print "---- %i files created" % len(out_files)
