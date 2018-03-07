//var host = "144.6.226.22";
var host = "localhost";

/**
 * Create new vector source from Geoserver WFS
 * 
 * @param {String}
 *           layer
 * @param {String}
 *           callback
 * @returns {ol.source.ServerVector}
 */
function createWFSSource(layer, callback)
{
	return new ol.source.ServerVector({
		format : new ol.format.GeoJSON(),
		loader : function(extent, resolution, projection)
		{
			var url = 'http://' + host + ':8080/geoserver/opengeo/ows?service=WFS'
					+ '&version=1.0.0&request=GetFeature&typename=opengeo:' + layer
					+ '&outputFormat=text/javascript' + '&format_options=callback:'
					+ callback;
			$.ajax({
				url : url,
				dataType : 'jsonp'
			});
		},
		strategy : ol.loadingstrategy.bbox,
		projection : 'EPSG:3857'
	});
}

// For cluster colour
var colorscheme = [ 'rgb(26,150,65)', 'rgb(166,217,106)', 'rgb(253,174,97)',
		'rgb(215,25,28)'
];

// For cluster as nodes; just to know the membership of clusters and
// differentiate between clusters
var nodescolor = [ 'rgb(141,211,199)', 'rgb(255,255,179)', 'rgb(190,186,218)',
		'rgb(251,128,114)', 'rgb(128,177,211)', 'rgb(253,180,98)',
		'rgb(179,222,105)', 'rgb(252,205,229)', 'rgb(217,217,217)',
		'rgb(188,128,189)', 'rgb(204,235,197)', 'rgb(255,237,111)'
];

var wmsURL = 'http://' + host + ':8080/geoserver/wms';

// Accident node map layer
var nodesWMSLayer = new ol.layer.Tile({
	source : new ol.source.TileWMS({
		url : wmsURL,
		params : {
			'FORMAT' : 'image/png',
			'SRS' : 'EPSG:4326',
			'LAYERS' : 'opengeo:nodes'
		}
	}),
	title : 'Raw Nodes',
	visible : true
});

/* ================================================================= */
/* =========================== Base maps =========================== */
/* ================================================================= */

var baseMapExtent = ol.proj.transformExtent([ 141, -34, 150, -39
], 'EPSG:4326', 'EPSG:3857');

var osmLayer = new ol.layer.Tile({
	extent : baseMapExtent,
	title : 'OSM',
	visible : false,
	source : new ol.source.OSM()
});

var mapQuest = new ol.layer.Tile({
	source : new ol.source.MapQuest({
		layer : 'osm'
	}),
	title : 'MapQuest',
	extent : baseMapExtent,
	visible : true
});

var waterColourLayer = new ol.layer.Tile({
	extent : baseMapExtent,
	title : 'Water Colour',
	visible : false,
	source : new ol.source.Stamen({
		layer : 'watercolor'
	})
});

var satelliteLayer = new ol.layer.Tile({
	extent : baseMapExtent,
	title : 'Satellite',
	visible : false,
	source : new ol.source.MapQuest({
		layer : 'sat'
	})
});

var tonerLayer = new ol.layer.Tile({
	source : new ol.source.Stamen({
		layer : 'toner'
	}),
	title : 'Toner',
	visible : false,
	extent : baseMapExtent
});
/* ================================================================= */

/* ========================================================================= */
/* ============================= Cluster Layer ============================= */
/* ========================================================================= */
var rangeArray = [];
function clusterStyleFunction(feature, resolution)
{
	var style;
	// var rank = feature.get('count');
	var rank = feature.get('density');

	if (rank < rangeArray[0])
	{
		style = [ new ol.style.Style({
			fill : new ol.style.Fill({
				color : colorscheme[0],
				weight : 10
			}),
			stroke : new ol.style.Stroke({
				color : colorscheme[0],
				width : 1,
				weight : 4
			})
		})
		];
	}
	else if (rank < rangeArray[1])
	{
		style = [ new ol.style.Style({
			fill : new ol.style.Fill({
				color : colorscheme[1],
				weight : 10
			}),
			stroke : new ol.style.Stroke({
				color : colorscheme[1],
				width : 1,
				weight : 4
			})
		})
		];
	}
	else if (rank < rangeArray[2])
	{
		style = [ new ol.style.Style({
			fill : new ol.style.Fill({
				color : colorscheme[2],
				weight : 10
			}),
			stroke : new ol.style.Stroke({
				color : colorscheme[2],
				width : 1,
				weight : 4
			})
		})
		];
	}
	else
	{
		style = [ new ol.style.Style({
			fill : new ol.style.Fill({
				color : colorscheme[3],
				weight : 10
			}),
			stroke : new ol.style.Stroke({
				color : colorscheme[3],
				width : 1,
				weight : 4
			})
		})
		];
	}

	return style;
}

var clusterSource = createWFSSource('convex_hull', 'loadClusterFeatures');

var loadClusterFeatures = function(response)
{
	clusterSource.addFeatures(clusterSource.readFeatures(response));
};

