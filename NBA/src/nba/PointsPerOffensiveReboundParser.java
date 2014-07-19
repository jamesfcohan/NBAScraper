package nba;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.io.*;
/*
 * This class parses the NBA Schedule to calculate the average points scored off offensive rebounds off misses
 * from different zones of the court.
 * 
 * It is a subclass of DataParser.
 */
public class PointsPerOffensiveReboundParser extends DataParser {
	
	private ArrayList<Integer>[] _ptsAfterOffRebounds;

	private double _totalNumOffRebounds;
	private double _totalPtsAfterOffRebounds;
	
	public PointsPerOffensiveReboundParser() {
		super();
		
		/*
		 * Create an array with indexes that represent the zones of the court shots can be taken from. 
		 * Each index then contains an arraylist whose entries are the number of points scored 
		 * after every offensive rebound off a miss from that zone.
		 */
		_ptsAfterOffRebounds = new ArrayList[CONSTANTS.NUMCOURTZONES];
		for (int i = 0; i < CONSTANTS.NUMCOURTZONES; i++) {
			_ptsAfterOffRebounds[i] = new ArrayList<Integer>();
		}
		
		// parse the data
		super.parseData();
	}
	
	/*
	 * This method gets passed in a miss and a rebound and records the desired data from that miss.
	 * If it is an offensive rebound, first classify the miss to see the zone it was taken from on the court.
	 * Then calculate points scored off the offensive rebound, and add the pts scored to that miss zone's array list.
	 */
	public void recordReboundData(Elements miss, Element reboundRow) {
		if (reboundRow.text().contains("offensive rebound")) {
			int missIndex = super.classifyShot(miss);
			int pointsScored = this.pointsOffRebound(reboundRow);
			_ptsAfterOffRebounds[missIndex].add(pointsScored);
			_totalNumOffRebounds++;
		}
	}
	
