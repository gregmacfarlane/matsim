/* *********************************************************************** *
 * project: org.matsim.*
 * PlanRouter.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
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
package org.matsim.core.router;

import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.api.experimental.facilities.ActivityFacilities;
import org.matsim.core.api.experimental.facilities.Facility;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.utils.misc.Time;
import org.matsim.population.algorithms.PersonAlgorithm;
import org.matsim.population.algorithms.PlanAlgorithm;

/**
 * {@link PlanAlgorithm} responsible for routing all trips of a plan.
 * Activity times are not updated, even if the previous trip arrival time
 * is after the activity end time.
 *
 * @author thibautd
 */
public class PlanRouter implements PlanAlgorithm, PersonAlgorithm {
	private final TripRouter routingHandler;
	private final ActivityFacilities facilities;
	private final MainModeIdentifier mainModeIdentifier;

	/**
	 * Initialises an instance.
	 * @param routingHandler the {@link TripRouter} to use to route individual trips
	 * @param facilities the {@link ActivityFacilities} to which activities are refering.
	 * May be <tt>null</tt>: in this case, the router will be given facilities wrapping the
	 * origin and destination activity.
	 * @param mainModeIdentifier the object to use to identify routing mode
	 */
	public PlanRouter(
			final TripRouter routingHandler,
			final ActivityFacilities facilities,
			final MainModeIdentifier mainModeIdentifier) {
		this.routingHandler = routingHandler;
		this.facilities = facilities;
		this.mainModeIdentifier = mainModeIdentifier;
	}

	/**
	 * Initialises an instance, using the default mode identification:
	 * the mode of a trip is the mode of the first leg of this trip,
	 * except if this leg has mode transit_walk, in which case the mode "pt"
	 * is used.
	 * @param routingHandler the {@link TripRouter} to use to route individual trips
	 * @param facilities the {@link ActivityFacilities} to which activities are refering.
	 * May be <tt>null</tt>: in this case, the router will be given facilities wrapping the
	 * origin and destination activity.
	 */
	public PlanRouter(
			final TripRouter routingHandler,
			final ActivityFacilities facilities ) {
		this( routingHandler,
				facilities,
				new MainModeIdentifierImpl());
	}

	/**
	 * Short for initialising without facilities.
	 * @param routingHandler
	 */
	public PlanRouter(
			final TripRouter routingHandler) {
		this( routingHandler , null );
	}

	/**
	 * Gives access to the {@link TripRouter} used
	 * to compute routes.
	 *
	 * @return the internal TripRouter instance.
	 */
	public TripRouter getTripRouter() {
		return routingHandler;
	}

	@Override
	public void run(final Plan plan) {
		final List<Trip> trips = TripStructureUtils.getTrips( plan , routingHandler.getStageActivityTypes() );

		for (Trip trip : trips) {
			final List<? extends PlanElement> newTrip =
				routingHandler.calcRoute(
						mainModeIdentifier.identifyMainMode( trip.getTripElements() ),
						toFacility( trip.getOriginActivity() ),
						toFacility( trip.getDestinationActivity() ),
						calcEndOfActivity( trip.getOriginActivity() , plan ),
						plan.getPerson() );

			TripRouter.insertTrip(
					plan, 
					trip.getOriginActivity(),
					newTrip,
					trip.getDestinationActivity());
		}
	}

	@Override
	public void run(final Person person) {
		for (Plan plan : person.getPlans()) {
			run( plan );
		}
	}

	// /////////////////////////////////////////////////////////////////////////
	// helpers
	// /////////////////////////////////////////////////////////////////////////
	private Facility toFacility(final Activity act) {
		if ((act.getLinkId() == null || act.getCoord() == null)
				&& facilities != null
				&& !facilities.getFacilities().isEmpty()) {
			// use facilities only if the activity does not provides the required fields.
			return facilities.getFacilities().get( act.getFacilityId() );
		}
		return new ActivityWrapperFacility( act );
	}

	private static double calcEndOfActivity(
			final Activity activity,
			final Plan plan) {
		if (activity.getEndTime() != Time.UNDEFINED_TIME) return activity.getEndTime();

		// no sufficient information in the activity...
		// do it the long way.
		// XXX This is inefficient! Using a cache for each plan may be an option
		// (knowing that plan elements are iterated in proper sequence,
		// no need to re-examine the parts of the plan already known)
		double now = 0;

		for (PlanElement pe : plan.getPlanElements()) {
			now = updateNow( now , pe );
			if (pe == activity) return now;
		}

		throw new RuntimeException( "activity "+activity+" not found in "+plan.getPlanElements() );
	}

	private static double updateNow(
			final double now,
			final PlanElement pe) {
		if (pe instanceof Activity) {
			Activity act = (Activity) pe;
			double endTime = act.getEndTime();
			double startTime = act.getStartTime();
			double dur = (act instanceof ActivityImpl ? ((ActivityImpl) act).getMaximumDuration() : Time.UNDEFINED_TIME);
			if (endTime != Time.UNDEFINED_TIME) {
				// use fromAct.endTime as time for routing
				return endTime;
			}
			else if ((startTime != Time.UNDEFINED_TIME) && (dur != Time.UNDEFINED_TIME)) {
				// use fromAct.startTime + fromAct.duration as time for routing
				return startTime + dur;
			}
			else if (dur != Time.UNDEFINED_TIME) {
				// use last used time + fromAct.duration as time for routing
				return now + dur;
			}
			else {
				throw new RuntimeException("activity has neither end-time nor duration." + act);
			}
		}
		double tt = ((Leg) pe).getTravelTime();
		return now + (tt != Time.UNDEFINED_TIME ? tt : 0);
	}	

	private static class ActivityWrapperFacility implements Facility {
		private final Activity act;

		public ActivityWrapperFacility(final Activity act) {
			this.act = act;
		}

		@Override
		public Coord getCoord() {
			return act.getCoord();
		}

		@Override
		public Id getId() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Map<String, Object> getCustomAttributes() {
			return null;
		}

		@Override
		public Id getLinkId() {
			return act.getLinkId();
		}
	}
}

