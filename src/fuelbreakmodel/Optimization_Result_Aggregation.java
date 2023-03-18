package fuelbreakmodel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Optimization_Result_Aggregation {
	
	public Optimization_Result_Aggregation(List<Double> percentile_list, List<Double> percent_list, List<Double> flame_length_list, String input_folder) {
//		// for objectives
//		try {
//			List<String> final_list = new ArrayList<String>();
//			int model_count = 0;
//			
//			for (double fire_size_percentile : percentile_list) {
//				for (double percent_invest : percent_list) {
//					for (double escape_flame_length : flame_length_list) {
//						String escape_info = (escape_flame_length < Double.MAX_VALUE) ? String.valueOf(escape_flame_length) : "inf";
//						String setup_info = String.join("_", String.valueOf(fire_size_percentile), String.valueOf(percent_invest), escape_info);
//						File problem_file = new File(input_folder + "/model_outputs/problem_" + setup_info + ".lp");
//						File solution_file = new File(input_folder + "/model_outputs/solution_" + setup_info + ".sol");
//						File output_01_summary = new File(input_folder + "/model_outputs/output_01_summary_" + setup_info + ".txt");
//						File output_02_breaks = new File(input_folder + "/model_outputs/output_02_breaks_" + setup_info + ".txt");
//						File output_03_fires = new File(input_folder + "/model_outputs/output_03_fires_" + setup_info + ".txt");
//						
//						List<String> list;
//						list = Files.readAllLines(Paths.get(output_01_summary.getAbsolutePath()), StandardCharsets.UTF_8);
//						if (model_count == 0) {
//							final_list.add(list.get(0)); // header
//							final_list.add(list.get(1));
//						} else {
//							final_list.add(list.get(1));
//						}
//						model_count = model_count + 1;
//						
//					}
//				}
//			}
//			
//			File output_optimization_results = new File(input_folder + "/model_outputs/1_optimization_results.txt");
//			output_optimization_results.delete();
//			try (BufferedWriter fileOut = new BufferedWriter(new FileWriter(output_optimization_results))) {
//				for (int i = 0; i < final_list.size(); i++) {
//					fileOut.write(final_list.get(i));
//					if (i < final_list.size() - 1) fileOut.newLine();
//				}
//				fileOut.close();
//			} catch (IOException e) {
//				System.err.println("FileWriter(output_optimization_results) error - "	+ e.getClass().getName() + ": " + e.getMessage());
//			}
//			output_optimization_results.createNewFile();
//		
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
		
		
		// for breaks
		try {
			int number_of_models = percentile_list.size() * percent_list.size() * flame_length_list.size();
			List<String> header = new ArrayList<String>();
			header.add("break_id");
			String[][] value = new String[number_of_models][];	// 1 = selected, 0 otherwise
			int model_count = 0;
			int number_of_breaks = 0;
			
			for (double fire_size_percentile : percentile_list) {
				for (double percent_invest : percent_list) {
					for (double escape_flame_length : flame_length_list) {
						String escape_info = (escape_flame_length < Double.MAX_VALUE) ? String.valueOf(escape_flame_length) : "inf";
						String setup_info = String.join("_", String.valueOf(fire_size_percentile), String.valueOf(percent_invest), escape_info);
						File output_02_breaks = new File(input_folder + "/model_outputs/output_02_breaks_" + setup_info + ".txt");
						
						header.add(setup_info);
						List<String> list = Files.readAllLines(Paths.get(output_02_breaks.getAbsolutePath()), StandardCharsets.UTF_8);
						list.remove(0);	// Remove the first row (header)
						String[] a = list.toArray(new String[list.size()]);
						number_of_breaks = a.length;
						
						value[model_count] = new String[number_of_breaks];
						for (int i = 0; i < number_of_breaks; i++) {
							String[] rowValue = a[i].split("\t");
							value[model_count][i] = rowValue[3];
						}
						model_count = model_count + 1;
						
					}
				}
			}
			
			File output_breaks = new File(input_folder + "/model_outputs/2_breaks.txt");
			output_breaks.delete();
			try (BufferedWriter fileOut = new BufferedWriter(new FileWriter(output_breaks))) {
				fileOut.write(String.join("\t", header));
				for (int i = 0; i < number_of_breaks; i++) {
					String st = String.valueOf(i);
					for (int c = 0; c < number_of_models; c++) {
						st = st + "\t" + value[c][i];
					}
					fileOut.newLine();
					fileOut.write(st);
				}
				fileOut.close();
			} catch (IOException e) {
				System.err.println("FileWriter(output_breaks) error - "	+ e.getClass().getName() + ": " + e.getMessage());
			}
			output_breaks.createNewFile();
		
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
