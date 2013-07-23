
function getUrlVar(key){
    // https://gist.github.com/1771618
    var result = new RegExp(key + "=([^&]*)", "i").exec(window.location.search);
    return result && unescape(result[1]) || "";
}

startsWith = function(s, pattern) {
    return s.lastIndexOf(pattern, 0) === 0;
}

function degrees(radians) {
    return radians / Math.PI * 180 - 90;
}

var defaultColor = d3.scale.category10()
    .domain(d3.range(20));

    function renderHive(nodes, links, nrAxes, container, infoElement, size, color, angle) {
        size = (typeof size === "undefined") ? 300 : size;
        console.log("size: " + size);

        var width = size,
            height = size,
            innerRadius = size / 20,
            outerRadius = size / 2 - 10,
            nodeRadius = 6;

        var radius = d3.scale.linear()
            .range([innerRadius, outerRadius]);

        color = (typeof color === "undefined") ? defaultColor : color;

        var defaultAngle = d3.scale.ordinal()
            .domain(d3.range(nrAxes + 1))
            .rangePoints([0, 2 * Math.PI]);

        angle = (typeof angle === "undefined") ? defaultAngle : angle;

        var canvas = d3.select(container)
            .attr("width", width)
            .append("svg")
            .attr("width", width)
            .attr("height", height);

        var svg = canvas.append("g")
            .attr("transform", "translate(" + width / 2 + "," + height / 2 + ")");

        svg.selectAll(".axis")
            .data(d3.range(nrAxes))
            .enter().append("line")
            .attr("class", "axis")
            .attr("transform", function(d) { return "rotate(" + degrees(angle(d)) + ")"; })
            .attr("x1", radius.range()[0])
            .attr("x2", radius.range()[1]);

        svg.selectAll(".link")
            .data(links)
            .enter().append("path")
            .attr("class", "link")
            .attr("d", link()
                    .angle(function(d) { return angle(d.group); })
                    .radius(function(d) { return radius(d.index); }))
            .style("stroke", function(d) { return color(d.source.group); })
            .on("mouseover", linkMouseover)
            .on("mouseout", mouseout);

        svg.selectAll(".node")
            .data(nodes)
            .enter().append("circle")
            .attr("class", "node")
            .attr("transform", function(d) { return "rotate(" + degrees(angle(d.group)) + ")"; })
            .attr("cx", function(d) { return radius(d.index); })
            .attr("r", nodeRadius)
            .style("fill", function(d) { return color(d.group); })
            .on("mouseover", nodeMouseover)
            .on("click", nodeClick)
            .on("mouseout", mouseout);

        var hiveInfo = canvas.append("text")
            .classed("tooltip", true)
            .attr("text-anchor", "middle")
            .style("opacity", 1e-6)
            .style("fill", "black")
            .style("font-size", 20)
            .style("font-variant", "small-caps")
            .style("text-shadow", "rgba(255, 255, 255, 1) 0px 0px 2px");

        var selectedInfo = canvas.append("text")
            .classed("tooltip", true)
            .attr("text-anchor", "middle")
            .style("opacity", 1e-6)
            .style("fill", "black")
            .style("font-size", 20)
            .style("font-variant", "small-caps")
            .style("text-shadow", "rgba(255, 255, 255, 1) 0px 0px 2px");

        var textInfo = d3.select(infoElement);

        var selected = undefined;

        // Highlight the link and connected nodes on mouseover.
        function linkMouseover(d) {
            d3.selectAll(".link").classed("active", function(p) { return p.id === d.id; });
            d3.selectAll(".node circle").classed("active", function(p) { return p.id === d.source.id || p.id === d.target.id; });
            //hiveInfo.attr("x", (d3.event.offsetX) + "px").attr("y", (d3.event.offsetY + 30) + "px");
            //hiveInfo.text(d.source.name + "→" + d.target.name).style("opacity", 1);
            textInfo.text(d.source.name + "→" + d.target.name).style("opacity", 1);
        }

        // Highlight the node and connected links on mouseover.
        function nodeMouseover(d) {
            d3.selectAll(".link").classed("active", function(p) { return p.source.id === d.id || p.target.id === d.id; });
            d3.select(this).classed("active", true);
            //hiveInfo.attr("x", (d3.event.offsetX) + "px").attr("y", (d3.event.offsetY + 30) + "px");
            //hiveInfo.text(d.name).style("opacity", 1);
            textInfo.text(d.name).style("opacity", 1);
        }
        function nodeClick(d) {
            console.log(d);
            d3.selectAll(".link").classed("selected", false);
            d3.selectAll(".node").classed("selected", false);
            d3.selectAll(".node circle").classed("selected", false);
            selected = d;
            d3.selectAll(".link").classed("selected", function(p) { return p.source.id === d.id || p.target.id === d.id; });
            d3.select(this).classed("selected", true);
            //hiveInfo.attr("x", (d3.event.offsetX) + "px").attr("y", (d3.event.offsetY + 30) + "px");
            //hiveInfo.text(d.name).style("opacity", 1);
            selectedInfo.attr("x", (d3.event.offsetX) + "px").attr("y", (d3.event.offsetY + 30) + "px");
            selectedInfo.text(d.name).style("opacity", 1);
            textInfo.text(d.name).style("opacity", 1);
        }

        // Clear any highlighted nodes or links.
        function mouseout() {
            d3.selectAll(".active").classed("active", false);
            //hiveInfo.style("opacity", 1e-6);
            //hiveInfo.attr("x", "0px").attr("y", "0px");
            textInfo.text("| | |");
        }
    }


