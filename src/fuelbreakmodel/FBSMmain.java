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
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Stream;

import ilog.concert.IloLPMatrix;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.Status;

public class FBSMmain {
	private static FBSMmain main;
	private DecimalFormat twoDForm = new DecimalFormat("#.##");	 // Only get 2 decimal
	private DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd - HH:mm:ss");
	private long time_start, time_end;
	private double time_reading, time_solving, time_writing;

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
					File problem_file = new File(input_folder + "/model_outputs/problem.lp");
					File solution_file = new File(input_folder + "/model_outputs/solution.sol");
					File output_variables_file = new File(input_folder + "/model_outputs/output_01_variables.txt");

					double percent_invest = 0.1;	// i.e. 10, 30, 50% total length of the break network.
					
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
					double B = percent_invest * total_network_length;

					
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
					for (int i = 0; i < number_of_fires; i++) {
						new_fire_id[i] = i;
						fire_id[i] = (int) data[i][0];
						smoothed_fire_size[i] = data[i][4];
						saved_fire_area[i] = data[i][5];
						saved_wui_area[i] = data[i][8];
						number_of_collaborated_breaks[i] = (int) data[i][10];
					}
					
				
					// Read input3 (no header) --------------------------------------------------------------------------------------------
					list = Files.readAllLines(Paths.get(input_3_file.getAbsolutePath()), StandardCharsets.UTF_8);
					// list.remove(0);	// Remove the first row (header)
					a = list.toArray(new String[list.size()]);
					total_rows = a.length;
					
					// For each fire, there is a list of breaks that jointly work together to block/stop the fire 
					List<Integer>[] collaborated_breaks_list = new ArrayList[number_of_fires];
					for (int i = 0; i < number_of_fires; i++) {	
						collaborated_breaks_list[i] = new ArrayList<Integer>();
					}
					for (int i = 0; i < total_rows; i++) {
						String[] rowValue = a[i].split(",");
						for (String s : rowValue) {
							if (!s.equals("")) collaborated_breaks_list[i].add(Integer.valueOf(s));
						}
					}
					
					// Read input4 --------------------------------------------------------------------------------------------
					list = Files.readAllLines(Paths.get(input_4_file.getAbsolutePath()), StandardCharsets.UTF_8);
					// list.remove(0);	// Remove the first row (header)
					a = list.toArray(new String[list.size()]);
					total_rows = a.length;

					// For each fire, there is a list of of max_flame_length associated with the breaks that jointly work together to block/stop the fire 
					List<Double>[] collaborated_flamelengths_list = new ArrayList[number_of_fires];
					for (int i = 0; i < number_of_fires; i++) {	
						collaborated_flamelengths_list[i] = new ArrayList<Double>();
					}
					for (int i = 0; i < total_rows; i++) {
						String[] rowValue = a[i].split(",");
						for (String s : rowValue) {
							if (!s.equals("")) collaborated_flamelengths_list[i].add(Double.valueOf(s));
						}
					}
					
					
					
					
					// DEFINITIONS --------------------------------------------------------------
					// DEFINITIONS --------------------------------------------------------------
					// DEFINITIONS --------------------------------------------------------------
					List<Information_Variable> var_info_list = new ArrayList<Information_Variable>();
					List<Double> objlist = new ArrayList<Double>();					// objective coefficient
					List<String> vnamelist = new ArrayList<String>();				// variable name
					List<Double> vlblist = new ArrayList<Double>();					// lower bound
					List<Double> vublist = new ArrayList<Double>();					// upper bound
					List<IloNumVarType> vtlist = new ArrayList<IloNumVarType>();	//variable type
					int nvars = 0;

					// declare arrays to keep variables. some variables are optimized by using jagged-arrays
					int x[] = new int[number_of_breaks];
					for (int i = 0; i < number_of_breaks; i++) {
						String var_name = "x_" + i;
						Information_Variable var_info = new Information_Variable(var_name);
						var_info_list.add(var_info);
						objlist.add((double) 0);
						vnamelist.add(var_name);
						vlblist.add((double) 0);
						vublist.add((double) 1);
						vtlist.add(IloNumVarType.Bool);
						x[i] = nvars;
						nvars++;
					}
					
