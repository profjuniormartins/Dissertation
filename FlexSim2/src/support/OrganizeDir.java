/*
 * Created on 19/05/2006.
 */
package support;

import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.Vector;

/**
 *
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class OrganizeDir {

	/**
	 * 
	 */
	public OrganizeDir() {
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//Verify if the arguments are correct. Otherwise, print usage information.
		if (args.length != 3) {
			System.err.println("Usage: java support.OrganizeDir preffix directory listFile");
			return;
		}
		readFiles(args[0],args[1],args[2]);
	}

	/**
	 * Read the files of the specified dir containing the indicated preffix.
	 * @param preffix The preffix of the files
	 * @param dir The directory to be inspected
	 * @param list The file containing the list of searchable keys.
	 */
	public static void readFiles(String preffix, String dir, String list) {
		try {
			//Open the file containing the list
			LineNumberReader reader = new LineNumberReader(new FileReader(list));
			Vector<String> keys = new Vector<String>();
			//Add the keys of the list to the vector
			while(reader.ready()) {
				String line = reader.readLine();
				keys.add(line);
			}
			//Now, list the files on the dir
			File directory = new File(dir);
			String[] listDir = directory.list();
			//Create the list of filtered files
			Vector<String> filtered = new Vector<String>();
			//Now filter the files by preffix
			for(String file:listDir) {
				//First, only the files containing the preffix
				if (file.regionMatches(true,0,preffix,0,preffix.length())) {
					filtered.add(file);
				}
			}
			//Now select the files and move them to a dir indicated by the key
			for (String key: keys) { //For each key
				Vector<String> selected = new Vector<String>(); 
				for (String file:filtered) { //for all files
					if (file.contains(key)) { //contain the key?
						selected.add(file); //add it to a selected list
					}
				}
				//Create a dir with the key name
				System.out.println("Creating sub-dir: "+dir+File.separator+key);
				(new File(dir+File.separator+key)).mkdir();
				//Call the system routine to move the selected files to the new dir
				for (String move:selected) {
					//Move the file
					System.out.println("Moving "+move+" to "+key);
					ProcessBuilder pb = new ProcessBuilder("mv",move,key);
					pb.directory(directory);
					pb.start();
					//Remove it from the list
					filtered.remove(move);
				}

			}
		} catch (Exception e) {e.printStackTrace();}
	}
}
