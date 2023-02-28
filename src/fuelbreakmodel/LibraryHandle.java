/*******************************************************************************
 * Copyright (C) 2016-2018 PRISM Development Team
 * 
 * PRISM is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * PRISM is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with PRISM.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package fuelbreakmodel;

import java.lang.reflect.Field;
import java.util.Arrays;

public class LibraryHandle {
	public static void addLibraryPath(String pathToAdd) throws Exception {
		//To help load the native libraries (usually the folder contains the .dll files) of the added jars
		
		Field usrPathsField = FBSMmain.get_main().getClass().getDeclaredField("usr_paths");
		usrPathsField.setAccessible(true);

		String[] paths = (String[]) usrPathsField.get(null);
		String[] arrayOfString1;
		
		int j = (arrayOfString1 = paths).length;
		
		for (int i = 0; i < j; i++) {
			String path = arrayOfString1[i];
			if (path.equals(pathToAdd)) {
				return;
			}
		}
		
		String[] newPaths = (String[]) Arrays.copyOf(paths, paths.length + 1);
		newPaths[(newPaths.length - 1)] = pathToAdd;
		usrPathsField.set(null, newPaths);
	}
	
	public static void setLibraryPath(String path) throws Exception {
	    System.setProperty("java.library.path", path);
	 
	    //set sys_paths to null
	    final Field sysPathsField = FBSMmain.get_main().getClass().getDeclaredField("sys_paths");
	    sysPathsField.setAccessible(true);
	    sysPathsField.set(null, null);
	}
}
