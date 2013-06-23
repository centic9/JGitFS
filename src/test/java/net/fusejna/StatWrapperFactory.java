package net.fusejna;

import net.fusejna.StructStat.StatWrapper;


public class StatWrapperFactory {
	public static StatWrapper create() {
		return new StatWrapper(new net.fusejna.StructStat.I686());
	}
}
