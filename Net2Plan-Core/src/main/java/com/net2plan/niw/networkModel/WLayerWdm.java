/*******************************************************************************
 * This program and the accompanying materials
 * are made available under the terms of the MIT License available at
 * https://opensource.org/licenses/MIT
 *******************************************************************************/

package com.net2plan.niw.networkModel;

import java.util.Optional;

import com.net2plan.interfaces.networkDesign.NetworkLayer;
import com.net2plan.niw.networkModel.WNetConstants.WTYPE;

public class WLayerWdm extends WAbstractNetworkElement
{
	final private NetworkLayer wdmLayer;

	WLayerWdm (NetworkLayer l) { super (l, Optional.empty()); this.wdmLayer = l; }

	@Override
	public NetworkLayer getNe() { return wdmLayer; }

	@Override
	void checkConsistency()
	{
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public WTYPE getWType() { return WTYPE.WLayerWdm; }
   
	
}
