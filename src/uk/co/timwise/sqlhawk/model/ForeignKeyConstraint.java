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
package uk.co.timwise.sqlhawk.model;

import static java.sql.DatabaseMetaData.importedKeyCascade;
import static java.sql.DatabaseMetaData.importedKeyNoAction;
import static java.sql.DatabaseMetaData.importedKeyRestrict;
import static java.sql.DatabaseMetaData.importedKeySetNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a <a href='http://en.wikipedia.org/wiki/Foreign_key'>
 * Foreign Key Constraint</a> that "ties" a child table to a parent table
 * via foreign and primary keys.
 */
public class ForeignKeyConstraint implements Comparable<ForeignKeyConstraint> {
	private final String name;
	private Table parentTable;
	private final List<TableColumn> parentColumns = new ArrayList<TableColumn>();
	private final Table childTable;
	private final List<TableColumn> childColumns = new ArrayList<TableColumn>();
	private final int deleteRule;
	private final int updateRule;
	private final static Logger logger = Logger.getLogger(ForeignKeyConstraint.class.getName());

	/**
	 * Construct a foreign key for the specified child table.
	 * Relationship details will be added later.
	 *
	 * @param child
	 * @param name
	 * @param deleteRule
	 */
	public ForeignKeyConstraint(Table child, String name, int updateRule, int deleteRule) {
		this.name = name; // implied constraints will have a null name and override getName()
		logger.finer("Adding foreign key constraint '" + getName() + "' to " + child);
		childTable = child;
		this.deleteRule = deleteRule;
		this.updateRule = updateRule;
	}

	/**
	 * This constructor is intended for use <b>after</b> all of the tables have been
	 * found in the system.  One impact of using this constructor is that it will
	 * "glue" the two tables together through their columns.
	 *
	 * @param parentColumn
	 * @param childColumn
	 */
	public ForeignKeyConstraint(TableColumn parentColumn, TableColumn childColumn,
			int updateRule, int deleteRule) {
		this(childColumn.getTable(), null, updateRule, deleteRule);

		addChildColumn(childColumn);
		addParentColumn(parentColumn);

		childColumn.addParent(parentColumn, this);
		parentColumn.addChild(childColumn, this);
	}

	/**
	 * Same as {@link #ForeignKeyConstraint(TableColumn, TableColumn, int, int)},
	 * but defaults updateRule and deleteRule to
	 * {@link java.sql.DatabaseMetaData#importedKeyNoAction}.
	 *
	 * @param parentColumn
	 * @param childColumn
	 */
	public ForeignKeyConstraint(TableColumn parentColumn, TableColumn childColumn) {
		this(parentColumn, childColumn, importedKeyNoAction, importedKeyNoAction);
	}

	/**
	 * Add a "parent" side to the constraint.
	 *
	 * @param column
	 */
	public void addParentColumn(TableColumn column) {
		if (column != null) {
			parentColumns.add(column);
			parentTable = column.getTable();
		}
	}

	/**
	 * Add a "child" side to the constraint.
	 *
	 * @param column
	 */
	public void addChildColumn(TableColumn column) {
		if (column != null) {
			childColumns.add(column);
		}
	}

	/**
	 * Returns the name of the constraint
	 *
	 * @return
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the parent table (the table that contains the referenced primary key
	 * column).
	 *
	 * @return
	 */
	public Table getParentTable() {
		return parentTable;
	}

	/**
	 * Returns all of the primary key columns that are referenced by this constraint.
	 *
	 * @return
	 */
	public List<TableColumn> getParentColumns() {
		return Collections.unmodifiableList(parentColumns);
	}

	/**
	 * Returns the table on the "child" end of the relationship (contains the foreign
	 * key that references the parent table's primary key).
	 *
	 * @return
	 */
	public Table getChildTable() {
		return childTable;
	}

	/**
	 * Returns all of the foreign key columns that are referenced by this constraint.
	 *
	 * @return
	 */
	public List<TableColumn> getChildColumns() {
		return Collections.unmodifiableList(childColumns);
	}

	/**
	 * Returns the delete rule for this constraint.
	 *
	 * @see {@link java.sql.DatabaseMetaData#importedKeyCascade}
	 */
	public int getDeleteRule() {
		return deleteRule;
	}

