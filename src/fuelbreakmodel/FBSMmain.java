package fuelbreakmodel;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FBSMmain {
	private static FBSMmain main;

	public static void main(String[] args) {
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				main = new FBSMmain();
			}
		});

	}
	
	public FBSMmain() {
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					String input_folder = get_workingLocation().replace("fbsm", "");
					File input_1_file = new File(input_folder + "/model_inputs/aggregated_break_input_1.csv");
					File input_2_file = new File(input_folder + "/model_inputs/aggregated_fire_input_1_general.csv");
					File input_3_file = new File(input_folder + "/model_inputs/aggregated_fire_input_2_blocked_breaks_ids.csv");
					File input_4_file = new File(input_folder + "/model_inputs/aggregated_fire_input_6_max_flame_lengths.csv");

					
					// Read input1 --------------------------------------------------------------------------------------------
					List<String> list = Files.readAllLines(Paths.get(input_1_file.getAbsolutePath()), StandardCharsets.UTF_8);
					list.remove(0);	// Remove the first row (header)
					String[] a = list.toArray(new String[list.size()]);
					int total_rows = a.length;
					int total_columns = a[0].split(",").length;				
					double[][] data = new double[total_rows][total_columns];
				
					// read all values from all rows and columns
					for (int i = 0; i < total_rows; i++) {
						String[] rowValue = a[i].split(",");
						for (int j = 0; j < total_columns; j++) {
							data[i][j] = Double.valueOf(rowValue[j]);
						}
					}
					
					int number_of_breaks = total_rows;						// total number of breaks
					int[] break_id = new int[number_of_breaks]; 			// break id
					double[] break_length = new double[number_of_breaks]; 	// break length
					double total_network_length = 0;
					for (int i = 0; i < number_of_breaks; i++) {
						break_id[i] = (int) data[i][0];
						break_length[i] = (int) data[i][8];
						total_network_length = total_network_length + break_length[i];
					}

					
					// Read input2 --------------------------------------------------------------------------------------------
					list = Files.readAllLines(Paths.get(input_2_file.getAbsolutePath()), StandardCharsets.UTF_8);
					list.remove(0);	// Remove the first row (header)
					a = list.toArray(new String[list.size()]);
					total_rows = a.length;
					total_columns = a[0].split(",").length;				
					data = new double[total_rows][total_columns];
					
					// read all values from all rows and columns
					for (int i = 0; i < total_rows; i++) {
						String[] rowValue = a[i].split(",");
						for (int j = 0; j < total_columns; j++) {
							data[i][j] = Double.valueOf(rowValue[j]);
						}
					}
				
					int number_of_fires = total_rows;							// total number of fires
					int[] fire_id = new int[number_of_fires]; 					// new fire id, start from 0, 1, 2, ... We use this in the model
					int[] new_fire_id = new int[number_of_fires]; 				// original fire id: 1, 3, 5, 9, ... We need this to associate model results
					double[] smoothed_fire_size = new double[number_of_fires];
					double[] saved_fire_area = new double[number_of_fires];
					double[] saved_wui_area = new double[number_of_fires];
					int[] number_of_collaborated_breaks = new int[number_of_fires];
					for (int j = 0; j < number_of_fires; j++) {
						new_fire_id[j] = j;
						fire_id[j] = (int) data[j][0];
						smoothed_fire_size[j] = data[j][4];
						saved_fire_area[j] = data[j][5];
						saved_wui_area[j] = data[j][8];
						number_of_collaborated_breaks[j] = (int) data[j][10];
					}
					
				
					// Read input3 (no header) --------------------------------------------------------------------------------------------
					list = Files.readAllLines(Paths.get(input_3_file.getAbsolutePath()), StandardCharsets.UTF_8);
					// list.remove(0);	// Remove the first row (header)
					a = list.toArray(new String[list.size()]);
					total_rows = a.length;
					
					// For each fire, there is a list of breaks that jointly work together to block/stop the fire 
					List<Integer>[] collaborated_breaks_list = new ArrayList[number_of_fires];
					for (int j = 0; j < number_of_fires; j++) {	
						collaborated_breaks_list[j] = new ArrayList<Integer>();
					}
					for (int j = 0; j < total_rows; j++) {
						String[] rowValue = a[j].split(",");
						for (String s : rowValue) {
							if (!s.equals("")) collaborated_breaks_list[j].add(Integer.valueOf(s));
						}
					}
					
					// Read input4 --------------------------------------------------------------------------------------------
					list = Files.readAllLines(Paths.get(input_4_file.getAbsolutePath()), StandardCharsets.UTF_8);
					// list.remove(0);	// Remove the first row (header)
					a = list.toArray(new String[list.size()]);
					total_rows = a.length;

					// For each fire, there is a list of of max_flame_length associated with the breaks that jointly work together to block/stop the fire 
					List<Double>[] collaborated_flamelengths_list = new ArrayList[number_of_fires];
					double[] max_flamelength_at_breaks = new double[number_of_fires];
					for (int j = 0; j < number_of_fires; j++) {	
						collaborated_flamelengths_list[j] = new ArrayList<Double>();
						max_flamelength_at_breaks[j] = 0;
					}
					for (int j = 0; j < total_rows; j++) {
						String[] rowValue = a[j].split(",");
						double max_fl = 0;
						for (String s : rowValue) {
							if (!s.equals("")) {
								double fl_value = Double.valueOf(s);
								collaborated_flamelengths_list[j].add(fl_value);
								if (max_fl < fl_value) max_fl = fl_value;
							}
						}
						max_flamelength_at_breaks[j] = max_fl;
					}
					
					
					// MODEL SETUP --------------------------------------------------------------
					// MODEL SETUP --------------------------------------------------------------
					// MODEL SETUP --------------------------------------------------------------
					List<Double> percentile_list = Arrays.asList(0.8, 1.0);									// i.e. 80% of fires (excluding the largest 20%)
					List<Double> percent_list = Arrays.asList(0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0);	// i.e. 10, 30, 50% total length of the break network.
					List<Double> flame_length_list = Arrays.asList(4.0, 8.0, Double.MAX_VALUE);				// i.e. 4ft, 8ft, ... if flame length the break can handle to contain fire. Fire exceeding this FL at the break will escape.
					
					// Solve optimization models
					for (double fire_size_percentile : percentile_list) {
						for (double percent_invest : percent_list) {
							for (double escape_flame_length : flame_length_list) {
								Optimization_Model model = new Optimization_Model(fire_size_percentile, percent_invest, escape_flame_length,
										input_folder, number_of_breaks, break_length, total_network_length,
										number_of_fires, fire_id, smoothed_fire_size, saved_fire_area,
										number_of_collaborated_breaks, collaborated_breaks_list, max_flamelength_at_breaks);
								model = null;
							}
						}
					}
					// Aggregate model results
					Optimization_Result_Aggregation models_aggragattion = new Optimization_Result_Aggregation(percentile_list, percent_list, flame_length_list, input_folder);
					models_aggragattion = null;
					
					
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	public static FBSMmain get_main() {
		return main;
	}
	
	public String get_workingLocation() {
		// Get working location of spectrumLite
		String workingLocation;
		// Get working location of the IDE project, or runnable jar file
		final File jarFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
		workingLocation = jarFile.getParentFile().toString();
		// Make the working location with correct name
		try {
			// to handle name with space (%20)
			workingLocation = URLDecoder.decode(workingLocation, "utf-8");
			workingLocation = new File(workingLocation).getPath();
		} catch (UnsupportedEncodingException e) {
			System.err.println(e.getClass().getName() + ": " + e.getMessage());
		}
		return workingLocation;
	}
}
