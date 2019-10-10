/*******************************************************************************
 * Copyright (c) 2017 Pablo Pavon Marino and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the 2-clause BSD License 
 * which accompanies this distribution, and is available at
 * https://opensource.org/licenses/BSD-2-Clause
 *
 * Contributors:
 *     Pablo Pavon Marino and others - initial API and implementation
 *******************************************************************************/


package com.net2plan.gui.plugins.networkDesign.viewEditTopolTables.controlTables.specificTables;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.net2plan.gui.plugins.GUINetworkDesign;
import com.net2plan.gui.plugins.GUINetworkDesignConstants.AJTableType;
import com.net2plan.gui.plugins.networkDesign.viewEditTopolTables.controlTables.AdvancedJTable_networkElement;
import com.net2plan.gui.plugins.networkDesign.viewEditTopolTables.controlTables.AjtColumnInfo;
import com.net2plan.gui.plugins.networkDesign.viewEditTopolTables.controlTables.AjtRcMenu;
import com.net2plan.gui.plugins.networkDesign.viewEditTopolTables.dialogs.DialogBuilder;
import com.net2plan.gui.plugins.networkDesign.viewEditTopolTables.dialogs.InputForDialog;
import com.net2plan.gui.plugins.networkDesign.viewEditTopolTables.monitoring.MonitoringUtils;
import com.net2plan.gui.utils.StringLabeller;
import com.net2plan.gui.utils.WiderJComboBox;
import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.Net2PlanException;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.NetworkElement;
import com.net2plan.interfaces.networkDesign.NetworkLayer;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.internal.Constants.NetworkElementType;
import com.net2plan.libraries.IPUtils;
import com.net2plan.niw.OpticalSimulationModule;
import com.net2plan.niw.OpticalSpectrumManager;
import com.net2plan.niw.WFiber;
import com.net2plan.niw.WIpLink;
import com.net2plan.niw.WNet;
import com.net2plan.niw.WNetConstants;
import com.net2plan.niw.WNetConstants.WTYPE;
import com.net2plan.niw.WNode;
import com.net2plan.utils.Constants.RoutingType;
import com.net2plan.utils.Pair;

import net.miginfocom.swing.MigLayout;

/**
 */
@SuppressWarnings({ "unchecked", "serial" })
public class Niw_AdvancedJTable_link extends AdvancedJTable_networkElement<Link>
{
	private static DecimalFormat df = new DecimalFormat("#.##");
	
    public Niw_AdvancedJTable_link(GUINetworkDesign callback , NetworkLayer layerThisTable)
    {
        super(callback, AJTableType.LINKS , layerThisTable.getName().equals(WNetConstants.ipLayerName)? "IP links" : "Fibers" , layerThisTable , true , e->e.isUp()? null : Color.RED);
    }

