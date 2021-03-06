/*
 * *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
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
 * *********************************************************************** *
 */

package org.matsim.contrib.dvrp.run;

import java.util.HashSet;

import org.matsim.core.config.ConfigGroup;

/**
 * @author Michal Maciejewski (michalm)
 */
public class ConfigConsistencyCheckers {
	public static <C extends ConfigGroup & Modal> boolean isEitherSingleOrMultiModeDeclared(C cfg,
			MultiModal<C> multiModeCfg) {
		return cfg != null ^ multiModeCfg != null;
	}

	public static boolean areModesUnique(MultiModal<?> multiModal) {
		return multiModal.modes().allMatch(new HashSet<>()::add);
	}
}
