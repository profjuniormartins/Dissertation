/*
 * Created on 03/05/2006.
 */
package support;

import java.io.*;
import java.util.*;

/**
 *
 *
 * @author Gustavo S. Pavani
 * @version 1.0
 *
 */
public class MultipleConfig {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//Verify if the arguments are correct. Otherwise, print usage information.
		if (args.length == 2) {
			readFiles1(args[0],args[1]);
		} else if (args.length == 3) {
			readFiles2(args[0],args[1],args[2]);
		} else if (args.length == 4) {
			readFiles3(args[0],args[1],args[2],args[3]);
		} else {
			System.err.println("Usage: java support.MultipleConfig file fileVariable1 or");
			System.err.println("Usage: java support.MultipleConfig file fileVariable1 fileVariable2");
			return;
		}
	}
	
	/**
	 * Read the files of the specified dir containing the indicated preffix.
	 * @param file The base configuration file
	 * @param fileVariables1 The file containing the name of files that contain the list to be used. 
	 */
	public static void readFiles1(String file, String fileVariable1) {
		//Get the preffix of the file
		int indexDot = file.lastIndexOf(".");
		String preffix = file.substring(0,indexDot);
		//Read the file of values and store it on the vector
		Vector<String> values1 = new Vector<String>();
		try {
			LineNumberReader valueReader1 = new LineNumberReader(new FileReader(fileVariable1));
			//Get the variable name
			String variable1 = valueReader1.readLine();
			//Get the values
			while (valueReader1.ready()) {
				values1.add(valueReader1.readLine());
			}
			valueReader1.close(); //close the reader
			//for each variable value do
			for (String value1: values1) {
				//Create the new file
				File output = new File(preffix+"_"+variable1+value1+".xml"); 
				output.createNewFile();
				//Create the writer
				FileWriter writer;
				writer = new FileWriter(output);
				//Now read the configuration file
				FileReader reader = new FileReader(file);
				int dollar = Character.toLowerCase('$');
				//System.out.println("Dollar: "+dollar);
				boolean flag = false;
				while (reader.ready()) {
					int c = reader.read();
					//System.out.println(c);
					if (c == dollar && !flag) {
						flag = true;
					} else if (!flag) {
						writer.write(c);
					} else { //reading variable
						if (c == dollar) { //finished variable
							flag = false;
							writer.write(value1.toCharArray());
						}
					}
				}
				//Close the reader and the writer
				writer.flush();
				writer.close();
				reader.close();
			}
		} catch(Exception e) {e.printStackTrace();}
	}

	/**
	 * Read the files of the specified dir containing the indicated preffix.
	 * @param file The base configuration file
	 * @param fileVariables1 The file containing the name of files that contain the list to be used. 
	 */
	public static void readFiles2(String file, String fileVariable1, String fileVariable2) {
		//Get the preffix of the file
		int indexDot = file.lastIndexOf(".");
		String preffix = file.substring(0,indexDot);
		//Read the file of values and store it on the vector
		Vector<String> values1 = new Vector<String>();
		Vector<String> values2 = new Vector<String>();
		try {
			/* For the first variable */
			LineNumberReader valueReader1 = new LineNumberReader(new FileReader(fileVariable1));
			//Get the variable name
			String variable1 = valueReader1.readLine();
			//Get the values
			while (valueReader1.ready()) {
				values1.add(valueReader1.readLine());
			}
			valueReader1.close(); //close the reader
			/* For the second variable. */
			LineNumberReader valueReader2 = new LineNumberReader(new FileReader(fileVariable2));
			//Get the variable name
			String variable2 = valueReader2.readLine();
			//Get the values
			while (valueReader2.ready()) {
				values2.add(valueReader2.readLine());
			}
			valueReader2.close(); //close the reader			
			//for each variable value do
			for (String value1: values1) {
				for (String value2: values2) {
					//Create the new file
					File output = new File(preffix+"_"+variable1+value1+"_"+variable2+value2+".xml"); 
					output.createNewFile();
					//Create the writer
					FileWriter writer;
					writer = new FileWriter(output);
					//Now read the configuration file
					FileReader reader = new FileReader(file);
					int dollar = Character.toLowerCase('$');
					//System.out.println("Dollar: "+dollar);
					boolean flag = false;
					StringBuilder var = null;
					while (reader.ready()) {
						int c = reader.read();
						//System.out.println(c);
						if (c == dollar && !flag) {
							flag = true;
							var = new StringBuilder();
						} else if (!flag) {
							writer.write(c);
						} else { //reading variable
							if (c == dollar) { //finished variable
								flag = false;
								//System.out.println("\""+var.toString()+"\""+" \""+variable1+"\"");
								if (var.toString().equals(variable1)) {
									writer.write(value1.toCharArray());
								} else { //variable2
									writer.write(value2.toCharArray());
								}
							} else {
								var.appendCodePoint(c);
							}
						}
					}
					//Close the reader and the writer
					writer.flush();
					writer.close();
					reader.close();
				}
			}
		} catch(Exception e) {e.printStackTrace();}
	}
	
	/**
	 * Read the files of the specified dir containing the indicated preffix.
	 * @param file The base configuration file
	 * @param fileVariables1 The file containing the name of files that contain the list to be used. 
	 */
	public static void readFiles3(String file, String fileVariable1, String fileVariable2, String fileVariable3) {
		//Get the preffix of the file
		int indexDot = file.lastIndexOf(".");
		String preffix = file.substring(0,indexDot);
		//Read the file of values and store it on the vector
		Vector<String> values1 = new Vector<String>();
		Vector<String> values2 = new Vector<String>();
		Vector<String> values3 = new Vector<String>();
		try {
			/* For the first variable */
			LineNumberReader valueReader1 = new LineNumberReader(new FileReader(fileVariable1));
			//Get the variable name
			String variable1 = valueReader1.readLine();
			//Get the values
			while (valueReader1.ready()) {
				values1.add(valueReader1.readLine());
			}
			valueReader1.close(); //close the reader
			/* For the second variable. */
			LineNumberReader valueReader2 = new LineNumberReader(new FileReader(fileVariable2));
			//Get the variable name
			String variable2 = valueReader2.readLine();
			//Get the values
			while (valueReader2.ready()) {
				values2.add(valueReader2.readLine());
			}
			valueReader2.close(); //close the reader			
			/* For the second variable. */
			LineNumberReader valueReader3 = new LineNumberReader(new FileReader(fileVariable3));
			//Get the variable name
			String variable3 = valueReader3.readLine();
			//Get the values
			while (valueReader3.ready()) {
				values3.add(valueReader3.readLine());
			}
			valueReader3.close(); //close the reader			
			//for each variable value do
			for (String value1: values1) {
				int index = 0; //index for getting the third value
				for (String value2: values2) {
					String value3 = values3.get(index);
					//Create the new file
					String newFile = preffix+"_"+value2+"-"+value3+"_"+variable1+value1+".xml";
					//System.out.println("Creating: "+newFile);
					File output = new File(newFile); 
					output.createNewFile();
					//Create the writer
					FileWriter writer;
					writer = new FileWriter(output);
					//Now read the configuration file
					FileReader reader = new FileReader(file);
					int dollar = Character.toLowerCase('$');
					//System.out.println("Dollar: "+dollar);
					boolean flag = false;
					StringBuilder var = null;
					while (reader.ready()) {
						int c = reader.read();
						//System.out.println(c);
						if (c == dollar && !flag) {
							flag = true;
							var = new StringBuilder();
						} else if (!flag) {
							writer.write(c);
						} else { //reading variable
							if (c == dollar) { //finished variable
								flag = false;
								//System.out.println("\""+var.toString()+"\""+" \""+variable1+"\"");
								if (var.toString().equals(variable1)) {
									writer.write(value1.toCharArray());
								} else if (var.toString().equals(variable2)) { //variable2
									writer.write(value2.toCharArray());
								} else { //variable 3
									writer.write(value3.toCharArray());
								}
							} else {
								var.appendCodePoint(c);
							}
						}
					}
					//Close the reader and the writer
					writer.flush();
					writer.close();
					reader.close();
					//Increment the index.
					index ++;
				}
			}
		} catch(Exception e) {e.printStackTrace();}
	}

}
