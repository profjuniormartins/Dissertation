/*
 * Created on 28/02/2008.
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
public class RandStat {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//Verify if the arguments are correct. Otherwise, print usage information.
		if (args.length != 2) {
			System.err.println("Usage: java support.RandStat preffix dir");
			return;
		}
		readFiles(args[0],args[1]);
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
				if (!file.contains(".txt_")&& !file.contains("stat")) { //remove the auxiliary files
					filtered.add(file);
					System.out.println("Adding: "+file);
				}
			}
		}
		//Read the files and write the statistics
		writeStat(dir+File.separator+preffix+"_stat.txt",filtered,dir); //total
	}

	/**
	 * Read the vector of files and output the average of the values.
	 * @param outputFile
	 * @param files
	 */
	public static void writeStat(String outputFile, Vector<String> files,String dir) {
		try {
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
				readers[i] = new LineNumberReader(new FileReader(dir+files.get(i)));
				//uncomment the line below for MS Windows
				//readers[i] = new LineNumberReader(new FileReader(dir+"\\"+files.get(i)));
			}
			while(readers[0].ready()) {
				String[] line = new String[size];
				for(int i=0; i < size; i++) {
					line[i] = readers[i].readLine();
				}
				double blocking=0;
				String load=null;
				Vector<Double> values = new Vector<Double>(); 
				//Vector<Double> valuesBlock = new Vector<Double>(); 
				for(int i=0; i < size; i++) {
					String[] split = line[i].split("\t");
					load = split[0];
					double valBlock = Double.parseDouble(split[1]);
					values.add(valBlock);
					blocking = blocking + valBlock;
				}
                //Divide by the total number
				blocking = blocking / ((double)size);
				//Calculate the standard deviation
				//double stdDevBlock = getDeviation(blocking,valuesBlock);				
				double stdErr = getStandardError(blocking,values);
				//Write to the output file
				writer.write(load+"\t"+blocking+"\t"+stdErr+"\n");
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
