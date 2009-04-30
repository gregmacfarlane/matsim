package playground.anhorni.locationchoice.preprocess.analyzePlansAndFacs;

import org.apache.log4j.Logger;
import org.matsim.core.api.facilities.Facilities;
import org.matsim.core.facilities.FacilitiesReaderMatsimV1;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.network.NetworkLayer;
import org.matsim.world.World;

import playground.anhorni.locationchoice.preprocess.AnalyzeFacilities.AnalyzeFacilities;

public class AnalyzePlansAndFacs {

	private final static Logger log = Logger.getLogger(AnalyzePlansAndFacs.class);
	private Facilities facilitiesAll;
	private Facilities facilitiesSL;
	private NetworkLayer network;
	
	public static void main(final String[] args) {

		Gbl.startMeasurement();
		final AnalyzePlansAndFacs analyzer = new AnalyzePlansAndFacs();
		if (args.length < 1) {
			log.info("Too few arguments!");
			System.exit(0);
		}
		analyzer.run("input/networks/ivtch.xml", args[0], "input/facilities.xml.gz", "input/facilities_KTIYear2.xml.gz");
		Gbl.printElapsedTime();
	}
	
	public void run(String networkfilePath, String plansfilePath, String facilitiesAllfilePath, String facilitiesSLfilePath) {
		
		World world = Gbl.createWorld();
		
		log.info("reading the facilities ...");
		this.facilitiesAll =(Facilities)world.createLayer(Facilities.LAYER_TYPE, null);
		new FacilitiesReaderMatsimV1(this.facilitiesAll).readFile(facilitiesAllfilePath);
			
		log.info("reading the network ...");
		this.network = new NetworkLayer();
		new MatsimNetworkReader(this.network).readFile(networkfilePath);
		
		log.info("analyze plans ...");
		AnalyzePlans plansAnalyzer = new AnalyzePlans();
		plansAnalyzer.run(plansfilePath, this.facilitiesAll, this.network);
		
		world.getLayers().remove(Facilities.LAYER_TYPE);
		this.facilitiesSL = (Facilities)world.createLayer(Facilities.LAYER_TYPE, null);
		new FacilitiesReaderMatsimV1(this.facilitiesSL).readFile(facilitiesSLfilePath);
		
		log.info("analyze facilities ...");
		AnalyzeFacilities facilitiesAnalyzer = new AnalyzeFacilities();
		facilitiesAnalyzer.run(this.facilitiesSL);				
	}
}
