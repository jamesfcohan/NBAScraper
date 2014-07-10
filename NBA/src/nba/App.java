package nba;
/*
 * This program is designed to scrape the play-by-play in every box score of every regular season NBA game on espn.com
 * 
 * In each play-by-play, it locates every single missed shot, and records the distance the shot was taken from,
 * and whether it led to an offensive or defensive rebound.
 * 
 * This information can be used to analyze whether missed shots from certain locations on the court are actually 
 * more valuable than missed shots taken from other locations. If shots taken from a certain location lead 
 * to offensive rebounds a higher percent of the time, then these misses are more likely to result in second chance points.
 * 
 * By James Cohan
 */
public class App {

	public App() {
		new BestOffensiveRebounds();
	}

	public static void main(String[] args) {
		new App();
	}	
}
