package nba;
/*
 * This program is designed to scrape the play-by-play in every box score of every regular season NBA game on espn.com
 * It can parse the play-by-plays in two different ways.
 * 
 * 1.If the parameter passed in "isOffRebPercent", in each play-by-play, it locates every single missed shot, and records the distance the shot was taken from,
 * and whether it led to an offensive or defensive rebound. It then calculates the offensive rebounding percentage off
 * misses from each zone of the court.
 * 
 * 2. If the parameter passed in is "PtsPerOffReb" in each play-by-play, it locates every single missed shot. If the shot led to an offensive rebound,
 * it records the distance the shot was taken from, and how many second chance points the shot resulted in. It then calculates 
 * the average second chance points scored per offensive rebound off misses from each zone of the court.
 * 
 * This information can be used to analyze whether missed shots from certain locations on the court are actually 
 * more valuable than missed shots taken from other locations. If shots taken from a certain location lead 
 * to offensive rebounds a higher percent of the time, and if offensive rebounds off certain misses lead to more second chance points,
 * then these shots actually have more value than just looking at the field goal percentage and how many points the shot is worth if it goes in.
 * 
 * By James Cohan
 */
public class App {

	public App(ParserType parser) {
		if (parser == ParserType.OffensiveReboundingPercentageParser) {
			new OffensiveReboundingPercentageParser();
		}
		else if (parser == ParserType.PointsPerOffensiveReboundParser) {
			new PointsPerOffensiveReboundParser();
		}
	}

	public static void main(String[] args) {
		ParserType parser = null;
		String program = args[0];
		
		if(program.equals("OffRebPercent")) {
			parser = ParserType.OffensiveReboundingPercentageParser;
		}
		else if (program.equals("PtsPerOffReb")) {
			parser = ParserType.PointsPerOffensiveReboundParser;
		}
		
		new App(parser);
	}	
}
