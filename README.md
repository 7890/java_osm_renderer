# java_osm_renderer
render OSM tiles. initially copied from https://code.google.com/archive/p/simple-osm-render/

```
"Simple and fast multi threaded OSM tile renderer inspired by libmemphis. It is fully threaded and can draw 60 tiles or more per second on old laptops."

this edition:

-remove need for package com.objectplanet.image (import com.objectplanet.image.PngEncoder), replace with javax.imageio
-reduce testdata set to "amsterdam.osm"
-add servlet-api-3.1.jar
-add build file (make.sh)
-create javadoc

```
