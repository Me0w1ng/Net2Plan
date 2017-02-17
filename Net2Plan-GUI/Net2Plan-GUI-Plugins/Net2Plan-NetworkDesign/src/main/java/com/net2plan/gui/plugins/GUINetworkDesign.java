// TODO: Hacer los pick de demanda, ruta etc, cogiendo lo que hice the multilayer. Hasta que compile todo salvo OSM
// TODO: Con Jorge hacer lo de OSM
// TODO: Repaso de llamadas a metodos llaman a ICallback, uno a uno, depurando los updates.
// TODO: Mirar dentro de los metodos updates: hay que tocar tambien el layer chooser y quiza mas cosas visibles
// TODO: Pruebas y pruebas...

/*******************************************************************************


 *
 *
 * Copyright (c) 2015 Pablo Pavon Mariño.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Contributors:
 * Pablo Pavon Mariño - initial API and implementation
 ******************************************************************************/


package com.net2plan.gui.plugins;

import com.net2plan.gui.focusPane.FocusPane;
import com.net2plan.gui.offlineExecPane.OfflineExecutionPanel;
import com.net2plan.gui.onlineSimulationPane.OnlineSimulationPane;
import com.net2plan.gui.topologyPane.GUILink;
import com.net2plan.gui.topologyPane.GUINode;
import com.net2plan.gui.topologyPane.TopologyPanel;
import com.net2plan.gui.topologyPane.jung.JUNGCanvas;
import com.net2plan.gui.utils.visualizationControl.VisualizationState;
import com.net2plan.gui.utils.IVisualizationCallback;
import com.net2plan.gui.utils.ProportionalResizeJSplitPaneListener;
import com.net2plan.gui.utils.UndoRedoManager;
import com.net2plan.gui.viewEditTopolTables.ViewEditTopologyTablesPane;
import com.net2plan.gui.viewEditWindows.WindowController;
import com.net2plan.gui.viewReportsPane.ViewReportPane;
import com.net2plan.gui.whatIfAnalysisPane.WhatIfAnalysisPane;
import com.net2plan.interfaces.networkDesign.*;
import com.net2plan.internal.Constants.NetworkElementType;
import com.net2plan.internal.ErrorHandling;
import com.net2plan.internal.plugins.IGUIModule;
import com.net2plan.internal.plugins.ITopologyCanvas;
import com.net2plan.internal.sim.SimCore.SimState;
import com.net2plan.utils.Pair;
import com.net2plan.utils.Triple;
import com.net2plan.utils.gui.WindowUtils;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.collections15.BidiMap;
import org.apache.commons.collections15.bidimap.DualHashBidiMap;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;

/**
 * Targeted to evaluate the network designs generated by built-in or user-defined
 * static planning algorithms, deciding on aspects such as the network topology,
 * the traffic routing, link capacities, protection routes and so on. Algorithms
 * based on constrained optimization formulations (i.e. ILPs) can be fast-prototyped
 * using the open-source Java Optimization Modeler library, to interface
 * to a number of external solvers such as GPLK, CPLEX or IPOPT.
 */

/**
 * @author Pablo
 */
public class GUINetworkDesign extends IGUIModule implements IVisualizationCallback
{
    private final static String TITLE = "Offline network design & Online network simulation";
    private final static int MAXSIZEUNDOLISTCHANGES = 10;
    private final static int MAXSIZEUNDOLISTPICK = 10;

    private TopologyPanel topologyPanel;

    private FocusPane focusPanel;

    private ViewEditTopologyTablesPane viewEditTopTables;
    private ViewReportPane reportPane;
    private OfflineExecutionPanel executionPane;
    private OnlineSimulationPane onlineSimulationPane;
    private WhatIfAnalysisPane whatIfAnalysisPane;

    private VisualizationState vs;
    private UndoRedoManager undoRedoManager;

    private NetPlan currentNetPlan;

    /**
     * Default constructor.
     *
     * @since 0.2.0
     */
    public GUINetworkDesign()
    {
        this(TITLE);
    }


    /**
     * Constructor that allows set a title for the tool in the top section of the panel.
     *
     * @param title Title of the tool (null or empty means no title)
     * @since 0.2.0
     */
    public GUINetworkDesign(String title)
    {
        super(title);
    }

