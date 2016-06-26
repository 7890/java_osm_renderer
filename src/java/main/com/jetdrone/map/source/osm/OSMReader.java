package com.jetdrone.map.source.osm;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.jetdrone.map.BoundingBox;
import com.jetdrone.map.MapException;
import com.jetdrone.map.source.Node;
import com.jetdrone.map.source.Way;

public abstract class OSMReader extends DefaultHandler {
	
	Map<Long, Node> nodeidx = new HashMap<Long, Node>(); // Node Hash

	private Node cNode = null;
	private Way cWay = null;
	
	private static final Logger LOG = Logger.getLogger(OSMReader.class.getName());

	private long generic_parse_counter=0;
	private long tag_parse_counter=0;
	private long node_parse_counter=0;
	private long way_parse_counter=0;
	private long waynode_parse_counter=0;

	public void load(InputStream stream) throws MapException {
		try {
			// Create a builder factory
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setValidating(false);
			// Create the builder and parse the file
			factory.newSAXParser().parse(new BufferedInputStream(stream), this);
		
			nodeidx.clear();
			nodeidx = null;
		} catch(SAXException e) {
			throw new MapException(e);
		} catch(IOException e) {
			throw new MapException(e);
		} catch(ParserConfigurationException e) {
			throw new MapException(e);
		}
	}

	@Override
	@SuppressWarnings("boxing")
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
//		LOG.fine("start element " + qName);
		// Parsing Bounds
		if ("bounds".equals(qName)) {
//			LOG.fine("Parsing bounds");
			initIndex(new BoundingBox(
					Double.parseDouble(attributes.getValue("minlat")),
					Double.parseDouble(attributes.getValue("minlon")),
					Double.parseDouble(attributes.getValue("maxlat")),
					Double.parseDouble(attributes.getValue("maxlon"))));
		}
		// Parsing bound (OSM 0.6) using osmosis
		if ("bound".equals(qName)) {
//			LOG.fine("Parsing bound");
			String[] box = attributes.getValue("box").split(",");
			initIndex(new BoundingBox(
					Double.parseDouble(box[0]),
					Double.parseDouble(box[1]),
					Double.parseDouble(box[2]),
					Double.parseDouble(box[3])));
		}
		// Parsing Node
		else if ("node".equals(qName)) {

//			LOG.fine("Parsing Node");
//			int id = Integer.parseInt(attributes.getValue("id"));
/*
using long instead of int prevents

Exception in thread "main" java.lang.NumberFormatException: For input string: "2147489458"
	at java.lang.NumberFormatException.forInputString(NumberFormatException.java:65)
	at java.lang.Integer.parseInt(Integer.java:495)
	at java.lang.Integer.parseInt(Integer.java:527)
	at com.jetdrone.map.source.osm.OSMReader.startElement(OSMReader.java:75)
	at com.sun.org.apache.xerces.internal.parsers.AbstractSAXParser.startElement(AbstractSAXParser.java:509)
	at com.sun.org.apache.xerces.internal.parsers.AbstractXMLDocumentParser.emptyElement(AbstractXMLDocumentParser.java:182)

ids in OSM data seem to be too loarge for int

*/

			long id = Long.parseLong(attributes.getValue("id"));

			cNode = new Node(
					Double.parseDouble(attributes.getValue("lat")),
					Double.parseDouble(attributes.getValue("lon")));

			// Insert Node local hash
			nodeidx.put(id, cNode);
			node_parse_counter++;
		}
		// Parsing Tags
		else if ("tag".equals(qName)) {
//			LOG.fine("Parsing Tag");

			if (cNode == null && cWay == null) // End if there is nothing to add the tag to
			{
				return;
			}
			String k, v;
			k = attributes.getValue("k").intern();
			v = attributes.getValue("v").intern();
			// attributes.getValue("created_by");
			// attributes.getValue("source");
			if ("layer".equals(k)) {
				int layer;
				try {
					if(v.charAt(0) == '+') v = v.substring(1);
					layer = Integer.parseInt(v);
				} catch(NumberFormatException nfe) {
					LOG.severe("Not a number: " + v);
					layer = 1;
				}
				if (cNode != null) {
					cNode.setLayer(layer);
				} else {
					cWay.setLayer(layer);
				}
			} else if ("name".equals(k)) {
				if (cWay != null) {
					cWay.setName(v);
				}
			}

			if (cNode != null) {
				cNode.insertTag(k, v);
			}
			else if (cWay != null) {
				cWay.insertTag(k, v);
			}

			tag_parse_counter++;
		}
		// Parsing Way
		else if ("way".equals(qName)) {
//			LOG.fine("Parsing Way");
			cWay = new Way();
			way_parse_counter++;
		}
		// Parsing WayNode
		else if ("nd".equals(qName)) {
//			LOG.fine("Parsing Nd");
			long ref = Long.parseLong(attributes.getValue("ref"));

			if (ref != 0) {
				Node n;

				n = nodeidx.get(ref);
				if (n == null) {
					LOG.severe("No node with reference " + ref + " found!");
					return;
				}

				// Insert WayNode
				cWay.addWayNode(n);
				cNode = null;
			}
			waynode_parse_counter++;
		}

		generic_parse_counter++;
		if(generic_parse_counter % 100000==0)
		{
			LOG.info("progress (every 100000 parse events): loaded "+node_parse_counter+" nodes, "
				+tag_parse_counter+" tags, "
				+way_parse_counter+" ways, "
				+waynode_parse_counter+" waynodes."
			);
		}
	}//end startElement()

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
//		LOG.fine("end element");
		if ("node".equals(qName)) {
			if(cNode != null) {
				indexNode(cNode);
			}
			cNode = null;
		} else if ("way".equals(qName)) {
			if(cWay != null) {
				indexWay(cWay);
			}
			cWay = null;
		}
	}
	
	public abstract void initIndex(BoundingBox bbox);
	
	public abstract void indexWay(Way w);
	
	public abstract void indexNode(Node n);
}