addSimpleHive = function(filename, selectedProtocol, groupFun, element, infoElement, size) {
    var nrAxes = 3;

    d3.json(filename, function(connections) {
        var nodes = [];
        connections.nodes.forEach(function(node) {
            var ns = groupFun(node);
            for (var i=0; i<ns.length; i++) {
                nodes.push(ns[i]);
            }
        });

        var links = [];
        var in_count = [], out_count = [];
        connections.links.forEach(function(link) {
            if (link.protocol == selectedProtocol) {
                if (nodes[link.source].group != nodes[link.target].group) {
                    links.push(link);
                    out_count[link.source] = 1;
                    in_count[link.target] = 1;
                }
            } 
        });

        for (var i = nodes.length - 1; i > -1; i--) {
            var node = nodes[i];
            if (isNaN(in_count[i]) && isNaN(out_count[i])) {
                nodes.splice(i, 1);
            }
        }

        var index = [];
        for (var i=0; i<nrAxes; i++) {
            index.push(0);
        };
        nodes.sort(function(a, b) {
            if (a.name < b.name) {
                return -1;
            }
            if (a.name > b.name) {
                return 1;
            }
            return 0;
        });
        nodes.forEach(function(node) {
            node.index = index[node.group]++;
        });
        nodes.forEach(function(node) {
            node.index /= (index[node.group] - 1);
        })
        links.forEach(function(link) {
            link.source = connections.nodes[link.source];
            link.target = connections.nodes[link.target];
            link.id = link.source.name + link.target.name;
        });

        renderHive(nodes, links, nrAxes, element, infoElement, size);
    })
}

function createGroupFunction(mapping) {
    console.log(mapping);
    grouping = function(node) {
        var result = 0;
        for (var key in mapping) {
            var mapped = mapping[key];
            if (typeof mapped != "undefined") {
                if (startsWith(node.name, key)) {
                    result = mapped;
                    break;
                }
            }
        }
        node.id = node.ip;
        node.group = result;
        return [node];
    }
    grouping.description = Array();
    for (var key in mapping) {
        var mapped = mapping[key];
        if (typeof mapped != "undefined") {
            grouping.description[mapped] =
                (typeof grouping.description[mapped] === "undefined") ?
                    key : grouping.description[mapped] + "/" + key;
        }
    }
    if ($.inArray(0, grouping.description)) {
        grouping.description[0] = "...";
    }
    return grouping;
}

addSelfConnectedHive = function(filename, selectedProtocol, groupFun, element) {
  d3.json(filename, function(connections) {
    var nrAxes = 2 * 3;
    var links = [];
    var connected = [];
    connections.links.forEach(function(link) {
      if (link.protocol == selectedProtocol) {
        links.push(link);
        connected[link.source] = 1;
        connected[link.target] = 1;
      }
    });

    var nodes = [], newNodes = [];
    var i = 0;
    connections.nodes.forEach(function(node) {
      node.nr = i++;
      var ns = groupFun(node);
      for (var i=0; i<ns.length; i++) {
        var node = ns[i];
        node.group = 2 * node.group;
        nodes.push(node);

        var newNode = {"name": node.name, "id": node.id, "group": node.group + 1};
        newNodes.push(newNode);
      }
    });

    var conn_count = [];
    links.forEach(function(link) {
      var source = nodes[link.source];
      var target = nodes[link.target];
      if (source.group == target.group) {
        target = newNodes[link.target];
      } else if ((source.group + 2) % nrAxes == target.group) {
        source = newNodes[link.source];
      } else if ((source.group + nrAxes - 2) % nrAxes == target.group) {
        target = newNodes[link.target];
      } else {
        console.log("linked nodes not adjacent: " + source.name + " -> " + target.name);
      }

      link.source = source;
      link.target = target;
      link.id = link.source.name + link.target.name;
    });
    for (var i = nodes.length - 1; i > -1; i--) {
      var node = nodes[i];
      if (isNaN(connected[i])) {
        nodes.splice(i, 1);
        newNodes.splice(i, 1);
      }
    }
    var index = [];
    for (var i=0; i<nrAxes; i++) {
      index.push(0);
    };
    nodes = nodes.concat(newNodes);
    nodes.sort(function(a, b) {
      if (a.name < b.name) {
        return -1;
      }
      if (a.name > b.name) {
        return 1;
      }
      return 0;
    });
    nodes.forEach(function(node) {
      node.index = index[node.group]++;
    });
    nodes.forEach(function(node) {
      node.index /= (index[node.group] - 1);
    })

    var groupDiff = .4;
    renderHive(nodes, links, nrAxes, element,
      600,
      color = function(group) {return defaultColor(Math.floor(group / 2));},
      angle = function(group) {
        group = group - groupDiff / 2 - (1 - groupDiff) * (group % 2);
        return group * 2 * Math.PI / nrAxes;
      }
    );
  })
}
