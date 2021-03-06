/* This file is a part of the sqlHawk project.
 * http://timabell.github.com/sqlHawk/
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
package uk.co.timwise.sqlhawk.db.read;

/**
 * Indicates that we had an issue launching a process
 */
public class ProcessExecutionException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	/**
	 * When a message is sufficient
	 *
	 * @param msg
	 */
	public ProcessExecutionException(String msg) {
		super(msg);
	}

	/**
	 * When there's an associated root cause.
	 * The resultant msg will be a combination of <code>msg</code> and cause's <code>msg</code>.
	 *
	 * @param msg
	 * @param cause
	 */
	public ProcessExecutionException(String msg, Throwable cause) {
		super(msg + " " + cause.getMessage(), cause);
	}

	/**
	 * When there are no details other than the root cause
	 *
	 * @param cause
	 */
	public ProcessExecutionException(Throwable cause) {
		super(cause);
	}
}