					int y[] = new int[number_of_fires];
					for (int j = 0; j < number_of_fires; j++) {
						String var_name = "y_" + j;
						Information_Variable var_info = new Information_Variable(var_name);
						var_info_list.add(var_info);
						objlist.add((double) saved_fire_area[j]);
						vnamelist.add(var_name);
						vlblist.add((double) 0);
						vublist.add((double) 1);
						vtlist.add(IloNumVarType.Bool);
						y[j] = nvars;
						nvars++;
					}
					
					// Convert lists to 1-D arrays
					double[] objvals = Stream.of(objlist.toArray(new Double[objlist.size()])).mapToDouble(Double::doubleValue).toArray();
					objlist = null;			// Clear the lists to save memory
					String[] vname = vnamelist.toArray(new String[nvars]);
					vnamelist = null;		// Clear the lists to save memory
					double[] vlb = Stream.of(vlblist.toArray(new Double[vlblist.size()])).mapToDouble(Double::doubleValue).toArray();
					vlblist = null;			// Clear the lists to save memory
					double[] vub = Stream.of(vublist.toArray(new Double[vublist.size()])).mapToDouble(Double::doubleValue).toArray();
					vublist = null;			// Clear the lists to save memory
					IloNumVarType[] vtype = vtlist.toArray(new IloNumVarType[vtlist.size()]);						
					vtlist = null;		// Clear the lists to save memory
					Information_Variable[] var_info_array = new Information_Variable[nvars];		// This array stores variable information
					for (int i = 0; i < nvars; i++) {
						var_info_array[i] = var_info_list.get(i);
					}	
					var_info_list = null;	// Clear the lists to save memory
					
					
					
					
					// CREATE CONSTRAINTS-------------------------------------------------
					// CREATE CONSTRAINTS-------------------------------------------------
					// CREATE CONSTRAINTS-------------------------------------------------
					// NOTE: Constraint bounds are optimized for better performance
					// Constraints 2------------------------------------------------------
					List<List<Integer>> c2_indexlist = new ArrayList<List<Integer>>();	
					List<List<Double>> c2_valuelist = new ArrayList<List<Double>>();
					List<Double> c2_lblist = new ArrayList<Double>();	
					List<Double> c2_ublist = new ArrayList<Double>();
					int c2_num = 0;
					
					for (int j = 0; j < number_of_fires; j++) {
						if (number_of_collaborated_breaks[j] > 0) {
							// Add constraint
							c2_indexlist.add(new ArrayList<Integer>());
							c2_valuelist.add(new ArrayList<Double>());
							
							// Add y[j]
							c2_indexlist.get(c2_num).add(y[j]);
							c2_valuelist.get(c2_num).add((double) 1);
							
							// Add -1/M[j]*sigma x[i]
							for (int index = 0; index < number_of_collaborated_breaks[j]; index++ ) {
								int i = collaborated_breaks_list[j].get(index);	// break id that contributes to fire containment
								int M = number_of_collaborated_breaks[j];
								c2_indexlist.get(c2_num).add(x[i]);
								c2_valuelist.get(c2_num).add((double) -1 / M);
							}
							// add bounds
							c2_lblist.add((double) -1);	// Lower bound = -1 instead of infinity because it may be faster
							c2_ublist.add((double) 0);	// Upper bound = 0
							c2_num++;
						}
					}
					
					double[] c2_lb = Stream.of(c2_lblist.toArray(new Double[c2_lblist.size()])).mapToDouble(Double::doubleValue).toArray();
					double[] c2_ub = Stream.of(c2_ublist.toArray(new Double[c2_ublist.size()])).mapToDouble(Double::doubleValue).toArray();		
					int[][] c2_index = new int[c2_num][];
					double[][] c2_value = new double[c2_num][];
				
					for (int i = 0; i < c2_num; i++) {
						c2_index[i] = new int[c2_indexlist.get(i).size()];
						c2_value[i] = new double[c2_indexlist.get(i).size()];
						for (int j = 0; j < c2_indexlist.get(i).size(); j++) {
							c2_index[i][j] = c2_indexlist.get(i).get(j);
							c2_value[i][j] = c2_valuelist.get(i).get(j);			
						}
					}	
					
					// Clear lists to save memory
					c2_indexlist = null;	
					c2_valuelist = null;
					c2_lblist = null;	
					c2_ublist = null;
					System.out.println("Total constraints as in the model formulation eq. (2):   " + c2_num + "             " + dateFormat.format(new Date()));
					
					
					
