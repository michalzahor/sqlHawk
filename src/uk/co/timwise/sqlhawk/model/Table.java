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
package uk.co.timwise.sqlhawk.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import uk.co.timwise.sqlhawk.util.CaseInsensitiveMap;


/**
 * A <code>Table</code> is one of the basic building blocks.
 * It holds everything about the database table's metadata.
 */
public class Table implements Comparable<Table> {
	private String schema;
	protected String name;
	protected final CaseInsensitiveMap<TableColumn> columns = new CaseInsensitiveMap<TableColumn>();
	private final List<TableColumn> primaryKeys = new ArrayList<TableColumn>();
	private final CaseInsensitiveMap<ForeignKeyConstraint> foreignKeys = new CaseInsensitiveMap<ForeignKeyConstraint>();
	private final CaseInsensitiveMap<TableIndex> indexes = new CaseInsensitiveMap<TableIndex>();
	private       Object id;
	private final Map<String, String> checkConstraints = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
	private Long numRows;
	private String comments;
	private int maxChildren;
	private int maxParents;

	public Table() {}

	/**
	 * Construct a table that knows everything about the database table's metadata
	 */
	public Table(String schema, String name, String comments) {
		this.schema = schema;
		this.name = name;
	}

	/**
	 * Get the foreign keys associated with this table
	 *
	 * @return
	 */
	public Collection<ForeignKeyConstraint> getForeignKeys() {
		return Collections.unmodifiableCollection(foreignKeys.values());
	}

	/**
	 * Add a check constraint to the table
	 * (no real details, just name and textual representation)
	 *
	 * @param constraintName
	 * @param text
	 */
	public void addCheckConstraint(String constraintName, String text) {
		checkConstraints.put(constraintName, text);
	}

	/**
	 * @param primaryColumn
	 */
	public void setPrimaryColumn(TableColumn primaryColumn) {
		primaryKeys.add(primaryColumn);
	}

	/**
	 * @param indexName
	 * @return
	 */
	public TableIndex getIndex(String indexName) {
		return indexes.get(indexName);
	}

	/**
	 * Returns the schema that the table belongs to
	 *
	 * @return
	 */
	public String getSchema() {
		return schema;
	}

	/**
	 * Returns the name of the table
	 *
	 * @return
	 */
	public String getName() {
		return name;
	}

	/**
	 * Object IDs are useful for tables such as DB/2 that many times
	 * give error messages based on object ID and not name
	 *
	 * @param id
	 */
	public void setId(Object id) {
		this.id = id;
	}

	/**
	 * @see #setId(Object)
	 *
	 * @return
	 */
	public Object getId() {
		return id;
	}

	/**
	 * Returns the check constraints associated with this table
	 *
	 * @return
	 */
	public Map<String, String> getCheckConstraints() {
		return checkConstraints;
	}

	/**
	 * Returns the indexes that are applied to this table
	 *
	 * @return
	 */
	public Set<TableIndex> getIndexes() {
		return new HashSet<TableIndex>(indexes.values());
	}

	/**
	 * Returns a collection of table columns that have been identified as "primary"
	 *
	 * @return
	 */
	public List<TableColumn> getPrimaryColumns() {
		return primaryKeys;
	}

	/**
	 * @return Comments associated with this table, or <code>null</code> if none.
	 */
	public String getComments() {
		return comments;
	}

	/**
	 * Sets the comments that are associated with this table
	 *
	 * @param comments
	 */
	public void setComments(String comments) {
		String cmts = (comments == null || comments.trim().length() == 0) ? null : comments.trim();

		// MySQL's InnoDB engine does some insane crap of storing erroneous details in
		// with table comments.  Here I attempt to strip the "crap" out without impacting
		// other databases.  Ideally this should happen in selectColumnCommentsSql (and
		// therefore isolate it to MySQL), but it's a bit too complex to do cleanly.
		if (cmts != null) {
			int crapIndex = cmts.indexOf("; InnoDB free: ");
			if (crapIndex == -1)
				crapIndex = cmts.startsWith("InnoDB free: ") ? 0 : -1;
			if (crapIndex != -1) {
				cmts = cmts.substring(0, crapIndex).trim();
				cmts = cmts.length() == 0 ? null : cmts;
			}
		}

		this.comments = cmts;
	}

	/**
	 * Returns the {@link TableColumn} with the given name, or <code>null</code>
	 * if it doesn't exist
	 *
	 * @param columnName
	 * @return
	 */
	public TableColumn getColumn(String columnName) {
		return columns.get(columnName);
	}

