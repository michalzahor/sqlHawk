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
import java.util.Date;
import java.util.Map;
import java.util.Set;

import uk.co.timwise.sqlhawk.util.CaseInsensitiveMap;


public class Database {
	private final String databaseName;
	private String schema;
	private String description;
	public String Dbms;
	private Map<String, Table> tables = new CaseInsensitiveMap<Table>();
	private Map<String, View> views = new CaseInsensitiveMap<View>();
	private final Map<String, Table> remoteTables = new CaseInsensitiveMap<Table>(); // key: schema.tableName value: RemoteTable
	private Map<String, Procedure> procs = new CaseInsensitiveMap<Procedure>();
	private Map<String, Function> functions = new CaseInsensitiveMap<Function>();
	private Date generatedDate = null;
	/**
	 * used for syntax highlighting and identifier quoting.
	 * dbms specific list of keywords.
	 */
	private Set<String> keywords;


	public Set<String> getKeywords() {
		return keywords;
	}

	public void setKeywords(Set<String> keywords) {
		this.keywords = keywords;
	}

	public Date getGeneratedDate() {
		return generatedDate;
	}

	public void setGeneratedDate(Date generatedDate) {
		this.generatedDate = generatedDate;
	}

	public Database(String name, String schema) {
		databaseName = name;
		this.schema = schema;
	}

	public String getName() {
		return databaseName;
	}

	public String getSchema() {
		return schema;
	}

	/**
	 * "DataBase Management System"
	 * i.e. what product and version this schema was retrieved from if any. 
	 * @return
	 */
	public String getDbms() {
		return Dbms;
	}

	public void setDbms(String dbms) {
		Dbms = dbms;
	}


	/**
	 * Details of the database type that's running under the covers.
	 *
	 * @return null if a description wasn't specified.
	 */
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Collection<Table> getTables() {
		return tables.values();
	}

	/**
	 * Return a {@link Map} of all {@link Table}s keyed by their name.
	 *
	 * @return
	 */
	public Map<String, Table> getTablesByName() {
		return tables;
	}

	public Collection<View> getViews() {
		return views.values();
	}

	public Map<String, View> getViewMap() {
		return views;
	}

	public Collection<Table> getRemoteTables() {
		return remoteTables.values();
	}

	public Map<String, Table> getRemoteTableMap() {
		return remoteTables;
	}


	public Collection<Procedure> getProcs() {
		return procs.values();
	}

	public void putProc(String name, Procedure proc) {
		procs.put(name, proc);
	}

	public Collection<Function> getFunctions() {
		return functions.values();
	}

	public void putFunction(String name, Function function) {
		functions.put(name, function);
	}

	public void putViews(String name, View view) {
		views.put(name,view);
	}

	public Collection<Table> getTablesAndViews() {
		Collection<Table> tablesAndViews = new ArrayList<Table>(getTables());
		tablesAndViews.addAll(getViews());
		return tablesAndViews;
	}

	public void setTables(Map<String, Table> tables) {
		this.tables = tables;
	}

	public void setProcs(Map<String, Procedure> procs) {
		this.procs = procs;
	}

	public void setSchema(String schema) {
		this.schema = schema;
	}

	public Map<String, Procedure> getProcMap() {
		return procs;
	}

	public Map<String, Function> getFunctionMap() {
		return functions;
	}

	public void setViews(Map<String, View> views) {
		this.views = views;
	}

	public void setFunctions(Map<String, Function> functions) {
		this.functions = functions;
	}

}
