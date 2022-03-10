/**
 * Created on 26/09/2016.
 */
package fon;

/**
 * A variable-sized optical frequency range that can be allocated to a connection. 
 * It considers a GMPLS-based control of flexi-grid enabled Dense Wavelength Division Multiplexing (DWDM) networks.
 * 
 * @see <href="https://tools.ietf.org/html/rfc7698">RFC7698</a>
 * @see <href="https://tools.ietf.org/html/rfc7792">RFC7792</a>
 * 
 * @author Gustavo Sousa Pavani
 * @version 1.0
 *
 */
public class FrequencySlot {
	/** The anchor frequency (THz). */
	protected double anchorFrequency;
	/** The nominal central frequency granularity (GHz). */
	protected double frequencyGranularity;
	/** The slot width granularity (GHz). */
	protected double slotGranularity;
	/** The number of slots. */
	protected int numberSlots;
	/** The central frequency slot. */
	protected int centralSlot;
	
	/**
	 * Creates a new FrequencySlot object.
	 * @param n The central frequency slot.
	 * @param m The number of slots.
	 */
	public FrequencySlot(int n, int m) {
		//Get the default values as of G.694.1
		this.anchorFrequency = FlexiLink.ANCHOR_FREQUENCY;
		this.frequencyGranularity = FlexiLink.NOMINAL_CENTRAL_FREQUENCY_GRANULARITY;
		this.slotGranularity = FlexiLink.SLOT_WIDTH_GRANULARITY;
		//Set the values of the central slot and the number of slots
		this.centralSlot = n;
		this.numberSlots = m;
	}
	
	/**
	 * Returns the slot width, in GHz.
	 * @return The slot width, in GHz.
	 */
	public double getSlotWidth() {
		return (this.numberSlots * this.slotGranularity);
	}
	
	/**
	 * Returns the nominal central frequency, in THz.
	 * @return The nominal central frequency, in THz.
	 */
	public double getNominalCentralFrequency() {
		return (this.anchorFrequency + (this.centralSlot * (frequencyGranularity/1E3)));
	}
	
	/**
	 * Returns the anchor frequency, in THz.
	 * @return The anchor frequency, in THz.
	 */
	public double getAnchorFrequency() {
		return this.anchorFrequency;
	}
	
	/**
	 * Returns the nominal central frequency granularity, in GHz.
	 * @return The nominal central frequency granularity, in GHz.
	 */
	public double getFrequencyGranularity() {
		return this.frequencyGranularity;
	}
	
	/**
	 * Returns the slot width granularity, in GHz.
	 * @return The slot width granularity, in GHz.
	 */
	public double getSlotGranularity() {
		return this.slotGranularity;
	}
	
	/**
	 * Returns the number of slots. 
	 * @return The number of slots.
	 */
	public int getNumberSlots() {
		return this.numberSlots;
	}
	
	/**
	 * Returns the central frequency slot index.
	 * @return The central frequency slot index.
	 */
	public int getCentralSlot() {
		return this.centralSlot;
	}
	
	/**
	 * Returns a String representation of this object.
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Central: ");
		builder.append(this.centralSlot);
		builder.append(", slots: ");
		builder.append(this.numberSlots);
		builder.append(".");
		return builder.toString();
	}
}