	/**
	 * Returns <code>List</code> of <code>TableColumn</code>s in ascending column number order.
	 *
	 * @return
	 */
	public List<TableColumn> getColumns() {
		Set<TableColumn> sorted = new TreeSet<TableColumn>(new ByColumnIdComparator());
		sorted.addAll(columns.values());
		return new ArrayList<TableColumn>(sorted);
	}

	/**
	 * Returns <code>true</code> if this table references no other tables..<p/>
	 * Used in dependency analysis.
	 * @return
	 */
	public boolean isRoot() {
		for (TableColumn column : columns.values()) {
			if (column.isForeignKey()) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Returns <code>true</code> if this table is referenced by no other tables.<p/>
	 * Used in dependency analysis.
	 * @return
	 */
	public boolean isLeaf() {
		for (TableColumn column : columns.values()) {
			if (!column.getChildren().isEmpty()) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Returns the maximum number of parents that this table has had before
	 * any had been removed during dependency analysis
	 *
	 * @return
	 */
	public int getMaxParents() {
		return maxParents;
	}

	/**
	 * Notification that's called to indicate that a parent has been added to
	 * this table
	 */
	public void addedParent() {
		maxParents++;
	}

	/**
	 * "Unlink" all of the parent tables from this table
	 */
	public void unlinkParents() {
		for (TableColumn column : columns.values()) {
			column.unlinkParents();
		}
	}

	/**
	 * Returns the maximum number of children that this table has had before
	 * any had been removed during dependency analysis
	 *
	 * @return
	 */
	public int getMaxChildren() {
		return maxChildren;
	}

	/**
	 * Notification that's called to indicate that a child has been added to
	 * this table
	 */
	public void addedChild() {
		maxChildren++;
	}

	/**
	 * "Unlink" all of the child tables from this table
	 */
	public void unlinkChildren() {
		for (TableColumn column : columns.values()) {
			column.unlinkChildren();
		}
	}

	/**
	 * Remove a single self referencing constraint if one exists.
	 *
	 * @return
	 */
	public ForeignKeyConstraint removeSelfReferencingConstraint() {
		return remove(getSelfReferencingConstraint());
	}

	/**
	 * Remove the specified {@link ForeignKeyConstraint} from this table.<p>
	 *
	 * This is a more drastic removal solution that was proposed by Remke Rutgers
	 *
	 * @param constraint
	 */
	private ForeignKeyConstraint remove(ForeignKeyConstraint constraint) {
		if (constraint != null) {
			for (int i = 0; i < constraint.getChildColumns().size(); i++) {
				TableColumn childColumn = constraint.getChildColumns().get(i);
				TableColumn parentColumn = constraint.getParentColumns().get(i);
				childColumn.removeParent(parentColumn);
				parentColumn.removeChild(childColumn);
			}
		}
		return constraint;
	}

	/**
	 * Return a self referencing constraint if one exists
	 *
	 * @return
	 */
	private ForeignKeyConstraint getSelfReferencingConstraint() {
		for (TableColumn column : columns.values()) {
			for (TableColumn parentColumn : column.getParents()) {
				if (compareTo(parentColumn.getTable()) == 0) {
					return column.getParentConstraint(parentColumn);
				}
			}
		}
		return null;
	}

	/**
	 * Remove any non-real foreign keys
	 *
	 * @return
	 */
	public List<ForeignKeyConstraint> removeNonRealForeignKeys() {
		List<ForeignKeyConstraint> nonReals = new ArrayList<ForeignKeyConstraint>();

		for (TableColumn column : columns.values()) {
			for (TableColumn parentColumn : column.getParents()) {
				ForeignKeyConstraint constraint = column.getParentConstraint(parentColumn);
				if (constraint != null && !constraint.isReal()) {
					nonReals.add(constraint);
				}
			}
		}

		// remove constraints outside of above loop to prevent
		// concurrent modification exceptions while iterating
		for (ForeignKeyConstraint constraint : nonReals) {
			remove(constraint);
		}

		return nonReals;
	}

	/**
	 * Returns the number of tables that reference this table
	 *
	 * @return
	 */
	public int getNumChildren() {
		int numChildren = 0;

		for (TableColumn column : columns.values()) {
			numChildren += column.getChildren().size();
		}

		return numChildren;
	}

	/**
	 * Returns the number of non-implied children
	 * @return
	 */
	public int getNumNonImpliedChildren() {
		int numChildren = 0;

		for (TableColumn column : columns.values()) {
			for (TableColumn childColumn : column.getChildren()) {
				if (!column.getChildConstraint(childColumn).isImplied())
					++numChildren;
			}
		}

		return numChildren;
	}

	/**
	 * Returns the number of tables that are referenced by this table
	 *
	 * @return
	 */
	public int getNumParents() {
		int numParents = 0;

		for (TableColumn column : columns.values()) {
			numParents += column.getParents().size();
		}

		return numParents;
	}

	/**
	 * Returns the number of non-implied parents
	 *
	 * @return
	 */
	public int getNumNonImpliedParents() {
		int numParents = 0;

		for (TableColumn column : columns.values()) {
			for (TableColumn parentColumn : column.getParents()) {
				if (!column.getParentConstraint(parentColumn).isImplied())
					++numParents;
			}
		}

		return numParents;
	}

	/**
	 * Remove one foreign key constraint.
	 *
	 * <p/>Used during dependency analysis phase.
	 *
	 * @return
	 */
	public ForeignKeyConstraint removeAForeignKeyConstraint() {
		final List<TableColumn> columns = getColumns();
		int numParents = 0;
		int numChildren = 0;
		// remove either a child or parent, choosing which based on which has the
		// least number of foreign key associations (when either gets to zero then
		// the table can be pruned)
		for (TableColumn column : columns) {
			numParents += column.getParents().size();
			numChildren += column.getChildren().size();
		}

		for (TableColumn column : columns) {
			ForeignKeyConstraint constraint;
			if (numParents <= numChildren)
				constraint = column.removeAParentFKConstraint();
			else
				constraint = column.removeAChildFKConstraint();
			if (constraint != null)
				return constraint;
		}

		return null;
	}

	/**
	 * Returns <code>true</code> if this is a view, <code>false</code> otherwise
	 *
	 * @return
	 */
	public boolean isView() {
		return false;
	}

	/**
	 * Returns <code>true</code> if this table is remote (in another schema), <code>false</code> otherwise
	 * @return
	 */
	public boolean isRemote() {
		return false;
	}

	/**
	 * Returns the number of rows contained in this table, or -1 if unable to determine
	 * the number of rows.
	 *
	 * @return
	 */
	public long getNumRows() {
		return numRows == null ? 0 : numRows;
	}

	/**
	 * Set the number of rows in this table
	 *
	 * @param numRows
	 */
	public void setNumRows(long numRows) {
		this.numRows = numRows;
	}

	@Override
	public String toString() {
		return getName();
	}

	/**
	 * Returns <code>true</code> if this table has no relationships
	 *
	 * @param withImpliedRelationships boolean
	 * @return boolean
	 */
	public boolean isOrphan(boolean withImpliedRelationships) {
		if (withImpliedRelationships)
			return getMaxParents() == 0 && getMaxChildren() == 0;

		for (TableColumn column : columns.values()) {
			for (TableColumn parentColumn : column.getParents()) {
				if (!column.getParentConstraint(parentColumn).isImplied())
					return false;
			}
			for (TableColumn childColumn : column.getChildren()) {
				if (!column.getChildConstraint(childColumn).isImplied())
					return false;
			}
		}
		return true;
	}

	/**
	 * Compare this table to another table.
	 * Results are based on 1: identity, 2: table name, 3: schema name<p/>
	 *
	 * This implementation was put in place to deal with analyzing multiple
	 * schemas that contain identically named tables.
	 *
	 * @see {@link Comparable#compareTo(Object)}
	 */
	public int compareTo(Table other) {
		if (other == this)  // fast way out
			return 0;

		int rc = getName().compareToIgnoreCase(other.getName());
		if (rc == 0) {
			// should only get here if we're dealing with cross-schema references (rare)
			String ours = getSchema();
			String theirs = other.getSchema();
			if (ours != null && theirs != null)
				rc = ours.compareToIgnoreCase(theirs);
			else if (ours == null)
				rc = -1;
			else
				rc = 1;
		}

		return rc;
	}

	/**
	 * Implementation of {@link Comparator} that sorts {@link TableColumn}s
	 * by {@link TableColumn#getId() ID} (ignored if <code>null</code>)
	 * followed by {@link TableColumn#getName() Name}.
	 */
	private static class ByColumnIdComparator implements Comparator<TableColumn> {
		public int compare(TableColumn column1, TableColumn column2) {
			if (column1.getId() == null || column2.getId() == null)
				return column1.getName().compareToIgnoreCase(column2.getName());
			if (column1.getId() instanceof Number)
				return ((Number)column1.getId()).intValue() - ((Number)column2.getId()).intValue();
			return column1.getId().toString().compareToIgnoreCase(column2.getId().toString());
		}
	}

	public CaseInsensitiveMap<TableColumn> getColumnMap() {
		return columns;
	}

	public ForeignKeyConstraint getForeignKey(String fkName) {
		return foreignKeys.get(fkName);
	}

	public void addForeignKey(String fkName, ForeignKeyConstraint foreignKey) {
		foreignKeys.put(fkName, foreignKey);
	}

	public void addIndex(String name, TableIndex index) {
		this.indexes.put(name, index);
	}
}