/* This file is a part of the sqlHawk project.
 * http://timabell.github.com/sqlHawk/
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
package uk.co.timwise.sqlhawk.html;

import java.io.File;
import java.io.IOException;

import uk.co.timwise.sqlhawk.model.Table;
import uk.co.timwise.sqlhawk.util.LineWriter;

public class HtmlTableDiagrammer extends HtmlDiagramFormatter {
	private static HtmlTableDiagrammer instance = new HtmlTableDiagrammer();

	private HtmlTableDiagrammer() {
	}

	public static HtmlTableDiagrammer getInstance() {
		return instance;
	}

	public void write(Table table, File diagramDir, LineWriter html) {
		File oneDegreeDotFile = new File(diagramDir, table.getName() + ".1degree.dot");
		File oneDegreeDiagramFile = new File(diagramDir, table.getName() + ".1degree.png");
		File twoDegreesDotFile = new File(diagramDir, table.getName() + ".2degrees.dot");
		File twoDegreesDiagramFile = new File(diagramDir, table.getName() + ".2degrees.png");
		File impliedDotFile = new File(diagramDir, table.getName() + ".implied2degrees.dot");
		File impliedDiagramFile = new File(diagramDir, table.getName() + ".implied2degrees.png");

		try {
			Dot dot = getDot();
			if (dot == null) {
				return; // getDot() will already have warned user so just pass
			}

			String map = dot.generateDiagram(oneDegreeDotFile, oneDegreeDiagramFile);

			html.write("<br><form action='get'><b>Close relationships");
			if (twoDegreesDotFile.exists()) {
				html.writeln("</b><span class='degrees' id='degrees' title='Detail diminishes with increased separation from " + table.getName() + "'>");
				html.write("&nbsp;within <label for='oneDegree'><input type='radio' name='degrees' id='oneDegree' checked>one</label>");
				html.write("  <label for='twoDegrees'><input type='radio' name='degrees' id='twoDegrees'>two degrees</label> of separation");
				html.write("</span><b>:</b>");
				html.writeln("</form>");
			} else {
				html.write(":</b></form>");
			}
			html.write(map);
			map = null;
			html.writeln("  <a name='diagram'><img id='oneDegreeImg' src='../diagrams/" + oneDegreeDiagramFile.getName() + "' usemap='#oneDegreeRelationshipsDiagram' class='diagram' border='0' alt='' align='left'></a>");

			if (impliedDotFile.exists()) {
				html.writeln(dot.generateDiagram(impliedDotFile, impliedDiagramFile));
				html.writeln("  <a name='diagram'><img id='impliedTwoDegreesImg' src='../diagrams/" + impliedDiagramFile.getName() + "' usemap='#impliedTwoDegreesRelationshipsDiagram' class='diagram' border='0' alt='' align='left'></a>");
			} else {
				impliedDotFile.delete();
				impliedDiagramFile.delete();
			}
			if (twoDegreesDotFile.exists()) {
				html.writeln(dot.generateDiagram(twoDegreesDotFile, twoDegreesDiagramFile));
				html.writeln("  <a name='diagram'><img id='twoDegreesImg' src='../diagrams/" + twoDegreesDiagramFile.getName() + "' usemap='#twoDegreesRelationshipsDiagram' class='diagram' border='0' alt='' align='left'></a>");
			} else {
				twoDegreesDotFile.delete();
				twoDegreesDiagramFile.delete();
			}
		} catch (Dot.DotFailure dotFailure) {
			logger.warning("Dot error while writing html" + dotFailure);
		} catch (IOException ioExc) {
			logger.warning("IO error while writing html" + ioExc);
		}
	}
}
