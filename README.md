bigpicture
==========

gathers, filters, converts and renders actual network information
(check the [examples](http://arnehilmann.github.com/bigpicture/))

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

fetch-live-data -g all
fetch-ips

build-live-model
augment-model
convert-model

# from here on: work in progress
# create_views

# google-chrome --allow-file-access-from-files file:///$(pwd)/view/smallhives.html
```

