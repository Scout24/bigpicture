bigpicture
==========

gathers, filters, converts and renders actual network information


Prerequisits
------------

* python >= 2.6
* graphviz

For Ubuntu:
```bash
sudo apt-get install graphviz libgraphviz-dev
```

Getting Started
---------------

```bash
git clone https://github.com/arnehilmann/bigpicture.git
```

```bash
./init
. ve/bin/activate
fetch_data HOST1 [HOST2 [HOST3 [...]]]
build_model
lookup_ips
augment_model
create_views

google-chrome --allow-file-access-from-files file:///$(pwd)/view/smallhives.html
```

