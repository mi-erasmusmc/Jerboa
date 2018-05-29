/***********************************************************************************
 *                                                                                 *
 * Copyright (C) 2017  Erasmus MC, Rotterdam, The Netherlands                      *
 *                                                                                 *
 * This file is part of Jerboa.                                                    *
 *                                                                                 *
 * This program is free software; you can redistribute it and/or                   *
 * modify it under the terms of the GNU General Public License                     *
 * as published by the Free Software Foundation; either version 2                  *
 * of the License, or (at your option) any later version.                          *
 *                                                                                 *
 * This program is distributed in the hope that it will be useful,                 *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                  *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   *
 * GNU General Public License for more details.                                    *
 *                                                                                 *
 * You should have received a copy of the GNU General Public License               *
 * along with this program; if not, write to the Free Software                     *
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. *
 *                                                                                 *
 ***********************************************************************************/

package org.erasmusmc.jerboa.utilities;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This interface defines a custom annotation that is to be used
 * either when generating the Java doc for the source code or
 * when the description/comments of a method or parameter are to be
 * retrieved and used somewhere else in the code.
 *
 * @author MG
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Documented
public @interface MemberDescription {

	public static final String[] DEFAULT_VALUE_PARAMETERS = {"DEFAULT"};

    /**
     * This would provide the description of a parameter or a method
     * when generating the Java doc or whenever the description is needed.
     * @return - the description under a string representation
     */
    public String comment() default "";

    /**
     * This would provide the parameters when generating the Java doc or
     * whenever this information is needed somewhere in the code.
     * @return - an array of strings with the definition of the method parameters
     */
    public String[] parameters() default "";

    //EXAMPLE OF ANNOTATION
    /**
     * This is an example class for the use of this custom annotation.
     * @author MG
     *
     */
    public static class Example{

    	@MemberDescription(comment = "This contains the name of the output file")
    	public String output;

    	@MemberDescription(comment = "This is an utility class",parameters={"name - the name of the file", "format - the format of the file"})
		public void read(String name, String format){
			System.out.println("Boo");
		}
    }

}