					// Constraints 3------------------------------------------------------
					List<List<Integer>> c3_indexlist = new ArrayList<List<Integer>>();	
					List<List<Double>> c3_valuelist = new ArrayList<List<Double>>();
					List<Double> c3_lblist = new ArrayList<Double>();	
					List<Double> c3_ublist = new ArrayList<Double>();
					int c3_num = 0;
					
					for (int j = 0; j < number_of_fires; j++) {
						if (number_of_collaborated_breaks[j] > 0) {
							// Add constraint
							c3_indexlist.add(new ArrayList<Integer>());
							c3_valuelist.add(new ArrayList<Double>());
							
							// Add y[j]
							c3_indexlist.get(c3_num).add(y[j]);
							c3_valuelist.get(c3_num).add((double) 1);
							
							// Add -sigma x[i]
							for (int index = 0; index < number_of_collaborated_breaks[j]; index++ ) {
								int i = collaborated_breaks_list[j].get(index);	// break id that contributes to fire containment
								c3_indexlist.get(c3_num).add(x[i]);
								c3_valuelist.get(c3_num).add((double) -1);
							}
							// add bounds
							int M = number_of_collaborated_breaks[j];
							c3_lblist.add((double) 1 - M);	// Lower bound = 1 - M
							c3_ublist.add((double) 1);		// Upper bound = 1 instead of infinity because it may be faster
							c3_num++;
						}
					}
					
					double[] c3_lb = Stream.of(c3_lblist.toArray(new Double[c3_lblist.size()])).mapToDouble(Double::doubleValue).toArray();
					double[] c3_ub = Stream.of(c3_ublist.toArray(new Double[c3_ublist.size()])).mapToDouble(Double::doubleValue).toArray();		
					int[][] c3_index = new int[c3_num][];
					double[][] c3_value = new double[c3_num][];
				
					for (int i = 0; i < c3_num; i++) {
						c3_index[i] = new int[c3_indexlist.get(i).size()];
						c3_value[i] = new double[c3_indexlist.get(i).size()];
						for (int j = 0; j < c3_indexlist.get(i).size(); j++) {
							c3_index[i][j] = c3_indexlist.get(i).get(j);
							c3_value[i][j] = c3_valuelist.get(i).get(j);			
						}
					}	
					
					// Clear lists to save memory
					c3_indexlist = null;	
					c3_valuelist = null;
					c3_lblist = null;	
					c3_ublist = null;
					System.out.println("Total constraints as in the model formulation eq. (3):   " + c3_num + "             " + dateFormat.format(new Date()));
					
					
					
					// Constraints 4------------------------------------------------------
					List<List<Integer>> c4_indexlist = new ArrayList<List<Integer>>();	
					List<List<Double>> c4_valuelist = new ArrayList<List<Double>>();
					List<Double> c4_lblist = new ArrayList<Double>();	
					List<Double> c4_ublist = new ArrayList<Double>();
					int c4_num = 0;
					
					for (int j = 0; j < number_of_fires; j++) {
						if (number_of_collaborated_breaks[j] == 0) {
							// Add constraint
							c4_indexlist.add(new ArrayList<Integer>());
							c4_valuelist.add(new ArrayList<Double>());
							
							// Add y[j]
							c4_indexlist.get(c4_num).add(y[j]);
							c4_valuelist.get(c4_num).add((double) 1);
							
							// add bounds
							c4_lblist.add((double) 0);	// Lower bound = 0
							c4_ublist.add((double) 0);	// Upper bound = 0
							c4_num++;
						}
					}	
					
					double[] c4_lb = Stream.of(c4_lblist.toArray(new Double[c4_lblist.size()])).mapToDouble(Double::doubleValue).toArray();
					double[] c4_ub = Stream.of(c4_ublist.toArray(new Double[c4_ublist.size()])).mapToDouble(Double::doubleValue).toArray();		
					int[][] c4_index = new int[c4_num][];
					double[][] c4_value = new double[c4_num][];
				
					for (int i = 0; i < c4_num; i++) {
						c4_index[i] = new int[c4_indexlist.get(i).size()];
						c4_value[i] = new double[c4_indexlist.get(i).size()];
						for (int j = 0; j < c4_indexlist.get(i).size(); j++) {
							c4_index[i][j] = c4_indexlist.get(i).get(j);
							c4_value[i][j] = c4_valuelist.get(i).get(j);			
						}
					}	
					
