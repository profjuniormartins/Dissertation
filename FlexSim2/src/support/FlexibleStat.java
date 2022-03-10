/**
 * Created on 21/11/2016.
 */
package support;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Vector;

/**
 * @author Gustavo Sousa Pavani
 * @version 1.0
 *
 */
public class FlexibleStat {
	public static int vars;
	public static String SEPARATOR = "\t";
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		vars = args[2].length();
		if (args.length != 3)
			System.err.println("Usage: java support.FlexibleStat preffix dir STRING, where A indicates Average, B indicates Average and Standard Deviation, and C indicates Average and Standard Error of the Mean for each row.");
		System.out.println("Calculating the statistics for prefix "+args[0]+" @ "+args[1]+ " with "+vars+" rows");
		readFiles(args);
	}
	
	public static void readFiles(String[] args) {
		String preffix = args[0];
		String dir = args[1];
		//List the files of the directory
		String[] listDir = (new File(dir)).list();
		//Create the list of filtered files
		ArrayList<String> files = new ArrayList<String>();
		//Now filter the files by preffix
		for(String file:listDir) {
			//First, only the files containing the preffix
			if (file.regionMatches(true,0,preffix,0,preffix.length())) {
				if (!file.contains(".txt_") && !file.contains("stat")) { //remove the auxiliary files
					files.add(file);
					System.out.println("Adding: "+file);
				}
			}
		}
		//Print output file
		String outputFile = dir+File.separator+preffix+"_"+args[2]+"_stat.txt";
		System.out.println("Output to "+outputFile);
		//Get the number of files to be read
		int size = files.size();
		try {
			//Create the output file
			File output = new File(outputFile);
			FileWriter writer;
			output.createNewFile();
			writer = new FileWriter(output);
			//Read the files
			LineNumberReader[] readers = new LineNumberReader[size]; 
			for(int i=0; i < size; i++) {
				//windows
				//readers[i] = new LineNumberReader(new FileReader(dir+"\\"+files.get(i)));
				//linux
				readers[i] = new LineNumberReader(new FileReader(dir+"/"+files.get(i)));
				//System.out.println("i:"+i+"File:"+files.get(i));
			}
			while(readers[0].ready()) {
				//Constains one line of each file
				String[] line = new String[size];
				for(int i=0; i < size; i++) {
					line[i] = readers[i].readLine();
				}
				//Instantiate the counters for each row
				ArrayList<ArrayList<Double>> values = new ArrayList<ArrayList<Double>>();
				for (int k=0; k < vars; k++) {
					values.add(new ArrayList<Double>());
				}
				//For each line do				
				for(int i=0; i < size; i++) {
					//System.out.println("Line: "+line[i]);
					String[] split = line[i].split(SEPARATOR);
					//System.out.println(split.length+" values splitted.");
					//For each row do
					for (int j=0; j < vars; j++) {
						if (!split[j].equals("NaN")) { //Do not consider NaN values
							ArrayList<Double> value = values.get(j);
							value.add(Double.valueOf(split[j]));
						}
					}
				}
				//Output line
				StringBuilder lineOutput = new StringBuilder();
				for (int k=0; k < vars; k++) {
					//Get the values
					ArrayList<Double> val = values.get(k);
					//System.out.println(Values: "+values.toString());
					//Calculate the average
					double average = getAverage(val);
					lineOutput.append(average);
					lineOutput.append(SEPARATOR);
					if (args[2].charAt(k) == 'B') { //Add standard deviation
						lineOutput.append(getDeviation(average,val));
						lineOutput.append(SEPARATOR);
					} else if (args[2].charAt(k) == 'C') { //Add standard error
						lineOutput.append(getStandardError(average,val));											
						lineOutput.append(SEPARATOR);
					}
				}
				//Write to the file
				lineOutput.append("\n");
				writer.write(lineOutput.toString());
				//System.out.println("****");				
			}
			//Close the output file
			writer.flush();
			writer.close();
		} catch (Exception e) {e.printStackTrace();}
		
	}

	public static double getAverage(ArrayList<Double> values) {
		int len = values.size();
		//If all values are NaN, then return NaN.
		if (len==0)
			return Double.NaN;
		//Otherwise, calculate the average
		double total=0.0;
		for(Double v: values) {
			total = total + v;
		}
		return total / ((double) len);
	}
	
	/**
	 * Calculates the standard deviation.
	 * @param avg The average.
	 * @param values All values.
	 * @return The standard deviation.
	 */
	public static double getDeviation(double avg, ArrayList<Double> values) {
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
	public static double getStandardError(double avg, ArrayList<Double> values) {
		return (getDeviation(avg,values) / Math.sqrt((double)values.size()));
	}
}
