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
package uk.co.timwise.sqlhawk.config;

/**
 * Base class to indicate that there was problem with how sqlHawk was configured / used.
 */
public class InvalidConfigurationException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private String paramName;

	/**
	 * When a message is sufficient
	 *
	 * @param msg
	 */
	public InvalidConfigurationException(String msg) {
		super(msg);
	}

	/**
	 * When there's an associated root cause.
	 * The resultant msg will be a combination of <code>msg</code> and cause's <code>msg</code>.
	 *
	 * @param msg
	 * @param cause
	 */
	public InvalidConfigurationException(String msg, Throwable cause) {
		super(msg, cause);
	}

	/**
	 * When there are no details other than the root cause
	 *
	 * @param cause
	 */
	public InvalidConfigurationException(Throwable cause) {
		super(cause);
	}

	public InvalidConfigurationException setParamName(String paramName) {
		this.paramName = paramName;
		return this;
	}

	public String getParamName() {
		return paramName;
	}
}
