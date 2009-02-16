/* *********************************************************************** *
 * project: org.matsim.*
 * CalcODMatrices.java
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

package org.matsim.events.algorithms;

import java.util.ArrayList;
import java.util.TreeMap;

import org.matsim.events.AgentArrivalEvent;
import org.matsim.events.AgentDepartureEvent;
import org.matsim.events.handler.AgentArrivalEventHandler;
import org.matsim.events.handler.AgentDepartureEventHandler;
import org.matsim.matrices.Entry;
import org.matsim.matrices.Matrices;
import org.matsim.matrices.Matrix;
import org.matsim.network.Link;
import org.matsim.network.NetworkLayer;
import org.matsim.utils.misc.Time;
import org.matsim.world.Location;
import org.matsim.world.ZoneLayer;

public class CalcODMatrices implements AgentArrivalEventHandler, AgentDepartureEventHandler {

	private final NetworkLayer network;
	private final ZoneLayer tvzLayer;
	private final TreeMap<String, Location> agents = new TreeMap<String, Location>(); // <AgentID, StartLoc>
	private final TreeMap<String, Double> agentstime = new TreeMap<String, Double>();
	private final Matrix matrix;
	private double minTime = Time.UNDEFINED_TIME; //Integer.MIN_VALUE;
	private double maxTime = Double.POSITIVE_INFINITY;//Integer.MAX_VALUE;
	public int counter = 0;

	public CalcODMatrices(final NetworkLayer network, final ZoneLayer tvzLayer, final String id) {
		this.network = network;
		this.tvzLayer = tvzLayer;
		this.matrix = Matrices.getSingleton().createMatrix(id, tvzLayer, "od for miv");
	}

	//////////////////////////////////////////////////////////////////////
	// Implementation of EventAlgorithmI
	//////////////////////////////////////////////////////////////////////

	public void handleEvent(final AgentDepartureEvent event) {
		double time = event.time;
		if ((time < this.minTime) || (time >= this.maxTime)) {
			return;
		}
		Location fromLoc = getLocation(event.linkId);
		if (fromLoc != null) {
			String agentId = event.agentId;
			this.agents.put(agentId, fromLoc);
			this.agentstime.put(agentId, time);
		}
	}

	public void handleEvent(final AgentArrivalEvent event) {
		double time = event.time;
		if (time < this.minTime) {
			return;
		}
		String agentId = event.agentId;
		Location fromLoc = this.agents.remove(agentId); // use remove instead of get to make sure one arrival event is not used for multiple departure events
		if (fromLoc == null) {
			// we have no information on where the agent started
			return;
		}
		Location toLoc = getLocation(event.linkId);
		if (toLoc != null) {
			this.agents.remove(agentId);
			Entry entry = this.matrix.getEntry(fromLoc, toLoc);
			this.counter++;
			if (entry == null) {
				this.matrix.setEntry(fromLoc, toLoc, 1);
			} else {
				this.matrix.setEntry(fromLoc, toLoc, entry.getValue() + 1);
			}
		}
	}

	/**
	 * sets the time range in which events are counted. only events in the
	 * range [minTime, maxTime[ are counted, with maxTime excluded
	 *
	 * @param minTime events with a smaller time will not be counted
	 * @param maxTime events with this time or higher will not be counted
	 */
	public void setTimeRange(final double minTime, final double maxTime) {
		this.minTime = minTime;
		this.maxTime = maxTime;
	}

	public Matrix getMatrix() {
		return this.matrix;
	}

	public void reset(final int iteration) {
		// nothing to do
	}

	private Location getLocation(final String linkId) {
		Link link = this.network.getLink(linkId);

		ArrayList<Location> locs = this.tvzLayer.getNearestLocations(link.getCenter(), null);
		if (locs.size() > 0) {
			return locs.get(0);
		}
		return null;
	}

}
