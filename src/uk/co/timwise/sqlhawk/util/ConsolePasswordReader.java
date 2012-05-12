/* This file is a part of the sqlHawk project.
 * http://github.com/timabell/sqlHawk
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package uk.co.timwise.sqlhawk.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Implementation of {@link PasswordReader} that takes advantage of the
 * built-in password reading abilities of Java6 (or higher).
 *
 * Use {@link PasswordReader#getInstance()} to get an instance of
 * PasswordReader that's appropriate for your JVM
 * (this one requires a Java6 or higher JVM).
 */
public class ConsolePasswordReader extends PasswordReader {
    private final Object console;
    private final Method readPassword;

    /**
     * Attempt to resolve the Console methods that were introduced in Java6.
     *
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     */
    protected ConsolePasswordReader() throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        // get the console by calling System.console() (Java6+ method)
        Method consoleGetter = System.class.getMethod("console", (Class[])null);
        console = consoleGetter.invoke(null, (Object[])null);

        // get Console.readPassword(String, Object[]) method
        Class<?>[] paramTypes = new Class<?>[] {String.class, Object[].class};
        readPassword = console.getClass().getMethod("readPassword", paramTypes);
    }

    /**
     * Attempt to use the previously resolved Console.
     * If unable to use it then revert to the one implemented in the base class.
     */
    @Override
    public char[] readPassword(String fmt, Object... args) {
        try {
            return (char[])readPassword.invoke(console, fmt, args);
        } catch (Throwable exc) {
            return super.readPassword(fmt, args);
        }
    }
}
