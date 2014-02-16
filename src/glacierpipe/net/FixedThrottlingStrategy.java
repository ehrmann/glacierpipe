package glacierpipe.net;

import glacierpipe.io.ThrottledInputStream.ThrottlingStrategy;

public class FixedThrottlingStrategy implements ThrottlingStrategy {

	protected double rate;
	
	public FixedThrottlingStrategy(double rate) {
		this.rate = rate;
	}
	
	@Override
	public double getBytesPerSecond() {
		return rate;
	}

}
