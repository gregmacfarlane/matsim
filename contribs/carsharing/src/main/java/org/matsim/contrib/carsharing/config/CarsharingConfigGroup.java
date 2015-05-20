package org.matsim.contrib.carsharing.config;

import org.matsim.core.config.experimental.ReflectiveConfigGroup;


public class CarsharingConfigGroup extends ReflectiveConfigGroup {
	public static final String GROUP_NAME = "Carsharing";
	
	private String statsWriterFrequency = null;
	
	

	
	public CarsharingConfigGroup() {
		super(GROUP_NAME);
	}
	
	@StringGetter( "statsWriterFrequency" )
	public String getStatsWriterFrequency() {
		return this.statsWriterFrequency;
	}

	@StringSetter( "statsWriterFrequency" )
	public void setStatsWriterFrequency(final String statsWriterFrequency) {
		this.statsWriterFrequency = statsWriterFrequency;
	}
	
}