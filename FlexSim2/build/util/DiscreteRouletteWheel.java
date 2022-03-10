/**
 * Created on 29/01/2019.
 */

package util;

import java.util.Random;

import random.MersenneTwister;

public class DiscreteRouletteWheel{
    private double[] values;
    private double[] probabilities;
    private Random rand;
    
    /**
     * 
     * @param values
     * @param probabilities
     */
    public DiscreteRouletteWheel(double[] values, double[] probabilities){
        this.values = values;
        this.probabilities = probabilities;
        rand = new MersenneTwister();
    }
    
    /**
     * 
     * @param seed 
     * @param values
     * @param probabilities
     */
    public DiscreteRouletteWheel(long seed, double[] values, double[] probabilities){
        this.values = values;
        this.probabilities = probabilities;
        rand = new MersenneTwister(seed);
    }
    
    /**
     * Return a random value according to the probabilities.
     * @return
     */
    public double random(){
        double p = rand.nextDouble();
        double sum = 0.0;
        int i = 0;
        while(sum < p){
            sum += probabilities[i];
            i++;
        }
        return values[i-1];
    }
}
