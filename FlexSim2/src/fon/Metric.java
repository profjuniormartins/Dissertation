/**
 * The Metrics
 */
package fon;

public class Metric {
	
	/** The OSPF metrics. */
	public enum OSPF{
		/** RFI */ RFI,
		/** Teste */ TESTE,
	}
	
	/** The Ant metrics */
	public enum Ant{
		/** First-fit. */ FIRST_FIT,
		/** Best-fit. */ BEST_FIT,
	}
	
	/**
	 * Calculate the link cost for the graph.
	 * @param ls The label set.
	 * @return The cost.
	 */
	public static double Teste(LabelSet ls) {
		return 1;
	} 

}
