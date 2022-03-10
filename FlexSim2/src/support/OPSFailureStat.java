/*
 * Created on 19/05/2006.
 */
package support;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.LineNumberReader;
import java.util.LinkedHashMap;
import java.util.Vector;

/**
 *
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class OPSFailureStat {


	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//Verify if the arguments are correct. Otherwise, print usage information.
		if (args.length == 2) {
			readFiles(args[0],args[1]);
		} else if (args.length == 3) {
			readFiles(args[0],args[1],args[2]);			
		} else {
			System.err.println("Usage: java support.OPSFailureStat preffix dir");
			System.err.println("Usage: java support.OPSFailureStat preffix dir suffix");
			return;			
		}
	}

	/**
	 * Read the files of the specified dir containing the indicated preffix.
	 * @param preffix
	 * @param dir
	 */
	public static void readFiles(String preffix, String dir) {
		//List the files of the directory
		String[] listDir = (new File(dir)).list();
		//Create the list of filtered files
		Vector<String> filtered = new Vector<String>();
		//Now filter the files by preffix
		for(String file:listDir) {
			//First, only the files containing the preffix
			if (file.regionMatches(true,0,preffix,0,preffix.length())) {
				if (!file.contains(".txt_") && !file.contains("stat")) { //remove the auxiliary files
					filtered.add(file);
					System.out.println("Adding: "+file);
				}
			}
		}
		//Hash for storing the files per last type
		LinkedHashMap<String,Vector<String>> filteredType = new LinkedHashMap<String,Vector<String>>(); 
		//Now select by type
		for(String file: filtered) {
			int indexUnderscore = file.lastIndexOf("_");
			String type = file.substring(preffix.length()+1,indexUnderscore);
			//System.out.println("type: "+type);
			Vector<String> types = filteredType.get(type);
			if (types == null) {
				types = new Vector<String>();
			}
			//Put in the hash
			types.add(file);
			filteredType.put(type,types);
		}
		//Read the files and write the statistics
		writeStat(dir+File.separator+preffix+"_stat.txt",filtered,dir); //total
		for(String type:filteredType.keySet()) { //per link
			writeStat(dir+File.separator+preffix+"_"+type+"_stat.txt",filteredType.get(type),dir);
		}

	}
	
	/**
	 * Read the files of the specified dir containing the indicated preffix.
	 * @param preffix
	 * @param dir
	 */
	public static void readFiles(String preffix, String dir, String suffix) {
		//List the files of the directory
		String[] listDir = (new File(dir)).list();
		//Create the list of filtered files
		Vector<String> filtered = new Vector<String>();
		//Now filter the files by preffix
		for(String file:listDir) {
			//First, only the files containing the preffix
			if (file.regionMatches(true,0,preffix,0,preffix.length())) {
				if (file.contains(suffix) && !file.contains("stat")) { //select only the files containing the suffix
					filtered.add(file);
					System.out.println("Adding: "+file);
				}
			}
		}
		//Hash for storing the files per last type
		LinkedHashMap<String,Vector<String>> filteredType = new LinkedHashMap<String,Vector<String>>(); 
		//Now select by link
		for(String file: filtered) {
			int firstDot = file.indexOf(".txt");
			int indexUnderscore = file.lastIndexOf("_",firstDot);
			String type = file.substring(preffix.length()+1,indexUnderscore);
			Vector<String> types = filteredType.get(type);
			if (types == null) {
				types = new Vector<String>();
			}
			//Put in the hash
			types.add(file);
			filteredType.put(type,types);
		}
		//Read the files and write the statistics
		writeStat(dir+File.separator+preffix+"_stat_"+suffix+".txt",filtered,dir); //total
		for(String type:filteredType.keySet()) { //per link
			writeStat(dir+File.separator+preffix+"_"+type+"_stat_"+suffix+".txt",filteredType.get(type),dir);
		}
	}
	
	/**
	 * Read the vector of files and output the average of the values.
	 * @param outputFile
	 * @param files
	 */
	public static void writeStat(String outputFile, Vector<String> files,String dir) {
		try {
			//System.out.println(files.toString());
			//Get the number of files to be read
			int size = files.size();
			//Create the output file
			File output = new File(outputFile);
			FileWriter writer;
			output.createNewFile();
			writer = new FileWriter(output);
			//Read the files
			LineNumberReader[] readers = new LineNumberReader[size]; 
			for(int i=0; i < size; i++) {
				readers[i] = new LineNumberReader(new FileReader(dir+File.separator+files.get(i)));
			}
			while(readers[0].ready()) {
				String[] line = new String[size];
				for(int i=0; i < size; i++) {
					line[i] = readers[i].readLine();
				}
				double loss=0;
				String load=null;
				Vector<Double> values = new Vector<Double>(); 
				for(int i=0; i < size; i++) {
					//System.out.println(line[i]);
					String[] split = line[i].split("\t");
					load = split[0];
					double value = Double.parseDouble(split[1]); 
					values.add(value);
					loss = loss + value;
				}
                //Divide by the total number
				double average = loss / ((double)size);
				//Calculate the standard deviation
				//double stdDev = getDeviation(average,values);
				double sem = getStandardError(average,values);
				//Write to the output file
				writer.write(load+"\t"+average+"\t"+sem+"\n");
			}
			//Close the output file
			writer.flush();
			writer.close();
		} catch(Exception e) {e.printStackTrace();}
	}
	
	/**
	 * Calculates the standard deviation.
	 * @param avg The average.
	 * @param values All values.
	 * @return The standard deviation.
	 */
	public static double getDeviation(double avg, Vector<Double> values) {
		double variance;
		double temp=0;
		for(double val:values) {
			temp = temp + ((val - avg)*(val - avg));
		}
		int size = values.size();
		variance = ((1.0)/((double)(size-1)))*temp;
		return Math.sqrt(variance);
	}
	
	
	/**
	 * Gets the standard error of the mean = stdDev / sqrt(n)
	 * @param avg The average.
	 * @param values All values.
	 * @return The standard error of the mean.
	 */
	public static double getStandardError(double avg, Vector<Double> values) {
		return (getDeviation(avg,values) / Math.sqrt((double)values.size()));
	}
	
}