    @Override
  public List<AjtColumnInfo<Link>> getNonBasicUserDefinedColumnsVisibleOrNot()
  {
    	final List<AjtColumnInfo<Link>> res = new LinkedList<> ();
    	final WNet wNet = new WNet (callback.getDesign()); 
    	final boolean isIpLayer = getTableNetworkLayer().equals(wNet.getIpLayer().getNe());
    	final boolean isWdmLayer = getTableNetworkLayer().equals(wNet.getWdmLayer().getNe());
    	assert isIpLayer || isWdmLayer;
    	assert !(isIpLayer && isWdmLayer);
    	final Function<Link,WFiber> toWFiber = d -> (WFiber) wNet.getWElement(d).get();
    	final Function<Link,WIpLink> toWIpLink = d ->(WIpLink) wNet.getWElement(d).get();
    	final Map<Link,SortedMap<String,Pair<Double,Double>>> allLinksPerQosOccupationAndQosViolationMap = isIpLayer? callback.getDesign().getAllLinksPerQosOccupationAndQosViolationMap (getTableNetworkLayer()) : null;
    	
        res.add(new AjtColumnInfo<Link>(this, Boolean.class, null, "Show/hide", "Indicates whether or not the link is visible in the topology canvas", (n, s) -> {
            if ((Boolean) s) callback.getVisualizationState().showOnCanvas(n);
            else callback.getVisualizationState().hideOnCanvas(n);
        }, n -> !callback.getVisualizationState().isHiddenOnCanvas(n), AGTYPE.COUNTTRUE, null));
        res.add(new AjtColumnInfo<Link>(this , Node.class, null , "A", "Origin node", null , d->d.getOriginNode() , AGTYPE.NOAGGREGATION , null));
        res.add(new AjtColumnInfo<Link>(this , Node.class, null , "B", "Destination node", null , d->d.getDestinationNode() , AGTYPE.NOAGGREGATION , null));
        res.add(new AjtColumnInfo<Link>(this , Link.class, null , "Bidirectional pair", "If the link is bidirectional, provides its bidirectional pair", null , d->d.getBidirectionalPair() , AGTYPE.NOAGGREGATION, null));

    	if (isIpLayer)
    	{
		      res.add(new AjtColumnInfo<Link>(this , String.class, null , "Trav. QoS types" , "The QoS types of the traversing IP demands", null , d->allLinksPerQosOccupationAndQosViolationMap.getOrDefault(d, new TreeMap<> ()).entrySet().stream().filter(ee->ee.getValue().getFirst() > 0).map(ee->ee.getKey()).collect(Collectors.joining(",")) , AGTYPE.SUMDOUBLE , null));
		      res.add(new AjtColumnInfo<Link>(this , Double.class, null , "Total QoS violation (Gbps)" , "The total amount of IP link capacity that is being used outside the QoS contract, so the traffic using it would not be carried (and thus would be blocked) if the network applied a drop policy to it, or just if such extra capacity is not present in the link", null , d->allLinksPerQosOccupationAndQosViolationMap.getOrDefault(d, new TreeMap<> ()).values().stream().mapToDouble(ee->ee.getSecond()).sum() , AGTYPE.SUMDOUBLE , e->allLinksPerQosOccupationAndQosViolationMap.getOrDefault(e, new TreeMap<> ()).values().stream().mapToDouble(ee->ee.getSecond()).sum() == 0? null : Color.RED));
		      res.add(new AjtColumnInfo<Link>(this , String.class, null , "QoS scheduling" , "The scheduling configuration for the link QoS enforcement. For each QoS type, the priority assigned (lower better), and the maximum link utilization allowed for it", null , d->d.getQosTypePriorityAndMaxLinkUtilizationMap().toString() , AGTYPE.NOAGGREGATION, null));
		      res.add(new AjtColumnInfo<Link>(this , NetworkElement.class, null , "Lp request coupled", "The lightpath request that is coupled to this IP link, if any", null , d->toWIpLink.apply(d).isCoupledtoLpRequest()? toWIpLink.apply(d).getCoupledLpRequest().getNe () : null , AGTYPE.NOAGGREGATION , null));
		      res.add(new AjtColumnInfo<Link>(this , Double.class, null , "Nominal cap. (Gbps)", "Nominal capacity of the IP link. If a bundle, the sum of the nominal capacities of the members. If coupled to a lightpath, the lightpath line rate. If not, a user-defined value", (d,val)->{ final WIpLink e = toWIpLink.apply(d); if (!e.isBundleOfIpLinks() && !e.isCoupledtoLpRequest()) toWIpLink.apply(d).setNominalCapacityGbps((Double) val); }, d->toWIpLink.apply(d).getNominalCapacityGbps() , AGTYPE.SUMDOUBLE , null));
		      res.add(new AjtColumnInfo<Link>(this , Double.class, null , "Current cap. (Gbps)", "Current capacity of the IP link. If a bundle, the sum of the current capacities of the members. If coupled to a lightpath, the lightpath line rate if up, or zero if down.", null , d->toWIpLink.apply(d).getCurrentCapacityGbps() , AGTYPE.SUMDOUBLE , null));
		      res.add(new AjtColumnInfo<Link>(this , Double.class, null , "Occupied cap. (Gbps)", "Occupied capacity of the IP link.", null , d->toWIpLink.apply(d).getCarriedTrafficGbps() , AGTYPE.SUMDOUBLE , null));
		      res.add(new AjtColumnInfo<Link>(this , Double.class, null , "Utilization (%)", "IP link utilization (occupied capacity vs. current capacity)", null , d->toWIpLink.apply(d).getCurrentUtilization() , AGTYPE.MAXDOUBLE , null));
		      res.add(new AjtColumnInfo<Link>(this , Double.class, null , "Length (km)", "IP link length in km, considering the worst case WDM layer propagation, and worst case IP member propagation if is a bundle. Can be edited: the user-defined value would be then used if the IP link is not coupled to a lightpath, and is not a bundle", (d,val)->toWIpLink.apply(d).setLengthIfNotCoupledInKm((Double) val) , d->toWIpLink.apply(d).getWorstCaseLengthInKm()  , AGTYPE.SUMDOUBLE, null));
		      res.add(new AjtColumnInfo<Link>(this , Double.class, null , "Latency (ms)", "IP link propagation delay in ms, considering the worst case WDM layer propagation, and worst case IP member propagation if is a bundle.", null , d->toWIpLink.apply(d).getWorstCasePropagationDelayInMs() , AGTYPE.MAXDOUBLE, null));
		      res.add(new AjtColumnInfo<Link>(this , String.class, null , "Type", "Indicates if the link is a Link Aggregation Group of other IP links, or a LAG member, or a regular IP link (not a bundle of IP links, nor a LAG member).", null , d->toWIpLink.apply(d).isBundleOfIpLinks()? "LAG" : (toWIpLink.apply(d).isBundleMember()? "LAG-member" : "Regular IP link") , AGTYPE.NOAGGREGATION, null));
		      res.add(new AjtColumnInfo<Link>(this , NetworkElement.class, null , "Parent LAG", "If the IP link is member of a LAG, indicates the parent LAG IP link.", null , d->toWIpLink.apply(d).isBundleMember()? toWIpLink.apply(d).getBundleParentIfMember().getNe () : "--" , AGTYPE.NOAGGREGATION, null));
		      res.add(new AjtColumnInfo<Link>(this , Collection.class, null , "LAG members", "If the IP link is a LAG bundle, this column links to the LAG members.", null , d->toWIpLink.apply(d).isBundleOfIpLinks()? toWIpLink.apply(d).getBundledIpLinks().stream().map(e->e.getNe()).collect (Collectors.toList()) : "--" , AGTYPE.NOAGGREGATION, null));
		      res.add(new AjtColumnInfo<Link>(this , Double.class, null , "IGP weitgh", "The strictly positive weight to be used for IGP routing calculations", (d,val)->toWIpLink.apply(d).setIgpWeight((Double) val) , d->toWIpLink.apply(d).getIgpWeight() , AGTYPE.NOAGGREGATION, null));
		      res.add(new AjtColumnInfo<Link>(this , Collection.class, null , "Trav. Unicast demands", "Unicast demands routed through this IP link (empty for bundle members)", null , d->toWIpLink.apply(d).getTraversingIpUnicastDemands().stream().map(e->e.getNe()).collect(Collectors.toList()) , AGTYPE.NOAGGREGATION, null));
		      res.add(new AjtColumnInfo<Link>(this , Collection.class, null , "Trav. IP connections", "IP source routed connections routed through this IP link (empty for bundle members)", null , d->toWIpLink.apply(d).getTraversingIpUnicastDemands().stream().map(e->e.getNe()).collect(Collectors.toList()) , AGTYPE.NOAGGREGATION, null));
		      res.add(new AjtColumnInfo<Link>(this , Collection.class, null , "Trav. SCs", "Service chains routed through this IP link (empty for bundle members)", null , d->toWIpLink.apply(d).getTraversingServiceChains().stream().map(e->e.getNe()).collect(Collectors.toList()) , AGTYPE.NOAGGREGATION, null));

		      res.addAll(AdvancedJTable_demand.getMonitoringAndTrafficEstimationColumns(this).stream().map(c->(AjtColumnInfo<Link>)(AjtColumnInfo<?>)c).collect(Collectors.toList()));
    	}
    	else
    	{
    		final OpticalSpectrumManager ospec = OpticalSpectrumManager.createFromRegularLps(wNet);
    		final OpticalSimulationModule osim = new OpticalSimulationModule (wNet);
    		osim.updateAllPerformanceInfo();
    		
		      res.add(new AjtColumnInfo<Link>(this , Double.class, null , "Length (km)", "WDM link length in km", (d,val)->toWFiber.apply(d).setLenghtInKm((Double) val) , d->toWFiber.apply(d).getLengthInKm()  , AGTYPE.SUMDOUBLE, null));
		      res.add(new AjtColumnInfo<Link>(this , Double.class, null , "Latency (ms)", "WDM link latency in ms", null , d->toWFiber.apply(d).getPropagationDelayInMs()  , AGTYPE.MAXDOUBLE, null));
		      res.add(new AjtColumnInfo<Link>(this , Collection.class, null , "SRGs", "The SRGs that this fiber link belongs to", null , d->toWFiber.apply(d).getSrgsThisElementIsAssociatedTo().stream().map(e->e.getNe()).collect(Collectors.toList()) , AGTYPE.NOAGGREGATION , null));
		      res.add(new AjtColumnInfo<Link>(this , Boolean.class, null , "Up?", "", (d,val)->
		      {
		          final boolean isLinkUp = (Boolean) val;
		          if (isLinkUp) toWFiber.apply(d).setAsUp(); else toWFiber.apply(d).setAsDown(); 
		      } , d->toWFiber.apply(d).isUp() , AGTYPE.COUNTTRUE , e->e.isUp()? null : Color.RED));
		      //fibras..
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, null , "Freq. ranges (THz)", "The ranges of the frequencies that are valid in this fiber, e.g. because of affecting amplifiers limits and/or fiber limits", null , d->
    	      {
    	    	  return toWFiber.apply(d).getValidOpticalSlotRanges().stream().
    	    			  map(p->df.format(OpticalSimulationModule.getLowestFreqfSlotTHz(p.getFirst())) + " - " + df.format(OpticalSimulationModule.getHighestFreqfSlotTHz(p.getSecond()))).
    	    			  collect(Collectors.joining(", "));
    	      }, AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, null , "Spectrum (THz)", "The total available spectrum in the fiber to be used by WDM channels", null , d->
    	      {
    	    	  final int numChannels = toWFiber.apply(d).getNumberOfValidOpticalChannels();
    	    	  return df.format(numChannels * WNetConstants.OPTICALSLOTSIZE_GHZ / 1000.0);
    	      }, AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Fiber physical coefs.") , "Coef. CD ps/nm/km", "Chromatic disperion coefficient in ps/nm/km, assumed to be the same in all the wavelengths", (d,val)->{ final WFiber e = toWFiber.apply(d); e.setChromaticDispersionCoeff_psPerNmKm((Double)val); }, d->toWFiber.apply(d).getChromaticDispersionCoeff_psPerNmKm() , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Fiber physical coefs.") , "Coef. PMD-Q ps/sqrt(km)", "PMD fiber coefficient, typically called Link Design Value, or PMD-Q. Measured in ps per square root of km of fiber", (d,val)->{ final WFiber e = toWFiber.apply(d); e.setPmdLinkDesignValueCoeff_psPerSqrtKm((Double)val); }, d->toWFiber.apply(d).getPmdLinkDesignValueCoeff_psPerSqrtKm() , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Fiber physical coefs.") , "Coef. Attenuation dB/km", "Fiber attenuation coefficient, measured in dB/km, assumed to be the same for all the wavelengths", (d,val)->{ final WFiber e = toWFiber.apply(d); e.setAttenuationCoefficient_dbPerKm((Double)val); }, d->toWFiber.apply(d).getAttenuationCoefficient_dbPerKm() , AGTYPE.NOAGGREGATION , null));

    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Optical signals") , "# Valid slots", "Number of valid slots (each of " + WNetConstants.OPTICALSLOTSIZE_GHZ + " GHz) in this fiber", null , d->ospec.getIdleOpticalSlotIds(toWFiber.apply(d)).size() , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Optical signals") , "# Occupied slots", "Number of occupied slots (each of " + WNetConstants.OPTICALSLOTSIZE_GHZ + " GHz) in this fiber", null , d->ospec.getOccupiedOpticalSlotIds(toWFiber.apply(d)).size() , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Optical signals") , "# Idle slots", "Number of idle slots (each of " + WNetConstants.OPTICALSLOTSIZE_GHZ + " GHz) in this fiber", null , d->ospec.getIdleOpticalSlotIds(toWFiber.apply(d)).size() , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Optical signals") , "# Clashing slots", "Number of slots (each of " + WNetConstants.OPTICALSLOTSIZE_GHZ + " GHz) occupied by two or more lightpaths, whose signal would be destroyed", null , d->ospec.getNumberOfClashingOpticalSlotIds(toWFiber.apply(d)) , AGTYPE.NOAGGREGATION , d->ospec.getNumberOfClashingOpticalSlotIds(toWFiber.apply(d)) > 0? Color.RED : null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Optical signals") , "Power at input (dBm)", "Total power at the start of this WDM fiber (output of previous OADM)", null , d->osim.getTotalPowerAtFiberEnds_dBm(toWFiber.apply(d)).getFirst() , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Optical signals") , "Power at output (dBm)", "Total power at the end of this WDM fiber (input of next OADM)", null , d->osim.getTotalPowerAtFiberEnds_dBm(toWFiber.apply(d)).getSecond() , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Optical signals") , "Net gain (dB)" , "Net gain of this fiber link, considering effect of line amplifiers and fiber attenuation", null , d->toWFiber.apply(d).getNetGain_dB() , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Optical signals") , "Net CD (ps/nm)" , "Net accummulated chromatic dispersion in the WDM link, considering fiber CD coefficient, and potnetial compensation in the line amplifiers", null , d->toWFiber.apply(d).getAccumulatedChromaticDispersion_psPerNm() , AGTYPE.NOAGGREGATION , null));
    	
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Line amplifiers") , "# OLAs", "Number of optical line amplifiers. Nota that each OLA can have chromatic dispersion compensation", null , d->toWFiber.apply(d).getNumberOfOpticalLineAmplifiersTraversed() , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Line amplifiers") , "OLA pos (km)", "Positions of OLAs, in km from the fiber start", null , d->toWFiber.apply(d).getAmplifierPositionsKmFromOrigin_km().stream().map(e->df.format(e)).collect(Collectors.joining(" ")) , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Line amplifiers") , "OLA gains (dB)", "Gains in dB of the OLAs", null , d->toWFiber.apply(d).getAmplifierGains_dB().stream().map(e->df.format(e)).collect(Collectors.joining(" ")) , AGTYPE.NOAGGREGATION , d->toWFiber.apply(d).isOkAllGainsOfLineAmplifiers()? null : Color.RED));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Line amplifiers") , "OLA Min gains (dB)", "Minimum gains acceptable for the OLAs", null , d->toWFiber.apply(d).getAmplifierMinAcceptableGains_dB().stream().map(e->df.format(e)).collect(Collectors.joining(" ")) , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Line amplifiers") , "OLA Max gains (dB)", "Maximum gains acceptable for the OLAs", null , d->toWFiber.apply(d).getAmplifierMaxAcceptableGains_dB().stream().map(e->df.format(e)).collect(Collectors.joining(" ")) , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Line amplifiers") , "OLA NFs (dB)", "Noise factors in dB of the OLAs", null , d->toWFiber.apply(d).getAmplifierNoiseFactor_dB().stream().map(e->df.format(e)).collect(Collectors.joining(" ")) , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Line amplifiers") , "OLA CD (ps/nm)", "Chromatic dispersion compensation inside this OLA if any", null , d->toWFiber.apply(d).getAmplifierCdCompensarion_psPerNm().stream().map(e->df.format(e)).collect(Collectors.joining(" ")) , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Line amplifiers") , "OLA PMD (ps)", "PMD factor for this OLA", null , d->toWFiber.apply(d).getAmplifierPmd_ps().stream().map(e->df.format(e)).collect(Collectors.joining(" ")) , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Line amplifiers") , "OLA input power (dBm)", "Total power at the input of the OLAs", null , d->osim.getTotalPowerAtAmplifierInputs_dBm(toWFiber.apply(d)).stream().map(e->df.format(e)).collect(Collectors.joining(" ")) , AGTYPE.NOAGGREGATION , d->osim.isOkOpticalPowerAtAmplifierInputAllOlas(toWFiber.apply(d))? null : Color.RED));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Line amplifiers") , "OLA output power (dBm)", "Total power at the output of the OLAs", null , d->osim.getTotalPowerAtAmplifierInputs_dBm(toWFiber.apply(d)).stream().map(e->df.format(e)).collect(Collectors.joining(" ")) , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Line amplifiers") , "OLA Min input power (dBm)", "Minimum acceptable power for the OLAs", null , d->toWFiber.apply(d).getAmplifierMinAcceptableInputPower_dBm().stream().map(e->df.format(e)).collect(Collectors.joining(" ")) , AGTYPE.NOAGGREGATION , null));
    	      res.add(new AjtColumnInfo<Link>(this , Double.class, Arrays.asList("Line amplifiers") , "OLA Max input power (dBm)", "Maximum acceptable power for the OLAs", null , d->toWFiber.apply(d).getAmplifierMaxAcceptableInputPower_dBm().stream().map(e->df.format(e)).collect(Collectors.joining(" ")) , AGTYPE.NOAGGREGATION , null));
    	}
      return res;
  	}

    @Override
    public List<AjtRcMenu> getNonBasicRightClickMenusInfo()
    {
    	final NetPlan np = callback.getDesign();
        final List<AjtRcMenu> res = new ArrayList<> ();
    	final WNet wNet = new WNet (callback.getDesign()); 
    	final boolean isIpLayer = getTableNetworkLayer().equals(wNet.getIpLayer().getNe());
    	final boolean isWdmLayer = getTableNetworkLayer().equals(wNet.getWdmLayer().getNe());
    	assert isIpLayer || isWdmLayer;
    	assert !(isIpLayer && isWdmLayer);
    	final Function<Link,WFiber> toWFiber = d -> (WFiber) wNet.getWElement(d).get();
    	final Function<Link,WIpLink> toWIpLink = d ->(WIpLink) wNet.getWElement(d).get();
    	final Function<Link,Boolean> isWIpLink = d -> { final WTYPE y = wNet.getWType(d).orElse (null); return y == null? false : y.isIpLink(); };
		final Function<String,Optional<WNode>> nodeByName = st -> 
		{
    		WNode a = wNet.getNodeByName(st).orElse(null);
    		if (a == null) a = wNet.getNodes().stream().filter(n->n.getName().equalsIgnoreCase(st)).findFirst().orElse(null);
    		return Optional.ofNullable(a);
		};



    	if (isIpLayer)
    	{
            res.add(new AjtRcMenu("Add bidirectional IP link", e->
            {
              DialogBuilder.launch(
              "Add IP link" , 
              "Please introduce the information required.", 
              "", 
              this, 
              Arrays.asList(
              		InputForDialog.inputTfString("Input node name", "Introduce the name of the input node", 10, ""),
              		InputForDialog.inputTfString("End node name", "Introduce the name of the end node", 10, ""),
              		InputForDialog.inputTfDouble ("Line rate (Gbps)", "Introduce the line rate in Gbps of the IP link", 10, 100.0)
              		),
              (list)->
              	{
            		final String aName  = (String) list.get(0).get();
            		final String bName  = (String) list.get(1).get();
            		final double rateGbps = (Double) list.get(2).get();
            		final WNode a = nodeByName.apply(aName).orElse(null);
            		final WNode b = nodeByName.apply(bName).orElse(null);
            		if (a == null || b == null) throw new Net2PlanException("Unkown node name. " + (a == null? aName : bName));
            		wNet.addIpLinkBidirectional(a, b, rateGbps);
              	}
              );
            }
            , (a,b)->true, null));
            
            res.add(new AjtRcMenu("Bundle selected IP links when possible", e->
            {
            	final Map<Pair<WNode,WNode>,SortedSet<WIpLink>> selectedNonBundlesLowHigh = new HashMap<> ();
            	for (Link d : getSelectedElements())
            	{
            		final WIpLink ee = toWIpLink.apply(d);
            		if (ee.isBundleOfIpLinks()) continue;
            		assert ee.isBidirectional();
            		if (ee.getId() < ee.getBidirectionalPair().getId()) selectedNonBundlesLowHigh.add(ee); else selectedNonBundlesLowHigh.add(ee.getBidirectionalPair());
            	}
            	
              DialogBuilder.launch(
              "Add IP link" , 
              "Please introduce the information required.", 
              "", 
              this, 
              Arrays.asList(
              		InputForDialog.inputTfString("Input node name", "Introduce the name of the input node", 10, ""),
              		InputForDialog.inputTfString("End node name", "Introduce the name of the end node", 10, ""),
              		InputForDialog.inputTfDouble ("Line rate (Gbps)", "Introduce the line rate in Gbps of the IP link", 10, 100.0)
              		),
              (list)->
              	{
            		final String aName  = (String) list.get(0).get();
            		final String bName  = (String) list.get(1).get();
            		final double rateGbps = (Double) list.get(2).get();
            		final WNode a = nodeByName.apply(aName).orElse(null);
            		final WNode b = nodeByName.apply(bName).orElse(null);
            		if (a == null || b == null) throw new Net2PlanException("Unkown node name. " + (a == null? aName : bName));
            		wNet.addIpLinkBidirectional(a, b, rateGbps);
              	}
              );
            }
            , (a,b)->true, null));
            
            
    		
    	}
    	
        res.add(new AjtRcMenu("Add link", e->AdvancedJTable_demand.createLinkDemandGUI(NetworkElementType.LINK, getTableNetworkLayer () , callback), (a,b)->true, null));
        res.add(new AjtRcMenu("Remove selected links", e->getSelectedElements().forEach(dd->((Link)dd).remove()) , (a,b)->b>0, null));
        res.add(new AjtRcMenu("Generate full-mesh", null , (a, b)->true, Arrays.asList( 
        		new AjtRcMenu("Link length as Euclidean distance", e->new FullMeshTopology(this , callback, true), (a, b)->true, null),
        		new AjtRcMenu("Link length as Haversine distance", e->new FullMeshTopology(this , callback, false), (a, b)->true, null)
        		)));
        
        res.add(new AjtRcMenu("Arrange selected links in bidirectional pairs", e->
        {
        	final SortedSet<Link> nonBidiLinks = getSelectedElements().stream().filter(ee->!ee.isBidirectional()).collect(Collectors.toCollection(TreeSet::new));
        	final Map<Pair<Node,Node> , Link> nodePair2link = new HashMap<>();
        	for (Link ee : nonBidiLinks)
        	{
        		final Pair<Node,Node> pair = Pair.of(ee.getOriginNode() , ee.getDestinationNode());
        		if (nodePair2link.containsKey(pair)) throw new Net2PlanException ("At most one link per node pair is allowed");
        		nodePair2link.put(pair, ee);
        	}
        	for (Link ee : nonBidiLinks)
        	{
        		if (ee.isBidirectional()) continue;
        		final Link opposite = nodePair2link.get(Pair.of(ee.getDestinationNode(), ee.getOriginNode()));
        		if (opposite == null) continue;
        		if (opposite.isBidirectional()) continue;
        		ee.setBidirectionalPair(opposite);
        	}
        }
        , (a,b)->b>0, null));

        res.add(new AjtRcMenu("Decouple selected links", e->getSelectedElements().forEach(dd->((Link)dd).decouple()) , (a,b)->b>0, null));
        res.add(new AjtRcMenu("Create lower layer coupled demand from uncoupled links in selection", e->
        {
            Collection<Long> layerIds = np.getNetworkLayerIds();
            final JComboBox<StringLabeller> layerSelector = new WiderJComboBox();
            for (long layerId : layerIds)
            {
                final String layerName = np.getNetworkLayerFromId(layerId).getName();
                String layerLabel = "Layer " + layerId;
                if (!layerName.isEmpty()) layerLabel += " (" + layerName + ")";
                layerSelector.addItem(StringLabeller.of(layerId, layerLabel));
            }

            layerSelector.setSelectedIndex(0);

            JPanel pane = new JPanel();
            pane.add(new JLabel("Select layer: "));
            pane.add(layerSelector);

            while (true)
            {
                int result = JOptionPane.showConfirmDialog(null, pane, "Please select the lower layer to create the demand", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (result != JOptionPane.OK_OPTION) return;
                final long layerId = (long) ((StringLabeller) layerSelector.getSelectedItem()).getObject();
                for (Link link : getSelectedElements ())
                    if (!link.isCoupled())
                        link.coupleToNewDemandCreated(np.getNetworkLayerFromId(layerId) , RoutingType.SOURCE_ROUTING);
                break;
            }
        } , (a,b)->b>0, null));

        res.add(new AjtRcMenu("Couple link to lower layer demand", e->
        {
            Collection<Long> layerIds = np.getNetworkLayerIds();
            final JComboBox<StringLabeller> layerSelector = new WiderJComboBox();
            final JComboBox<StringLabeller> demandSelector = new WiderJComboBox();
            for (long layerId : layerIds)
            {
                if (layerId == this.getTableNetworkLayer().getId()) continue;

                final String layerName = np.getNetworkLayerFromId(layerId).getName();
                String layerLabel = "Layer " + layerId;
                if (!layerName.isEmpty()) layerLabel += " (" + layerName + ")";
                layerSelector.addItem(StringLabeller.of(layerId, layerLabel));
            }

            layerSelector.addItemListener(e1 ->
            {
                if (layerSelector.getSelectedIndex() >= 0)
                {
                    long selectedLayerId = (Long) ((StringLabeller) layerSelector.getSelectedItem()).getObject();

                    demandSelector.removeAllItems();
                    for (Demand demand : np.getDemands(np.getNetworkLayerFromId(selectedLayerId)))
                    {
                        if (demand.isCoupled()) continue;
                        long ingressNodeId = demand.getIngressNode().getId();
                        long egressNodeId = demand.getEgressNode().getId();
                        String ingressNodeName = demand.getIngressNode().getName();
                        String egressNodeName = demand.getEgressNode().getName();

                        demandSelector.addItem(StringLabeller.unmodifiableOf(demand.getId(), "d" + demand.getId() + " [n" + ingressNodeId + " (" + ingressNodeName + ") -> n" + egressNodeId + " (" + egressNodeName + ")]"));
                    }
                }

                if (demandSelector.getItemCount() == 0)
                {
                    demandSelector.setEnabled(false);
                } else
                {
                    demandSelector.setSelectedIndex(0);
                    demandSelector.setEnabled(true);
                }
            });

            layerSelector.setSelectedIndex(-1);
            layerSelector.setSelectedIndex(0);

            JPanel pane = new JPanel(new MigLayout("", "[][grow]", "[][]"));
            pane.add(new JLabel("Select layer: "));
            pane.add(layerSelector, "growx, wrap");
            pane.add(new JLabel("Select demand: "));
            pane.add(demandSelector, "growx, wrap");

            while (true)
            {
                int result = JOptionPane.showConfirmDialog(null, pane, "Please select the lower layer demand", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (result != JOptionPane.OK_OPTION) return;
                final Long demandId = (Long) ((StringLabeller) demandSelector.getSelectedItem()).getObject();
                final Demand demand = np.getDemandFromId(demandId);
                final Link link = getSelectedElements().first();
                demand.coupleToUpperOrSameLayerLink(link);
                break;
            }
        } , (a,b)->b>0, null));

        res.add(new AjtRcMenu("Set selected links capacity", null , (a,b)->b>0, Arrays.asList(
        		new AjtRcMenu("As constant value", e->
                {
                    DialogBuilder.launch(
                            "Set selected links capacity" , 
                            "Please introduce the link capacity. Negative values are not allowed. The capacity will be assigned to not coupled links", 
                            "", 
                            this, 
                            Arrays.asList(InputForDialog.inputTfDouble("Link capacity (" + getTableNetworkLayer().getLinkCapacityUnits() + ")", "Introduce the link capacity", 10, 0.0)),
                            (list)->
                            	{
                            		final double newLinkCapacity = (Double) list.get(0).get();
                            		getSelectedElements().stream().filter(ee->!ee.isCoupled()).forEach(ee->ee.setCapacity(newLinkCapacity));
                            	}
                            );
                } , (a,b)->b>0, null),
        		new AjtRcMenu("To match a given utilization", e->
                {
                    DialogBuilder.launch(
                            "Set selected links capacity to match utilization" , 
                            "Please introduce the link target utilization. Negative values are not allowed. The capacity will be assigned to not coupled links", 
                            "", 
                            this, 
                            Arrays.asList(
                            		InputForDialog.inputTfDouble("Link utilization", "Introduce the link utilization", 10, 0.9),
                            		InputForDialog.inputTfDouble("Capacity module (if > 0, capacities are multiple of this)", "Introduce the capacity module, so the link capacity will be the lowest multiple of this quantity that matches the required utilization limit. A non-positive value means no modular capacity is applied", 10, 100.0),
                            		InputForDialog.inputCheckBox("Bidirectional modules", "If checked, if links are bidirectional, the module will have a capacity which is the largest between the traffic in both directions", true, null)
                            		),
                            (list)->
                            	{
                            		final double newLinkUtilization = (Double) list.get(0).get();
                            		final double capacityModule = (Double) list.get(1).get();
                            		final boolean isBidirectional = (Boolean) list.get(2).get();
                            		if (newLinkUtilization <= 0) throw new Net2PlanException ("Link utilization must be positive");
                            		getSelectedElements().stream().filter(ee->!ee.isCoupled()).forEach(ee->
                            		{
                            			double occupiedCap = isBidirectional && ee.isBidirectional ()? Math.max (ee.getOccupiedCapacity() , ee.getBidirectionalPair ().getOccupiedCapacity ()) : ee.getCapacity ();
                            			if (newLinkUtilization > 0) occupiedCap /= newLinkUtilization;
                            			if (capacityModule > 0) occupiedCap = capacityModule * Math.ceil(occupiedCap / capacityModule);
                            			ee.setCapacity(occupiedCap);
                            		});
                            	}
                            );
                } , (a,b)->b>0, null)
        		)));

        res.add(new AjtRcMenu("Set selected links length as", null , (a,b)->b>0, Arrays.asList(
        		new AjtRcMenu("Constant value", e->
                {
                    DialogBuilder.launch(
                            "Set selected links length (km)" , 
                            "Please introduce the link length. Negative values are not allowed. The length will be assigned to not coupled links", 
                            "", 
                            this, 
                            Arrays.asList(InputForDialog.inputTfDouble("Link length (km)", "Introduce the link length", 10, 0.0)),
                            (list)->
                            	{
                            		final double newLinkLength = (Double) list.get(0).get();
                            		getSelectedElements().stream().filter(ee->!ee.isCoupled()).forEach(ee->ee.setLengthInKm(newLinkLength));
                            	}
                            );
                } , (a,b)->b>0, null) , 
        		
        		new AjtRcMenu("Scaled version of current lengths", e->
                {
                    DialogBuilder.launch(
                            "Scale selected links length (km)" , 
                            "Please introduce the scaling factor for which the link lengths will be multiplied. Negative values are not allowed. The length will be assigned to not coupled links", 
                            "", 
                            this, 
                            Arrays.asList(InputForDialog.inputTfDouble("Scaling factor", "Introduce the scaling factor", 10, 1.0)),
                            (list)->
                            	{
                            		final double scalingFactor = (Double) list.get(0).get();
                            		getSelectedElements().stream().filter(ee->!ee.isCoupled()).forEach(ee->ee.setLengthInKm(scalingFactor * ee.getLengthInKm()));
                            	}
                            );
                } , (a,b)->b>0, null) ,         		
        		new AjtRcMenu("As the euclidean node pair distance", e->
                {
            		getSelectedElements().stream().filter(ee->!ee.isCoupled()).forEach(ee->ee.setLengthInKm(np.getNodePairEuclideanDistance(ee.getOriginNode(), ee.getDestinationNode())));
                } , (a,b)->b>0, null) ,
        		
        		new AjtRcMenu("As the harversine node pair distance", e->
                {
            		getSelectedElements().stream().filter(ee->!ee.isCoupled()).forEach(ee->ee.setLengthInKm(np.getNodePairHaversineDistanceInKm(ee.getOriginNode(), ee.getDestinationNode())));
                } , (a,b)->b>0, null)
        		)));

        res.add(new AjtRcMenu("Set IGP link weights of selected links", null , (a, b)->true, Arrays.asList( 
        		new AjtRcMenu("as constant value", e->
                {
                    DialogBuilder.launch(
                            "Set IGP weight as constant value" , 
                            "Please introduce the IGP weight for the selected links. Non-positive values are not allowed.", 
                            "", 
                            this, 
                            Arrays.asList(InputForDialog.inputTfDouble("IGP weight", "Introduce the IGP weight for selected links", 10, 1.0)),
                            (list)->
                            	{
                            		final double newLinWeight = (Double) list.get(0).get();
                            		if (newLinWeight <= 0) throw new Net2PlanException ("IGP weights must be strictly positive");
                            		getSelectedElements().stream().forEach(ee->IPUtils.setLinkWeight(ee, newLinWeight));
                            		IPUtils.setECMPForwardingRulesFromLinkWeights(np, null);
                            	}
                            );
                } , (a,b)->b>0, null) , 
        		new AjtRcMenu("proportional to link latency", e->
                {
                    DialogBuilder.launch(
                            "Set IGP weight proportional to latency" , 
                            "Please introduce the information required for computing the IGP weight.", 
                            "", 
                            this, 
                            Arrays.asList(
                            		InputForDialog.inputTfDouble("IGP weight to links of minimum latency", "Introduce the IGP weight to assign to the links of the minimum latency among the selected ones. IGP weight must be strictly positive.", 10, 1.0),
                            		InputForDialog.inputTfDouble("IGP weight to links of maximum latency", "Introduce the IGP weight to assign to the links of the maximum latency among the selected ones, IGP weight must be strictly positive.", 10, 10.0),
                            		InputForDialog.inputCheckBox("Round the weights to closest integer?", "If cheked, the weights will be rounded to the closest integer, with a minimum value of one.", true, null)
                            		),
                            (list)->
                            	{
                                	final double minLatency = getSelectedElements().stream().mapToDouble(ee->ee.getPropagationDelayInMs()).min().orElse(0.0);
                                	final double maxLatency = getSelectedElements().stream().mapToDouble(ee->ee.getPropagationDelayInMs()).max().orElse(0.0);
                                	final double difLatency = maxLatency - minLatency;
                            		final double minLatencyWeight = (Double) list.get(0).get();
                            		final double maxLatencyWeight = (Double) list.get(1).get();
                            		final boolean roundToInteger = (Boolean) list.get(2).get();
                            		final double difWeight = maxLatencyWeight - minLatencyWeight;
                            		if (minLatencyWeight <= 0 || maxLatencyWeight <= 0) throw new Net2PlanException ("Weights must be positive");
                        			for (Link ee : getSelectedElements())
                        			{
                        				double linkWeight = difLatency == 0? minLatencyWeight : minLatencyWeight + difWeight * (ee.getPropagationDelayInMs() - minLatency) / difLatency;  
                        				if (roundToInteger) linkWeight = Math.max(1, Math.round(linkWeight));
                        				if (linkWeight <= 0) throw new Net2PlanException ("Weights must be positive");
                        				IPUtils.setLinkWeight(ee, linkWeight);
                        			}
                            		IPUtils.setECMPForwardingRulesFromLinkWeights(np, null);
                            	}
                            );
                } , (a,b)->b>0, null) ,
        		new AjtRcMenu("inversely proportional to capacity", e->
                {
                    DialogBuilder.launch(
                            "Set IGP weight inversely proportional to link capacity" , 
                            "Please introduce the information required for computing the IGP weight.", 
                            "", 
                            this, 
                            Arrays.asList(
                            		InputForDialog.inputTfDouble("Reference bandwidth (to assign IGP weight one)", "Introduce the reference bandwidth (REFBW), measured in the same units as the traffic. IGP weight of link of capacity c is REFBW/c. REFBW must be positive", 10, 0.1),
                            		InputForDialog.inputCheckBox("Round the weights to closest integer?", "If cheked, the weights will be rounded to the closest integer, with a minimum value of one.", true, null)
                            		),
                            (list)->
                            	{
                            		final double refBw = (Double) list.get(0).get();
                            		final boolean roundToInteger = (Boolean) list.get(1).get();
                            		if (refBw <= 0) throw new Net2PlanException ("The reference bandwidth must be positive");
                        			for (Link ee : getSelectedElements())
                        			{
                        				double linkWeight = ee.getCapacity() == 0? Double.MAX_VALUE : refBw / ee.getCapacity();  
                        				if (roundToInteger) linkWeight = Math.max(1, Math.round(linkWeight));
                        				if (linkWeight <= 0) throw new Net2PlanException ("Weights must be positive");
                        				IPUtils.setLinkWeight(ee, linkWeight);
                        			}
                            		IPUtils.setECMPForwardingRulesFromLinkWeights(np, null);
                            	}
                            );
                } , (a,b)->b>0, null) 
        		)));

        res.add(new AjtRcMenu("Monitor/forecast...",  null , (a,b)->true, Arrays.asList(
                MonitoringUtils.getMenuAddSyntheticMonitoringInfo (this),
                MonitoringUtils.getMenuExportMonitoringInfo(this),
                MonitoringUtils.getMenuImportMonitoringInfo (this),
                MonitoringUtils.getMenuSetMonitoredTraffic(this),
                MonitoringUtils.getMenuSetTrafficPredictorAsConstantEqualToTrafficInElement (this),
                MonitoringUtils.getMenuAddLinkMonitoringInfoSimulatingTrafficVariations (this),
                MonitoringUtils.getMenuPercentileFilterMonitSamples (this) , 
                MonitoringUtils.getMenuCreatePredictorTraffic (this),
                MonitoringUtils.getMenuForecastDemandTrafficUsingGravityModel (this),
                MonitoringUtils.getMenuForecastDemandTrafficFromLinkInfo (this),
                MonitoringUtils.getMenuForecastDemandTrafficFromLinkForecast(this),
                new AjtRcMenu("Remove traffic predictors of selected elements", e->getSelectedElements().forEach(dd->((Link)dd).removeTrafficPredictor()) , (a,b)->b>0, null),
                new AjtRcMenu("Remove monitored/forecast stored information of selected elements", e->getSelectedElements().forEach(dd->((Link)dd).getMonitoredOrForecastedCarriedTraffic().removeAllValues()) , (a,b)->b>0, null),
                new AjtRcMenu("Remove monitored/forecast stored information...", null , (a,b)->b>0, Arrays.asList(
                        MonitoringUtils.getMenuRemoveMonitorInfoBeforeAfterDate (this , true) ,
                        MonitoringUtils.getMenuRemoveMonitorInfoBeforeAfterDate (this , false)
                		))
        		)));
        

        return res;
    }
    
    
    static class FullMeshTopology
    {
        private final GUINetworkDesign callback;
        private final boolean euclidean;
        private AdvancedJTable_networkElement table;
        
        public FullMeshTopology(AdvancedJTable_networkElement table , GUINetworkDesign callback, boolean euclidean)
        {
            this.callback = callback;
            this.euclidean = euclidean;
            this.table = table;
            create();
        }

        public void create()
        {
            assert callback != null;

            NetPlan netPlan = callback.getDesign();

            // Ask for current element removal
            if (netPlan.hasLinks(table.getTableNetworkLayer ()))
            {
                final int answer = JOptionPane.showConfirmDialog(null, "Remove all existing links?", "", JOptionPane.YES_NO_CANCEL_OPTION);
                if (answer == JOptionPane.CANCEL_OPTION || answer == JOptionPane.CLOSED_OPTION) return;
                if (answer == JOptionPane.OK_OPTION) netPlan.removeAllLinks(table.getTableNetworkLayer ());
            }

            for (long nodeId_1 : netPlan.getNodeIds())
            {
                for (long nodeId_2 : netPlan.getNodeIds())
                {
                    if (nodeId_1 >= nodeId_2) continue;
                    Node n1 = netPlan.getNodeFromId(nodeId_1);
                    Node n2 = netPlan.getNodeFromId(nodeId_2);

                    Pair<Link, Link> out = netPlan.addLinkBidirectional(n1, n2, 0, euclidean ? netPlan.getNodePairEuclideanDistance(n1, n2) : netPlan.getNodePairHaversineDistanceInKm(n1, n2), 200000, null);
                }
            }
            callback.getVisualizationState().recomputeCanvasTopologyBecauseOfLinkOrNodeAdditionsOrRemovals();
            callback.updateVisualizationAfterChanges();
            callback.addNetPlanChange();
        }

    }


    
}