					// Clear lists to save memory
					c4_indexlist = null;	
					c4_valuelist = null;
					c4_lblist = null;	
					c4_ublist = null;
					System.out.println("Total constraints as in the model formulation eq. (4):   " + c4_num + "             " + dateFormat.format(new Date()));
					
					
					
					// Constraints 5------------------------------------------------------
					List<List<Integer>> c5_indexlist = new ArrayList<List<Integer>>();	
					List<List<Double>> c5_valuelist = new ArrayList<List<Double>>();
					List<Double> c5_lblist = new ArrayList<Double>();	
					List<Double> c5_ublist = new ArrayList<Double>();
					int c5_num = 0;
					
					// Add constraint
					c5_indexlist.add(new ArrayList<Integer>());
					c5_valuelist.add(new ArrayList<Double>());
					for (int i = 0; i < number_of_breaks; i++) {
						// Add sigma L[i]x[i]
						c5_indexlist.get(c5_num).add(x[i] );
						c5_valuelist.get(c5_num).add((double) break_length[i]);
					}
					// add bounds
					c5_lblist.add((double) 0);			// Lower bound = 0
					c5_ublist.add((double) B);			// Upper bound = B
					c5_num++;
					
					double[] c5_lb = Stream.of(c5_lblist.toArray(new Double[c5_lblist.size()])).mapToDouble(Double::doubleValue).toArray();
					double[] c5_ub = Stream.of(c5_ublist.toArray(new Double[c5_ublist.size()])).mapToDouble(Double::doubleValue).toArray();		
					int[][] c5_index = new int[c5_num][];
					double[][] c5_value = new double[c5_num][];
				
					for (int i = 0; i < c5_num; i++) {
						c5_index[i] = new int[c5_indexlist.get(i).size()];
						c5_value[i] = new double[c5_indexlist.get(i).size()];
						for (int j = 0; j < c5_indexlist.get(i).size(); j++) {
							c5_index[i][j] = c5_indexlist.get(i).get(j);
							c5_value[i][j] = c5_valuelist.get(i).get(j);			
						}
					}	
					
					// Clear lists to save memory
					c5_indexlist = null;	
					c5_valuelist = null;
					c5_lblist = null;	
					c5_ublist = null;
					System.out.println("Total constraints as in the model formulation eq. (5):   " + c5_num + "             " + dateFormat.format(new Date()));
					time_end = System.currentTimeMillis();		// measure time after reading
					time_reading = (double) (time_end - time_start) / 1000;
					
					
					
					
					