    @Override
    public WhatIfAnalysisPane getWhatIfAnalysisPane()
    {
        return whatIfAnalysisPane;
    }

    @Override
    public UndoRedoManager getUndoRedoNavigationManager()
    {
        return undoRedoManager;
    }

    @Override
    public void requestUndoAction()
    {
        if (inOnlineSimulationMode()) return;

        final Triple<NetPlan, BidiMap<NetworkLayer, Integer>, Map<NetworkLayer, Boolean>> back = undoRedoManager.getNavigationBackElement();
        if (back == null) return;
        this.currentNetPlan = back.getFirst();
        this.vs.setCanvasLayerVisibilityAndOrder(this.currentNetPlan, back.getSecond(), back.getThird());
        updateVisualizationAfterNewTopology();
    }

    @Override
    public void requestRedoAction()
    {
        if (inOnlineSimulationMode()) return;

        final Triple<NetPlan, BidiMap<NetworkLayer, Integer>, Map<NetworkLayer, Boolean>> forward = undoRedoManager.getNavigationForwardElement();
        if (forward == null) return;
        this.currentNetPlan = forward.getFirst();
        this.vs.setCanvasLayerVisibilityAndOrder(this.currentNetPlan, forward.getSecond(), forward.getThird());
        updateVisualizationAfterNewTopology();
    }

