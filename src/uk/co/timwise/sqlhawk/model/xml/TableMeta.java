/* This file is a part of the sqlHawk project.
 * http://github.com/timabell/sqlHawk
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package uk.co.timwise.sqlhawk.model.xml;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Additional metadata about a table as expressed in XML instead of from
 * the database.
 */
public class TableMeta {
	private final String name;
	private final String comments;
	private final List<TableColumnMeta> columns = new ArrayList<TableColumnMeta>();
	private final String remoteSchema;
	private static final Logger logger = Logger.getLogger(TableMeta.class.getName());

	TableMeta(Node tableNode) {
		NamedNodeMap attribs = tableNode.getAttributes();

		name = attribs.getNamedItem("name").getNodeValue();

		Node commentNode = attribs.getNamedItem("comments");
		if (commentNode != null) {
			String tmp = commentNode.getNodeValue().trim();
			comments = tmp.length() == 0 ? null : tmp;
		} else {
			comments = null;
		}

		Node remoteSchemaNode = attribs.getNamedItem("remoteSchema");
		if (remoteSchemaNode != null) {
			remoteSchema = remoteSchemaNode.getNodeValue().trim();
		} else {
			remoteSchema = null;
		}

		logger.fine("Found XML table metadata for " + name +
				" remoteSchema: " + remoteSchema +
				" comments: " + comments);

		NodeList columnNodes = ((Element)tableNode.getChildNodes()).getElementsByTagName("column");

		for (int i = 0; i < columnNodes.getLength(); ++i) {
			Node colNode = columnNodes.item(i);
			columns.add(new TableColumnMeta(colNode));
		}
	}

	public String getName() {
		return name;
	}

	public String getComments() {
		return comments;
	}

	public List<TableColumnMeta> getColumns() {
		return columns;
	}

	public String getRemoteSchema() {
		return remoteSchema;
	}
}