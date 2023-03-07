package fuelbreakmodel;

public class Information_Variable {
	private String var_name;
	private int break_id, fire_id;

	public Information_Variable(String var_name) {
		this.var_name = var_name;
		String[] term = var_name.substring(2).split("_"); // remove first 2 letters and then split
		try {
			String first_letter_of_var_name = var_name.substring(0, 1);
			switch (first_letter_of_var_name) {
			case "x":
				break_id = Integer.parseInt(term[0]);
				break;
			case "y":
				fire_id = Integer.parseInt(term[1]);
				break;
			default:
				break;
			}
		} catch (Exception e) {
		}
	}

	public String get_var_name() {
		return var_name;
	}

	public int get_break_id() {
		return break_id;
	}

	public int get_fire_id() {	// this is the new fire id: 0, 1, 2,...
		return fire_id;
	}
}
