package org.opendaylight.controller.multipath;


import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.opendaylight.controller.hosttracker.IfIptoHost;
import org.opendaylight.controller.hosttracker.hostAware.HostNodeConnector;
import org.opendaylight.controller.sal.core.Edge;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.core.Property;
import org.opendaylight.controller.sal.reader.NodeConnectorStatistics;
import org.opendaylight.controller.sal.utils.ServiceHelper;
import org.opendaylight.controller.statisticsmanager.IStatisticsManager;
import org.opendaylight.controller.topologymanager.ITopologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CalculateDataRates implements Runnable {

    protected static Logger log = LoggerFactory.getLogger(CalculateDataRates.class);
    /** Last timestamp of Executor run */
    protected long lastDataRateTimestamp = System.currentTimeMillis();
    /** The interval for the Executor, in TimeUnit.SECONDS */
    protected final int DATARATE_CALCULATOR_INTERVAL = 60;
    /** The map that maintains up to date link data rate */
    protected ConcurrentHashMap<Edge, Double> linkDataRate = null;
    /** The map that maintains up to date link Bytes transferred data */
    protected ConcurrentHashMap<Edge, Long> linkBytesTransferred = null;

    public void init() {
        linkDataRate = new ConcurrentHashMap<Edge, Double>();
        linkBytesTransferred = new ConcurrentHashMap<Edge, Long>();
    }

    public void run() {

        long thisDataRateTimestamp = System.currentTimeMillis();

        try {

            //log.info("CalculateDataRates running");

            ITopologyManager topologyManager = (ITopologyManager) ServiceHelper
                    .getGlobalInstance(ITopologyManager.class, this);

            IStatisticsManager statisticsManager = (IStatisticsManager) ServiceHelper
                    .getGlobalInstance(IStatisticsManager.class, this);

            if(topologyManager == null || statisticsManager == null) {
                log.error("CalculateDataRates: topology or statistics Manager is null!");
                return;
            }
            if(linkDataRate == null || linkBytesTransferred == null) {
                log.error("CalculateDataRates: linkDataRate or linkBytesTransferred maps are null");
                return;
            }
            Map<Edge, Set<Property>> edgeTopology = topologyManager.getEdges();

            // Elapsed time in seconds
            double elapsedTime = 0.001 * (double) (thisDataRateTimestamp - lastDataRateTimestamp);
            Set<Edge> currentEdges = edgeTopology.keySet();

            for (Edge edge : currentEdges) {
                //log.info("Data rate calculator for edge {}", edge.toString());
                // For this edge, find the nodeconnector of the tail (the source
                // of the traffic)
                NodeConnector tailNodeConnector = edge.getTailNodeConnector();
                // Get the statistics for this NodeConnector
                NodeConnectorStatistics ncStats = statisticsManager
                        .getNodeConnectorStatistics(tailNodeConnector);
                if(ncStats == null) continue;
                // long receiveBytes = ncStats.getReceiveByteCount();
                long transmitBytes = ncStats.getTransmitByteCount();
                long totalBytes = transmitBytes;

                double dataRate = 0;
                if (linkBytesTransferred.containsKey(edge)
                        && linkDataRate.containsKey(edge)) {
                    // Already have a measurement for this edge
                    dataRate = (totalBytes - linkBytesTransferred.get(edge))
                            / elapsedTime;
                }
                linkBytesTransferred.put(edge, totalBytes);
                linkDataRate.put(edge, dataRate);
            }
            //log.info("CalculateDataRates finished calculating {} edges",currentEdges.size());

            IfIptoHost hostTracker = (IfIptoHost) ServiceHelper
                    .getGlobalInstance(IfIptoHost.class, this);

            if (hostTracker != null) {
                Set<HostNodeConnector> allHosts = hostTracker.getAllHosts();

                String allHostIPSwitches = "";
                for(HostNodeConnector hnc: allHosts) {
                    allHostIPSwitches += hnc.getNetworkAddressAsString() + " at " + hnc.getnodeconnectorNode() + ", ";
                }
                log.info("hostTracker reports there are {} hosts: {}", allHosts.size(), allHostIPSwitches);
            }

        } catch (Exception e) {
            log.warn("CalculateDataRates exception {}", e);
        }

        lastDataRateTimestamp = thisDataRateTimestamp;
    }

    public Map<Edge,Double> getEdgeDataRates() {
        return linkDataRate;
    }
}



