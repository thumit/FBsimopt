package fuelbreakmodel;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.stream.Stream;

import ilog.concert.IloLPMatrix;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.Status;

public class Random_Model {
	private DecimalFormat twoDForm = new DecimalFormat("#.##");	 // Only get 2 decimal
	private DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd - HH:mm:ss");
	private long time_start, time_end;
	private double time_reading, time_solving, time_writing;
	private double fire_size_percentile;
	private double percent_invest;
	private double escape_flame_length;
	private double size_of_modeled_fires;
	private double wuisize_of_modeled_fires;
	private int number_of_modeled_fires;
	private double B;
	private Status cplex_status;
	private int cplex_algorithm;
	private long cplex_iteration;
	private double objective_value;
	private int number_of_contained_fires;
	private int number_of_invested_breaks;
	private double length_of_invested_breaks;
	
	private int number_of_runs = 50;
	
	private double[] all_objective_value = new double[number_of_runs];
	private int[] all_number_of_contained_fires = new int[number_of_runs];
	private int[] all_number_of_invested_breaks = new int[number_of_runs];
	private double[] all_length_of_invested_breaks = new double[number_of_runs];
	private double[] all_time_solving = new double[number_of_runs];
	
	private double average_objective_value;
	private int average_number_of_contained_fires;
	private int average_number_of_invested_breaks;
	private double average_length_of_invested_breaks;
	private double average_time_solving;
	