var clusterLayer = new ol.layer.Vector({
	source : clusterSource,
	style : clusterStyleFunction,
	opacity : 0.8,
	title : 'Cluster Convex Hull'
});

/* ========================================================================= */
/* ========================================================================= */

/* ========================================================================= */
/* ========================== Road Cluster Layer =========================== */
/* ========================================================================= */

var rcSource = createWFSSource('road_cluster', 'loadRCFeatures');

var loadRCFeatures = function(response)
{
	rcSource.addFeatures(rcSource.readFeatures(response));
};

var rcLayer = new ol.layer.Vector({
	source : rcSource,
	style : clusterStyleFunction,
	title : 'Road Cluster',
	visible : false
});

/* ========================================================================= */
/* =========================== Nodes Vector Layer ========================== */
/* ========================================================================= */

var nodesSource = createWFSSource('nodes', 'loadNodesFeatures');

var loadNodesFeatures = function(response)
{
	if (nodesSource.getFeatures().length == 0)
		nodesSource.addFeatures(nodesSource.readFeatures(response));
};

function nodesStyleFunction(feature, resolution)
{
	var style;
	var cluster = feature.get('cluster');
	var radiusSize = 2;

	if (cluster > 0)
	{
		var i = cluster % nodescolor.length;
		style = [ new ol.style.Style({
			image : new ol.style.Circle({
				radius : radiusSize,
				fill : new ol.style.Fill({
					color : nodescolor[i]
				}),
				stroke : new ol.style.Stroke({
					color : 'black',
					width : 1
				})
			})
		})
		];
	}
	else
	{

		var fill = new ol.style.Fill({
			color : 'rgba(255,255,255,0.4)'
		});
		var stroke = new ol.style.Stroke({
			color : '#3399CC',
			width : 1
		});
		style = [ new ol.style.Style({
			image : new ol.style.Circle({
				fill : fill,
				stroke : stroke,
				radius : radiusSize
			}),
			fill : fill,
			stroke : stroke
		})
		];
	}

	return style;
}

var nodesLayer = new ol.layer.Vector({
	source : nodesSource,
	style : nodesStyleFunction,
	title : 'Accident Nodes'
});

var nodesLayer = new ol.layer.Vector({
	source : nodesSource,
	style : nodesStyleFunction,
	title : 'Accident Nodes',
	visible : false
});

/* ========================================================================= */
/* ========================================================================= */

var large_roads_mornington = createWFSSource('large_roads_mornington',
		'loadLargeRoadsMornington');

var loadLargeRoadsMornington = function(response)
{
	if (large_roads_mornington.getFeatures().length == 0)
		large_roads_mornington.addFeatures(large_roads_mornington
				.readFeatures(response));
};

var lrmLayer = new ol.layer.Vector({
	source : large_roads_mornington,
	title : 'Weighted Large Roads',
	visible : false
});

/* Heatmap */
var nodes_heatmap = createWFSSource('nodes_weight', 'loadNodesWeightFeatures');

var loadNodesWeightFeatures = function(response)
{
	if (nodes_heatmap.getFeatures().length == 0)
		nodes_heatmap.addFeatures(nodes_heatmap.readFeatures(response));
};

var blur = $('#blur');
var radius = $('#radius');
var opacity = $('#opacity');

var heatmap = new ol.layer.Heatmap({
	source : nodes_heatmap,
	title : 'Heatmap',
	blur : parseInt(blur.val(), 10),
	radius : parseInt(radius.val(), 10),
	opacity : parseFloat(opacity.val()),
	visible : false
});

blur.on('input', function()
{
	heatmap.setBlur(parseInt(blur.val(), 10));
});

radius.on('input', function()
{
	heatmap.setRadius(parseInt(radius.val(), 10));
});

opacity.on('input', function()
{
	heatmap.setOpacity(parseFloat(opacity.val()));
});
/* ======= */

var baseMapGroup = new ol.layer.Group({
	'title' : 'Base maps',
	layers : [ tonerLayer, osmLayer, mapQuest, waterColourLayer, satelliteLayer
	]
})

var overlayGroup = new ol.layer.Group({
	'title' : 'Overlays',
	layers : [ lrmLayer, nodesWMSLayer, nodesLayer, clusterLayer, rcLayer,
			heatmap,
	]
})

/**
 * Main map object
 */
var olMap = new ol.Map({
	controls : ol.control.defaults().extend([ new ol.control.FullScreen()
	]),
	target : 'map',
	renderer : 'canvas',
	layers : [ baseMapGroup
	],
	view : new ol.View({
		center : [ 16188662.578498561, -4598335.902866491
		],
		minZoom : 1,
		maxZoom : 19,
		zoom : 9
	}),
	controls : ol.control.defaults({}).extend([ new ol.control.ScaleLine({
		'geodesic' : true,
		'units' : 'metric'
	}), new ol.control.ZoomSlider({
		'className' : 'ol-zoomslider'
	})
	])
});

