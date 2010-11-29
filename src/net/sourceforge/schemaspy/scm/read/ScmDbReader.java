package net.sourceforge.schemaspy.scm.read;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

import net.sourceforge.schemaspy.Config;
import net.sourceforge.schemaspy.model.Database;
import net.sourceforge.schemaspy.model.Procedure;
import net.sourceforge.schemaspy.util.CaseInsensitiveMap;

public class ScmDbReader {

	public static Database Load(Config config, File inputDir) throws Exception {
		Database db = new Database(null, null);
		File procFolder = new File(inputDir, "Procedures");
		if (!procFolder.isDirectory())
			throw new Exception("input folder not found");
		File[] files = procFolder.listFiles();
	    Map<String, Procedure> procs = new CaseInsensitiveMap<Procedure>();
		for(File file : files){
			if (!file.getName().endsWith(".sql")) //skip non sql files
				continue;
			String name = file.getName();
			name = name.substring(0, name.length()-4);// trim extension from filename
			String definition = readFile(file);
			Procedure proc = new Procedure(null, name, definition);
			procs.put(name, proc);
		}
		db.setProcs(procs);
		return db;
	}

	 private static String readFile( File file ) throws IOException {
		 //http://stackoverflow.com/questions/326390/how-to-create-a-java-string-from-the-contents-of-a-file
		BufferedReader reader = new BufferedReader( new FileReader (file));
		String line  = null;
		StringBuilder stringBuilder = new StringBuilder();
		String ls = System.getProperty("line.separator");
		while( ( line = reader.readLine() ) != null ) {
			stringBuilder.append( line );
			stringBuilder.append( ls );
		}
		return stringBuilder.toString();
	 }

}