	public Random_Model(String value_at_risk_option, double fire_size_percentile, double percent_invest, double escape_flame_length,
			String input_folder, int number_of_breaks, double[] break_length, double total_network_length,
			int number_of_fires, int[] fire_id, double[] smoothed_fire_size, double[] saved_fire_area, double[] wui_area, double[] saved_wui_area,
			int[] number_of_collaborated_breaks, List<Integer>[] collaborated_breaks_list, double[] max_flamelength_at_breaks) {
		// MODEL SETUP --------------------------------------------------------------
		// MODEL SETUP --------------------------------------------------------------
		// MODEL SETUP --------------------------------------------------------------
		// NOTE: Only equation 5 is changed so that we can randomly select breaks until meet budget (i.e. 10%, 20%, 305,.. of the total network length)
		this.fire_size_percentile = fire_size_percentile;
		this.percent_invest = percent_invest;
		this.escape_flame_length = escape_flame_length;
		
		double[] saved_value_at_risk = null;
		if (value_at_risk_option.equals("FIRE")) {
			saved_value_at_risk = saved_fire_area;
		} else { // WUI case
			saved_value_at_risk = saved_wui_area;
		}
		double[] sorted_smoothed_fire_size = new double[smoothed_fire_size.length];
		System.arraycopy(smoothed_fire_size, 0, sorted_smoothed_fire_size, 0, smoothed_fire_size.length);
		Arrays.sort(sorted_smoothed_fire_size);
		int percentile_index = (int) Math.floor(sorted_smoothed_fire_size.length * fire_size_percentile) - 1;	// Calculate the percentile index
		double percentile_value = sorted_smoothed_fire_size[percentile_index];									// Calculate the percentile value
		number_of_modeled_fires = 0;	// Note: exclude fires based on fire size percentile only
		size_of_modeled_fires = 0;
		wuisize_of_modeled_fires = 0;
		for (int j = 0; j < number_of_fires; j++) {
			if (smoothed_fire_size[j] <= percentile_value) {
				number_of_modeled_fires = number_of_modeled_fires + 1;
				size_of_modeled_fires = size_of_modeled_fires + smoothed_fire_size[j];
				wuisize_of_modeled_fires = wuisize_of_modeled_fires + wui_area[j];
			}
		}
		
		B = percent_invest * total_network_length;
		
		boolean[] containable_fires = new boolean[number_of_fires];	// Get the set of containable fires (existing breaks can contain fire, max flame length at breaks not too high, not large fires)
		for (int j = 0; j < number_of_fires; j++) {
			if (number_of_collaborated_breaks[j] > 0 && max_flamelength_at_breaks[j] <= escape_flame_length && smoothed_fire_size[j] <= percentile_value) {
				containable_fires[j] = true;
			} else {
				containable_fires[j] = false;
			}
		}
		
		
		
		
		for (int r = 0; r < number_of_runs; r++) {	// For each case, run many random models, then summarize results
			Random_Selection random_selection = new Random_Selection(number_of_breaks, break_length, B);
			List<Integer> random_breaks = random_selection.get_random_breaks();
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
				objlist.add((double) saved_value_at_risk[j]);
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
				if (containable_fires[j]) {
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
				if (containable_fires[j]) {
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
				if (!containable_fires[j]) {
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
			
			for (int i = 0; i < number_of_breaks; i++) {
				if (random_breaks.contains(i)) {
					// Add constraint
					c5_indexlist.add(new ArrayList<Integer>());
					c5_valuelist.add(new ArrayList<Double>());
					
					// Add x[i]
					c5_indexlist.get(c5_num).add(x[i]);
					c5_valuelist.get(c5_num).add((double) 1);
					
					// add bounds
					c5_lblist.add((double) 1);			// Lower bound = 1
					c5_ublist.add((double) 1);			// Upper bound = 1
					c5_num++;
				} else {
					// Add constraint
					c5_indexlist.add(new ArrayList<Integer>());
					c5_valuelist.add(new ArrayList<Double>());
					
					// Add x[i]
					c5_indexlist.get(c5_num).add(x[i]);
					c5_valuelist.get(c5_num).add((double) 1);
					
					// add bounds
					c5_lblist.add((double) 0);			// Lower bound = 0
					c5_ublist.add((double) 0);			// Upper bound = 0
					c5_num++;
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
			System.out.println("Total constraints as in the model formulation eq. (5):   " + c5_num + "             " + dateFormat.format(new Date()));
			time_end = System.currentTimeMillis();		// measure time after reading
			time_reading = (double) (time_end - time_start) / 1000;
			
			
			
			
			
			// SOLVE --------------------------------------------------------------
			// SOLVE --------------------------------------------------------------
			// SOLVE --------------------------------------------------------------
			try {
				// Add the CPLEX native library path dynamically at run time
//					LibraryHandle.setLibraryPath(FilesHandle.get_temporaryFolder().getAbsolutePath().toString());
//					LibraryHandle.addLibraryPath(FilesHandle.get_temporaryFolder().getAbsolutePath().toString());
//					System.out.println("Successfully loaded CPLEX .dll files from " + FilesHandle.get_temporaryFolder().getAbsolutePath().toString());
				
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
//					cplex.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Auto); // Auto choose optimization method
				cplex.setParam(IloCplex.Param.MIP.Strategy.Search, IloCplex.MIPSearch.Traditional);	// MIP method
//					cplex.setParam(IloCplex.DoubleParam.EpGap, 0.00); // Gap is 0%
//					int solvingTimeLimit = 30 * 60; //Get time Limit in minute * 60 = seconds
//					cplex.setParam(IloCplex.DoubleParam.TimeLimit, solvingTimeLimit); // Set Time limit
//					cplex.setParam(IloCplex.Param.MIP.Tolerances.Integrality, 0); 	// Set integrality tolerance: https://www.ibm.com/docs/en/icos/20.1.0?topic=parameters-integrality-tolerance;    https://www.ibm.com/support/pages/why-does-binary-or-integer-variable-take-noninteger-value-solution
//					cplex.setParam(IloCplex.BooleanParam.PreInd, false);			// page 40: sets the Boolean parameter PreInd to false, instructing CPLEX not to apply presolve before solving the problem.
//					cplex.setParam(IloCplex.Param.Preprocessing.Presolve, false);	// turn off presolve to prevent it from completely solving the model before entering the actual LP optimizer (same as above ???)
				
				time_start = System.currentTimeMillis();		// measure time before solving
				if (cplex.solve()) {
					double[] value = cplex.getValues(lp);
					// double[] reduceCost = cplex.getReducedCosts(lp);
					// double[] dual = cplex.getDuals(lp);
					double[] slack = cplex.getSlacks(lp);
					objective_value = cplex.getObjValue();
					cplex_status = cplex.getStatus();
					cplex_algorithm = cplex.getAlgorithm();
					cplex_iteration = cplex.getNiterations64();
					time_end = System.currentTimeMillis();		// measure time after solving
					time_solving = (double) (time_end - time_start) / 1000;
					
					number_of_invested_breaks = 0;
					length_of_invested_breaks = 0;
					for (int i = 0; i < value.length; i++) {
						if (vname[i].startsWith("x") && value[i] == 1) {
							int br_id = var_info_array[i].get_break_id();
							length_of_invested_breaks = length_of_invested_breaks + value[i] * break_length[br_id];
							number_of_invested_breaks = number_of_invested_breaks + 1;
						}
					}
					
					number_of_contained_fires = 0;
					for (int i = 0; i < value.length; i++) {
						if (vname[i].startsWith("y") && value[i] == 1) {
							number_of_contained_fires = number_of_contained_fires + 1;
						}
					}
					
					all_objective_value[r] = objective_value;
					all_number_of_contained_fires[r] = number_of_contained_fires;
					all_number_of_invested_breaks[r] = number_of_invested_breaks;
					all_length_of_invested_breaks[r] = length_of_invested_breaks;
					all_time_solving[r] = time_solving;
				}
				cplex.end();
			} catch (Exception e) {
				System.err.println("Panel Solve Runs - cplexLib.addLibraryPath error - " + e.getClass().getName() + ": " + e.getMessage());
			}
		}
		
		// summarize results
		double sum_objective_value = 0;
		int sum_number_of_contained_fires = 0;
		int sum_number_of_invested_breaks = 0;
		double sum_length_of_invested_breaks = 0;
		double sum_time_solving = 0;
		for (int r = 0; r < number_of_runs; r++) {
			sum_objective_value = sum_objective_value + all_objective_value[r];
			sum_number_of_contained_fires = sum_number_of_contained_fires + all_number_of_contained_fires[r];
			sum_number_of_invested_breaks = sum_number_of_invested_breaks + all_number_of_invested_breaks[r];
			sum_length_of_invested_breaks = sum_length_of_invested_breaks + all_length_of_invested_breaks[r];
			sum_time_solving = sum_time_solving + all_time_solving[r];
		}
		average_objective_value = sum_objective_value / number_of_runs;
		average_number_of_contained_fires = sum_number_of_contained_fires / number_of_runs;
		average_number_of_invested_breaks = sum_number_of_invested_breaks / number_of_runs;
		average_length_of_invested_breaks = sum_length_of_invested_breaks / number_of_runs;
		average_time_solving = sum_time_solving / number_of_runs;
	}
	
	public double get_fire_size_percentile() {
		return fire_size_percentile;
	}
	
	public double get_percent_invest() {
		return percent_invest;
	}
	
	public double get_escape_flame_length() {
		return escape_flame_length;
	}
	
	public double get_size_of_modeled_fires() {
		return size_of_modeled_fires;
	}
	
	public double get_wuisize_of_modeled_fires() {
		return wuisize_of_modeled_fires;
	}
	
	public int get_number_of_modeled_fires() {
		return number_of_modeled_fires;
	}
	
	public double get_B() {
		return B;
	}
	
	public Status cplex_status() {
		return cplex_status;
	}
	
	public int get_cplex_algorithm() {
		return cplex_algorithm;
	}
	
	public long get_cplex_iteration() {
		return cplex_iteration;
	}
	
	public double get_average_time_solving() {
		return average_time_solving;
	}
	
	public double get_average_objective_value() {
		return average_objective_value;
	}
	
	public int get_average_number_of_contained_fires() {
		return average_number_of_contained_fires;
	}
	
	public int get_average_number_of_invested_breaks() {
		return average_number_of_invested_breaks;
	}
	
	public double get_average_length_of_invested_breaks() {
		return average_length_of_invested_breaks;
	}
	
	
	public class Random_Selection {
		private List<Integer> random_breaks = new ArrayList<Integer>();
	    public Random_Selection(int number_of_breaks, double[] break_length, double B) {
	    	// put the original index
	    	int[] random_break_ids = new int[number_of_breaks];
	    	for (int i = 0; i < number_of_breaks; i++) {
	    		random_break_ids[i] = i;
	    	}
	    	// Shuffle the index
			Random random = new Random();
			for (int i = 0; i < number_of_breaks; i++) {
				int randomIndex = random.nextInt(number_of_breaks); // generate a random index
				int temp = random_break_ids[i]; 					// swap the current element with the randomly selected element
				random_break_ids[i] = random_break_ids[randomIndex];
				random_break_ids[randomIndex] = temp;
			}
	    	
	    	// Add original break id until meet budget (or not exceeding the budget)
			int current_sum = 0;
	        for (int i = 0; i < number_of_breaks; i++) {
	        	int selected_break_id = random_break_ids[i];
	        	double value = break_length[selected_break_id]; 	// get the length of the selected break
	            if (current_sum + value <= B && value > 0) { 		// check if the selected break can be added without exceeding the target B (value > 0 means we will exclude the 77 breaks that do not contribute to any fire containment)
	            	random_breaks.add(selected_break_id); 			// add the selected break to the list
	                current_sum += value; 							// update the current sum
	            }
	    	}

	        // print the total value of the randomly selected breaks
	        // System.out.println("Sum vs Budget vs number of seleced breaks: " + current_sum + " " + B + " " + selected_breaks.size());
	    }
	    
	    public List<Integer> get_random_breaks() {
	    	return random_breaks;
	    }
	} 
}
