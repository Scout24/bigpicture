#!/usr/bin/env python
import logging
import os
import re
import ConfigParser

BLACKLIST = set(["localhost"])
SETTINGS_FILE = "/etc/bigpicture.conf.d/settings.ini"
config = None   # lazy loading


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


def get_config(section, key):
    global config
    if not config:
        config = ConfigParser.ConfigParser()
        config.readfp(open(SETTINGS_FILE))
    try:
        return config.get(section, key)
    except:
        return None


def retrieve_config(key, arguments, config_section):
    result = arguments.get('--%s' % key)
    if not result:
        result = get_config(config_section, key)
    return result


def init_config(defaults):
    global config
    config = ConfigParser.ConfigParser(defaults=defaults)
    config.readfp(open(SETTINGS_FILE))