    @Override
    public void configure(JPanel contentPane)
    {
        this.currentNetPlan = new NetPlan();

        BidiMap<NetworkLayer, Integer> mapLayer2VisualizationOrder = new DualHashBidiMap<>();
        Map<NetworkLayer, Boolean> layerVisibilityMap = new HashMap<>();
        for (NetworkLayer layer : currentNetPlan.getNetworkLayers())
        {
            mapLayer2VisualizationOrder.put(layer, mapLayer2VisualizationOrder.size());
            layerVisibilityMap.put(layer, true);
        }
        this.vs = new VisualizationState(currentNetPlan, mapLayer2VisualizationOrder, layerVisibilityMap, MAXSIZEUNDOLISTPICK);

        topologyPanel = new TopologyPanel(this, JUNGCanvas.class);

        JPanel leftPane = new JPanel(new BorderLayout());
        JPanel logSection = configureLeftBottomPanel();
        if (logSection == null)
        {
            leftPane.add(topologyPanel, BorderLayout.CENTER);
        } else
        {
            JSplitPane splitPaneTopology = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            splitPaneTopology.setTopComponent(topologyPanel);
            splitPaneTopology.setBottomComponent(logSection);
            splitPaneTopology.addPropertyChangeListener(new ProportionalResizeJSplitPaneListener());
            splitPaneTopology.setBorder(new LineBorder(contentPane.getBackground()));
            splitPaneTopology.setOneTouchExpandable(true);
            splitPaneTopology.setDividerSize(7);
            leftPane.add(splitPaneTopology, BorderLayout.CENTER);
        }
        contentPane.add(leftPane, "grow");

        viewEditTopTables = new ViewEditTopologyTablesPane(GUINetworkDesign.this, new BorderLayout());

        reportPane = new ViewReportPane(GUINetworkDesign.this, JSplitPane.VERTICAL_SPLIT);

        setCurrentNetPlanDoNotUpdateVisualization(currentNetPlan);
        Pair<BidiMap<NetworkLayer, Integer>, Map<NetworkLayer, Boolean>> res = VisualizationState.generateCanvasDefaultVisualizationLayerInfo(getDesign());
        vs.setCanvasLayerVisibilityAndOrder(getDesign(), res.getFirst(), res.getSecond());

        /* Initialize the undo/redo manager, and set its initial design */
        this.undoRedoManager = new UndoRedoManager(this, MAXSIZEUNDOLISTCHANGES);
        this.undoRedoManager.addNetPlanChange();

        onlineSimulationPane = new OnlineSimulationPane(this);
        executionPane = new OfflineExecutionPanel(this);
        whatIfAnalysisPane = new WhatIfAnalysisPane(this);

        // Closing windows
        WindowUtils.clearFloatingWindows((JFrame) SwingUtilities.getWindowAncestor(this));

        final JTabbedPane tabPane = new JTabbedPane();
        tabPane.add(WindowController.WindowToTab.getTabName(WindowController.WindowToTab.network), viewEditTopTables);
        tabPane.add(WindowController.WindowToTab.getTabName(WindowController.WindowToTab.offline), executionPane);
        tabPane.add(WindowController.WindowToTab.getTabName(WindowController.WindowToTab.online), onlineSimulationPane);
        tabPane.add(WindowController.WindowToTab.getTabName(WindowController.WindowToTab.whatif), whatIfAnalysisPane);
        tabPane.add(WindowController.WindowToTab.getTabName(WindowController.WindowToTab.report), reportPane);

        // Installing customized mouse listener
        MouseListener[] ml = tabPane.getListeners(MouseListener.class);

        for (int i = 0; i < ml.length; i++)
        {
            tabPane.removeMouseListener(ml[i]);
        }

        // Left click works as usual, right click brings up a pop-up menu.
        tabPane.addMouseListener(new MouseAdapter()
        {
            public void mousePressed(MouseEvent e)
            {
                JTabbedPane tabPane = (JTabbedPane) e.getSource();

                int tabIndex = tabPane.getUI().tabForCoordinate(tabPane, e.getX(), e.getY());

                if (tabIndex >= 0 && tabPane.isEnabledAt(tabIndex))
                {
                    if (tabIndex == tabPane.getSelectedIndex())
                    {
                        if (tabPane.isRequestFocusEnabled())
                        {
                            tabPane.requestFocus();

                            tabPane.repaint(tabPane.getUI().getTabBounds(tabPane, tabIndex));
                        }
                    } else
                    {
                        tabPane.setSelectedIndex(tabIndex);
                    }

                    if (!tabPane.isEnabled() || SwingUtilities.isRightMouseButton(e))
                    {
                        final JPopupMenu popupMenu = new JPopupMenu();

                        final JMenuItem popWindow = new JMenuItem("Pop window out");
                        popWindow.addActionListener(e1 ->
                        {
                            final int selectedIndex = tabPane.getSelectedIndex();
                            final String tabName = tabPane.getTitleAt(selectedIndex);
                            final JComponent selectedComponent = (JComponent) tabPane.getSelectedComponent();

                            // Pops up the selected tab.
                            final WindowController.WindowToTab windowToTab = WindowController.WindowToTab.parseString(tabName);

                            if (windowToTab != null)
                            {
                                switch (windowToTab)
                                {
                                    case WindowToTab.offline:
                                        WindowController.buildOfflineWindow(selectedComponent);
                                        WindowController.showOfflineWindow(true);
                                        break;
                                    case WindowToTab.online:
                                        WindowController.buildOnlineWindow(selectedComponent);
                                        WindowController.showOnlineWindow(true);
                                        break;
                                    case WindowToTab.whatif:
                                        WindowController.buildWhatifWindow(selectedComponent);
                                        WindowController.showWhatifWindow(true);
                                        break;
                                    case WindowToTab.report:
                                        WindowController.buildReportWindow(selectedComponent);
                                        WindowController.showReportWindow(true);
                                        break;
                                    default:
                                        return;
                                }
                            }

                            tabPane.setSelectedIndex(0);
                        });

                        // Disabling the pop up button for the network state tab.
                        if (WindowController.WindowToTab.parseString(tabPane.getTitleAt(tabPane.getSelectedIndex())) == WindowController.WindowToTab.network)
                        {
                            popWindow.setEnabled(false);
                        }

                        popupMenu.add(popWindow);

                        popupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        // Building windows
        WindowController.buildTableControlWindow(tabPane);
        WindowController.showTablesWindow(false);

        addAllKeyCombinationActions();
        updateVisualizationAfterNewTopology();
    }


    private JPanel configureLeftBottomPanel()
    {
        this.focusPanel = new FocusPane(this);
        final JPanel focusPanelContainer = new JPanel(new BorderLayout());
        final JToolBar navigationToolbar = new JToolBar(JToolBar.VERTICAL);
        navigationToolbar.setRollover(true);
        navigationToolbar.setFloatable(false);
        navigationToolbar.setOpaque(false);

        final JButton btn_pickNavigationUndo, btn_pickNavigationRedo;

        btn_pickNavigationUndo = new JButton("");
        btn_pickNavigationUndo.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/undoPick.png")));
        btn_pickNavigationUndo.setToolTipText("Navigate back to the previous element picked");
        btn_pickNavigationRedo = new JButton("");
        btn_pickNavigationRedo.setIcon(new ImageIcon(TopologyPanel.class.getResource("/resources/gui/redoPick.png")));
        btn_pickNavigationRedo.setToolTipText("Navigate forward to the next element picked");

        final ActionListener action = e ->
        {
            Pair<NetworkElement, Pair<Demand, Link>> backOrForward;
            do
            {
                backOrForward = (e.getSource() == btn_pickNavigationUndo) ? GUINetworkDesign.this.getVisualizationState().getPickNavigationBackElement() : GUINetworkDesign.this.getVisualizationState().getPickNavigationForwardElement();
                if (backOrForward == null) break;
                final NetworkElement ne = backOrForward.getFirst(); // For network elements
                final Pair<Demand, Link> fr = backOrForward.getSecond(); // For forwarding rules
                if (ne != null)
                {
                    if (ne.getNetPlan() != GUINetworkDesign.this.getDesign()) continue;
                    if (ne.getNetPlan() == null) continue;
                    break;
                } else if (fr != null)
                {
                    if (fr.getFirst().getNetPlan() != GUINetworkDesign.this.getDesign()) continue;
                    if (fr.getFirst().getNetPlan() == null) continue;
                    if (fr.getSecond().getNetPlan() != GUINetworkDesign.this.getDesign()) continue;
                    if (fr.getSecond().getNetPlan() == null) continue;
                    break;
                } else break; // null,null => reset picked state
            } while (true);
            if (backOrForward != null)
            {
                if (backOrForward.getFirst() != null)
                    GUINetworkDesign.this.getVisualizationState().pickElement(backOrForward.getFirst());
                else if (backOrForward.getSecond() != null)
                    GUINetworkDesign.this.getVisualizationState().pickForwardingRule(backOrForward.getSecond());
                else GUINetworkDesign.this.getVisualizationState().resetPickedState();

                GUINetworkDesign.this.updateVisualizationAfterPick();
            }
        };

        btn_pickNavigationUndo.addActionListener(action);
        btn_pickNavigationRedo.addActionListener(action);

        btn_pickNavigationRedo.setFocusable(false);
        btn_pickNavigationUndo.setFocusable(false);

        navigationToolbar.add(btn_pickNavigationUndo);
        navigationToolbar.add(btn_pickNavigationRedo);

        final JScrollPane scPane = new JScrollPane(focusPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scPane.getVerticalScrollBar().setUnitIncrement(20);
        scPane.getHorizontalScrollBar().setUnitIncrement(20);
        scPane.setBorder(BorderFactory.createEmptyBorder());

        // Control the scroll
        scPane.getHorizontalScrollBar().addAdjustmentListener(e ->
        {
            // Repaints the panel each time the horizontal scroll bar is moves, in order to avoid ghosting.
            focusPanelContainer.revalidate();
            focusPanelContainer.repaint();
        });

        focusPanelContainer.add(navigationToolbar, BorderLayout.WEST);
        focusPanelContainer.add(scPane, BorderLayout.CENTER);

        JPanel pane = new JPanel(new MigLayout("fill, insets 0 0 0 0"));
        pane.setBorder(BorderFactory.createTitledBorder(new LineBorder(Color.BLACK), "Focus panel"));

        pane.add(focusPanelContainer, "grow");
        return pane;
    }

    @Override
    public String getDescription()
    {
        return getName();
    }

    @Override
    public KeyStroke getKeyStroke()
    {
        return KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.ALT_DOWN_MASK);
    }

    @Override
    public String getMenu()
    {

        return "Tools|" + TITLE;
    }

    @Override
    public String getName()
    {
        return TITLE + " (GUI)";
    }

    @Override
    public List<Triple<String, String, String>> getParameters()
    {
        return null;
    }

    @Override
    public int getPriority()
    {
        return Integer.MAX_VALUE;
    }

    @Override
    public NetPlan getDesign()
    {
        if (inOnlineSimulationMode()) return onlineSimulationPane.getSimKernel().getCurrentNetPlan();
        else return currentNetPlan;
    }

    @Override
    public NetPlan getInitialDesign()
    {
        if (inOnlineSimulationMode()) return onlineSimulationPane.getSimKernel().getInitialNetPlan();
        else return null;
    }

    @Override
    public void setCurrentNetPlanDoNotUpdateVisualization(NetPlan netPlan)
    {
        netPlan.checkCachesConsistency();
//        if (onlineSimulationPane != null) onlineSimulationPane.getSimKernel().setNetPlan(netPlan);
        currentNetPlan = netPlan;
//        netPlan.checkCachesConsistency();
    }

    private void resetButton()
    {
        try
        {
            final int result = JOptionPane.showConfirmDialog(null, "Are you sure you want to reset? This will remove all unsaved data", "Reset", JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) return;

            if (inOnlineSimulationMode())
            {
                switch (onlineSimulationPane.getSimKernel().getSimCore().getSimulationState())
                {
                    case SimState.NOT_STARTED:
                    case SimState.STOPPED:
                        break;
                    default:
                        onlineSimulationPane.getSimKernel().getSimCore().setSimulationState(SimState.STOPPED);
                        break;
                }
                onlineSimulationPane.getSimKernel().reset();
                setCurrentNetPlanDoNotUpdateVisualization(onlineSimulationPane.getSimKernel().getCurrentNetPlan());
            } else
            {
                setCurrentNetPlanDoNotUpdateVisualization(new NetPlan());
                //algorithmSelector.reset();
                executionPane.reset();
            }
//            reportSelector.reset();
//            reportContainer.removeAll();
        } catch (Throwable ex)
        {
            ErrorHandling.addErrorOrException(ex, GUINetworkDesign.class);
            ErrorHandling.showErrorDialog("Unable to reset");
        }
        Pair<BidiMap<NetworkLayer, Integer>, Map<NetworkLayer, Boolean>> res = VisualizationState.generateCanvasDefaultVisualizationLayerInfo(getDesign());
        vs.setCanvasLayerVisibilityAndOrder(getDesign(), res.getFirst(), res.getSecond());
        updateVisualizationAfterNewTopology();
        undoRedoManager.addNetPlanChange();
    }


    @Override
    public void resetPickedStateAndUpdateView()
    {
        vs.resetPickedState();
        topologyPanel.getCanvas().resetPickedStateAndRefresh();
        viewEditTopTables.getNetPlanViewTable().get(NetworkElementType.DEMAND).clearSelection();
        viewEditTopTables.getNetPlanViewTable().get(NetworkElementType.MULTICAST_DEMAND).clearSelection();
        viewEditTopTables.getNetPlanViewTable().get(NetworkElementType.FORWARDING_RULE).clearSelection();
        viewEditTopTables.getNetPlanViewTable().get(NetworkElementType.LINK).clearSelection();
        viewEditTopTables.getNetPlanViewTable().get(NetworkElementType.NODE).clearSelection();
        viewEditTopTables.getNetPlanViewTable().get(NetworkElementType.MULTICAST_TREE).clearSelection();
        viewEditTopTables.getNetPlanViewTable().get(NetworkElementType.SRG).clearSelection();
        viewEditTopTables.getNetPlanViewTable().get(NetworkElementType.RESOURCE).clearSelection();
    }

    /**
     * Shows the tab corresponding associated to a network element.
     *
     * @param type   Network element type
     * @param itemId Item identifier (if null, it will just show the tab)
     * @since 0.3.0
     */
    @SuppressWarnings("unchecked")
    private void selectNetPlanViewItem(NetworkElementType type, Object itemId)
    {
        NetworkLayer elementLayer = null;
        if (type.equals(NetworkElementType.LINK)) elementLayer = getDesign().getLinkFromId((long) itemId).getLayer();
        else if (type.equals(NetworkElementType.DEMAND))
            elementLayer = getDesign().getDemandFromId((long) itemId).getLayer();
        else if (type.equals(NetworkElementType.FORWARDING_RULE))
            elementLayer = getDesign().getDemand(((Pair<Integer, Integer>) itemId).getFirst()).getLayer();
        else if (type.equals(NetworkElementType.MULTICAST_DEMAND))
            elementLayer = getDesign().getMulticastDemandFromId((long) itemId).getLayer();
        else if (type.equals(NetworkElementType.MULTICAST_TREE))
            elementLayer = getDesign().getMulticastTreeFromId((long) itemId).getLayer();
        else if (type.equals(NetworkElementType.ROUTE))
            elementLayer = getDesign().getRouteFromId((long) itemId).getLayer();
        if (elementLayer != null)
            if (elementLayer != getDesign().getNetworkLayerDefault())
            {
                getDesign().setNetworkLayerDefault(elementLayer);
                viewEditTopTables.updateView();
            }
        topologyPanel.updateMultilayerVisibilityAndOrderPanel();
        viewEditTopTables.selectViewItem(type, itemId);
    }

    /**
     * Indicates whether or not the initial {@code NetPlan} object is stored to be
     * compared with the current one (i.e. after some simulation steps).
     *
     * @return {@code true} if the initial {@code NetPlan} object is stored. Otherwise, {@code false}.
     * @since 0.3.0
     */
    public boolean inOnlineSimulationMode()
    {
        if (onlineSimulationPane == null) return false;
        final SimState simState = onlineSimulationPane.getSimKernel().getSimCore().getSimulationState();
        if (simState == SimState.PAUSED || simState == SimState.RUNNING || simState == SimState.STEP)
            return true;
        else return false;
    }

    private void addAllKeyCombinationActions()
    {
        addKeyCombinationAction("Resets the tool", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                resetButton();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK));

        addKeyCombinationAction("Outputs current design to console", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                System.out.println(getDesign().toString());
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F11, InputEvent.CTRL_DOWN_MASK));

        /* FROM THE OFFLINE ALGORITHM EXECUTION */

        addKeyCombinationAction("Execute algorithm", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                executionPane.doClickInExecutionButton();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK));

        /* From the TOPOLOGY PANEL */
        addKeyCombinationAction("Load design", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                topologyPanel.loadDesign();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));

        addKeyCombinationAction("Save design", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                topologyPanel.saveDesign();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));

        addKeyCombinationAction("Zoom in", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (topologyPanel.getSize().getWidth() != 0 && topologyPanel.getSize().getHeight() != 0)
                    topologyPanel.getCanvas().zoomIn();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK), KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, InputEvent.CTRL_DOWN_MASK));

        addKeyCombinationAction("Zoom out", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (topologyPanel.getSize().getWidth() != 0 && topologyPanel.getSize().getHeight() != 0)
                    topologyPanel.getCanvas().zoomOut();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK), KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));

        addKeyCombinationAction("Zoom all", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (topologyPanel.getSize().getWidth() != 0 && topologyPanel.getSize().getHeight() != 0)
                    topologyPanel.getCanvas().zoomAll();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_MULTIPLY, InputEvent.CTRL_DOWN_MASK));

        addKeyCombinationAction("Take snapshot", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                topologyPanel.takeSnapshot();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_F12, InputEvent.CTRL_DOWN_MASK));

        addKeyCombinationAction("Load traffic demands", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                topologyPanel.loadTrafficDemands();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK));
        
        /* FROM REPORT */
        addKeyCombinationAction("Close selected report", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                int tab = reportPane.getReportContainer().getSelectedIndex();
                if (tab == -1) return;
                reportPane.getReportContainer().remove(tab);
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK));

        addKeyCombinationAction("Close all reports", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                reportPane.getReportContainer().removeAll();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));


        /* Online simulation */
        addKeyCombinationAction("Run simulation", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                try
                {
                    if (onlineSimulationPane.isRunButtonEnabled()) onlineSimulationPane.runSimulation(false);
                } catch (Net2PlanException ex)
                {
                    if (ErrorHandling.isDebugEnabled())
                        ErrorHandling.addErrorOrException(ex, OnlineSimulationPane.class);
                    ErrorHandling.showErrorDialog(ex.getMessage(), "Error executing simulation");
                } catch (Throwable ex)
                {
                    ErrorHandling.addErrorOrException(ex, OnlineSimulationPane.class);
                    ErrorHandling.showErrorDialog("An error happened");
                }

            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK));

        // Windows
        addKeyCombinationAction("Show control window", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                WindowController.showTablesWindow(true);
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_1, ActionEvent.ALT_MASK + ActionEvent.SHIFT_MASK));

        viewEditTopTables.setInputMap(WHEN_IN_FOCUSED_WINDOW, this.getInputMap(WHEN_IN_FOCUSED_WINDOW));
        viewEditTopTables.setActionMap(this.getActionMap());

        reportPane.setInputMap(WHEN_IN_FOCUSED_WINDOW, this.getInputMap(WHEN_IN_FOCUSED_WINDOW));
        reportPane.setActionMap(this.getActionMap());

        executionPane.setInputMap(WHEN_IN_FOCUSED_WINDOW, this.getInputMap(WHEN_IN_FOCUSED_WINDOW));
        executionPane.setActionMap(this.getActionMap());

        onlineSimulationPane.setInputMap(WHEN_IN_FOCUSED_WINDOW, this.getInputMap(WHEN_IN_FOCUSED_WINDOW));
        onlineSimulationPane.setActionMap(this.getActionMap());

        whatIfAnalysisPane.setInputMap(WHEN_IN_FOCUSED_WINDOW, this.getInputMap(WHEN_IN_FOCUSED_WINDOW));
        whatIfAnalysisPane.setActionMap(this.getActionMap());
    }

    @Override
    public VisualizationState getVisualizationState()
    {
        return vs;
    }


    @Override
    public void updateVisualizationAfterPick()
    {
        if (vs.getPickedElementType() != null) // can be null if picked a resource type
        {
            if (vs.getPickedNetworkElement() != null)
                selectNetPlanViewItem(vs.getPickedElementType(), vs.getPickedNetworkElement().getId());
            else
            {
                final Pair<Demand, Link> fr = vs.getPickedForwardingRule();
                selectNetPlanViewItem(vs.getPickedElementType(), Pair.of(fr.getFirst().getIndex(), fr.getSecond().getIndex()));
            }
        }
        topologyPanel.getCanvas().refresh(); // needed with or w.o. pick, since maybe you unpick with an undo
        focusPanel.updateView();
    }

    @Override
    public void putTransientColorInElementTopologyCanvas(Collection<? extends NetworkElement> linksAndNodes, Color color)
    {
        for (NetworkElement e : linksAndNodes)
        {
            if (e instanceof Link)
            {
                final GUILink gl = vs.getCanvasAssociatedGUILink((Link) e);
                if (gl != null)
                {
                    gl.setArrowDrawPaint(color);
                    gl.setArrowFillPaint(color);
                    gl.setEdgeDrawPaint(color);
                }
            } else if (e instanceof Node)
            {
                for (GUINode gn : vs.getCanvasVerticallyStackedGUINodes((Node) e))
                {
                    gn.setDrawPaint(color);
                    gn.setFillPaint(color);
                }
            } else throw new RuntimeException();
        }

        resetPickedStateAndUpdateView();
    }

    @Override
    public void updateVisualizationAfterNewTopology()
    {
        topologyPanel.updateMultilayerVisibilityAndOrderPanel();
        topologyPanel.getCanvas().rebuildCanvasGraphAndRefresh();
        topologyPanel.getCanvas().zoomAll();
        viewEditTopTables.updateView();
        focusPanel.updateView();
    }

    @Override
    public void updateVisualizationJustCanvasLinkNodeVisibilityOrColor()
    {
        topologyPanel.getCanvas().refresh();
    }

    @Override
    public void updateVisualizationAfterChanges(Set<NetworkElementType> modificationsMade)
    {
        if (modificationsMade == null)
        {
            throw new RuntimeException("Unable to update non-existent network elements");
        }

        if (modificationsMade.contains(NetworkElementType.LAYER))
        {
            topologyPanel.updateMultilayerVisibilityAndOrderPanel();
            topologyPanel.getCanvas().rebuildCanvasGraphAndRefresh();
            viewEditTopTables.updateView();
            focusPanel.updateView();
        } else if ((modificationsMade.contains(NetworkElementType.LINK) || modificationsMade.contains(NetworkElementType.NODE) || modificationsMade.contains(NetworkElementType.LAYER)))
        {
            topologyPanel.getCanvas().rebuildCanvasGraphAndRefresh();
            viewEditTopTables.updateView();
            focusPanel.updateView();
        } else
        {
            viewEditTopTables.updateView();
            focusPanel.updateView();
        }
    }

