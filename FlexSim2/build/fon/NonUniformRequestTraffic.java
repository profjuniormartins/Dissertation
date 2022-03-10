/**
 * Created on 28/01/2019.
 */
package fon;

import util.DiscreteRouletteWheel;
import net.Network;


/**
 * @author Leandro Alvarez de Lima
 * @version 1.0
 *
 */
public class NonUniformRequestTraffic extends FixedRequestTraffic{
	/** Serial version uid. */
	private static final long serialVersionUID = 1L;	
	/** The random seed. */
	protected long seed;
	/** The roulette wheel for generating random values according to the probabilities. */
    protected DiscreteRouletteWheel random;
	/** The requested bandwidths (flexible). */
	protected double[] bandwidths;
	/** The requested bandwidths probabilities (flexible). */
	protected double[] probabilities;
	/** The roulette wheel for generating random bandwidths. */
	protected DiscreteRouletteWheel rouletteWheel;

	/**
	 * Creates a new NonUniformRequestTraffic object.
	 * @param aMaxTries
	 * @param seed
	 * @param aBandwidths
	 * @param aProbabilities
	 */
	public NonUniformRequestTraffic(int aMaxTries, long seed, double[] aBandwidths, double[] aProbabilities) {
		super(aMaxTries);
		this.seed = seed;
		this.bandwidths = aBandwidths;
		this.probabilities = aProbabilities;
		//Create the random choice
		this.rouletteWheel = new DiscreteRouletteWheel(seed, bandwidths, probabilities);
	}
		
	@Override
	public Object getContent() {
		//Get source and destination nodes
		String source = Network.getSourceNode();
		String destination = Network.getDestinationNode(source);
		//Get the service time (duration)
		double duration = distribution.getServiceTime();
		//Generate the request
		double bandwidth =  rouletteWheel.random();	
		//System.out.println("bandwidth: "+ bandwidth);
		Request req = new Request((new Long(counter)).toString(), source, destination, duration, bandwidth, retries);	
		//Increment counter
		counter++;
		//Return request
		return req;
	}

			
}
