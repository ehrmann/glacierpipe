/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * 
 */

package glacierpipe.terminal;

import glacierpipe.format.PrintWriterFormat;
import glacierpipe.format.StringFormat;

import java.io.PrintWriter;

public class ProgressPrinter {
	
	protected int width = 80;
	
	protected long lastPrint = System.nanoTime();
	protected final long start = lastPrint;
	
	protected long lastCurrent = 0;
	
	protected double rateHistory = Double.NaN;
	
	protected final long total;
	protected long current = 0;
	protected boolean done = false;
	
	protected int bouncerPosition = 0;
	protected int bouncerVelocity = 1;
	protected long lastSpinTime = start;
	
	public ProgressPrinter(long total) {
		this.total = total;
	}
	
	public void setCurrent(long current) {
		if (this.done) {
			throw new IllegalStateException("done() already called");
		} else if (this.total >= 0 && current > this.total) {
			throw new IllegalArgumentException("current is larger than the total");
		} else if (current < 0) {
			throw new IllegalArgumentException("current was negative");
		}
		
		this.current = current;
	}
	
	public void addCurrent(long addend) {
		if (this.done) {
			throw new IllegalStateException("done() already called");
		} else if (this.total >= 0 && current + addend > this.total) {
			throw new IllegalArgumentException("current is larger than the total");
		} else if (current + addend < 0) {
			throw new IllegalArgumentException("current would be negative");
		}
		
		this.current += addend;
	}
	
	public void done() {
		this.done = true;
		if (this.total >= 0) {
			this.current = this.total;
		}
	}
	
	public void print(PrintWriter writer) {
		
		long now = System.nanoTime();
		
		double rate = (double)(this.current - this.lastCurrent) / ((now - this.lastPrint) / 1000000000.0);
		
		if (Double.isNaN(rateHistory)) {
			rateHistory = rate;
		} else {
			rateHistory = .75 * rateHistory + .25 * rate;
		}
		
		int totalMarks = this.width - 50;
		
		if (total >= 0) {
			writer.format("%3d%% ", Math.round(100.0 * this.current / this.total));
			writer.print('[');
			
			int marks = Math.round((float)totalMarks * this.current / this.total);
			
			for (int i = 0; i <= totalMarks; i++) {
				if (i < marks) {
					writer.print('=');
				} else if (i == marks) {
					writer.print('>');
				} else {
					writer.print(' ');
				}
			}
			
			writer.print("] ");
		} else {
			writer.print('[');
			
			for (int i = 0; i <= totalMarks; i++) {
				if (bouncerPosition == i) {
					writer.print("<=>");
					i += 2;
				} else {
					writer.print(' ');
				}
			}
			
			writer.print("] ");
			
			if (now - lastSpinTime >= 500000000) {
				bouncerPosition += bouncerVelocity;
				if (bouncerPosition < 0) {
					bouncerPosition = 1;
					bouncerVelocity = 1;
				} else if (bouncerPosition + 2 > totalMarks) {
					bouncerPosition -= 2;
					bouncerVelocity = -1;
				}
				
				lastSpinTime = now;
			}
		}
		
		writer.print(StringFormat.toHumanReadableDataSize(current));
		writer.print(' ');
		
		if (done) {
			double seconds = (double)(now - start) / 1000000000.0;
			writer.print(StringFormat.toHumanReadableDataSize(Math.round((double)this.current / seconds)));
			writer.print("/s in ");
			PrintWriterFormat.printTime(writer, (now - start) / 1000000, false);
		} else {
			if (Double.isNaN(rateHistory)) {
				writer.print("--.-B");
			} else {
				writer.print(StringFormat.toHumanReadableDataSize(Math.round(rateHistory)));
			}
			writer.print("/s ");
			
			if (total >= 0) {
				writer.print("eta ");
				
				if (Double.isNaN(rate) || rate < 1.0) {
					writer.print("-");
				} else {
					PrintWriterFormat.printTime(writer, Math.round(1000.0 * (total - current) / rate), false);
				}
			} else {
				writer.print("duration ");
				PrintWriterFormat.printTime(writer, (now - start) / 1000000, false);
			}
		}
	}
}