/*
 * This file is part of MaruNeko.
 *
 * Copyleft 2017 Mark Jeronimus. All Rights Reversed.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NativeAccessHooks. If not, see <http://www.gnu.org/licenses/>.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.digitalmodular.maruneko.diskscanner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * @author Mark Jeronimus
 */
// Created 2012-11-15
public class StatUtils {
	public static int getHardLinkCount(String filePath) {
		Process        process = null;
		BufferedReader in      = null;
		try {
			process = Runtime.getRuntime().exec(new String[]{"stat", "--printf=%h", '"' + filePath + '"'});

			int exitValue = process.waitFor();
			if (exitValue != 0) {
				return 1;
			}

			in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
			String inpMsg = in.readLine();
			if (inpMsg == null) {
				return 1;
			}

			return Integer.parseInt(inpMsg);
		} catch (NumberFormatException e) {
			return 1;
		} catch (InterruptedException e) {
			return 1;
		} catch (IOException e) {
			return 1;
		} finally {
			if (process != null) {
				process.destroy();
				if (in != null) {
					try {
						in.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
}
