package glacierpipe.net;

import glacierpipe.io.ThrottledInputStream.ThrottlingStrategy;

public class FixedThrottlingStrategy implements ThrottlingStrategy {

	protected double rate;
	
	public FixedThrottlingStrategy(double rate) {
		
	}
	
	@Override
	public double getBytesPerSecond() {
		return rate;
	}

}