					// SOLVE --------------------------------------------------------------
					// SOLVE --------------------------------------------------------------
					// SOLVE --------------------------------------------------------------
					try {
						// Add the CPLEX native library path dynamically at run time
//						LibraryHandle.setLibraryPath(FilesHandle.get_temporaryFolder().getAbsolutePath().toString());
//						LibraryHandle.addLibraryPath(FilesHandle.get_temporaryFolder().getAbsolutePath().toString());
//						System.out.println("Successfully loaded CPLEX .dll files from " + FilesHandle.get_temporaryFolder().getAbsolutePath().toString());
						
						System.out.println("Prism found the below java library paths:");
						String property = System.getProperty("java.library.path");
						StringTokenizer parser = new StringTokenizer(property, ";");
						while (parser.hasMoreTokens()) {
							System.out.println("           - " + parser.nextToken());
						}
						
						IloCplex cplex = new IloCplex();
						IloLPMatrix lp = cplex.addLPMatrix();
						IloNumVar[] var = cplex.numVarArray(cplex.columnArray(lp, nvars), vlb, vub, vtype, vname);
						vlb = null; vub = null; vtype = null; // vname = null;		// Clear arrays to save memory
						
						// Add constraints
						lp.addRows(c2_lb, c2_ub, c2_index, c2_value); 		// Constraints 2
						lp.addRows(c3_lb, c3_ub, c3_index, c3_value); 		// Constraints 3
						lp.addRows(c4_lb, c4_ub, c4_index, c4_value); 		// Constraints 4
						lp.addRows(c5_lb, c5_ub, c5_index, c5_value); 		// Constraints 4
						
						// Clear arrays to save memory
						c2_lb = null;  c2_ub = null;  c2_index = null;  c2_value = null;
						c3_lb = null;  c3_ub = null;  c3_index = null;  c3_value = null;
						c4_lb = null;  c4_ub = null;  c4_index = null;  c4_value = null;
						c5_lb = null;  c5_ub = null;  c5_index = null;  c5_value = null;
						
						// Set constraints set name: Notice THIS WILL EXTREMELY SLOW THE SOLVING PROCESS (recommend for debugging only)
						int indexOfC2 = c2_num;
						int indexOfC3 = indexOfC2 + c3_num;
						int indexOfC4 = indexOfC3 + c4_num;
						int indexOfC5 = indexOfC4 + c5_num;	// Note: lp.getRanges().length = indexOfC5
						for (int i = 0; i < lp.getRanges().length; i++) {	
							if (0 <= i && i < indexOfC2) lp.getRanges() [i].setName("S.2");
							if (indexOfC2<=i && i<indexOfC3) lp.getRanges() [i].setName("S.3");
							if (indexOfC3<=i && i<indexOfC4) lp.getRanges() [i].setName("S.4");
							if (indexOfC4<=i && i<indexOfC5) lp.getRanges() [i].setName("S.5");
						}
						
						cplex.addMaximize(cplex.scalProd(var, objvals));
						objvals = null;		// Clear arrays to save memory
//						cplex.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Auto); // Auto choose optimization method
						cplex.setParam(IloCplex.Param.MIP.Strategy.Search, IloCplex.MIPSearch.Traditional);	// MIP method
//						cplex.setParam(IloCplex.DoubleParam.EpGap, 0.00); // Gap is 0%
//						int solvingTimeLimit = 30 * 60; //Get time Limit in minute * 60 = seconds
//						cplex.setParam(IloCplex.DoubleParam.TimeLimit, solvingTimeLimit); // Set Time limit
//						cplex.setParam(IloCplex.Param.MIP.Tolerances.Integrality, 0); 	// Set integrality tolerance: https://www.ibm.com/docs/en/icos/20.1.0?topic=parameters-integrality-tolerance;    https://www.ibm.com/support/pages/why-does-binary-or-integer-variable-take-noninteger-value-solution
//						cplex.setParam(IloCplex.BooleanParam.PreInd, false);			// page 40: sets the Boolean parameter PreInd to false, instructing CPLEX not to apply presolve before solving the problem.
//						cplex.setParam(IloCplex.Param.Preprocessing.Presolve, false);	// turn off presolve to prevent it from completely solving the model before entering the actual LP optimizer (same as above ???)
						
						time_start = System.currentTimeMillis();		// measure time before solving
						cplex.exportModel(problem_file.getAbsolutePath());
						if (cplex.solve()) {
							cplex.exportModel(problem_file.getAbsolutePath());
							cplex.writeSolution(solution_file.getAbsolutePath());
							double[] value = cplex.getValues(lp);
							// double[] reduceCost = cplex.getReducedCosts(lp);
							// double[] dual = cplex.getDuals(lp);
							double[] slack = cplex.getSlacks(lp);
							double objective_value = cplex.getObjValue();
							Status cplex_status = cplex.getStatus();
							int cplex_algorithm = cplex.getAlgorithm();
							long cplex_iteration = cplex.getNiterations64();
							time_end = System.currentTimeMillis();		// measure time after solving
							time_solving = (double) (time_end - time_start) / 1000;
							
							// WRITE SOLUTION --------------------------------------------------------------
							// WRITE SOLUTION --------------------------------------------------------------
							// WRITE SOLUTION --------------------------------------------------------------
							// output_01_variables
							output_variables_file.delete();
							try (BufferedWriter fileOut = new BufferedWriter(new FileWriter(output_variables_file))) {
								String file_header = String.join("\t", "var_id", "var_name", "var_value", "var_slack");
								fileOut.write(file_header);
								
								for (int i = 0; i < value.length; i++) {
									if (value[i] != 0) {	// only write variable that is not zero
										fileOut.newLine();
										fileOut.write(i + "\t" + vname[i] + "\t" + value[i] + "\t" + slack[i]);
									}
								}
								fileOut.close();
							} catch (IOException e) {
								System.err.println("FileWriter(output_variables_file) error - "	+ e.getClass().getName() + ": " + e.getMessage());
							}
							output_variables_file.createNewFile();
							
							
							
							
							
							
							
							
						}
						cplex.end();
					} catch (Exception e) {
						System.err.println("Panel Solve Runs - cplexLib.addLibraryPath error - " + e.getClass().getName() + ": " + e.getMessage());
					}
					
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
