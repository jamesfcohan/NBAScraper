package nba;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
/*
 * This class parses the NBA Schedule to calculate the offensive rebounding percentage off misses
 * from different zones of the court.
 * 
 * It is a subclass of DataParser.
 */
public class OffensiveReboundingPercentageParser extends DataParser {
	
	private int[] _offensiveRebounds;
	private int[] _defensiveRebounds;
	
	public OffensiveReboundingPercentageParser() {
		super();
		
		// indexes in array are zones of the court misses were taken from.
		// increment index to indicate whether a miss from that zone led to an offensive or defensive rebound
		_offensiveRebounds = new int[CONSTANTS.NUMCOURTZONES];
		_defensiveRebounds = new int[CONSTANTS.NUMCOURTZONES];
		
		super.parseData();
	}

	/*
	 * This method gets passed in a miss and a rebound and records the desired data from that miss.
	 * First classify the miss, to see which zone of the court the shot was taken from.
	 * Then record whether it led to an offensive or defensive rebound.
	 */
	public void recordReboundData(Elements miss, Element reboundRow) {
		int missIndex = super.classifyShot(miss);
		String text = reboundRow.text();
		
		if (text.contains("offensive")) {
			_offensiveRebounds[missIndex]++;
		}
		else if (text.contains("defensive")) {
			_defensiveRebounds[missIndex]++;
		}
	}

	/*
	 *  This method takes in the offensive and defensive rebounding arrays and interprets and prints out their results
	 *  Since array indexes indicate the zone of the court the miss is taken from, all it has to do is print out the number
	 *  of offensive or defensive rebounds that index is holding, and calculate and print out the offensive rebounding percentage.
	 */
	public void interpretResults() {
		PrintWriter writer = super.getWriter();
		double totalOffensiveRebounds = 0;
		double totalRebounds = 0;
		for (int i = 0; i < _offensiveRebounds.length; i++) {
			if (_offensiveRebounds[i] !=0 || _defensiveRebounds[i] != 0) {
				totalOffensiveRebounds += _offensiveRebounds[i];
				totalRebounds += (_offensiveRebounds[i] + _defensiveRebounds[i]);
				
				double offensiveReboundingPercentage = 0;
			
				if (i == CONSTANTS.ATRIM) {
					writer.println("Offensive rebounds off shots at rim: " + _offensiveRebounds[i]);
					writer.println("Defensive rebounds off shots at rim: " + _defensiveRebounds[i]);
					offensiveReboundingPercentage = _offensiveRebounds[i]/(double)(_offensiveRebounds[i] + _defensiveRebounds[i]) * 100;
					writer.println("Offensive rebounding percentage at rim: " + offensiveReboundingPercentage + "%\n");
				}
				else if (i == CONSTANTS.THREETONINE) {
					writer.println("Offensive rebounds off shots from 3 to 9 feet: " + _offensiveRebounds[i]);
					writer.println("Defensive rebounds off shots from 3 to 9 feet: " + _defensiveRebounds[i]);
					offensiveReboundingPercentage = _offensiveRebounds[i]/(double)(_offensiveRebounds[i] + _defensiveRebounds[i]) * 100;
					writer.println("Offensive rebounding percentage off shots from 3 to 9 feet: " + offensiveReboundingPercentage + "%\n");
				}
				else if (i == CONSTANTS.TENTOFIFTEEN) {
					writer.println("Offensive rebounds off shots from 10 to 15 feet: " + _offensiveRebounds[i]);
					writer.println("Defensive rebounds off shots from 10 to 15: " + _defensiveRebounds[i]);
					offensiveReboundingPercentage = _offensiveRebounds[i]/(double)(_offensiveRebounds[i] + _defensiveRebounds[i]) * 100;
					writer.println("Offensive rebounding percentage of shots from 10 to 15 feet: " + offensiveReboundingPercentage + "%\n");
				}
				else if (i == CONSTANTS.SIXTEENTOTWENTYTHREE) {
					writer.println("Offensive rebounds off shots from 16 to 23 feet: " + _offensiveRebounds[i]);
					writer.println("Defensive rebounds off shots from 16 to 23: " + _defensiveRebounds[i]);
					offensiveReboundingPercentage = _offensiveRebounds[i]/(double)(_offensiveRebounds[i] + _defensiveRebounds[i]) * 100;
					writer.println("Offensive rebounding percentage off shots from 16 to 23 feet: " + offensiveReboundingPercentage + "%\n");
				}
				else if (i == CONSTANTS.THREEPOINTER) {
					writer.println("Offensive rebounds off threes: " + _offensiveRebounds[i]);
					writer.println("Defensive rebounds off threes: " + _defensiveRebounds[i]);
					offensiveReboundingPercentage = _offensiveRebounds[i]/(double)(_offensiveRebounds[i] + _defensiveRebounds[i]) * 100;
					writer.println("Offensive rebounding percentage of threes: " + offensiveReboundingPercentage + "%\n");
				}
				else if (i == CONSTANTS.FREETHROW) {
					writer.println("Offensive rebounds off free throw: " + _offensiveRebounds[i]);
					writer.println("Defensive rebounds off free throws: " + _defensiveRebounds[i]);
					offensiveReboundingPercentage = _offensiveRebounds[i]/(double)(_offensiveRebounds[i] + _defensiveRebounds[i]) * 100;
					writer.println("Offensive rebounding percentage off free throws: " + offensiveReboundingPercentage + "%\n");
				}
				else if (i == CONSTANTS.MISC) {
					writer.println("Offensive rebounds misc shots: " + _offensiveRebounds[i]);
					writer.println("Defensive rebounds off misc shots: " + _defensiveRebounds[i]);
					offensiveReboundingPercentage = _offensiveRebounds[i]/(double)(_offensiveRebounds[i] + _defensiveRebounds[i]) * 100;
					writer.println("Offensive rebounding percentage off misc shots: " + offensiveReboundingPercentage + "%\n");
				}
			}
		}
		double totalOffensiveReboundingPercentage = (totalOffensiveRebounds/totalRebounds) * 100;
		writer.println("Offensive rebounding percentage of all shots: " + totalOffensiveReboundingPercentage + "%");
		writer.close();
	}
}