	/*
	 * Returns how many points are scored off an offensive rebound
	 * Tons of edge cases to consider. 
	 */
	public int pointsOffRebound(Element reboundRow) {
		
		int pointsScored = 0;
		boolean calculatedPointsScored = false;
		
		// determine who got rebound
		Possession reboundPossession = super.getPossession(reboundRow);
		Possession currentPossession = null;
		Element currentRow = reboundRow;
		
		while (calculatedPointsScored == false) {
				// get text and possession for next row in play-by-play
				currentRow = currentRow.nextElementSibling();
				currentPossession = super.getPossession(currentRow);
				String rowText = currentRow.text();
				
				// if quarter over, text says "End of the nth Quarter." Possession over.
				if (rowText.contains("End of the")) {
					calculatedPointsScored = true;
				}
				
				// if there is a substitution, timeout, jump ball, delay of game violation, ejection, or technical foul continue to next line in play-by-play since possession doesn't change (if possession changes on jump ball, it says turnover in next line)
				else if (rowText.contains("enters the game") || rowText.contains("timeout") || rowText.contains("vs") || rowText.contains("delay") || rowText.contains("technical foul") || rowText.contains("ejected")){
					continue;
				}
				
				// if the team that has possession in the play-by-play is the team that got the offensive rebound
				else if (currentPossession == reboundPossession) {
					// if a team makes the shot, return how many points they got (if goaltending, it appears after made shot in play-by-play, so no need to account for it) 
					if (rowText.contains("makes")) {
						// classify shot. must pass in exact cell and not whole row because gametime numbers inneighboring play-by-play cells will mess up shot location 
						int shot = 0;
						if (currentPossession == Possession.HOME) shot = super.classifyShot(currentRow.select("td:nth-child(4)"));
						else if (currentPossession == Possession.AWAY) shot = super.classifyShot(currentRow.select("td:nth-child(2)"));
						
						// if he makes an "and one" on the basket (fouled on made basket and makes free throw), increment points scored by one
						if (this.makesAndOne(currentRow)) pointsScored++;
						
						// if offensive team makes a technical free throw, they keep possession so increment by one point and continue
						if (rowText.contains("technical")) {
							pointsScored++;
							continue;
						}
						// increment points scored by how much made shot is worth
						else if (shot == CONSTANTS.FREETHROW) pointsScored += this.freeThrowsMade(currentRow);
						else if (shot == CONSTANTS.ATRIM || shot == CONSTANTS.THREETONINE || shot == CONSTANTS.TENTOFIFTEEN  || shot == CONSTANTS.SIXTEENTOTWENTYTHREE || shot == CONSTANTS.MISC) pointsScored +=2;
						else if ((shot > 23 && shot < 94) || shot == CONSTANTS.THREEPOINTER) pointsScored +=3;
			
						calculatedPointsScored = true;
					}
					// edge case - if someone misses technical free throw, continue since they keep possession
					else if (rowText.contains("misses") && rowText.contains("technical")) {
						continue;
					}
					// edge case - if someone misses first regular free throw, they could still get points (unlike any other shot), so calculate free throws made
					else if (rowText.contains("misses") && rowText.contains("free")) {
						pointsScored += this.freeThrowsMade(currentRow);
						calculatedPointsScored = true;
					}
					// if team does not score and turns ball over on next possession in any of the following ways, they get 0 points
					else if (rowText.contains("misses") || rowText.contains("blocks") || rowText.contains("turnover") || rowText.contains("foul") || rowText.contains("traveling") || rowText.contains("bad pass") || rowText.contains("violation") || rowText.contains("3-seconds")) {
						calculatedPointsScored = true;
					}
					// accidentally credits offensive rebound twice sometimes (player then team), continue to find out whether they score
					else if (rowText.contains("rebound")) {
						continue;
					}
				}
				
				// if the team that has possession in the play-by-play is NOT the team that got the offensive rebound
				else if (currentPossession != reboundPossession) {
					// edge case - if offense commits a tech, defensive team shoots tech but offense keeps possession so continue
					if (rowText.contains("technical free throw")) {
						continue;
					}
					// if opposing team gets a rebound, misses/makes/blocks a shot, or makes a bad pass/turns it over, offensive team's possession is over since other team had possession
					else if (rowText.contains("rebound") || rowText.contains("misses") || rowText.contains("makes") || rowText.contains("pass") || rowText.contains("blocks") || rowText.contains("turnover")) {
						calculatedPointsScored = true;
					}
					// if defensive team commits foul, kicked ball/jump ball/lane violation, defensive 3-seconds, continue to next iteration since offense still has possession
					else if (rowText.contains("foul") || rowText.contains("violation") || rowText.contains("3-seconds")) {
						continue;
					}
				}
		}
		return pointsScored;
	}
	
	// checks to see if someone gets an "And One" on a basket (gets fouled on a made basket and hits the additional free throw)
	public boolean makesAndOne(Element basketRow) {
		// first, check to see if the opposing team commits a shooting foul. If not, return false right away
		Possession tookShotPossession = super.getPossession(basketRow);
		Element nextRow = basketRow.nextElementSibling();
		Possession nextActionPossession = super.getPossession(nextRow);
		boolean isFoulOnBasket = false;
		if (tookShotPossession != nextActionPossession) {
			if (nextRow.text().contains("shooting foul")) {
				isFoulOnBasket = true;
			}
		}
		if (isFoulOnBasket == false) {
			return false;
		}
		
		// if there is a foul and they make the free throw, return true. otherwise, return false
		if (this.freeThrowsMade(nextRow) == 1) {
			return true;
		}
		
		return false;
	}
	
	// count the number of made free throws
	public int freeThrowsMade(Element freeThrowRow) {
		
		String rowText = freeThrowRow.text();
		// find first shot free throw (sometimes things like timeout are in the next row after the foul)
		boolean shotFreeThrow = false;
		while (shotFreeThrow == false) { 
			if (rowText.contains("of 1") || rowText.contains("of 2") || rowText.contains("of 3")) {
				shotFreeThrow = true;
			}
			else {
				freeThrowRow = freeThrowRow.nextElementSibling();
				rowText = freeThrowRow.text();
			}
		}
		// find out number of free throws are being taken
		int numFreeThrows = 0;
		if (rowText.contains("of 1")) numFreeThrows = 1;
		else if (rowText.contains("of 2")) numFreeThrows = 2;
		else if (rowText.contains("of 3")) numFreeThrows = 3;
		
		// count number of free throws made
		int pointsScored = 0;
		int freeThrowsCounted = 0;
		// only increment freeThrowsCounted on make or miss (not on a substitution or something between shots)
		while (freeThrowsCounted != numFreeThrows) {
			if (rowText.contains("makes")) {
				pointsScored++;
				freeThrowsCounted++;
			}
			else if (rowText.contains("misses")) freeThrowsCounted++;
			
			freeThrowRow = freeThrowRow.nextElementSibling();
			rowText = freeThrowRow.text();
		}
		
		return pointsScored;
	}
	