var layerSwitcher = new ol.control.LayerSwitcher();
olMap.addControl(layerSwitcher);

olMap.on('change:size', function(e)
{
	$("#map_size").html(JSON.stringify(olMap.getSize()));
})

var mapView = olMap.getView();

mapView.on('change:resolution', function(e)
{
	$("#map_res").html(mapView.getResolution());
});

mapView.on('change:center', function(e)
{
	$("#map_center").html(JSON.stringify(mapView.getCenter()));
});

olMap.on('pointermove', function(e)
{
	var pixel = olMap.getEventPixel(e.originalEvent);
	displayFeatureInfo(e);
});

// Create an ol.Overlay with a popup anchored to the map
var popup = new ol.Overlay({
	element : document.getElementById('popup')
});
olMap.addOverlay(popup);

var displayFeatureInfo = function(evt)
{
	var pixel = evt.pixel;
	var fl = olMap.forEachFeatureAtPixel(pixel, function(feature, layer)
	{
		return {
			'feature' : feature,
			'layer' : layer
		};
	});
	var info = document.getElementById('info');

	if (fl)
	{
		var feature = fl.feature;
		var layer = fl.layer;
		if (layer == clusterLayer)
		{
			info.innerHTML = 'Cluster ' + feature.getId() + ': '
					+ feature.get('count') + ' accidents (Density: ' + feature.get('density') + ')';
		}
		else if (layer == heatmap)
		{
			info.innerHTML = 'Heatmap weight: ' + feature.get('weight');
		}
		else
		{
			info.innerHTML = '&nbsp;';
		}
	}
	else
	{
		info.innerHTML = 'pixel: ' + pixel;
	}
};
/** *************************************************************************** */

/*
 * Respond to the cluster button click by sending the parameters to the Java
 * Servlet and do the clustering on the server. After the clustering finishes,
 * refresh the map or output an error message if any.
 */
function dbscan()
{
	var extent = olMap.getView().calculateExtent(olMap.getSize());
	var params = new Object();
	params.eps = $('#eps').val();
	params.minpts = $('#minpts').val();
	params.extent = extent;

	var start = new Date().getTime();
	$.ajax({
		url : "http://" + host + ":8080/RiskMap/DBSCANServlet",
		type : 'POST',
		dataType : 'json',
		data : JSON.stringify(params),
		contentType : 'application/json',
		mimeType : 'application/json',
		success : function(data)
		{
			clusterPostProcessing(data, start);
		},
		error : function(data, status, er)
		{
			alert("Error: " + JSON.stringify(data) + " Status: " + status + " er:"
					+ er);
		}
	});
}

function hdbscan()
{
	var extent = olMap.getView().calculateExtent(olMap.getSize());

	var params = new Object();
	params.minpts = $('#minpts-h').val();
	params.mincls = $('#mincls').val();
	params.extent = extent;
	var pixel = $('#pixel').val();
	params.res = mapView.getResolution();
	params.pixel = pixel;
	params.usemode = document.getElementById("usemode").checked;
	params.small_roads_threshold = $('#small_roads_threshold').val();

	var start = new Date().getTime();
	$.ajax({
		url : "http://" + host + ":8080/RiskMap/HDBSCANServlet",
		type : 'POST',
		dataType : 'json',
		data : JSON.stringify(params),
		contentType : 'application/json',
		mimeType : 'application/json',
		success : function(data)
		{
			clusterPostProcessing(data, start);
		},
		error : function(data, status, er)
		{
			alert("Error: " + JSON.stringify(data) + " Status: " + status + " er:"
					+ er);
		}
	});
}

function clusterPostProcessing(data, start)
{
	var end = new Date().getTime();
	var time = end - start;
	rangeArray = data.rangeArray;
	setClusterLegend();
	$('#exec_time').html(time);
	$('#res_data').html(JSON.stringify(data));

	// Refresh map
	refreshLayers();
}

function refreshLayers()
{
	clusterSource.clear();
	rcSource.clear();
	nodesSource.clear();
	nodes_heatmap.clear();
}

function setClusterLegend()
{
	var html = "";
	var num = 0;
	for (var i = 0; i < 4; i++)
	{
		var htmlval = rangeArray[i].toFixed(3);
		html += "<div id='key" + (i + 1)
				+ "' class='keyitem' style='background-color: " + colorscheme[i]
				+ ";'>" + num + "-" + htmlval + "</div>";
		num = htmlval;
	}
	$("#keyitems").html(html);
}

$(document).ready(function()
{
	$.getJSON('./conf.json', function(data)
	{
		rangeArray = data["rangeArray"];
		setClusterLegend();

		$('#res_data').html(JSON.stringify(data));
	});

	olMap.addLayer(overlayGroup);

	$("#map_size").html(JSON.stringify(olMap.getSize()));
	$("#map_res").html(mapView.getResolution());
	$("#map_center").html(JSON.stringify(mapView.getCenter()));
});