//    public void updateWarnings()
//    {
//        Map<String, String> net2planParameters = Configuration.getNet2PlanOptions();
//        List<String> warnings = NetworkPerformanceMetrics.checkNetworkState(getDesign(), net2planParameters);
//        String warningMsg = warnings.isEmpty() ? "Design is successfully completed!" : StringUtils.join(warnings, StringUtils.getLineSeparator());
//        txt_netPlanLog.setText(null);
//        txt_netPlanLog.setText(warningMsg);
//        txt_netPlanLog.setCaretPosition(0);
//    }

    @Override
    public void updateVisualizationJustTables()
    {
        viewEditTopTables.updateView();
    }

    @Override
    public void moveNodeTo(final GUINode guiNode, final Point2D toPoint)
    {
        if (!vs.isNetPlanEditable()) throw new UnsupportedOperationException("NetPlan is not editable");

        final ITopologyCanvas canvas = topologyPanel.getCanvas();
        final Node node = guiNode.getAssociatedNetPlanNode();

        final Point2D netPlanPoint = canvas.getCanvasPointFromMovement(toPoint);
        if (netPlanPoint == null) return;

        final Point2D jungPoint = canvas.getCanvasPointFromNetPlanPoint(toPoint);

        node.setXYPositionMap(netPlanPoint);

        viewEditTopTables.updateView();

        // Updating GUINodes position having in mind the selected layer.
        final List<GUINode> guiNodes = vs.getCanvasVerticallyStackedGUINodes(node);
        final int selectedLayerVisualizationOrder = vs.getCanvasVisualizationOrderRemovingNonVisible(guiNode.getLayer());

        for (GUINode stackedGUINode : guiNodes)
        {
            final int vlIndex = vs.getCanvasVisualizationOrderRemovingNonVisible(stackedGUINode.getLayer());
            final double interLayerDistanceInNpCoord = canvas.getInterLayerDistanceInNpCoordinates();

            if (vlIndex > selectedLayerVisualizationOrder)
            {
                final int layerDistance = vlIndex - selectedLayerVisualizationOrder;
                canvas.moveVertexToXYPosition(stackedGUINode, new Point2D.Double(jungPoint.getX(), -(jungPoint.getY() + (layerDistance * interLayerDistanceInNpCoord))));
            } else if (vlIndex == selectedLayerVisualizationOrder)
            {
                canvas.moveVertexToXYPosition(stackedGUINode, new Point2D.Double(jungPoint.getX(), -(jungPoint.getY())));
            } else
            {
                final int layerDistance = selectedLayerVisualizationOrder - vlIndex;
                canvas.moveVertexToXYPosition(stackedGUINode, new Point2D.Double(jungPoint.getX(), -(jungPoint.getY() - (layerDistance * interLayerDistanceInNpCoord))));
            }
        }

        canvas.refresh();
    }

    @Override
    public void runCanvasOperation(ITopologyCanvas.CanvasOperation... canvasOperation)
    {
        // NOTE: The operations should executed in the same order as their are brought.
        for (ITopologyCanvas.CanvasOperation operation : canvasOperation)
        {
            switch (operation)
            {
                case ZOOM_ALL:
                    topologyPanel.getCanvas().zoomAll();
                    break;
                case ZOOM_IN:
                    topologyPanel.getCanvas().zoomIn();
                    break;
                case ZOOM_OUT:
                    topologyPanel.getCanvas().zoomOut();
                    break;
            }
        }
    }
}