	/*
	 * This method takes in an arraylist with the points scored after offensive rebounds off misses from one particular zone of the court, 
	 * and returns the average points scored after offensive rebounds off misses from that zone.
	 */
	public double avgPtsAfterOffRebound(ArrayList<Integer> ptsAfterOffRebounds) {
		double sumPtsAfterOffRebounds = 0;
		double numOffRebounds = ptsAfterOffRebounds.size();
		
		for (double pts: ptsAfterOffRebounds) {
			sumPtsAfterOffRebounds += pts;
		}
		
		_totalPtsAfterOffRebounds += sumPtsAfterOffRebounds;
		
		double avgPtsAfterOffRebound = sumPtsAfterOffRebounds / numOffRebounds;
		return avgPtsAfterOffRebound;
	}
	
	/*
	 * This method interprets the data inside the _ptsAfterOffRebounds array.
	 * It calculates the average points after offensive rebounds off misses from each zone of the court, and prints out the results.
	 */
	public void interpretResults() {
		PrintWriter writer = super.getWriter();
		for (int i = 0; i < _ptsAfterOffRebounds.length; i++) {
			if (_ptsAfterOffRebounds[i].isEmpty() == false) {
				writer.println("\n");
				double avgPtsAfterOffRebound = 0;
				if (i == CONSTANTS.MISC) {
					avgPtsAfterOffRebound = this.avgPtsAfterOffRebound(_ptsAfterOffRebounds[i]);
					writer.println("Average points after offensive rebounds off misc shots: " + avgPtsAfterOffRebound + "\n");
				}
				else if (i == CONSTANTS.THREEPOINTER) {
					avgPtsAfterOffRebound = this.avgPtsAfterOffRebound(_ptsAfterOffRebounds[i]);
					writer.println("Average points after offensive rebounds off three-pointers: " + avgPtsAfterOffRebound + "\n");
				}
				else if (i == CONSTANTS.FREETHROW) {
					avgPtsAfterOffRebound = this.avgPtsAfterOffRebound(_ptsAfterOffRebounds[i]);
					writer.println("Average points after offensive rebounds off free throws: " + avgPtsAfterOffRebound + "\n");
				}
				else if (i == CONSTANTS.ATRIM) {
					avgPtsAfterOffRebound = this.avgPtsAfterOffRebound(_ptsAfterOffRebounds[i]);
					writer.println("Average points after offensive rebounds off shots at rim: " + avgPtsAfterOffRebound + "\n");
				}
				else if (i == CONSTANTS.THREETONINE)  {
					avgPtsAfterOffRebound = this.avgPtsAfterOffRebound(_ptsAfterOffRebounds[i]);
					writer.println("Average points after offensive rebounds off shots from 3 to 9 feet: "+ avgPtsAfterOffRebound + "\n");
				}	
				else if (i == CONSTANTS.TENTOFIFTEEN)  {
					avgPtsAfterOffRebound = this.avgPtsAfterOffRebound(_ptsAfterOffRebounds[i]);
					writer.println("Average points after offensive rebounds off shots from 1O to 15 feet: "+ avgPtsAfterOffRebound + "\n");
				}
				else if (i == CONSTANTS.SIXTEENTOTWENTYTHREE)  {
					avgPtsAfterOffRebound = this.avgPtsAfterOffRebound(_ptsAfterOffRebounds[i]);
					writer.println("Average points after offensive rebounds off shots from 16 to 23 feet: "+ avgPtsAfterOffRebound + "\n");
				}
			}
		}
		double totalAvgPtsAfterOffRebound = _totalPtsAfterOffRebounds/_totalNumOffRebounds;
		writer.println("Average points after all offensive rebounds: " + totalAvgPtsAfterOffRebound);
		writer.close();
	}
}
