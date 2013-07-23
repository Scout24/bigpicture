#!/usr/bin/env python
from datetime import datetime
import os
import subprocess


def fetch_live_data(hosts, out_dir, parallel):
    now = datetime.now().strftime("%Y-%m-%d--%H-%M-%S")
    cmd = "netstat"

    cmds = ["/usr/bin/pdsh", "-w", "-", "-R", "ssh", "-f", str(parallel),
        "netstat -a -t --numeric-hosts"]

    process = subprocess.Popen(cmds,
            stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    (stdout, stderr) = process.communicate(input="\n".join(hosts))

    out_files = {}
    for line in stdout.splitlines():
        try:
            host, data = line.split(": ", 1)
        except ValueError, e:
            print "---- %s: %s" % (str(e), line)
            continue
        if host not in out_files:
            host_out_dir = os.path.join(out_dir, host, now)
            if not os.path.exists(host_out_dir):
                os.makedirs(host_out_dir)
            out_filename = os.path.join(host_out_dir, cmd)
            out_files[host] = open(out_filename, "w")
        out_files[host].write(data + "\n")
    for out_file in out_files.values():
        out_file.close()
    returncode = process.wait()

    print "---- %i files created" % len(out_files)
    print "---- return code %i" % returncode
    return returncode
