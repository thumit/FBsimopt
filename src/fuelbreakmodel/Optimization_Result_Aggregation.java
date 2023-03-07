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
		try {
			List<String> final_list = new ArrayList<String>();
			int model_count = 0;
			
			for (double fire_size_percentile : percentile_list) {
				for (double percent_invest : percent_list) {
					for (double escape_flame_length : flame_length_list) {
						String escape_info = (escape_flame_length < Double.MAX_VALUE) ? String.valueOf(escape_flame_length) : "inf";
						String setup_info = String.join("_", String.valueOf(fire_size_percentile), String.valueOf(percent_invest), escape_info);
						File problem_file = new File(input_folder + "/model_outputs/problem_" + setup_info + ".lp");
						File solution_file = new File(input_folder + "/model_outputs/solution_" + setup_info + ".sol");
						File output_01_summary = new File(input_folder + "/model_outputs/output_01_summary_" + setup_info + ".txt");
						File output_02_breaks = new File(input_folder + "/model_outputs/output_02_breaks_" + setup_info + ".txt");
						File output_03_fires = new File(input_folder + "/model_outputs/output_03_fires_" + setup_info + ".txt");
						
						List<String> list;
						list = Files.readAllLines(Paths.get(output_01_summary.getAbsolutePath()), StandardCharsets.UTF_8);
						if (model_count == 0) {
							final_list.add(list.get(0)); // header
							final_list.add(list.get(1));
						} else {
							final_list.add(list.get(1));
						}
						model_count = model_count + 1;
						
					}
				}
			}
			
			File output_optimization_results = new File(input_folder + "/model_outputs/1_optimization_results.txt");
			output_optimization_results.delete();
			try (BufferedWriter fileOut = new BufferedWriter(new FileWriter(output_optimization_results))) {
				for (int i = 0; i < final_list.size(); i++) {
					fileOut.write(final_list.get(i));
					if (i < final_list.size() - 1) fileOut.newLine();
				}
				fileOut.close();
			} catch (IOException e) {
				System.err.println("FileWriter(output_optimization_results) error - "	+ e.getClass().getName() + ": " + e.getMessage());
			}
			output_optimization_results.createNewFile();
		
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
