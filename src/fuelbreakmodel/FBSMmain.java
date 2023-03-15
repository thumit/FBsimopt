package fuelbreakmodel;

import java.awt.EventQueue;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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
					
					int number_of_breaks = total_rows;							// total number of breaks
					int[] break_id = new int[number_of_breaks]; 				// break id
					double[] break_length = new double[number_of_breaks]; 		// break length
					double[] fire_effectiveness = new double[number_of_breaks]; // total saved fire area / break full length
					double[] wui_effectiveness = new double[number_of_breaks]; 	// total saved wui area / break full length
					double total_network_length = 0;
					for (int i = 0; i < number_of_breaks; i++) {
						break_id[i] = (int) data[i][0];
						break_length[i] = (int) data[i][8];
						total_network_length = total_network_length + break_length[i];
						fire_effectiveness[i] = ((double) data[i][3]) / break_length[i];
						wui_effectiveness[i] = ((double) data[i][6]) / break_length[i];
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
					double[] wui_area = new double[number_of_fires];
					double[] saved_wui_area = new double[number_of_fires];
					int[] number_of_collaborated_breaks = new int[number_of_fires];
					for (int j = 0; j < number_of_fires; j++) {
						new_fire_id[j] = j;
						fire_id[j] = (int) data[j][0];
						smoothed_fire_size[j] = data[j][4];
						saved_fire_area[j] = data[j][5];
						wui_area[j] = data[j][7];
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
					
					// SOLVE OPTIMIZATION MODEL --------------------------------------------------------------
					// SOLVE OPTIMIZATION MODEL --------------------------------------------------------------
					// SOLVE OPTIMIZATION MODEL --------------------------------------------------------------
//					for (double fire_size_percentile : percentile_list) {
//						for (double percent_invest : percent_list) {
//							for (double escape_flame_length : flame_length_list) {
//								Optimization_Model model = new Optimization_Model("WUI", fire_size_percentile, percent_invest, escape_flame_length,
//										input_folder, number_of_breaks, break_length, total_network_length,
//										number_of_fires, fire_id, smoothed_fire_size, saved_fire_area, wui_area, saved_wui_area,
//										number_of_collaborated_breaks, collaborated_breaks_list, max_flamelength_at_breaks);
//								model = null;
//							}
//						}
//					}
//					// Aggregate model results
//					Optimization_Result_Aggregation models_aggragattion = new Optimization_Result_Aggregation(percentile_list, percent_list, flame_length_list, input_folder);
//					models_aggragattion = null;
					
					// SOLVE RANDOM SELECTION MODEL --------------------------------------------------------------
					// SOLVE RANDOM SELECTION MODEL --------------------------------------------------------------
					// SOLVE RANDOM SELECTION MODEL --------------------------------------------------------------
					// NOTE: the below code is use for both top-rank model and random model. We will need to change 2 places in the "Simulation_Model" class:
					//			1. number_of_runs = 1 for top-rank and = 50 for random)
					//			2. Disable either of the line: Random_Selection selection_method = ...		or 		Toprank_Selection selection_method = ...
					double size_of_modeled_fires = 0;		// same for every random run
					double wuisize_of_modeled_fires = 0;	// same for every random run
					int number_of_modeled_fires = 0;		// same for every random run
					double B = 0;							// same for every random run
					double[][][] average_objective_value = new double[percentile_list.size()][][];
					int[][][] average_number_of_contained_fires = new int[percentile_list.size()][][];
					int[][][] average_number_of_invested_breaks = new int[percentile_list.size()][][];
					double[][][] average_length_of_invested_breaks = new double[percentile_list.size()][][];
					double[][][] average_time_solving = new double[percentile_list.size()][][];

					for (int i = 0; i < percentile_list.size(); i++) {
						average_objective_value[i] = new double[percent_list.size()][];
						average_number_of_contained_fires[i] = new int[percent_list.size()][];
						average_number_of_invested_breaks[i] = new int[percent_list.size()][];
						average_length_of_invested_breaks[i] = new double[percent_list.size()][];
						average_time_solving[i] = new double[percent_list.size()][];
						for (int j = 0; j < percent_list.size(); j++) {
							average_objective_value[i][j] = new double[flame_length_list.size()];
							average_number_of_contained_fires[i][j] = new int[flame_length_list.size()];
							average_number_of_invested_breaks[i][j] = new int[flame_length_list.size()];
							average_length_of_invested_breaks[i][j] = new double[flame_length_list.size()];
							average_time_solving[i][j] = new double[flame_length_list.size()];
						}
					}
					
					for (int i = 0; i < percentile_list.size(); i++) {
						double fire_size_percentile = percentile_list.get(i);
						for (int j = 0; j < percent_list.size(); j++) {
							double percent_invest = percent_list.get(j);
							for (int k = 0; k < flame_length_list.size(); k++) {
								double escape_flame_length = flame_length_list.get(k);
								Simulation_Model model = new Simulation_Model("WUI", fire_size_percentile, percent_invest, escape_flame_length,
										input_folder, number_of_breaks, break_length, total_network_length,
										number_of_fires, fire_id, smoothed_fire_size, saved_fire_area, wui_area, saved_wui_area,
										number_of_collaborated_breaks, collaborated_breaks_list, max_flamelength_at_breaks,
										 fire_effectiveness, wui_effectiveness);
								
								size_of_modeled_fires = model.get_size_of_modeled_fires();
								wuisize_of_modeled_fires = model.get_wuisize_of_modeled_fires();
								number_of_modeled_fires = model.get_number_of_modeled_fires();
								B = model.get_B();
								average_objective_value[i][j][k] = model.get_average_objective_value();
								average_number_of_contained_fires[i][j][k] = model.get_average_number_of_contained_fires();
								average_number_of_invested_breaks[i][j][k] = model.get_average_number_of_invested_breaks();
								average_length_of_invested_breaks[i][j][k] = model.get_average_length_of_invested_breaks();
								average_time_solving[i][j][k] = model.get_average_time_solving();
								System.out.println(fire_size_percentile + " " + percent_invest + " " + escape_flame_length + "-----------------------50 runs done------------------------------------");
							}
						}
					}
					
					// Write summary for random selection models
					File random_summary = new File(input_folder + "/model_outputs/output_simulation_summary.txt");
					random_summary.delete();
					try (BufferedWriter fileOut = new BufferedWriter(new FileWriter(random_summary))) {
						String file_header = String.join("\t", "fire_size_percentile", "breaks_percent_limit", "escape_flame_length",
								"size_of_modeled_fires", "wuisize_of_modeled_fires", "number_of_modeled_fires", "breaks_length_limit",
								"average_solution_time",
								"average_objective", "average_number_of_contained_fires", "average_number_of_invested_breaks", "average_length_of_invested_breaks");
						fileOut.write(file_header);
						
						for (int i = 0; i < percentile_list.size(); i++) {
							double fire_size_percentile = percentile_list.get(i);
							for (int j = 0; j < percent_list.size(); j++) {
								double percent_invest = percent_list.get(j);
								for (int k = 0; k < flame_length_list.size(); k++) {
									double escape_flame_length = flame_length_list.get(k);
									fileOut.newLine();
									fileOut.write(fire_size_percentile + "\t" + percent_invest + "\t" + escape_flame_length + "\t" +
											size_of_modeled_fires + "\t" + wuisize_of_modeled_fires + "\t" + number_of_modeled_fires + "\t" + B + "\t" +
											average_time_solving[i][j][k] + "\t" + 
											average_objective_value[i][j][k] + "\t" + average_number_of_contained_fires[i][j][k] + "\t" + average_number_of_invested_breaks[i][j][k] + "\t" + average_length_of_invested_breaks[i][j][k]);
									
								}
							}
						}
						fileOut.close();
					} catch (IOException e) {
						System.err.println("FileWriter(simulation_summary) error - "	+ e.getClass().getName() + ": " + e.getMessage());
					}
					random_summary.createNewFile();
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
