#!/bin/bash

#//tb/1604

FULLPATH="`pwd`/$0"
DIR=`dirname "$FULLPATH"`

src="$DIR"/src
build="$DIR"/_build
archive="$DIR"/archive
doc="$DIR"/doc

jsource=1.6
jtarget=1.6

#linux / osx different mktemp call
TMPFILE=`mktemp 2>/dev/null || mktemp -t /tmp`

JAVAC="javac -source $jsource -target $jtarget -nowarn"

NOW=`date +"%s"`

#========================================================================
checkAvail()
{
	which "$1" >/dev/null 2>&1
	ret=$?
	if [ $ret -ne 0 ]
	then
		echo "tool \"$1\" not found. please install"
		exit 1
	fi
}

#========================================================================
compile_simple-osm-render()
{
	echo "building simple-osm-render (com.jetdrone.map.*)"
	echo "==============================================="

	echo "compiling files in $src/java/main to directory $build ..."

	find "$src/java/main/" -name *.java > "$TMPFILE"

	cp "$archive"/servlet-api-3.1.jar "$build"

	$JAVAC -classpath "$build"/servlet-api-3.1.jar:"$build" -sourcepath "$src/java/main" -d "$build" @"$TMPFILE"

	ln -s "$src"/resources .

	echo "create tiles:"
	echo ""
	echo "java -cp _build com.jetdrone.map.render.MapRender -t --zoom 14 -o tiles testdata/rule.xml testdata/amsterdam.osm"
	echo ""
	echo "this will create 256x256 png tiles (26) for amsterdam.osm in directory tiles (newly created)."
	echo "the first run takes a bit longer (creating amsterdam.osm.idx, ~database of node ids)"
}

#========================================================================
create_javadoc()
{
	echo "creating doc"
	echo "============"

	cd "$src/java/main"
	javadoc -quiet -private -linksource -sourcetab 2 -d "$doc" \
		-classpath .:"$build"/servlet-api-3.1.jar -sourcepath . \
		com.jetdrone.map \
		com.jetdrone.map.index \
		com.jetdrone.map.mercator \
		com.jetdrone.map.render \
		com.jetdrone.map.render.backend \
		com.jetdrone.map.rules \
		com.jetdrone.map.rules.osm \
		com.jetdrone.map.source \
		com.jetdrone.map.source.osm
	cd "$DIR"
}

for tool in java javac jar javadoc date; \
	do checkAvail "$tool"; done

mkdir -p "$build"
rm -rf "$build"/*

compile_simple-osm-render
#create_javadoc

echo ""
echo "done."
