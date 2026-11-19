package com.tfaticktracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TfaTickTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TfaTickTrackerPlugin.class);
		RuneLite.main(args);
	}
}
