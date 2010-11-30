/*
 * This file is a part of the SchemaSpy project (http://schemaspy.sourceforge.net).
 * Copyright (C) 2004, 2005, 2006, 2007, 2008, 2009, 2010 John Currier
 *
 * SchemaSpy is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * SchemaSpy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.sourceforge.schemaspy.db.read;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import net.sourceforge.schemaspy.Config;
import net.sourceforge.schemaspy.InvalidConfigurationException;
import net.sourceforge.schemaspy.model.Database;
import net.sourceforge.schemaspy.model.ExplicitRemoteTable;
import net.sourceforge.schemaspy.model.Procedure;
import net.sourceforge.schemaspy.model.RemoteTable;
import net.sourceforge.schemaspy.model.Table;
import net.sourceforge.schemaspy.model.TableColumn;
import net.sourceforge.schemaspy.model.TableIndex;
import net.sourceforge.schemaspy.model.View;
import net.sourceforge.schemaspy.model.xml.SchemaMeta;
import net.sourceforge.schemaspy.model.xml.TableMeta;
import net.sourceforge.schemaspy.util.CaseInsensitiveMap;

public class DbReader {
	private Database database;
    private Connection connection;
    private DatabaseMetaData meta;
    private String schema;
    private Set<String> sqlKeywords;
    private final Logger logger = Logger.getLogger(getClass().getName());
    private final boolean fineEnabled = logger.isLoggable(Level.FINE);

    public Database Read(Config config, Connection connection, DatabaseMetaData meta, String name, String schema, Properties properties, SchemaMeta schemaMeta) throws SQLException, MissingResourceException {
    	database = new Database(name, schema);
    	database.setGeneratedDate(new Date());
        this.connection = connection;
        this.meta = meta;
        this.schema = schema;
        database.setDescription(config.getDescription());
        database.setDbms(getDatabaseProduct());
        database.setKeywords(getKeywords(meta));

        initTables(meta, properties, config);
        if (config.isViewsEnabled())
            initViews(meta, properties, config);

        initCheckConstraints(properties);
        initTableIds(properties);
        initIndexIds(properties);
        initTableComments(properties);
        initTableColumnComments(properties);
        initViewComments(properties);
        initViewColumnComments(properties);
        initStoredProcedures(properties);

        connectTables();
        updateFromXmlMetadata(schemaMeta);
        return database;
    }

    private void initStoredProcedures(Properties properties) throws SQLException {
        String sql = properties.getProperty("selectStoredProcsSql");
        if (sql == null)
        	return; 
        
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            stmt = prepareStatement(sql, null);
            rs = stmt.executeQuery();

            while (rs.next()) {
                String procName = rs.getString("name");
                String procDefinition = rs.getString("definition");
                Procedure proc = new Procedure(schema, procName, procDefinition);
                if (logger.isLoggable(Level.FINE))
                    logger.fine("Read procedure definition '" + procName + "'");
                database.putProc(procName, proc);
            }
        } catch (SQLException sqlException) {
            // don't die just because this failed
            System.err.println();
            System.err.println("Failed to retrieve procedure definitions: " + sqlException);
            System.err.println(sql);
        } finally {
            if (rs != null)
                rs.close();
            if (stmt != null)
                stmt.close();
        }
        
	}

    private String getDatabaseProduct() {
        try {
            return meta.getDatabaseProductName() + " - " + meta.getDatabaseProductVersion();
        } catch (SQLException exc) {
            return "";
        }
    }

    /**
     *  "macro" to validate that a table is somewhat valid
     */
    class NameValidator {
        private final String clazz;
        private final Pattern include;
        private final Pattern exclude;
        private final Set<String> validTypes;

        /**
         * @param clazz table or view
         * @param include
         * @param exclude
         * @param verbose
         * @param validTypes
         */
        NameValidator(String clazz, Pattern include, Pattern exclude, String[] validTypes) {
            this.clazz = clazz;
            this.include = include;
            this.exclude = exclude;
            this.validTypes = new HashSet<String>();
            for (String type : validTypes)
            {
                this.validTypes.add(type.toUpperCase());
            }
        }

        /**
         * Returns <code>true</code> if the table/view name is deemed "valid"
         *
         * @param name name of the table or view
         * @param type type as returned by metadata.getTables():TABLE_TYPE
         * @return
         */
        boolean isValid(String name, String type) {
            // some databases (MySQL) return more than we wanted
            if (!validTypes.contains(type.toUpperCase()))
                return false;

            // Oracle 10g introduced problematic flashback tables
            // with bizarre illegal names
            if (name.indexOf("$") != -1) {
                if (fineEnabled) {
                    logger.fine("Excluding " + clazz + " " + name +
                                ": embedded $ implies illegal name");
                }
                return false;
            }

            if (exclude.matcher(name).matches()) {
                if (fineEnabled) {
                    logger.fine("Excluding " + clazz + " " + name +
                                ": matches exclusion pattern \"" + exclude + '"');
                }
                return false;
            }

            boolean valid = include.matcher(name).matches();
            if (fineEnabled) {
                if (valid) {
                    logger.fine("Including " + clazz + " " + name +
                                ": matches inclusion pattern \"" + include + '"');
                } else {
                    logger.fine("Excluding " + clazz + " " + name +
                                ": doesn't match inclusion pattern \"" + include + '"');
                }
            }
            return valid;
        }
    }

    /**
     * Create/initialize any tables in the schema.

     * @param metadata
     * @param properties
     * @param config
     * @throws SQLException
     */
    private void initTables(final DatabaseMetaData metadata, final Properties properties,
                            final Config config) throws SQLException {
        final Pattern include = config.getTableInclusions();
        final Pattern exclude = config.getTableExclusions();
        final int maxThreads = config.getMaxDbThreads();

        String[] types = getTypes("tableTypes", "TABLE", properties);
        NameValidator validator = new NameValidator("table", include, exclude, types);
        List<BasicTableMeta> entries = getBasicTableMeta(metadata, true, properties, types);

    	final Map<String, Table> tables = new CaseInsensitiveMap<Table>();
    	
        TableCreator creator;
        if (maxThreads == 1) {
            creator = new TableCreator(tables);
        } else {
            // creating tables takes a LONG time (based on JProbe analysis),
            // so attempt to speed it up by doing several in parallel.
            // note that it's actually DatabaseMetaData.getIndexInfo() that's expensive

            creator = new ThreadedTableCreator(tables, maxThreads);

            // "prime the pump" so if there's a database problem we'll probably see it now
            // and not in a secondary thread
            while (!entries.isEmpty()) {
                BasicTableMeta entry = entries.remove(0);

                if (validator.isValid(entry.name, entry.type)) {
                    new TableCreator(tables).create(database, entry, properties, this);
                    break;
                }
            }
        }

        // kick off the secondary threads to do the creation in parallel
        for (BasicTableMeta entry : entries) {
            if (validator.isValid(entry.name, entry.type)) {
                creator.create(database, entry, properties, this);
            }
        }

        // wait for everyone to finish
        creator.join();
        
        database.setTables(creator.getTables());
    }

    /**
     * Create/initialize any views in the schema.
     *
     * @param metadata
     * @param properties
     * @param config
     * @throws SQLException
     */
    private void initViews(DatabaseMetaData metadata, Properties properties,
                            Config config) throws SQLException {
        Pattern includeTables = config.getTableInclusions();
        Pattern excludeTables = config.getTableExclusions();

        String[] types = getTypes("viewTypes", "VIEW", properties);
        NameValidator validator = new NameValidator("view", includeTables, excludeTables, types);

        for (BasicTableMeta entry : getBasicTableMeta(metadata, false, properties, types)) {
            if (validator.isValid(entry.name, entry.type)) {
                View view = new View(entry.schema, entry.name, entry.remarks, entry.viewSql);
                database.putViews(view.getName(), view);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Found details of view " + view.getName());
                } else {
                    System.out.print('.');
                }
            }
        }
    }

    /**
     * Collection of fundamental table/view metadata
     */
    private class BasicTableMeta
    {
        @SuppressWarnings("hiding")
        final String schema;
        final String name;
        final String type;
        final String remarks;
        final String viewSql;
        final int numRows;  // -1 if not determined

        /**
         * @param schema
         * @param name
         * @param type typically "TABLE" or "VIEW"
         * @param remarks
         * @param text optional textual SQL used to create the view
         * @param numRows number of rows, or -1 if not determined
         */
        BasicTableMeta(String schema, String name, String type, String remarks, String text, int numRows)
        {
            this.schema = schema;
            this.name = name;
            this.type = type;
            this.remarks = remarks;
            viewSql = text;
            this.numRows = numRows;
        }
    }

    /**
     * Return a list of basic details of the tables in the schema.
     *
     * @param metadata
     * @param forTables true if we're getting table data, false if getting view data
     * @param properties
     * @return
     * @throws SQLException
     */
    private List<BasicTableMeta> getBasicTableMeta(DatabaseMetaData metadata,
                                                    boolean forTables,
                                                    Properties properties,
                                                    String... types) throws SQLException {
        String queryName = forTables ? "selectTablesSql" : "selectViewsSql";
        String sql = properties.getProperty(queryName);
        List<BasicTableMeta> basics = new ArrayList<BasicTableMeta>();
        ResultSet rs = null;

        if (sql != null) {
            String clazz = forTables ? "table" : "view";
            PreparedStatement stmt = null;

            try {
                stmt = prepareStatement(sql, null);
                rs = stmt.executeQuery();

                while (rs.next()) {
                    String name = rs.getString(clazz + "_name");
                    String sch = getOptionalString(rs, clazz + "_schema");
                    if (sch == null)
                        sch = schema;
                    String remarks = getOptionalString(rs, clazz + "_comment");
                    String text = forTables ? null : getOptionalString(rs, "view_definition");
                    String rows = forTables ? getOptionalString(rs, "table_rows") : null;
                    int numRows = rows == null ? -1 : Integer.parseInt(rows);

                    basics.add(new BasicTableMeta(sch, name, clazz, remarks, text, numRows));
                }
            } catch (SQLException sqlException) {
                // don't die just because this failed
                System.out.flush();
                System.err.println();
                System.err.println("Failed to retrieve " + clazz + " names with custom SQL: " + sqlException);
                System.err.println(sql);
            } finally {
                if (rs != null)
                    rs.close();
                if (stmt != null)
                    stmt.close();
            }
        }

        if (basics.isEmpty()) {
            rs = metadata.getTables(null, schema, "%", types);

            try {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    String type = rs.getString("TABLE_TYPE");
                    String schem = rs.getString("TABLE_SCHEM");
                    String remarks = getOptionalString(rs, "REMARKS");

                    basics.add(new BasicTableMeta(schem, name, type, remarks, null, -1));
                }
            } catch (SQLException exc) {
                if (forTables)
                    throw exc;

                System.out.flush();
                System.err.println();
                System.err.println("Ignoring view " + rs.getString("TABLE_NAME") + " due to exception:");
                exc.printStackTrace();
                System.err.println("Continuing analysis.");
            } finally {
                if (rs != null)
                    rs.close();
            }
        }

        return basics;
    }

    /**
     * Return a database-specific array of types from the .properties file
     * with the specified property name.
     *
     * @param propName
     * @param defaultValue
     * @param props
     * @return
     */
    private String[] getTypes(String propName, String defaultValue, Properties props) {
        String value = props.getProperty(propName, defaultValue);
        List<String> types = new ArrayList<String>();
        for (String type : value.split(",")) {
            type = type.trim();
            if (type.length() > 0)
                types.add(type);
        }

        return types.toArray(new String[types.size()]);
    }

    /**
     * Some databases don't play nice with their metadata.
     * E.g. Oracle doesn't have a REMARKS column at all.
     * This method ignores those types of failures, replacing them with null.
     */
    private String getOptionalString(ResultSet rs, String columnName)
    {
        try {
            return rs.getString(columnName);
        } catch (SQLException ignore) {
            return null;
        }
    }

    private void initCheckConstraints(Properties properties) throws SQLException {
        String sql = properties.getProperty("selectCheckConstraintsSql");
        if (sql != null) {
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try {
                stmt = prepareStatement(sql, null);
                rs = stmt.executeQuery();

                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    Table table = database.getTablesByName().get(tableName);
                    if (table != null)
                        table.addCheckConstraint(rs.getString("constraint_name"), rs.getString("text"));
                }
            } catch (SQLException sqlException) {
                // don't die just because this failed
                System.err.println();
                System.err.println("Failed to retrieve check constraints: " + sqlException);
                System.err.println(sql);
            } finally {
                if (rs != null)
                    rs.close();
                if (stmt != null)
                    stmt.close();
            }
        }
    }

    private void initTableIds(Properties properties) throws SQLException {
        String sql = properties.getProperty("selectTableIdsSql");
        if (sql != null) {
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try {
                stmt = prepareStatement(sql, null);
                rs = stmt.executeQuery();

                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    Table table = database.getTablesByName().get(tableName);
                    if (table != null)
                        table.setId(rs.getObject("table_id"));
                }
            } catch (SQLException sqlException) {
                System.err.println();
                System.err.println(sql);
                throw sqlException;
            } finally {
                if (rs != null)
                    rs.close();
                if (stmt != null)
                    stmt.close();
            }
        }
    }

    private void initIndexIds(Properties properties) throws SQLException {
        String sql = properties.getProperty("selectIndexIdsSql");
        if (sql != null) {
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try {
                stmt = prepareStatement(sql, null);
                rs = stmt.executeQuery();

                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    Table table = database.getTablesByName().get(tableName);
                    if (table != null) {
                        TableIndex index = table.getIndex(rs.getString("index_name"));
                        if (index != null)
                            index.setId(rs.getObject("index_id"));
                    }
                }
            } catch (SQLException sqlException) {
                System.err.println();
                System.err.println(sql);
                throw sqlException;
            } finally {
                if (rs != null)
                    rs.close();
                if (stmt != null)
                    stmt.close();
            }
        }
    }

    /**
     * Initializes table comments.
     * If the SQL also returns view comments then they're plugged into the
     * appropriate views.
     *
     * @param properties
     * @throws SQLException
     */
    private void initTableComments(Properties properties) throws SQLException {
        String sql = properties.getProperty("selectTableCommentsSql");
        if (sql != null) {
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try {
                stmt = prepareStatement(sql, null);
                rs = stmt.executeQuery();

                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    Table table = database.getTablesByName().get(tableName);
                    if (table == null)
                        table = database.getViewMap().get(tableName);

                    if (table != null)
                        table.setComments(rs.getString("comments"));
                }
            } catch (SQLException sqlException) {
                // don't die just because this failed
                System.err.println();
                System.err.println("Failed to retrieve table/view comments: " + sqlException);
                System.err.println(sql);
            } finally {
                if (rs != null)
                    rs.close();
                if (stmt != null)
                    stmt.close();
            }
        }
    }

    /**
     * Initializes view comments.
     *
     * @param properties
     * @throws SQLException
     */
    private void initViewComments(Properties properties) throws SQLException {
        String sql = properties.getProperty("selectViewCommentsSql");
        if (sql != null) {
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try {
                stmt = prepareStatement(sql, null);
                rs = stmt.executeQuery();

                while (rs.next()) {
                    String viewName = rs.getString("view_name");
                    if (viewName == null)
                        viewName = rs.getString("table_name");
                    Table view = database.getViewMap().get(viewName);

                    if (view != null)
                        view.setComments(rs.getString("comments"));
                }
            } catch (SQLException sqlException) {
                // don't die just because this failed
                System.err.println();
                System.err.println("Failed to retrieve table/view comments: " + sqlException);
                System.err.println(sql);
            } finally {
                if (rs != null)
                    rs.close();
                if (stmt != null)
                    stmt.close();
            }
        }
    }

    /**
     * Initializes table column comments.
     * If the SQL also returns view column comments then they're plugged into the
     * appropriate views.
     *
     * @param properties
     * @throws SQLException
     */
    private void initTableColumnComments(Properties properties) throws SQLException {
        String sql = properties.getProperty("selectColumnCommentsSql");
        if (sql != null) {
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try {
                stmt = prepareStatement(sql, null);
                rs = stmt.executeQuery();

                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    Table table = database.getTablesByName().get(tableName);
                    if (table == null)
                        table = database.getViewMap().get(tableName);

                    if (table != null) {
                        TableColumn column = table.getColumn(rs.getString("column_name"));
                        if (column != null)
                            column.setComments(rs.getString("comments"));
                    }
                }
            } catch (SQLException sqlException) {
                // don't die just because this failed
                System.err.println();
                System.err.println("Failed to retrieve column comments: " + sqlException);
                System.err.println(sql);
            } finally {
                if (rs != null)
                    rs.close();
                if (stmt != null)
                    stmt.close();
            }
        }
    }

    /**
     * Initializes view column comments.
     *
     * @param properties
     * @throws SQLException
     */
    private void initViewColumnComments(Properties properties) throws SQLException {
        String sql = properties.getProperty("selectViewColumnCommentsSql");
        if (sql != null) {
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try {
                stmt = prepareStatement(sql, null);
                rs = stmt.executeQuery();

                while (rs.next()) {
                    String viewName = rs.getString("view_name");
                    if (viewName == null)
                        viewName = rs.getString("table_name");
                    Table view = database.getViewMap().get(viewName);

                    if (view != null) {
                        TableColumn column = view.getColumn(rs.getString("column_name"));
                        if (column != null)
                            column.setComments(rs.getString("comments"));
                    }
                }
            } catch (SQLException sqlException) {
                // don't die just because this failed
                System.err.println();
                System.err.println("Failed to retrieve view column comments: " + sqlException);
                System.err.println(sql);
            } finally {
                if (rs != null)
                    rs.close();
                if (stmt != null)
                    stmt.close();
            }
        }
    }

    /**
     * Create a <code>PreparedStatement</code> from the specified SQL.
     * The SQL can contain these named parameters (but <b>not</b> question marks).
     * <ol>
     * <li>:schema - replaced with the name of the schema
     * <li>:owner - alias for :schema
     * <li>:table - replaced with the name of the table
     * </ol>
     * @param sql String - SQL without question marks
     * @param tableName String - <code>null</code> if the statement doesn't deal with <code>Table</code>-level details.
     * @throws SQLException
     * @return PreparedStatement
     */
    PreparedStatement prepareStatement(String sql, String tableName) throws SQLException {
        StringBuilder sqlBuf = new StringBuilder(sql);
        List<String> sqlParams = getSqlParams(sqlBuf, tableName); // modifies sqlBuf
        PreparedStatement stmt = connection.prepareStatement(sqlBuf.toString());

        try {
            for (int i = 0; i < sqlParams.size(); ++i) {
                stmt.setString(i + 1, sqlParams.get(i).toString());
            }
        } catch (SQLException exc) {
            stmt.close();
            throw exc;
        }

        return stmt;
    }

    public Table addRemoteTable(String remoteSchema, String remoteTableName, String baseSchema, Properties properties, Pattern excludeIndirectColumns, Pattern excludeColumns) throws SQLException {
        String fullName = remoteSchema + "." + remoteTableName;
        Table remoteTable = database.getRemoteTableMap().get(fullName);
        if (remoteTable == null) {
            if (properties != null)
                remoteTable = new RemoteTable(remoteSchema, remoteTableName, baseSchema);
            else
                remoteTable = new ExplicitRemoteTable(remoteSchema, remoteTableName, baseSchema);

            logger.fine("Adding remote table " + fullName);
            database.getRemoteTableMap().put(fullName, remoteTable);
            RemoteTableReader remoteTableReader = new RemoteTableReader();
            remoteTableReader.connectForeignKeys(remoteTable, database.getTablesByName(), excludeIndirectColumns, excludeColumns, this);
        }

        return remoteTable;
    }

    /**
     * Return an uppercased <code>Set</code> of all SQL keywords used by a database
     *
     * @return
     * @throws SQLException
     */
    private Set<String> getSqlKeywords() throws SQLException {
        if (sqlKeywords == null) {
            // from http://www.contrib.andrew.cmu.edu/~shadow/sql/sql1992.txt:
            String[] sql92Keywords =
                ("ADA" +
                "| C | CATALOG_NAME | CHARACTER_SET_CATALOG | CHARACTER_SET_NAME" +
                "| CHARACTER_SET_SCHEMA | CLASS_ORIGIN | COBOL | COLLATION_CATALOG" +
                "| COLLATION_NAME | COLLATION_SCHEMA | COLUMN_NAME | COMMAND_FUNCTION | COMMITTED" +
                "| CONDITION_NUMBER | CONNECTION_NAME | CONSTRAINT_CATALOG | CONSTRAINT_NAME" +
                "| CONSTRAINT_SCHEMA | CURSOR_NAME" +
                "| DATA | DATETIME_INTERVAL_CODE | DATETIME_INTERVAL_PRECISION | DYNAMIC_FUNCTION" +
                "| FORTRAN" +
                "| LENGTH" +
                "| MESSAGE_LENGTH | MESSAGE_OCTET_LENGTH | MESSAGE_TEXT | MORE | MUMPS" +
                "| NAME | NULLABLE | NUMBER" +
                "| PASCAL | PLI" +
                "| REPEATABLE | RETURNED_LENGTH | RETURNED_OCTET_LENGTH | RETURNED_SQLSTATE" +
                "| ROW_COUNT" +
                "| SCALE | SCHEMA_NAME | SERIALIZABLE | SERVER_NAME | SUBCLASS_ORIGIN" +
                "| TABLE_NAME | TYPE" +
                "| UNCOMMITTED | UNNAMED" +
                "| ABSOLUTE | ACTION | ADD | ALL | ALLOCATE | ALTER | AND" +
                "| ANY | ARE | AS | ASC" +
                "| ASSERTION | AT | AUTHORIZATION | AVG" +
                "| BEGIN | BETWEEN | BIT | BIT_LENGTH | BOTH | BY" +
                "| CASCADE | CASCADED | CASE | CAST | CATALOG | CHAR | CHARACTER | CHAR_LENGTH" +
                "| CHARACTER_LENGTH | CHECK | CLOSE | COALESCE | COLLATE | COLLATION" +
                "| COLUMN | COMMIT | CONNECT | CONNECTION | CONSTRAINT" +
                "| CONSTRAINTS | CONTINUE" +
                "| CONVERT | CORRESPONDING | COUNT | CREATE | CROSS | CURRENT" +
                "| CURRENT_DATE | CURRENT_TIME | CURRENT_TIMESTAMP | CURRENT_USER | CURSOR" +
                "| DATE | DAY | DEALLOCATE | DEC | DECIMAL | DECLARE | DEFAULT | DEFERRABLE" +
                "| DEFERRED | DELETE | DESC | DESCRIBE | DESCRIPTOR | DIAGNOSTICS" +
                "| DISCONNECT | DISTINCT | DOMAIN | DOUBLE | DROP" +
                "| ELSE | END | END-EXEC | ESCAPE | EXCEPT | EXCEPTION" +
                "| EXEC | EXECUTE | EXISTS" +
                "| EXTERNAL | EXTRACT" +
                "| FALSE | FETCH | FIRST | FLOAT | FOR | FOREIGN | FOUND | FROM | FULL" +
                "| GET | GLOBAL | GO | GOTO | GRANT | GROUP" +
                "| HAVING | HOUR" +
                "| IDENTITY | IMMEDIATE | IN | INDICATOR | INITIALLY | INNER | INPUT" +
                "| INSENSITIVE | INSERT | INT | INTEGER | INTERSECT | INTERVAL | INTO | IS" +
                "| ISOLATION" +
                "| JOIN" +
                "| KEY" +
                "| LANGUAGE | LAST | LEADING | LEFT | LEVEL | LIKE | LOCAL | LOWER" +
                "| MATCH | MAX | MIN | MINUTE | MODULE | MONTH" +
                "| NAMES | NATIONAL | NATURAL | NCHAR | NEXT | NO | NOT | NULL" +
                "| NULLIF | NUMERIC" +
                "| OCTET_LENGTH | OF | ON | ONLY | OPEN | OPTION | OR" +
                "| ORDER | OUTER" +
                "| OUTPUT | OVERLAPS" +
                "| PAD | PARTIAL | POSITION | PRECISION | PREPARE | PRESERVE | PRIMARY" +
                "| PRIOR | PRIVILEGES | PROCEDURE | PUBLIC" +
                "| READ | REAL | REFERENCES | RELATIVE | RESTRICT | REVOKE | RIGHT" +
                "| ROLLBACK | ROWS" +
                "| SCHEMA | SCROLL | SECOND | SECTION | SELECT | SESSION | SESSION_USER | SET" +
                "| SIZE | SMALLINT | SOME | SPACE | SQL | SQLCODE | SQLERROR | SQLSTATE" +
                "| SUBSTRING | SUM | SYSTEM_USER" +
                "| TABLE | TEMPORARY | THEN | TIME | TIMESTAMP | TIMEZONE_HOUR | TIMEZONE_MINUTE" +
                "| TO | TRAILING | TRANSACTION | TRANSLATE | TRANSLATION | TRIM | TRUE" +
                "| UNION | UNIQUE | UNKNOWN | UPDATE | UPPER | USAGE | USER | USING" +
                "| VALUE | VALUES | VARCHAR | VARYING | VIEW" +
                "| WHEN | WHENEVER | WHERE | WITH | WORK | WRITE" +
                "| YEAR" +
                "| ZONE").split("|,\\s*");

            String[] nonSql92Keywords = meta.getSQLKeywords().toUpperCase().split(",\\s*");

            sqlKeywords = new HashSet<String>();
            sqlKeywords.addAll(Arrays.asList(sql92Keywords));
            sqlKeywords.addAll(Arrays.asList(nonSql92Keywords));
        }

        return sqlKeywords;
    }

    /**
     * Replaces named parameters in <code>sql</code> with question marks and
     * returns appropriate matching values in the returned <code>List</code> of <code>String</code>s.
     *
     * @param sql StringBuffer input SQL with named parameters, output named params are replaced with ?'s.
     * @param tableName String
     * @return List of Strings
     *
     * @see #prepareStatement(String, String)
     */
    private List<String> getSqlParams(StringBuilder sql, String tableName) {
        Map<String, String> namedParams = new HashMap<String, String>();
        @SuppressWarnings("hiding")
        String schema = database.getSchema();
        if (schema == null)
            schema = database.getName(); // some 'schema-less' db's treat the db name like a schema (unusual case)
        namedParams.put(":schema", schema);
        namedParams.put(":owner", schema); // alias for :schema
        if (tableName != null) {
            namedParams.put(":table", tableName);
            namedParams.put(":view", tableName); // alias for :table
        }

        List<String> sqlParams = new ArrayList<String>();
        int nextColon = sql.indexOf(":");
        while (nextColon != -1) {
            String paramName = new StringTokenizer(sql.substring(nextColon), " ,\"')").nextToken();
            String paramValue = namedParams.get(paramName);
            if (paramValue == null)
                throw new InvalidConfigurationException("Unexpected named parameter '" + paramName + "' found in SQL '" + sql + "'");
            sqlParams.add(paramValue);
            sql.replace(nextColon, nextColon + paramName.length(), "?"); // replace with a ?
            nextColon = sql.indexOf(":", nextColon);
        }

        return sqlParams;
    }

    /**
     * Take the supplied XML-based metadata and update our model of the schema with it
     *
     * @param schemaMeta
     * @throws SQLException
     */
    private void updateFromXmlMetadata(SchemaMeta schemaMeta) throws SQLException {
        if (schemaMeta != null) {
            final Pattern excludeNone = Pattern.compile("[^.]");
            final Properties noProps = new Properties();

            database.setDescription(schemaMeta.getComments());

            // done in three passes:
            // 1: create any new tables
            // 2: add/mod columns
            // 3: connect

        	TableReader tableReader = new TableReader();
            // add the newly defined tables and columns first
            for (TableMeta tableMeta : schemaMeta.getTables()) {
                Table table;

                if (tableMeta.getRemoteSchema() != null) {
                    table = database.getRemoteTableMap().get(tableMeta.getRemoteSchema() + '.' + tableMeta.getName());
                    if (table == null) {
                        table = addRemoteTable(tableMeta.getRemoteSchema(), tableMeta.getName(), database.getSchema(), null, excludeNone, excludeNone);
                    }
                } else {
                    table = database.getTablesByName().get(tableMeta.getName());

                    if (table == null)
                        table = database.getViewMap().get(tableMeta.getName());

                    if (table == null) {
                        table = tableReader.ReadTable(database, database.getSchema(), tableMeta.getName(), null, noProps, excludeNone, excludeNone, meta, this);
                        database.getTablesByName().put(table.getName(), table);
                    }
                }

                tableReader.update(tableMeta);
            }

            // then tie the tables together
            for (TableMeta tableMeta : schemaMeta.getTables()) {
                Table table;

                if (tableMeta.getRemoteSchema() != null) {
                    table = database.getRemoteTableMap().get(tableMeta.getRemoteSchema() + '.' + tableMeta.getName());
                } else {
                    table = database.getTablesByName().get(tableMeta.getName());
                    if (table == null)
                        table = database.getViewMap().get(tableMeta.getName());
                }

                tableReader.connect(tableMeta, database.getTablesByName(), database.getRemoteTableMap());
            }
        }
    }

    private void connectTables() throws SQLException {
        Pattern excludeColumns = Config.getInstance().getColumnExclusions();
        Pattern excludeIndirectColumns = Config.getInstance().getIndirectColumnExclusions();

    	TableReader tableReader = new TableReader();
    	tableReader.setMeta(meta);
        for (Table table : database.getTablesByName().values()) {
            tableReader.connectForeignKeys(table, database.getTablesByName(), excludeIndirectColumns, excludeColumns, this);
        }
    }

    /**
     * Single-threaded implementation of a class that creates tables
     */
    private class TableCreator {
        private final Pattern excludeColumns = Config.getInstance().getColumnExclusions();
        private final Pattern excludeIndirectColumns = Config.getInstance().getIndirectColumnExclusions();
    	private final Map<String, Table> tables;
    	
    	public TableCreator(Map<String, Table> tables){
    		this.tables = tables;
    	}
    	
    	public Map<String, Table> getTables(){
    		return this.tables;
    	}

        /**
         * Create a table and put it into <code>tables</code>
         */
        void create(Database db, BasicTableMeta tableMeta, Properties properties, DbReader dbReader) throws SQLException {
            createImpl(db, tableMeta, properties, dbReader);
        }

        protected void createImpl(Database db, BasicTableMeta tableMeta, Properties properties, DbReader dbReader) throws SQLException {
        	TableReader reader = new TableReader();
        	Table table = reader.ReadTable(db, tableMeta.schema, tableMeta.name, tableMeta.remarks, properties, excludeIndirectColumns, excludeColumns, meta, dbReader);

            if (tableMeta.numRows != -1) {
                table.setNumRows(tableMeta.numRows);
            }

            synchronized (tables) {
                tables.put(table.getName(), table);
            }

            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Found details of table " + table.getName());
            } else {
                System.out.print('.');
            }
        }

        /**
         * Wait for all of the tables to be created.
         * By default this does nothing since this implementation isn't threaded.
         */
        void join() {
        }
    }

    /**
     * Multi-threaded implementation of a class that creates tables
     */
    private class ThreadedTableCreator extends TableCreator {
        private final Set<Thread> threads = new HashSet<Thread>();
        private final int maxThreads;

        ThreadedTableCreator(Map<String, Table> tables, int maxThreads) {
        	super(tables);
            this.maxThreads = maxThreads;
        }

        @Override
        void create(final Database db, final BasicTableMeta tableMeta, final Properties properties, final DbReader dbReader) throws SQLException {
            Thread runner = new Thread() {
                @Override
                public void run() {
                    try {
                        createImpl(db, tableMeta, properties, dbReader);
                    } catch (SQLException exc) {
                        exc.printStackTrace(); // nobody above us in call stack...dump it here
                    } finally {
                        synchronized (threads) {
                            threads.remove(this);
                            threads.notify();
                        }
                    }
                }
            };

            synchronized (threads) {
                // wait for enough 'room'
                while (threads.size() >= maxThreads) {
                    try {
                        threads.wait();
                    } catch (InterruptedException interrupted) {
                    }
                }

                threads.add(runner);
            }

            runner.start();
        }

        /**
         * Wait for all of the started threads to complete
         */
        @Override
        public void join() {
            while (true) {
                Thread thread;

                synchronized (threads) {
                    Iterator<Thread> iter = threads.iterator();
                    if (!iter.hasNext())
                        break;

                    thread = iter.next();
                }

                try {
                    thread.join();
                } catch (InterruptedException exc) {
                }
            }
        }
    }

    /**
     * Extract the SQL that describes this view from the database
     *
     * @return
     * @throws SQLException
     */
    public String fetchViewSql(Properties properties, String viewName) throws SQLException {
        String selectViewSql = properties.getProperty("selectViewSql");
        if (selectViewSql == null)
        {
            System.err.println("selectViewSql missing from properties, couldn't read view");
            return null;
        }
        PreparedStatement stmt = null;
        ResultSet rs = null;
        String viewSql;
        try {
            stmt = prepareStatement(selectViewSql, viewName);
            rs = stmt.executeQuery();
            rs.next();
            try {
            	viewSql = rs.getString("view_definition");
            } catch (SQLException tryOldName) {
            	viewSql = rs.getString("text");
            }   
        } catch (SQLException sqlException) {
            System.err.println(selectViewSql);
            throw sqlException;
        } finally {
            if (rs != null)
                rs.close();
            if (stmt != null)
                stmt.close();
        }
        if (viewSql==null)
        	return null;
        viewSql = viewSql.trim();
        if (viewSql.length()==0)
        	return null;
        return viewSql;
    }

    /**
     * get keywords for current dbms.
     * @param meta
     * @return
     */
    public Set<String> getKeywords(DatabaseMetaData meta) {
    	Set<String> keywords;
        keywords = new HashSet<String>(Arrays.asList(new String[] {
            "ABSOLUTE", "ACTION", "ADD", "ALL", "ALLOCATE", "ALTER", "AND",
            "ANY", "ARE", "AS", "ASC", "ASSERTION", "AT", "AUTHORIZATION", "AVG",
            "BEGIN", "BETWEEN", "BIT", "BIT_LENGTH", "BOTH", "BY",
            "CASCADE", "CASCADED", "CASE", "CAST", "CATALOG", "CHAR", "CHARACTER",
            "CHAR_LENGTH", "CHARACTER_LENGTH", "CHECK", "CLOSE", "COALESCE",
            "COLLATE", "COLLATION", "COLUMN", "COMMIT", "CONNECT", "CONNECTION",
            "CONSTRAINT", "CONSTRAINTS", "CONTINUE", "CONVERT", "CORRESPONDING",
            "COUNT", "CREATE", "CROSS", "CURRENT", "CURRENT_DATE", "CURRENT_TIME",
            "CURRENT_TIMESTAMP", "CURRENT_USER", "CURSOR",
            "DATE", "DAY", "DEALLOCATE", "DEC", "DECIMAL", "DECLARE", "DEFAULT",
            "DEFERRABLE", "DEFERRED", "DELETE", "DESC", "DESCRIBE", "DESCRIPTOR",
            "DIAGNOSTICS", "DISCONNECT", "DISTINCT", "DOMAIN", "DOUBLE", "DROP",
            "ELSE", "END", "END - EXEC", "ESCAPE", "EXCEPT", "EXCEPTION", "EXEC",
            "EXECUTE", "EXISTS", "EXTERNAL", "EXTRACT",
            "FALSE", "FETCH", "FIRST", "FLOAT", "FOR", "FOREIGN", "FOUND", "FROM", "FULL",
            "GET", "GLOBAL", "GO", "GOTO", "GRANT", "GROUP",
            "HAVING", "HOUR",
            "IDENTITY", "IMMEDIATE", "IN", "INDICATOR", "INITIALLY", "INNER", "INPUT",
            "INSENSITIVE", "INSERT", "INT", "INTEGER", "INTERSECT", "INTERVAL", "INTO",
            "IS", "ISOLATION",
            "JOIN",
            "KEY",
            "LANGUAGE", "LAST", "LEADING", "LEFT", "LEVEL", "LIKE", "LOCAL", "LOWER",
            "MATCH", "MAX", "MIN", "MINUTE", "MODULE", "MONTH",
            "NAMES", "NATIONAL", "NATURAL", "NCHAR", "NEXT", "NO", "NOT", "NULL",
            "NULLIF", "NUMERIC",
            "OCTET_LENGTH", "OF", "ON", "ONLY", "OPEN", "OPTION", "OR", "ORDER",
            "OUTER", "OUTPUT", "OVERLAPS",
            "PAD", "PARTIAL", "POSITION", "PRECISION", "PREPARE", "PRESERVE", "PRIMARY",
            "PRIOR", "PRIVILEGES", "PROCEDURE", "PUBLIC",
            "READ", "REAL", "REFERENCES", "RELATIVE", "RESTRICT", "REVOKE", "RIGHT",
            "ROLLBACK", "ROWS",
            "SCHEMA", "SCROLL", "SECOND", "SECTION", "SELECT", "SESSION", "SESSION_USER",
            "SET", "SIZE", "SMALLINT", "SOME", "SPACE", "SQL", "SQLCODE", "SQLERROR",
            "SQLSTATE", "SUBSTRING", "SUM", "SYSTEM_USER",
            "TABLE", "TEMPORARY", "THEN", "TIME", "TIMESTAMP", "TIMEZONE_HOUR",
            "TIMEZONE_MINUTE", "TO", "TRAILING", "TRANSACTION", "TRANSLATE",
            "TRANSLATION", "TRIM", "TRUE",
            "UNION", "UNIQUE", "UNKNOWN", "UPDATE", "UPPER", "USAGE", "USER", "USING",
            "VALUE", "VALUES", "VARCHAR", "VARYING", "VIEW",
            "WHEN", "WHENEVER", "WHERE", "WITH", "WORK", "WRITE",
            "YEAR",
            "ZONE"
        }));

        try {
            String keywordsArray[] = new String[] {
                meta.getSQLKeywords(),
                meta.getSystemFunctions(),
                meta.getNumericFunctions(),
                meta.getStringFunctions(),
                meta.getTimeDateFunctions()
            };
            for (int i = 0; i < keywordsArray.length; ++i) {
                StringTokenizer tokenizer = new StringTokenizer(keywordsArray[i].toUpperCase(), ",");

                while (tokenizer.hasMoreTokens()) {
                    keywords.add(tokenizer.nextToken().trim());
                }
            }
        } catch (Exception exc) {
            // don't totally fail just because we can't extract these details...
            System.err.println(exc);
        }
        return keywords;
    }
}
