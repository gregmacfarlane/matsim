/* *********************************************************************** *
 * project: org.matsim.*
 * CoopersAgentLogicFactory.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.withinday.coopers;

import java.util.LinkedList;
import java.util.List;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.groups.CharyparNagelScoringConfigGroup;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.withinday.WithindayAgent;
import org.matsim.withinday.WithindayAgentLogicFactory;
import org.matsim.withinday.beliefs.AgentBeliefs;
import org.matsim.withinday.contentment.AgentContentment;
import org.matsim.withinday.coopers.cooperscontentment.CoopersContentment;
import org.matsim.withinday.coopers.routeprovider.CoopersRouteProvider;
import org.matsim.withinday.percepts.AgentPercepts;
import org.matsim.withinday.routeprovider.RouteProvider;
import org.matsim.withinday.trafficmanagement.VDSSign;


/**
 * @author dgrether
 */
public class CoopersAgentLogicFactory extends WithindayAgentLogicFactory {

	private List<VDSSign> signs;
	
	public CoopersAgentLogicFactory(final Network network,
			final CharyparNagelScoringConfigGroup scoringConfig, final List<VDSSign> signs) {
		super(network, scoringConfig);
		this.signs = signs;
	}

	@Override
	public AgentContentment createAgentContentment(final WithindayAgent agent) {
		return new CoopersContentment();
	}

	@Override
	public RouteProvider createRouteProvider() {
		return new CoopersRouteProvider(super.aStarRouteProvider, this.network, this.signs);
	}

	
	@Override
	public Tuple<AgentBeliefs, List<AgentPercepts>> createAgentPerceptsBeliefs(final int sightDistance) {
		return new Tuple<AgentBeliefs, List<AgentPercepts>>(null, new LinkedList<AgentPercepts>());
	}

}