	/**
	 * Returns <code>true</code> if this constraint should
	 * <a href='http://en.wikipedia.org/wiki/Cascade_delete'>cascade deletions</code>.
	 *
	 * @return
	 */
	public boolean isCascadeOnDelete() {
		return getDeleteRule() == importedKeyCascade;
	}

	/**
	 * Returns <code>true</code> if the constraint prevents the parent table
	 * from being deleted if child tables exist.
	 *
	 * @return
	 */
	public boolean isRestrictDelete() {
		return getDeleteRule() == importedKeyNoAction || getDeleteRule() == importedKeyRestrict;
	}

	/**
	 * Returns <code>true</code> if the constraint indicates that the foreign key
	 * will be set to <code>null</code> when the parent key is deleted.
	 *
	 * @return
	 */
	public boolean isNullOnDelete() {
		return getDeleteRule() == importedKeySetNull;
	}

	public String getDeleteRuleName() {
		switch (getDeleteRule()) {
		case importedKeyCascade:
			return "Cascade on delete";

		case importedKeyRestrict:
		case importedKeyNoAction:
			return "Restrict delete";

		case importedKeySetNull:
			return "Null on delete";

		default:
			return "";
		}
	}

	public String getDeleteRuleDescription() {
		switch (getDeleteRule()) {
		case importedKeyCascade:
			return "Cascade on delete:\n Deletion of parent deletes child";

		case importedKeyRestrict:
		case importedKeyNoAction:
			return "Restrict delete:\n Parent cannot be deleted if children exist";

		case importedKeySetNull:
			return "Null on delete:\n Foreign key to parent set to NULL when parent deleted";

		default:
			return "";
		}
	}

	public String getDeleteRuleAlias() {
		switch (getDeleteRule()) {
		case importedKeyCascade:
			return "C";

		case importedKeyRestrict:
		case importedKeyNoAction:
			return "R";

		case importedKeySetNull:
			return "N";

		default:
			return "";
		}
	}

	/**
	 * Returns the update rule for this constraint.
	 *
	 * @see {@link java.sql.DatabaseMetaData#importedKeyCascade}
	 */
	public int getUpdateRule() {
		return updateRule;
	}

	/**
	 * Returns <code>true</code> if this is an implied constraint or
	 * <code>false</code> if it is "real".
	 *
	 * Subclasses that implement implied constraints should override this method.
	 *
	 * @return
	 */
	public boolean isImplied() {
		return false;
	}

	/**
	 * We have several types of constraints.
	 * This returns <code>true</code> if the constraint came from the database
	 * metadata and not inferred by something else.
	 * This is different than {@link #isImplied()} in that implied relationships
	 * are a specific type of non-real relationships.
	 *
	 * @return
	 */
	public boolean isReal() {
		return getClass() == ForeignKeyConstraint.class;
	}

	/**
	 * Custom comparison method to deal with foreign key names that aren't
	 * unique across all schemas being evaluated
	 *
	 * @param other ForeignKeyConstraint
	 *
	 * @return
	 */
	public int compareTo(ForeignKeyConstraint other) {
		if (other == this)
			return 0;

		int rc = getName().compareToIgnoreCase(other.getName());
		if (rc == 0) {
			// should only get here if we're dealing with cross-schema references (rare)
			String ours = getChildColumns().get(0).getTable().getSchema();
			String theirs = other.getChildColumns().get(0).getTable().getSchema();
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
	 * Static method that returns a string representation of the specified
	 * list of {@link TableColumn columns}.
	 *
	 * @param columns
	 * @return
	 */
	public static String toString(List<TableColumn> columns) {
		if (columns.size() == 1)
			return columns.iterator().next().toString();
		return columns.toString();
	}

	/**
	 * Returns a string representation of this foreign key constraint.
	 *
	 * @return
	 */
	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append(childTable.getName());
		buf.append('.');
		buf.append(toString(childColumns));
		buf.append(" refs ");
		buf.append(parentTable.getName());
		buf.append('.');
		buf.append(toString(parentColumns));
		if (parentTable.isRemote()) {
			buf.append(" in ");
			buf.append(parentTable.getSchema());
		}
		if (name != null) {
			buf.append(" via ");
			buf.append(name);
		}

		return buf.toString();
	}
}
