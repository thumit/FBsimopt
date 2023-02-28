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
					String input_folder = get_workingLocation().replace("fuelbreakmodel", "");
					File input_1_file = new File(input_folder + "/model_inputs/fuelbreak1.txt");
					File input_2_file = new File(input_folder + "/model_inputs/fuelbreak2.txt");
					File input_3_file = new File(input_folder + "/model_inputs/fuelbreak3.txt");
					File input_4_file = new File(input_folder + "/model_inputs/fuelbreak4.txt");
					File problem_file = new File(input_folder + "/model_outputs/problem.lp");
					File solution_file = new File(input_folder + "/model_outputs/solution.sol");
					File output_variables_file = new File(input_folder + "/model_outputs/output_01_variables.txt");

					double M = 10000;
					double budget = 0;
					// Read input1 --------------------------------------------------------------------------------------------
					List<String> list = Files.readAllLines(Paths.get(input_1_file.getAbsolutePath()), StandardCharsets.UTF_8);
					list.remove(0);	// Remove the first row (header)
					String[] a = list.toArray(new String[list.size()]);
					int total_rows = a.length;
					int total_columns = a[0].split("\t").length;				
					int[][] data = new int[total_rows][total_columns];
				
					// read all values from all rows and columns
					for (int i = 0; i < total_rows; i++) {
						String[] rowValue = a[i].split("\t");
						for (int j = 0; j < total_columns; j++) {
							data[i][j] = Integer.parseInt(rowValue[j]);
						}
					}
					
					int number_of_fires = total_rows;	// total number of fires
					int[] number_of_PODS = new int[number_of_fires]; 	// total number of dynamic PODs for each fire
					int[] n = new int[number_of_fires]; 				// total number of dynamic PODs for each fire minus one
					int[] ignition_POD = new int[number_of_fires]; 		// the ignition POD of each fire
					for (int e = 0; e < number_of_fires; e++) {
						number_of_PODS[e] = data[e][1];
						n[e] = number_of_PODS[e] - 1;
						ignition_POD[e] = data[e][2] - 1;	// Ignition POD 1 will be indexed as 0 in the model
					}

					
					// Read input2 --------------------------------------------------------------------------------------------
					list = Files.readAllLines(Paths.get(input_2_file.getAbsolutePath()), StandardCharsets.UTF_8);
					list.remove(0);	// Remove the first row (header)
					a = list.toArray(new String[list.size()]);
					total_rows = a.length;
					total_columns = a[0].split("\t").length;				
					String[][] string_data = new String[total_rows][total_columns];
				
					// read all values from all rows and columns
					for (int i = 0; i < total_rows; i++) {
						String[] rowValue = a[i].split("\t");
						for (int j = 0; j < total_columns; j++) {
							string_data[i][j] = rowValue[j];
						}
					}
					
					// get NPC parameters
					double[][] ENVC = null;	// w(e,ie) or ENVC, with e is the FireID, i is the dynamic POD
					ENVC = new double[number_of_fires][];
					for (int e = 0; e < number_of_fires; e++) {
						ENVC[e] = new double[number_of_PODS[e]];
					}
					
					for (int i = 0; i < total_rows; i++) {
						int ee = Integer.parseInt(string_data[i][0]) - 1;
						int ii = Integer.parseInt(string_data[i][1]) - 1;
						ENVC[ee][ii] = Double.parseDouble(string_data[i][2]);
					}

					// Adjacent PODs
					List<Integer>[][] adjacent_PODS = new ArrayList[number_of_fires][];
					for (int e = 0; e < number_of_fires; e++) {	
						adjacent_PODS[e] = new ArrayList[number_of_PODS[e]];
						for (int i = 0; i < number_of_PODS[e]; i++) {
							adjacent_PODS[e][i] = new ArrayList<Integer>();
						}
					}
					for (int i = 0; i < total_rows; i++) {
						int ee = Integer.parseInt(string_data[i][0]) - 1;
						int ii = Integer.parseInt(string_data[i][1]) - 1;
						String[] AdjPODs = string_data[i][3].split(",");
						for (String s : AdjPODs) {
							int id = Integer.parseInt(s) - 1;
							adjacent_PODS[ee][ii].add(id);
						}
					}
					
					// Read input4 --------------------------------------------------------------------------------------------
					list = Files.readAllLines(Paths.get(input_4_file.getAbsolutePath()), StandardCharsets.UTF_8);
					list.remove(0);	// Remove the first row (header)
					a = list.toArray(new String[list.size()]);
					total_rows = a.length;
					total_columns = a[0].split("\t").length;				
					string_data = new String[total_rows][total_columns];
				
					// read all values from all rows and columns
					for (int i = 0; i < total_rows; i++) {
						String[] rowValue = a[i].split("\t");
						for (int j = 0; j < total_columns; j++) {
							string_data[i][j] = rowValue[j];
						}
					}
					
					int number_of_filtered_fuelbreaks = total_rows;				// identified from all the fuel breaks after step 1 line 135 in the manuscript 
					double[] q0 = new double[number_of_filtered_fuelbreaks]; 	// the current capacity of a fuel break
					double[] d1 = new double[number_of_filtered_fuelbreaks]; 	// parameter for D1 variable
					double[] d2 = new double[number_of_filtered_fuelbreaks]; 	// parameter for D2 variable
					double[] d3 = new double[number_of_filtered_fuelbreaks]; 	// parameter for D3 variable
					for (int b = 0; b < number_of_filtered_fuelbreaks; b++) {
						q0[b] = Double.parseDouble(string_data[b][2]);
						d1[b] = Double.parseDouble(string_data[b][3]);
						d2[b] = Double.parseDouble(string_data[b][4]);
						d3[b] = Double.parseDouble(string_data[b][5]);
						// System.out.println(q0[b] + " " + d1[b] + " " + d2[b] + " " + d3[b]);
					}
					
					// Need to read the FuealBreak ID conversion here as well. Do it later
					
					
					// Read input3 --------------------------------------------------------------------------------------------
					list = Files.readAllLines(Paths.get(input_3_file.getAbsolutePath()), StandardCharsets.UTF_8);
					list.remove(0);	// Remove the first row (header)
					a = list.toArray(new String[list.size()]);
					total_rows = a.length;
					total_columns = a[0].split("\t").length;				
					string_data = new String[total_rows][total_columns];
				
					// read all values from all rows and columns
					for (int i = 0; i < total_rows; i++) {
						String[] rowValue = a[i].split("\t");
						for (int j = 0; j < total_columns; j++) {
							string_data[i][j] = rowValue[j];
						}
					}
					
					double[][][] fl = null;	// fl(e,ie,je) with e is the FireID, i and j are the dynamic POD
					fl = new double[number_of_fires][][];
					for (int e = 0; e < number_of_fires; e++) {
						fl[e] = new double[number_of_PODS[e] - 1][];
						for (int i = 0; i < number_of_PODS[e] - 1; i++) {
							fl[e][i] = new double[number_of_PODS[e]];
						}
					}
					
					for (int i = 0; i < total_rows; i++) {
						int ee = Integer.parseInt(string_data[i][0]) - 1;
						String[] pairPODs = string_data[i][4].split(",");
						int ii = Integer.parseInt(pairPODs[0]) - 1;
						int jj = Integer.parseInt(pairPODs[1]) - 1;
						fl[ee][ii][jj] = Double.parseDouble(string_data[i][5]);
					}
					
					// Need to add information to this list: Note add POD ID in the file - 1 so all PODs starts from 0
					List<Integer>[][][] b_list = new ArrayList[number_of_fires][][];
					for (int e = 0; e < number_of_fires; e++) {	
						b_list[e] = new ArrayList[number_of_PODS[e] - 1][];
						for (int i = 0; i < number_of_PODS[e] - 1; i++) {
							b_list[e][i] = new ArrayList[number_of_PODS[e]];
							for (int j = i + 1; j < number_of_PODS[e]; j++) {
								b_list[e][i][j] = new ArrayList<Integer>();
							}
						}
					}
					
					for (int i = 0; i < total_rows; i++) {
						int ee = Integer.parseInt(string_data[i][0]) - 1;
						String[] pairPODs = string_data[i][4].split(",");
						int ii = Integer.parseInt(pairPODs[0]) - 1;
						int jj = Integer.parseInt(pairPODs[1]) - 1;
						String[] filteredFuelBreakIDs = string_data[i][2].split(",");
						for (String s : filteredFuelBreakIDs) {
							int id = Integer.parseInt(s) - 1;
							b_list[ee][ii][jj].add(id);
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

					// declare arrays to keep variables. some variables are optimized by using jagged-arrays (xE, xR, fire)
					int[][] X = null;	// X(e,ie) with e is the FireID, i is the dynamic POD
					X = new int[number_of_fires][];
					for (int e = 0; e < number_of_fires; e++) {
						X[e] = new int[number_of_PODS[e]];
						for (int i = 0; i < number_of_PODS[e]; i++) {
							int fire_ID = e + 1;
							int POD_ID = i + 1;
							String var_name = "X_" + fire_ID + "_" + POD_ID;
							Information_Variable var_info = new Information_Variable(var_name);
							var_info_list.add(var_info);
							objlist.add((double) 1);
							vnamelist.add(var_name);
							vlblist.add((double) 0);
							vublist.add((double) 1);
							vtlist.add(IloNumVarType.Bool);
							X[e][i] = nvars;
							nvars++;
						}
					}
					
					int[] Q = new int[number_of_filtered_fuelbreaks];	// Q(b)	is the upper bound flame length for the fuel break b to sustain
					for (int b = 0; b < number_of_filtered_fuelbreaks; b++) {
						int fuelbreak_ID = b + 1;
						String var_name = "Q_" + fuelbreak_ID;
						Information_Variable var_info = new Information_Variable(var_name);
						var_info_list.add(var_info);
						objlist.add((double) 0);
						vnamelist.add(var_name);
						vlblist.add(q0[b]);				// very important to add this bound here
						vublist.add(Double.MAX_VALUE);
						vtlist.add(IloNumVarType.Float);
						Q[b] = nvars;
						nvars++;
					}
					
//					int[] D = new int[number_of_filtered_fuelbreaks];	// D(b) is total invest in the fuel break b. D = D1 + D2 +D3
//					for (int b = 0; b < number_of_filtered_fuelbreaks; b++) {
//						int fuelbreak_ID = b + 1;
//						String var_name = "D_" + fuelbreak_ID;
//						Information_Variable var_info = new Information_Variable(var_name);
//						var_info_list.add(var_info);
//						objlist.add((double) 0);
//						vnamelist.add(var_name);
//						vlblist.add((double) 0);
//						vublist.add(Double.MAX_VALUE);
//						vtlist.add(IloNumVarType.Float);
//						D[b] = nvars;
//						nvars++;
//					}
					
					int[] D1 = new int[number_of_filtered_fuelbreaks];
					for (int b = 0; b < number_of_filtered_fuelbreaks; b++) {
						int fuelbreak_ID = b + 1;
						String var_name = "D1_" + fuelbreak_ID;
						Information_Variable var_info = new Information_Variable(var_name);
						var_info_list.add(var_info);
						objlist.add((double) 0);
						vnamelist.add(var_name);
						vlblist.add((double) 0);
						vublist.add(Double.MAX_VALUE);
						vtlist.add(IloNumVarType.Float);
						D1[b] = nvars;
						nvars++;
					}
					
					int[] D2 = new int[number_of_filtered_fuelbreaks];
					for (int b = 0; b < number_of_filtered_fuelbreaks; b++) {
						int fuelbreak_ID = b + 1;
						String var_name = "D2_" + fuelbreak_ID;
						Information_Variable var_info = new Information_Variable(var_name);
						var_info_list.add(var_info);
						objlist.add((double) 0);
						vnamelist.add(var_name);
						vlblist.add((double) 0);
						vublist.add(Double.MAX_VALUE);
						vtlist.add(IloNumVarType.Float);
						D2[b] = nvars;
						nvars++;
					}
					
					int[] D3 = new int[number_of_filtered_fuelbreaks];
					for (int b = 0; b < number_of_filtered_fuelbreaks; b++) {
						int fuelbreak_ID = b + 1;
						String var_name = "D3_" + fuelbreak_ID;
						Information_Variable var_info = new Information_Variable(var_name);
						var_info_list.add(var_info);
						objlist.add((double) 0);
						vnamelist.add(var_name);
						vlblist.add((double) 0);
						vublist.add(Double.MAX_VALUE);
						vtlist.add(IloNumVarType.Float);
						D3[b] = nvars;
						nvars++;
					}
					
					int[][][] Y = null;	// Y(e,ie,je) with e is the FireID, i and j are the dynamic POD
					Y = new int[number_of_fires][][];
					for (int e = 0; e < number_of_fires; e++) {
						Y[e] = new int[number_of_PODS[e] - 1][];
						for (int i = 0; i < number_of_PODS[e] - 1; i++) {
							Y[e][i] = new int[number_of_PODS[e]];
							for (int j = i + 1; j < number_of_PODS[e]; j++) {
								if (adjacent_PODS[e][i].contains(j)) {
									int fire_ID = e + 1;
									int dynamic_POD_i = i + 1;
									int dynamic_POD_j = j + 1;
									String var_name = "Y_" + fire_ID + "_" + dynamic_POD_i + "_" + dynamic_POD_j;
									Information_Variable var_info = new Information_Variable(var_name);
									var_info_list.add(var_info);
									objlist.add((double) 0);
									vnamelist.add(var_name);
									vlblist.add((double) 0);
									vublist.add((double) 1);
									vtlist.add(IloNumVarType.Bool);
									Y[e][i][j] = nvars;
									nvars++;
								}
							}
						}
					}
					
					int[][][] B = null;	// B(e,ie,je) with e is the FireID, i and j are the dynamic POD
					B = new int[number_of_fires][][];
					for (int e = 0; e < number_of_fires; e++) {
						B[e] = new int[number_of_PODS[e]][];
						for (int i = 0; i < number_of_PODS[e]; i++) {
							B[e][i] = new int[number_of_PODS[e]];
							for (int j = 0; j < number_of_PODS[e]; j++) {
								if (adjacent_PODS[e][i].contains(j)) {
									int fire_ID = e + 1;
									int dynamic_POD_i = i + 1;
									int dynamic_POD_j = j + 1;
									String var_name = "B_" + fire_ID + "_" + dynamic_POD_i + "_" + dynamic_POD_j;
									Information_Variable var_info = new Information_Variable(var_name);
									var_info_list.add(var_info);
									objlist.add((double) 0);
									vnamelist.add(var_name);
									vlblist.add((double) 0);
									vublist.add((double) 1);
									vtlist.add(IloNumVarType.Bool);
									B[e][i][j] = nvars;
									nvars++;
								}
							}
						}
					}
					
					int[][] F = null;	// F(e,ie) with e is the FireID, i is the dynamic POD
					F = new int[number_of_fires][];
					for (int e = 0; e < number_of_fires; e++) {
						F[e] = new int[number_of_PODS[e]];
						for (int i = 0; i < number_of_PODS[e]; i++) {
							int fire_ID = e + 1;
							int POD_ID = i + 1;
							String var_name = "F_" + fire_ID + "_" + POD_ID;
							Information_Variable var_info = new Information_Variable(var_name);
							var_info_list.add(var_info);
							objlist.add((double) 0);
							vnamelist.add(var_name);
							vlblist.add((double) 0);
							vublist.add((double) number_of_PODS[e]);
							vtlist.add(IloNumVarType.Int);
							F[e][i] = nvars;
							nvars++;
						}
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
					
//					for (int e = 0; e < number_of_fires; e++) {
//						for (int i = 0; i < number_of_PODS[e] - 1; i++) {
//							for (int j = i + 1; j < number_of_PODS[e]; j++) {
//								for (int b : b_list[e][i][j]) {
//									// Add constraint
//									c2_indexlist.add(new ArrayList<Integer>());
//									c2_valuelist.add(new ArrayList<Double>());
//				
//									// Add Y[e][i][j]
//									c2_indexlist.get(c2_num).add(Y[e][i][j]);
//									c2_valuelist.get(c2_num).add((double) 1);
//									
//									// Add -Q[b]/M
//									c2_indexlist.get(c2_num).add(Q[b]);
//									c2_valuelist.get(c2_num).add((double) -1 / M);
//				
//									// add bounds
//									c2_lblist.add((double) -1);						// Lower bound set to -1 instead of INF (infeasibility --> cutoff)
//									c2_ublist.add((double) 1 - fl[e][i][j] / M);	// Upper bound = 1 -fl/M
//									c2_num++;
//								}
//							}
//						}
//					}
					
					for (int e = 0; e < number_of_fires; e++) {
						for (int i = 0; i < number_of_PODS[e] - 1; i++) {
							for (int j = i + 1; j < number_of_PODS[e]; j++) {
								for (int b : b_list[e][i][j]) {
									// Add constraint
									c2_indexlist.add(new ArrayList<Integer>());
									c2_valuelist.add(new ArrayList<Double>());
				
									// Add fl[e][i][j] * Y[e][i][j]
									c2_indexlist.get(c2_num).add(Y[e][i][j]);
									c2_valuelist.get(c2_num).add(fl[e][i][j]);
									
									// Add -Q[b]
									c2_indexlist.get(c2_num).add(Q[b]);
									c2_valuelist.get(c2_num).add((double) -1);
				
									// add bounds
									c2_lblist.add((double) -q0[b]);	// Lower bound = negative flame length
									c2_ublist.add((double) 0);		// Upper bound = 0
									c2_num++;
								}
							}
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
					System.out.println("Total constraints as in PRISM model formulation eq. (2):   " + c2_num + "             " + dateFormat.format(new Date()));
					
					
					// Constraints 3------------------------------------------------------
					List<List<Integer>> c3_indexlist = new ArrayList<List<Integer>>();	
					List<List<Double>> c3_valuelist = new ArrayList<List<Double>>();
					List<Double> c3_lblist = new ArrayList<Double>();	
					List<Double> c3_ublist = new ArrayList<Double>();
					int c3_num = 0;
					
					for (int e = 0; e < number_of_fires; e++) {
						for (int i = 0; i < number_of_PODS[e]; i++) {
							if (i == ignition_POD[e]) {
								// Add constraint
								c3_indexlist.add(new ArrayList<Integer>());
								c3_valuelist.add(new ArrayList<Double>());
			
								// Add X[e][i]		--> POD i is the ignition location
								c3_indexlist.get(c3_num).add(X[e][i]);
								c3_valuelist.get(c3_num).add((double) 1);
			
								// add bounds
								c3_lblist.add((double) 1);	// Lower bound = 1
								c3_ublist.add((double) 1);	// Upper bound = 1
								c3_num++;
							}
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
					System.out.println("Total constraints as in PRISM model formulation eq. (3):   " + c3_num + "             " + dateFormat.format(new Date()));
					
					
					// Constraints 4------------------------------------------------------
					List<List<Integer>> c4_indexlist = new ArrayList<List<Integer>>();	
					List<List<Double>> c4_valuelist = new ArrayList<List<Double>>();
					List<Double> c4_lblist = new ArrayList<Double>();	
					List<Double> c4_ublist = new ArrayList<Double>();
					int c4_num = 0;
					
					// 4a
					for (int e = 0; e < number_of_fires; e++) {
						for (int i = 0; i < number_of_PODS[e] - 1; i++) {
							for (int j : adjacent_PODS[e][i]) {
								// Add constraint
								c4_indexlist.add(new ArrayList<Integer>());
								c4_valuelist.add(new ArrayList<Double>());
								
								// Add Y[e][i][j]
								c4_indexlist.get(c4_num).add(Y[e][i][j]);
								c4_valuelist.get(c4_num).add((double) 1);
								
								// Add -X[e][i]
								c4_indexlist.get(c4_num).add(X[e][i]);
								c4_valuelist.get(c4_num).add((double) -1);
								
								// Add +X[e][j]
								c4_indexlist.get(c4_num).add(X[e][j]);
								c4_valuelist.get(c4_num).add((double) 1);
								
								// add bounds
								c4_lblist.add((double) 0);			// Lower bound = 0
								c4_ublist.add((double) 2);			// Upper bound = 2
								c4_num++;
							}
						}
					}
					
					// 4b
					for (int e = 0; e < number_of_fires; e++) {
						for (int i = 0; i < number_of_PODS[e] - 1; i++) {
							for (int j : adjacent_PODS[e][i]) {
								// Add constraint
								c4_indexlist.add(new ArrayList<Integer>());
								c4_valuelist.add(new ArrayList<Double>());
								
								// Add Y[e][i][j]
								c4_indexlist.get(c4_num).add(Y[e][i][j]);
								c4_valuelist.get(c4_num).add((double) 1);
								
								// Add +X[e][i]
								c4_indexlist.get(c4_num).add(X[e][i]);
								c4_valuelist.get(c4_num).add((double) 1);
								
								// Add -X[e][j]
								c4_indexlist.get(c4_num).add(X[e][j]);
								c4_valuelist.get(c4_num).add((double) -1);
								
								// add bounds
								c4_lblist.add((double) 0);	// Lower bound = 0
								c4_ublist.add((double) 2);	// Upper bound = 2
								c4_num++;
							}
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
					System.out.println("Total constraints as in PRISM model formulation eq. (4):   " + c4_num + "             " + dateFormat.format(new Date()));
					
					
					// Constraints 5------------------------------------------------------		
					List<List<Integer>> c5_indexlist = new ArrayList<List<Integer>>();	
					List<List<Double>> c5_valuelist = new ArrayList<List<Double>>();
					List<Double> c5_lblist = new ArrayList<Double>();	
					List<Double> c5_ublist = new ArrayList<Double>();
					int c5_num = 0;
					
					for (int e = 0; e < number_of_fires; e++) {
						for (int i = 0; i < number_of_PODS[e]; i++) {
							for (int j : adjacent_PODS[e][i]) {
								// Add constraint
								c5_indexlist.add(new ArrayList<Integer>());
								c5_valuelist.add(new ArrayList<Double>());
								
								// Add B[e][i][j]
								c5_indexlist.get(c5_num).add(B[e][i][j]);
								c5_valuelist.get(c5_num).add((double) 1);
								
								// Add -X[e][i]
								c5_indexlist.get(c5_num).add(X[e][i]);
								c5_valuelist.get(c5_num).add((double) -1);
								
								// add bounds
								c5_lblist.add((double) -1);		// Lower bound = -1 will make this equation not fail
								c5_ublist.add((double) 0);		// Upper bound = 0
								c5_num++;
							}
						}
					}
					
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
					System.out.println("Total constraints as in PRISM model formulation eq. (5):   " + c5_num + "             " + dateFormat.format(new Date()));
					
					
					// Constraints 6------------------------------------------------------		
					List<List<Integer>> c6_indexlist = new ArrayList<List<Integer>>();	
					List<List<Double>> c6_valuelist = new ArrayList<List<Double>>();
					List<Double> c6_lblist = new ArrayList<Double>();	
					List<Double> c6_ublist = new ArrayList<Double>();
					int c6_num = 0;
					
					for (int e = 0; e < number_of_fires; e++) {
						for (int j = 0; j < number_of_PODS[e]; j++) {
							if (j != ignition_POD[e]) {	// if this is not the ignition POD
								// Add constraint
								c6_indexlist.add(new ArrayList<Integer>());
								c6_valuelist.add(new ArrayList<Double>());
								
								// Add X[e][j]
								c6_indexlist.get(c6_num).add(X[e][j]);
								c6_valuelist.get(c6_num).add((double) 1);
								
								// Add -Sigma B[e][i][j]
								for (int i : adjacent_PODS[e][j]) {
									c6_indexlist.get(c6_num).add(B[e][i][j]);
									c6_valuelist.get(c6_num).add((double) -1);
								}
								// add bounds
								c6_lblist.add((double) -adjacent_PODS[e][j].size());	// Lower bound = - total number of adjacent PODS of POD j
								c6_ublist.add((double) 0);								// Upper bound = 0
								c6_num++;
							}
						}
					}
					
					double[] c6_lb = Stream.of(c6_lblist.toArray(new Double[c6_lblist.size()])).mapToDouble(Double::doubleValue).toArray();
					double[] c6_ub = Stream.of(c6_ublist.toArray(new Double[c6_ublist.size()])).mapToDouble(Double::doubleValue).toArray();		
					int[][] c6_index = new int[c6_num][];
					double[][] c6_value = new double[c6_num][];
				
					for (int i = 0; i < c6_num; i++) {
						c6_index[i] = new int[c6_indexlist.get(i).size()];
						c6_value[i] = new double[c6_indexlist.get(i).size()];
						for (int j = 0; j < c6_indexlist.get(i).size(); j++) {
							c6_index[i][j] = c6_indexlist.get(i).get(j);
							c6_value[i][j] = c6_valuelist.get(i).get(j);			
						}
					}	
					
					// Clear lists to save memory
					c6_indexlist = null;	
					c6_valuelist = null;
					c6_lblist = null;	
					c6_ublist = null;
					System.out.println("Total constraints as in PRISM model formulation eq. (6):   " + c6_num + "             " + dateFormat.format(new Date()));
					
					
					// Constraints 7------------------------------------------------------		
					List<List<Integer>> c7_indexlist = new ArrayList<List<Integer>>();	
					List<List<Double>> c7_valuelist = new ArrayList<List<Double>>();
					List<Double> c7_lblist = new ArrayList<Double>();	
					List<Double> c7_ublist = new ArrayList<Double>();
					int c7_num = 0;
					
					for (int e = 0; e < number_of_fires; e++) {
						for (int i = 0; i < number_of_PODS[e]; i++) {
							for (int j : adjacent_PODS[e][i]) {
								// 7a
								if (i < j) {
									// Add constraint
									c7_indexlist.add(new ArrayList<Integer>());
									c7_valuelist.add(new ArrayList<Double>());
									
									// Add B[e][i][j]
									c7_indexlist.get(c7_num).add(B[e][i][j]);
									c7_valuelist.get(c7_num).add((double) 1);
									
									// Add Y[e][i][j]
									c7_indexlist.get(c7_num).add(Y[e][i][j]);
									c7_valuelist.get(c7_num).add((double) 1);
									
									// add bounds
									c7_lblist.add((double) 0);		// Lower bound = 0
									c7_ublist.add((double) 1);		// Upper bound = 1
									c7_num++;
								}
								
								// 7b
								if (i > j) {
									// Add constraint
									c7_indexlist.add(new ArrayList<Integer>());
									c7_valuelist.add(new ArrayList<Double>());
									
									// Add B[e][i][j]
									c7_indexlist.get(c7_num).add(B[e][i][j]);
									c7_valuelist.get(c7_num).add((double) 1);
									
									// Add Y[e][j][i]
									c7_indexlist.get(c7_num).add(Y[e][j][i]);
									c7_valuelist.get(c7_num).add((double) 1);
									
									// add bounds
									c7_lblist.add((double) 0);		// Lower bound = 0
									c7_ublist.add((double) 1);		// Upper bound = 1
									c7_num++;
								}
							}
						}
					}
					
					double[] c7_lb = Stream.of(c7_lblist.toArray(new Double[c7_lblist.size()])).mapToDouble(Double::doubleValue).toArray();
					double[] c7_ub = Stream.of(c7_ublist.toArray(new Double[c7_ublist.size()])).mapToDouble(Double::doubleValue).toArray();		
					int[][] c7_index = new int[c7_num][];
					double[][] c7_value = new double[c7_num][];
				
					for (int i = 0; i < c7_num; i++) {
						c7_index[i] = new int[c7_indexlist.get(i).size()];
						c7_value[i] = new double[c7_indexlist.get(i).size()];
						for (int j = 0; j < c7_indexlist.get(i).size(); j++) {
							c7_index[i][j] = c7_indexlist.get(i).get(j);
							c7_value[i][j] = c7_valuelist.get(i).get(j);			
						}
					}	
					
					// Clear lists to save memory
					c7_indexlist = null;	
					c7_valuelist = null;
					c7_lblist = null;	
					c7_ublist = null;
					System.out.println("Total constraints as in PRISM model formulation eq. (7):   " + c7_num + "             " + dateFormat.format(new Date()));
					
					
					// Constraints 8------------------------------------------------------		
					List<List<Integer>> c8_indexlist = new ArrayList<List<Integer>>();	
					List<List<Double>> c8_valuelist = new ArrayList<List<Double>>();
					List<Double> c8_lblist = new ArrayList<Double>();	
					List<Double> c8_ublist = new ArrayList<Double>();
					int c8_num = 0;
					
					for (int e = 0; e < number_of_fires; e++) {
						for (int i = 0; i < number_of_PODS[e]; i++) {
							if (i == ignition_POD[e]) {
								// Add constraint
								c8_indexlist.add(new ArrayList<Integer>());
								c8_valuelist.add(new ArrayList<Double>());
			
								// Add X[e][i]		--> POD i is the ignition location
								c8_indexlist.get(c8_num).add(F[e][i]);
								c8_valuelist.get(c8_num).add((double) 1);
			
								// add bounds
								c8_lblist.add((double) 1);	// Lower bound = 1
								c8_ublist.add((double) 1);	// Upper bound = 1
								c8_num++;
							}
						}
					}
					
					double[] c8_lb = Stream.of(c8_lblist.toArray(new Double[c8_lblist.size()])).mapToDouble(Double::doubleValue).toArray();
					double[] c8_ub = Stream.of(c8_ublist.toArray(new Double[c8_ublist.size()])).mapToDouble(Double::doubleValue).toArray();		
					int[][] c8_index = new int[c8_num][];
					double[][] c8_value = new double[c8_num][];
				
					for (int i = 0; i < c8_num; i++) {
						c8_index[i] = new int[c8_indexlist.get(i).size()];
						c8_value[i] = new double[c8_indexlist.get(i).size()];
						for (int j = 0; j < c8_indexlist.get(i).size(); j++) {
							c8_index[i][j] = c8_indexlist.get(i).get(j);
							c8_value[i][j] = c8_valuelist.get(i).get(j);			
						}
					}	
					
					// Clear lists to save memory
					c8_indexlist = null;	
					c8_valuelist = null;
					c8_lblist = null;	
					c8_ublist = null;
					System.out.println("Total constraints as in PRISM model formulation eq. (8):   " + c8_num + "             " + dateFormat.format(new Date()));
					
					
					// Constraints 9------------------------------------------------------		
					List<List<Integer>> c9_indexlist = new ArrayList<List<Integer>>();	
					List<List<Double>> c9_valuelist = new ArrayList<List<Double>>();
					List<Double> c9_lblist = new ArrayList<Double>();	
					List<Double> c9_ublist = new ArrayList<Double>();
					int c9_num = 0;
					
					for (int e = 0; e < number_of_fires; e++) {
						for (int j = 0; j < number_of_PODS[e]; j++) {
							for (int i : adjacent_PODS[e][j]) {
								if (j != ignition_POD[e]) {
									// 9a
									// Add constraint
									c9_indexlist.add(new ArrayList<Integer>());
									c9_valuelist.add(new ArrayList<Double>());
									
									// Add F[e][j]
									c9_indexlist.get(c9_num).add(F[e][j]);
									c9_valuelist.get(c9_num).add((double) 1);
									
									// Add -F[e][i]
									c9_indexlist.get(c9_num).add(F[e][i]);
									c9_valuelist.get(c9_num).add((double) -1);
									
									// Add n[e]*B[e][i][j]
									c9_indexlist.get(c9_num).add(B[e][i][j]);
									c9_valuelist.get(c9_num).add((double) n[e]);
									
									// add bounds
									// c9_lblist.add((double) Integer.MIN_VALUE);	// Lower bound
									c9_lblist.add((double) -number_of_PODS[e]);		// Lower bound is modified to optimize better
									c9_ublist.add((double) n[e] + 1);				// Upper bound = n[e] + 1
									c9_num++;
									
									// 9b
									// Add constraint
									c9_indexlist.add(new ArrayList<Integer>());
									c9_valuelist.add(new ArrayList<Double>());
									
									// Add F[e][j]
									c9_indexlist.get(c9_num).add(F[e][j]);
									c9_valuelist.get(c9_num).add((double) 1);
									
									// Add -F[e][i]
									c9_indexlist.get(c9_num).add(F[e][i]);
									c9_valuelist.get(c9_num).add((double) -1);
									
									// Add -n[e]*B[e][i][j]
									c9_indexlist.get(c9_num).add(B[e][i][j]);
									c9_valuelist.get(c9_num).add((double) -n[e]);
									
									// add bounds
									c9_lblist.add((double) -n[e] + 1);				// Lower bound = -n[e] + 1
									// c9_ublist.add((double) Integer.MAX_VALUE);	// Upper bound = INF
									c9_ublist.add((double) n[e] + 1);				// Upper bound is modified to optimize better
									c9_num++;
								}
							}
						}
					}
					
					// 9c
					for (int e = 0; e < number_of_fires; e++) {
						for (int j = 0; j < number_of_PODS[e]; j++) {
							if (j != ignition_POD[e]) {
								// Add constraint
								c9_indexlist.add(new ArrayList<Integer>());
								c9_valuelist.add(new ArrayList<Double>());
								
								// Add F[e][j]
								c9_indexlist.get(c9_num).add(F[e][j]);
								c9_valuelist.get(c9_num).add((double) 1);
								
								// Add -(n[e] + 1)*B[e][i][j]
								for (int i : adjacent_PODS[e][j]) {
									c9_indexlist.get(c9_num).add(B[e][i][j]);
									c9_valuelist.get(c9_num).add((double) -n[e] - 1);
								}
								
								// add bounds
								// c9_lblist.add((double) Integer.MIN_VALUE);								// Lower bound
								c9_lblist.add((double) -number_of_PODS[e] * adjacent_PODS[e][j].size());	// Lower bound is modified to optimize better
								c9_ublist.add((double) 0);													// Upper bound = 0
								c9_num++;
							}
						}
					}
					
					double[] c9_lb = Stream.of(c9_lblist.toArray(new Double[c9_lblist.size()])).mapToDouble(Double::doubleValue).toArray();
					double[] c9_ub = Stream.of(c9_ublist.toArray(new Double[c9_ublist.size()])).mapToDouble(Double::doubleValue).toArray();		
					int[][] c9_index = new int[c9_num][];
					double[][] c9_value = new double[c9_num][];
				
					for (int i = 0; i < c9_num; i++) {
						c9_index[i] = new int[c9_indexlist.get(i).size()];
						c9_value[i] = new double[c9_indexlist.get(i).size()];
						for (int j = 0; j < c9_indexlist.get(i).size(); j++) {
							c9_index[i][j] = c9_indexlist.get(i).get(j);
							c9_value[i][j] = c9_valuelist.get(i).get(j);			
						}
					}	
					
					// Clear lists to save memory
					c9_indexlist = null;	
					c9_valuelist = null;
					c9_lblist = null;	
					c9_ublist = null;
					System.out.println("Total constraints as in PRISM model formulation eq. (9):   " + c9_num + "             " + dateFormat.format(new Date()));
					
					
					// Constraints 10------------------------------------------------------		
					List<List<Integer>> c10_indexlist = new ArrayList<List<Integer>>();	
					List<List<Double>> c10_valuelist = new ArrayList<List<Double>>();
					List<Double> c10_lblist = new ArrayList<Double>();	
					List<Double> c10_ublist = new ArrayList<Double>();
					int c10_num = 0;
					
					for (int b = 0; b < number_of_filtered_fuelbreaks; b++) {
						// Add constraint
						c10_indexlist.add(new ArrayList<Integer>());
						c10_valuelist.add(new ArrayList<Double>());
						
						// Add Q[b]
						c10_indexlist.get(c10_num).add(Q[b]);
						c10_valuelist.get(c10_num).add((double) 1);
						
						// Add -D1[b]
						c10_indexlist.get(c10_num).add(D1[b]);
						c10_valuelist.get(c10_num).add((double) -d1[b]);
						
						// Add -D2[b]
						c10_indexlist.get(c10_num).add(D2[b]);
						c10_valuelist.get(c10_num).add((double) -d2[b]);
						
						// Add -D3[b]
						c10_indexlist.get(c10_num).add(D3[b]);
						c10_valuelist.get(c10_num).add((double) -d3[b]);
						
						// add bounds
						// c10_lblist.add(Double.MIN_VALUE);		// Lower bound (old but I think we should use equal constraint as below)
						c10_lblist.add(q0[b]);		// Lower bound = q[b]
						c10_ublist.add(q0[b]);		// Upper bound = q[b]
						c10_num++;
					}
					
					double[] c10_lb = Stream.of(c10_lblist.toArray(new Double[c10_lblist.size()])).mapToDouble(Double::doubleValue).toArray();
					double[] c10_ub = Stream.of(c10_ublist.toArray(new Double[c10_ublist.size()])).mapToDouble(Double::doubleValue).toArray();		
					int[][] c10_index = new int[c10_num][];
					double[][] c10_value = new double[c10_num][];
				
					for (int i = 0; i < c10_num; i++) {
						c10_index[i] = new int[c10_indexlist.get(i).size()];
						c10_value[i] = new double[c10_indexlist.get(i).size()];
						for (int j = 0; j < c10_indexlist.get(i).size(); j++) {
							c10_index[i][j] = c10_indexlist.get(i).get(j);
							c10_value[i][j] = c10_valuelist.get(i).get(j);			
						}
					}	
					
					// Clear lists to save memory
					c10_indexlist = null;	
					c10_valuelist = null;
					c10_lblist = null;	
					c10_ublist = null;
					System.out.println("Total constraints as in PRISM model formulation eq. (10):   " + c10_num + "             " + dateFormat.format(new Date()));
					
					
					// Constraints 11------------------------------------------------------		
					List<List<Integer>> c11_indexlist = new ArrayList<List<Integer>>();	
					List<List<Double>> c11_valuelist = new ArrayList<List<Double>>();
					List<Double> c11_lblist = new ArrayList<Double>();	
					List<Double> c11_ublist = new ArrayList<Double>();
					int c11_num = 0;
					
					// Add constraint
					c11_indexlist.add(new ArrayList<Integer>());
					c11_valuelist.add(new ArrayList<Double>());
					
					// Add Sigma D
					for (int b = 0; b < number_of_filtered_fuelbreaks; b++) {
						// Add D1[b]
						c11_indexlist.get(c11_num).add(D1[b]);
						c11_valuelist.get(c11_num).add((double) 1);
						
						// Add D2[b]
						c11_indexlist.get(c11_num).add(D2[b]);
						c11_valuelist.get(c11_num).add((double) 1);
						
						// Add D3[b]
						c11_indexlist.get(c11_num).add(D3[b]);
						c11_valuelist.get(c11_num).add((double) 1);
					}
					
					// add bounds
					c11_lblist.add((double) 0);			// Lower bound = 0
					c11_ublist.add(budget);				// Upper bound = budget
					c11_num++;
					
					double[] c11_lb = Stream.of(c11_lblist.toArray(new Double[c11_lblist.size()])).mapToDouble(Double::doubleValue).toArray();
					double[] c11_ub = Stream.of(c11_ublist.toArray(new Double[c11_ublist.size()])).mapToDouble(Double::doubleValue).toArray();		
					int[][] c11_index = new int[c11_num][];
					double[][] c11_value = new double[c11_num][];
				
					for (int i = 0; i < c11_num; i++) {
						c11_index[i] = new int[c11_indexlist.get(i).size()];
						c11_value[i] = new double[c11_indexlist.get(i).size()];
						for (int j = 0; j < c11_indexlist.get(i).size(); j++) {
							c11_index[i][j] = c11_indexlist.get(i).get(j);
							c11_value[i][j] = c11_valuelist.get(i).get(j);			
						}
					}	
					
					// Clear lists to save memory
					c11_indexlist = null;	
					c11_valuelist = null;
					c11_lblist = null;	
					c11_ublist = null;
					System.out.println("Total constraints as in PRISM model formulation eq. (11):   " + c11_num + "             " + dateFormat.format(new Date()));
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
						lp.addRows(c5_lb, c5_ub, c5_index, c5_value); 		// Constraints 5
						lp.addRows(c6_lb, c6_ub, c6_index, c6_value);		// Constraints 6
						lp.addRows(c7_lb, c7_ub, c7_index, c7_value); 		// Constraints 7
						lp.addRows(c8_lb, c8_ub, c8_index, c8_value);		// Constraints 8
						lp.addRows(c9_lb, c9_ub, c9_index, c9_value); 		// Constraints 9
						lp.addRows(c10_lb, c10_ub, c10_index, c10_value); 	// Constraints 10
						lp.addRows(c11_lb, c11_ub, c11_index, c11_value); 	// Constraints 11
						
						// Clear arrays to save memory
						c2_lb = null;  c2_ub = null;  c2_index = null;  c2_value = null;
						c3_lb = null;  c3_ub = null;  c3_index = null;  c3_value = null;
						c5_lb = null;  c5_ub = null;  c5_index = null;  c5_value = null;
						c6_lb = null;  c6_ub = null;  c6_index = null;  c6_value = null;
						c7_lb = null;  c7_ub = null;  c7_index = null;  c7_value = null;
						c8_lb = null;  c8_ub = null;  c8_index = null;  c8_value = null;
						c9_lb = null;  c9_ub = null;  c9_index = null;  c9_value = null;
						c10_lb = null;  c10_ub = null;  c10_index = null;  c10_value = null;
						c11_lb = null;  c11_ub = null;  c11_index = null;  c11_value = null;
						
						// Set constraints set name: Notice THIS WILL EXTREMELY SLOW THE SOLVING PROCESS (recommend for debugging only)
						int indexOfC2 = c2_num;
						int indexOfC3 = indexOfC2 + c3_num;
						int indexOfC4 = indexOfC3 + c4_num;
						int indexOfC5 = indexOfC4 + c5_num;
						int indexOfC6 = indexOfC5 + c6_num;
						int indexOfC7 = indexOfC6 + c7_num;
						int indexOfC8 = indexOfC7 + c8_num;
						int indexOfC9 = indexOfC8 + c9_num;
						int indexOfC10 = indexOfC9 + c10_num;
						int indexOfC11 = indexOfC10 + c11_num;	// Note: lp.getRanges().length = indexOfC11
						for (int i = 0; i < lp.getRanges().length; i++) {	
							if (0 <= i && i < indexOfC2) lp.getRanges() [i].setName("S.2");
							if (indexOfC2<=i && i<indexOfC3) lp.getRanges() [i].setName("S.3");
							if (indexOfC3<=i && i<indexOfC4) lp.getRanges() [i].setName("S.4");
							if (indexOfC4<=i && i<indexOfC5) lp.getRanges() [i].setName("S.5");
							if (indexOfC5<=i && i<indexOfC6) lp.getRanges() [i].setName("S.6");
							if (indexOfC6<=i && i<indexOfC7) lp.getRanges() [i].setName("S.7");
							if (indexOfC7<=i && i<indexOfC8) lp.getRanges() [i].setName("S.8");
							if (indexOfC8<=i && i<indexOfC9) lp.getRanges() [i].setName("S.9");
							if (indexOfC9<=i && i<indexOfC10) lp.getRanges() [i].setName("S.10");
							if (indexOfC10<=i && i<indexOfC11) lp.getRanges() [i].setName("S.11");
						}
						
						cplex.addMinimize(cplex.scalProd(var, objvals));
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
